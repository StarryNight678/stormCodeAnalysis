# Storm 源码分析

storm版本0.9

## 1总体代码框架



## 2搭建storm集群

## 3storm编程基础

## 4基础函数和工具类

## 5通信机制

ZMQ进行进程间通信

Disruptor进行线程间通信

## 6Nimbus

数据结构P85

StormBase 拓扑信息

assignment 当前任务分配情况

supervisorInfo 定义了supervisor本身的信息

## nimbus-data

```
(defn nimbus-data [conf inimbus]
  (let [forced-scheduler (.getForcedScheduler inimbus)]
    你{:conf conf
     :inimbus inimbus
     :submitted-count (atom 0)
     :storm-cluster-state (cluster/mk-storm-cluster-state conf)
     :submit-lock (Object.)
     :heartbeats-cache (atom {})
     :downloaders (file-cache-map conf)
     :uploaders (file-cache-map conf)
     :uptime (uptime-computer)
     :validator (new-instance (conf NIMBUS-TOPOLOGY-VALIDATOR))
     :timer (mk-timer :kill-fn (fn [t]
                                 (log-error t "Error when processing event")
                                 (halt-process! 20 "Error when processing an event")
                                 ))
     :scheduler (mk-scheduler conf inimbus)
     }))
```

## state-transitions状态转移

当现在状态是active,时rebalance会触发rebalance-transition

```
(defn state-transitions [nimbus storm-id status]
  {:active {:inactivate :inactive            
            :activate nil
            :rebalance (rebalance-transition nimbus storm-id status)
            :kill (kill-transition nimbus storm-id)
            }
   :rebalancing {:startup (fn [] (delay-event nimbus
                                              storm-id
                                              (:delay-secs status)
                                              :do-rebalance)
                                 nil)
                 :kill (kill-transition nimbus storm-id)
                 :do-rebalance (fn []
                                 (do-rebalance nimbus storm-id status)
                                 (:old-status status))
                 }})
```

## delay-event

```
(defn delay-event [nimbus storm-id delay-secs event]
  (log-message "Delaying event " event " for " delay-secs " secs for " storm-id)
  (schedule (:timer nimbus)
            delay-secs
            #(transition! nimbus storm-id event false)
            ))
```

## schedule

```
;storm/timer.clj P60
65  (defnk schedule [timer delay-secs afn :check-active true]
66    (when check-active (check-active! timer))
67    (let [id (uuid)
68          ^PriorityQueue queue (:queue timer)]
69      (locking (:lock timer)
70        (.add queue [(+ (current-time-millis) (* 1000 (long delay-secs))) afn id])
71        )))
```

## kill-transition

```
(defn kill-transition [nimbus storm-id]
  (fn [kill-time]
    (let [delay (if kill-time
                  kill-time
                  (get (read-storm-conf (:conf nimbus) storm-id)
                       TOPOLOGY-MESSAGE-TIMEOUT-SECS))]
      (delay-event nimbus
                   storm-id
                   delay
                   :remove)
      {:type :killed
       :kill-time-secs delay})
    ))
```

## rebalance-transition 和 do-rebalance
```
(defn rebalance-transition [nimbus storm-id status]
  (fn [time num-workers executor-overrides]
    (let [delay (if time
                  time
                  (get (read-storm-conf (:conf nimbus) storm-id)
                       TOPOLOGY-MESSAGE-TIMEOUT-SECS))]
      (delay-event nimbus
                   storm-id
                   delay
                   :do-rebalance)
      {:type :rebalancing
       :delay-secs delay
       :old-status status
       :num-workers num-workers
       :executor-overrides executor-overrides
       })))

(defn do-rebalance [nimbus storm-id status]
  (.update-storm! (:storm-cluster-state nimbus)
                  storm-id
                  (assoc-non-nil
                    {:component->executors (:executor-overrides status)}
                    :num-workers
                    (:num-workers status)))
  (mk-assignments nimbus :scratch-topology-id storm-id))

```

## **注意**

do-rebalance

调用 (mk-assignments nimbus :scratch-topology-id storm-id)) 进行新的任务分配

可以进行修改,调度


## 启动Nimbus服务

launch-server!是Nimbus服务入口.定义了核心的处理逻辑.

```java
(defn launch-server! [conf nimbus]
  (validate-distributed-mode! conf)
  (let [service-handler (service-handler conf nimbus)
        options (-> (TNonblockingServerSocket. (int (conf NIMBUS-THRIFT-PORT)))
                    (THsHaServer$Args.)
                    (.workerThreads 64)
                    (.protocolFactory (TBinaryProtocol$Factory.))
                    (.processor (Nimbus$Processor. service-handler))
                    )
       server (THsHaServer. options)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.shutdown service-handler) (.stop server))))
    (log-message "Starting Nimbus server...")
    (.serve server)))
```

service-handler是真正处理请求的地方

```java
;daemon/nimbus.clj:881:(defserverfn service-handler [conf inimbus]

```

提交拓扑

daemon/nimbus.clj:906:      (^void submitTopologyWithOpts

```java
  (^void submitTopologyWithOpts
        [this ^String storm-name ^String uploadedJarLocation ^String serializedConf ^StormTopology topology
         ^SubmitOptions submitOptions]
        (try
          (assert (not-nil? submitOptions))
          (validate-topology-name! storm-name)
          (check-storm-active! nimbus storm-name false)
          (.validate ^backtype.storm.nimbus.ITopologyValidator (:validator nimbus)
                     storm-name
                     (from-json serializedConf)
                     topology)
          (swap! (:submitted-count nimbus) inc)
          (let [storm-id (str storm-name "-" @(:submitted-count nimbus) "-" (current-time-secs))
                storm-conf (normalize-conf
                            conf
                            (-> serializedConf
                                from-json
                                (assoc STORM-ID storm-id)
                              (assoc TOPOLOGY-NAME storm-name))
                            topology)
                total-storm-conf (merge conf storm-conf)
                topology (normalize-topology total-storm-conf topology)
                topology (if (total-storm-conf TOPOLOGY-OPTIMIZE)
                           (optimize-topology topology)
                           topology)
                storm-cluster-state (:storm-cluster-state nimbus)]
            (system-topology! total-storm-conf topology) ;; this validates the structure of the topology
            (log-message "Received topology submission for " storm-name " with conf " storm-conf)
            ;; lock protects against multiple topologies being submitted at once and
            ;; cleanup thread killing topology in b/w assignment and starting the topology
            (locking (:submit-lock nimbus)
              (setup-storm-code conf storm-id uploadedJarLocation storm-conf topology)
              (.setup-heartbeats! storm-cluster-state storm-id)
              (let [thrift-status->kw-status {TopologyInitialStatus/INACTIVE :inactive
                                              TopologyInitialStatus/ACTIVE :active}]
                (start-storm nimbus storm-name storm-id (thrift-status->kw-status (.get_initial_status submitOptions))))
              (mk-assignments nimbus)))
          (catch Throwable e
            (log-warn-error e "Topology submission exception. (topology name='" storm-name "')")
            (throw e))))
      
      (^void submitTopology
        [this ^String storm-name ^String uploadedJarLocation ^String serializedConf ^StormTopology topology]
        (.submitTopologyWithOpts this storm-name uploadedJarLocation serializedConf topology
                                 (SubmitOptions. TopologyInitialStatus/ACTIVE)))
```

主要函数submitTopologyWithOpts和 submitTopology

提交时调用(mk-assignments nimbus)进行任务分配.


```java
      (^void submitTopologyWithOpts
        [this ^String storm-name ^String uploadedJarLocation ^String serializedConf ^StormTopology topology
         ^SubmitOptions submitOptions]
        (try
          (assert (not-nil? submitOptions))
          (validate-topology-name! storm-name)
          (check-storm-active! nimbus storm-name false)
          (.validate ^backtype.storm.nimbus.ITopologyValidator (:validator nimbus)
                     storm-name
                     (from-json serializedConf)
                     topology)
          (swap! (:submitted-count nimbus) inc)
          (let [storm-id (str storm-name "-" @(:submitted-count nimbus) "-" (current-time-secs))
                storm-conf (normalize-conf
                            conf
                            (-> serializedConf
                                from-json
                                (assoc STORM-ID storm-id)
                              (assoc TOPOLOGY-NAME storm-name))
                            topology)
                total-storm-conf (merge conf storm-conf)
                topology (normalize-topology total-storm-conf topology)
                topology (if (total-storm-conf TOPOLOGY-OPTIMIZE)
                           (optimize-topology topology)
                           topology)
                storm-cluster-state (:storm-cluster-state nimbus)]
            (system-topology! total-storm-conf topology) ;; this validates the structure of the topology
            (log-message "Received topology submission for " storm-name " with conf " storm-conf)
            ;; lock protects against multiple topologies being submitted at once and
            ;; cleanup thread killing topology in b/w assignment and starting the topology
            (locking (:submit-lock nimbus)
              (setup-storm-code conf storm-id uploadedJarLocation storm-conf topology)
              (.setup-heartbeats! storm-cluster-state storm-id)
              (let [thrift-status->kw-status {TopologyInitialStatus/INACTIVE :inactive
                                              TopologyInitialStatus/ACTIVE :active}]
                (start-storm nimbus storm-name storm-id (thrift-status->kw-status (.get_initial_status submitOptions))))
              (mk-assignments nimbus)  ))
          (catch Throwable e
            (log-warn-error e "Topology submission exception. (topology name='" storm-name "')")
            (throw e))))
```


##  kill rebalance actice 和 deactive方法上的实现.

依赖于transition-name

```java
    (^void killTopology [this ^String name]
        (.killTopologyWithOpts this name (KillOptions.)))

      (^void killTopologyWithOpts [this ^String storm-name ^KillOptions options]
        (check-storm-active! nimbus storm-name true)
        (let [wait-amt (if (.is_set_wait_secs options)
                         (.get_wait_secs options)                         
                         )]
          (transition-name! nimbus storm-name [:kill wait-amt] true)
          ))
```


```java
(^void rebalance [this ^String storm-name ^RebalanceOptions options]
        (check-storm-active! nimbus storm-name true)
        (let [wait-amt (if (.is_set_wait_secs options)
                         (.get_wait_secs options))
              num-workers (if (.is_set_num_workers options)
                            (.get_num_workers options))
              executor-overrides (if (.is_set_num_executors options)
                                   (.get_num_executors options)
                                   {})]
          (doseq [[c num-executors] executor-overrides]
            (when (<= num-executors 0)
              (throw (InvalidTopologyException. "Number of executors must be greater than 0"))
              ))
          (transition-name! nimbus storm-name [:rebalance wait-amt num-workers executor-overrides] true)
          ))
```

文件上传 beginFileUpload,文件下载beginFileDownload.

## 获取UI的信息

getClusterInfo 获取当前集群的统计信息

```java
      (^ClusterSummary getClusterInfo [this]
        (let [storm-cluster-state (:storm-cluster-state nimbus)
              supervisor-infos (all-supervisor-info storm-cluster-state)
              ;; TODO: need to get the port info about supervisors...
              ;; in standalone just look at metadata, otherwise just say N/A?
              supervisor-summaries (dofor [[id info] supervisor-infos]
                                          (let [ports (set (:meta info)) ;;TODO: this is only true for standalone
                                                ]
                                            (SupervisorSummary. (:hostname info)
                                                                (:uptime-secs info)
                                                                (count ports)
                                                                (count (:used-ports info))
                                                                id )
                                            ))
              nimbus-uptime ((:uptime nimbus))
              bases (topology-bases storm-cluster-state)
              topology-summaries (dofor [[id base] bases]
                                        (let [assignment (.assignment-info storm-cluster-state id nil)]
                                          (TopologySummary. id
                                                            (:storm-name base)
                                                            (->> (:executor->node+port assignment)
                                                                 keys
                                                                 (mapcat executor-id->tasks)
                                                                 count) 
                                                            (->> (:executor->node+port assignment)
                                                                 keys
                                                                 count)                                                            
                                                            (->> (:executor->node+port assignment)
                                                                 vals
                                                                 set
                                                                 count)
                                                            (time-delta (:launch-time-secs base))
                                                            (extract-status-str base))
                                          ))]
          (ClusterSummary. supervisor-summaries
                           nimbus-uptime
                           topology-summaries)
          ))
```

## compute-new-topology

函数名字 **compute-new-topology->executor->node+port**

(defn compute-new-topology->executor->node+port [nimbus existing-assignments topologies scratch-topology-id]
 

找到每个任务对应的分配情况

<topology-id <executor [node,port]>>

几个函数
```java
405  (defn- compute-executors [nimbus storm-id]
421  (defn- compute-executor->component [nimbus storm-id]
433  (defn- compute-topology->executors [nimbus storm-ids]
438  (defn- compute-topology->alive-executors [nimbus existing-assignments topologies topology->executors scratch-topology-id]
448  (defn- compute-supervisor->dead-ports [nimbus existing-assignments topology->executors topology->alive-executors]
463  (defn- compute-topology->scheduler-assignment [nimbus existing-assignments topology->alive-executors]
499  (defn- compute-topology->executor->node+port [scheduler-assignments]
537  (defn compute-new-topology->executor->node+port [nimbus existing-assignments topologies scratch-topology-id]
jason@jason-HP:~/Desktop/storm-0.9.0/storm-core/src/clj/backtype/storm$ 
```




## 7Scheduler

## IScheduler借口
 
```java
package backtype.storm.scheduler;

import java.util.Map;


public interface IScheduler {
    
    void prepare(Map conf);
    
    /**
     * Set assignments for the topologies which needs scheduling. The new assignments is available 
     * through <code>cluster.getAssignments()</code>
     *
     *@param topologies all the topologies in the cluster, some of them need schedule. Topologies object here 
     *       only contain static information about topologies. Information like assignments, slots are all in
     *       the <code>cluster</code>object.
     *@param cluster the cluster these topologies are running in. <code>cluster</code> contains everything user
     *       need to develop a new scheduling logic. e.g. supervisors information, available slots, current 
     *       assignments for all the topologies etc. User can set the new assignment for topologies using
     *       <code>cluster.setAssignmentById</code>
     */
    void schedule(Topologies topologies, Cluster cluster);
}
```

prepare方法,接收nimbus参数进行初始化工作

schedule进行任务分配

Topologies拓扑信息,Cluster 集群信息。



## default-schedule 默认调度器

```java
(ns backtype.storm.scheduler.DefaultScheduler
  (:use [backtype.storm util config])
  (:require [backtype.storm.scheduler.EvenScheduler :as EvenScheduler])
  (:import [backtype.storm.scheduler IScheduler Topologies
            Cluster TopologyDetails WorkerSlot SchedulerAssignment
            EvenScheduler ExecutorDetails])
  (:gen-class
    :implements [backtype.storm.scheduler.IScheduler]))

(defn- bad-slots [existing-slots num-executors num-workers]
  (if (= 0 num-workers)
    '()
    (let [distribution (atom (integer-divided num-executors num-workers))
          keepers (atom {})]
      (doseq [[node+port executor-list] existing-slots :let [executor-count (count executor-list)]]
        (when (pos? (get @distribution executor-count 0))
          (swap! keepers assoc node+port executor-list)
          (swap! distribution update-in [executor-count] dec)
          ))
      (->> @keepers
           keys
           (apply dissoc existing-slots)
           keys
           (map (fn [[node port]]
                  (WorkerSlot. node port)))))))

(defn slots-can-reassign [^Cluster cluster slots]
  (->> slots
      (filter
        (fn [[node port]]
          (if-not (.isBlackListed cluster node)
            (if-let [supervisor (.getSupervisorById cluster node)]
              (.contains (.getAllPorts supervisor) (int port))
              ))))))

(defn -prepare [this conf]
  )

(defn default-schedule [^Topologies topologies ^Cluster cluster]
  (let [needs-scheduling-topologies (.needsSchedulingTopologies cluster topologies)]
    (doseq [^TopologyDetails topology needs-scheduling-topologies
            :let [topology-id (.getId topology)
                  available-slots (->> (.getAvailableSlots cluster)
                                       (map #(vector (.getNodeId %) (.getPort %))))
                  all-executors (->> topology
                                     .getExecutors
                                     (map #(vector (.getStartTask %) (.getEndTask %)))
                                     set)
                  alive-assigned (EvenScheduler/get-alive-assigned-node+port->executors cluster topology-id)
                  alive-executors (->> alive-assigned vals (apply concat) set)
                  can-reassign-slots (slots-can-reassign cluster (keys alive-assigned))
                  total-slots-to-use (min (.getNumWorkers topology)
                                          (+ (count can-reassign-slots) (count available-slots)))
                  bad-slots (if (or (> total-slots-to-use (count alive-assigned)) 
                                    (not= alive-executors all-executors))
                                (bad-slots alive-assigned (count all-executors) total-slots-to-use)
                                [])]]
      (.freeSlots cluster bad-slots)
      (EvenScheduler/schedule-topologies-evenly (Topologies. {topology-id topology}) cluster))))

(defn -schedule [this ^Topologies topologies ^Cluster cluster]
  (default-schedule topologies cluster))

```



## 8Scheduler Supervisor

Supervisor常用方法和数据结构


standalone-supervisor定义了一些常用的方法

```java
daemon/supervisor.clj:478:(defn standalone-supervisor []

(defn standalone-supervisor []
  (let [conf-atom (atom nil)
        id-atom (atom nil)]
    (reify ISupervisor
      (prepare [this conf local-dir]
        (reset! conf-atom conf)
        (let [state (LocalState. local-dir)
              curr-id (if-let [id (.get state LS-ID)]
                        id
                        (generate-supervisor-id))]
          (.put state LS-ID curr-id)
          (reset! id-atom curr-id))
        )
      (confirmAssigned [this port]
        true)
      (getMetadata [this]
        (doall (map int (get @conf-atom SUPERVISOR-SLOTS-PORTS))))
      (getSupervisorId [this]
        @id-atom)
      (getAssignmentId [this]
        @id-atom)
      (killedWorker [this port]
        )
      (assigned [this ports]
        ))))
```

## supervisor-data

supervisor数据结构

```java
(defn supervisor-data [conf shared-context ^ISupervisor isupervisor]
  {:conf conf
   :shared-context shared-context
   :isupervisor isupervisor
   :active (atom true)
   :uptime (uptime-computer)
   :worker-thread-pids-atom (atom {})
   :storm-cluster-state (cluster/mk-storm-cluster-state conf)
   :local-state (supervisor-state conf)
   :supervisor-id (.getSupervisorId isupervisor)
   :assignment-id (.getAssignmentId isupervisor)
   :my-hostname (if (contains? conf STORM-LOCAL-HOSTNAME)
                  (conf STORM-LOCAL-HOSTNAME)
                  (local-hostname))
   :curr-assignment (atom nil) ;; used for reporting used ports when heartbeating
   :timer (mk-timer :kill-fn (fn [t]
                               (log-error t "Error when processing event")
                               (halt-process! 20 "Error when processing an event")
                               ))
   })
```



## 9Worker

## worker-data 数据

```java
(defn worker-data [conf mq-context storm-id assignment-id port worker-id]
  (let [cluster-state (cluster/mk-distributed-cluster-state conf)
        storm-cluster-state (cluster/mk-storm-cluster-state cluster-state)
        storm-conf (read-supervisor-storm-conf conf storm-id)
        executors (set (read-worker-executors storm-conf storm-cluster-state storm-id assignment-id port))
        transfer-queue (disruptor/disruptor-queue (storm-conf TOPOLOGY-TRANSFER-BUFFER-SIZE)
                                                  :wait-strategy (storm-conf TOPOLOGY-DISRUPTOR-WAIT-STRATEGY))
        executor-receive-queue-map (mk-receive-queue-map storm-conf executors)
        
        receive-queue-map (->> executor-receive-queue-map
                               (mapcat (fn [[e queue]] (for [t (executor-id->tasks e)] [t queue])))
                               (into {}))

        topology (read-supervisor-topology conf storm-id)]
    (recursive-map
      :conf conf
      :mq-context (if mq-context
                      mq-context
                      (TransportFactory/makeContext storm-conf))
      :storm-id storm-id
      :assignment-id assignment-id
      :port port
      :worker-id worker-id
      :cluster-state cluster-state
      :storm-cluster-state storm-cluster-state
      :storm-active-atom (atom false)
      :executors executors
      :task-ids (->> receive-queue-map keys (map int) sort)
      :storm-conf storm-conf
      :topology topology
      :system-topology (system-topology! storm-conf topology)
      :heartbeat-timer (mk-halting-timer)
      :refresh-connections-timer (mk-halting-timer)
      :refresh-active-timer (mk-halting-timer)
      :executor-heartbeat-timer (mk-halting-timer)
      :user-timer (mk-halting-timer)
      :task->component (HashMap. (storm-task-info topology storm-conf)) ; for optimized access when used in tasks later on
      :component->stream->fields (component->stream->fields (:system-topology <>))
      :component->sorted-tasks (->> (:task->component <>) reverse-map (map-val sort))
      :endpoint-socket-lock (mk-rw-lock)
      :cached-node+port->socket (atom {})
      :cached-task->node+port (atom {})
      :transfer-queue transfer-queue
      :executor-receive-queue-map executor-receive-queue-map
      :short-executor-receive-queue-map (map-key first executor-receive-queue-map)
      :task->short-executor (->> executors
                                 (mapcat (fn [e] (for [t (executor-id->tasks e)] [t (first e)])))
                                 (into {})
                                 (HashMap.))
      :suicide-fn (mk-suicide-fn conf)
      :uptime (uptime-computer)
      :default-shared-resources (mk-default-resources <>)
      :user-shared-resources (mk-user-resources <>)
      :transfer-local-fn (mk-transfer-local-fn <>)
      :transfer-fn (mk-transfer-fn <>)
      )))
```




## 10Executor


```java

```

```java

```


```java

```

```java

```

```java

```

不说了,我和涛哥脑袋瓦特了.

涛哥写c的编译出现问题.
我用了和HashMap最后发现不应该用map.



```java

```

## 11Task

## 12Storm Ack框架

## 13系统运行统计

## 14运行统计的另一种实现

## 15事务Topology实现

## 16事务Topology示例

## 17Trident Spout

## 18Tridnet 存储

## 19Trident消息

## 20Trident操作和处理节点

## 21Trident流的基本操作

## 22Tridnet流的交互操作

## 23Trident Bolt节点

## 24Trident的执行优化

## 25Trident与DRPC

## 26Trident的Topology构建器

## 27多语言

## 28storm配置项
