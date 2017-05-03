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


```java

```

```java

```

```java

```


```java

```


## 7Scheduler

## 8Scheduler

## 9Worker

## 10Executor

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
