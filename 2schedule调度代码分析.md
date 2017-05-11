# 调度代码分析


## 实现直接调度器

[storm-scheduler/DirectScheduler.java](https://github.com/linyiqun/storm-scheduler/blob/master/DirectScheduler.java)


```java
/**
 * 直接分配调度器,可以分配组件到指定节点中
 * Created by zhexuan on 15/7/6.
 */
public class DirectScheduler implements IScheduler{

    @Override
    public void prepare(Map conf) {

    }

    @Override
    public void schedule(Topologies topologies, Cluster cluster) {
        System.out.println("DirectScheduler: begin scheduling");
        // Gets the topology which we want to schedule
        Collection<TopologyDetails> topologyDetailes;
        TopologyDetails topology;
        //作业是否要指定分配的标识
        String assignedFlag;
        Map map;
        Iterator<String> iterator = null;

        topologyDetailes = topologies.getTopologies();
        for(TopologyDetails td: topologyDetailes){
            map = td.getConf();
            assignedFlag = (String)map.get("assigned_flag");

            //如何找到的拓扑逻辑的分配标为1则代表是要分配的,否则走系统的调度
            if(assignedFlag != null && assignedFlag.equals("1")){
                System.out.println("finding topology named " + td.getName());
                topologyAssign(cluster, td, map);
            }else {
                System.out.println("topology assigned is null");
            }
        }

        //其余的任务由系统自带的调度器执行
        new EvenScheduler().schedule(topologies, cluster);
    }
```

## 	IScheduler 接口
public interface IScheduler {
void schedule(Topologies topologies, Cluster cluster);


## Topologies topologies


| 类型 | 方法 |
| ---- | ---- |
Map<String,Map<String,Component>> | getAllComponents() 
TopologyDetails | getById(String topologyId) 
TopologyDetails | getByName(String topologyName) 
Collection<TopologyDetails> | getTopologies() 
String | toString()


## TopologyDetails

[TopologyDetails](http://storm.apache.org/releases/1.0.3/javadocs/org/apache/storm/scheduler/TopologyDetails.html)


获得组件 和 task 需要的资源

| 类型 | 方法 |
| ---- | ---- |
Map<String,Component> | getComponents()
Returns a representation of the non-system components of the topology graph Each Component object in the returning map is populated with the list of its parents, children and execs assigned to that component.
Map | getConf() 
Collection<ExecutorDetails> | getExecutors() 
Map<ExecutorDetails,String> | getExecutorToComponent() 
String | getId() 
int | getLaunchTime()
Get the timestamp of when this topology was launched
String | getName() 
int | getNumWorkers() 


## Class Component


包含的字段

| 类型 | 字段 |
| ---- | ---- |
Modifier and Type | Field and Description
List<String> | children 
List<ExecutorDetails> | execs 
String | id 
List<String> | parents 
Component.ComponentType | type 


## Class ExecutorDetails

| 类型 | 字段 |
| ---- | ---- |
boolean | equals(Object other) 
int | getEndTask() 
int | getStartTask() 
int | hashCode() 
String | toString() 

## Class StormTopology

Thrift 定义的内容

## Class Cluster

Class Cluster

java.lang.Object
org.apache.storm.scheduler.Cluster


得到任务分配方案

| 类型 | 字段 |
| ---- | ---- |
Map<String,SchedulerAssignment> | getAssignments()

Get all the assignments.


## 其他


```java
 List<WorkerSlot> lws=new ArrayList<>();
 lws=cluster.getAssignableSlots();
 for(int i=0;i<lws.size();i++){
     System.out.println("----"+lws.get(i).getNodeId() + "  "+lws.get(i).getPort() );
 }
```java


结果

```java
MyJasonScheduler: begin scheduling
----138efdd0-6fe2-435c-b0c7-78c2e3820682  6800
----138efdd0-6fe2-435c-b0c7-78c2e3820682  6801
----138efdd0-6fe2-435c-b0c7-78c2e3820682  6802
----138efdd0-6fe2-435c-b0c7-78c2e3820682  6803
```


## 直接进行分配cluster.assign

```java
// 选取节点上第一个slot,进行分配
cluster.assign(availableSlots.get(0), topology.getId(), executors);

//获取任务分配方案
Map<String,SchedulerAssignment> getAssignments()
Get all the assignments.

String 是拓扑的id 例如:getId:word-count-1-1494232550


//设置新的任务分配
void    setAssignments(Map<String,SchedulerAssignment> newAssignments)
set assignments for cluster

```


```java
Map<ExecutorDetails,String> getExecutorToComponent() 

String  ExecutorDetails
sentence-spout  [4,4]
report-bolt  [3,3]
count-bolt  [2,2]
__acker  [1,1]
split-bolt  [5,5]
```


- 判断使用经过调度

```java
cluster.needsScheduling(t)
```


 - 获得conf内容

`String user = (String)td.getConf().get(Config.TOPOLOGY_SUBMITTER_USER);`

```java
  for (TopologyDetails td: topologies.getTopologies()) {
    String user = (String)td.getConf().get(Config.TOPOLOGY_SUBMITTER_USER);
    LOG.debug("Found top {} run by user {}",td.getId(), user);
    NodePool pool = userPools.get(user);
    if (pool == null || !pool.canAdd(td)) {
      pool = defaultPool;
    }
    pool.addTopology(td);
  }
```


```java

Map<String,List<ExecutorDetails>>   getNeedsSchedulingComponentToExecutors(TopologyDetails topology) 

Map<String, List<ExecutorDetails>> componentToExecutors = 
cluster.getNeedsSchedulingComponentToExecutors(topology);

```



```java
Class WorkerSlot


Constructors 
Constructor and Description
WorkerSlot(String nodeId, Number port) 
WorkerSlot(String nodeId, Number port, double memOnHeap, double memOffHeap, double cpu) 

boolean equals(Object o) 
double  getAllocatedCpu() 
double  getAllocatedMemOffHeap() 
double  getAllocatedMemOnHeap() 
String  getId() 
String  getNodeId() 
int getPort() 
int hashCode() 
String  toString() 
```

## clojure调度

```java
(defn schedule-topologies-evenly [^Topologies topologies ^Cluster cluster]
  (let [needs-scheduling-topologies (.needsSchedulingTopologies cluster topologies)]
    (doseq [^TopologyDetails topology needs-scheduling-topologies
            :let [topology-id (.getId topology)
                  new-assignment (schedule-topology topology cluster)
                  node+port->executors (reverse-map new-assignment)]]
      (doseq [[node+port executors] node+port->executors
              :let [^WorkerSlot slot (WorkerSlot. (first node+port) (last node+port))
                    executors (for [[start-task end-task] executors]
                                (ExecutorDetails. start-task end-task))]]
        (.assign cluster slot topology-id executors)))))
```

```java

```

## 调度代码分析

```java
(defn sort-slots [all-slots]
  (let [split-up (sort-by count > (vals (group-by first all-slots)))]
    (apply interleave-all split-up)
    ))
```

<node,port>

首先根据node进行分组


## get capacity latency


How to get Capacity & Latency shown on UI by Java Code



:5807,"capacity":"0.315"}]
刷新时间

:5825,"capacity":"0.316"

Storm UI REST API

## http://10.128.12.198:8080/api/v1/topology/summary

{"topologies":[{"assignedTotalMem":2496.0,"owner":"jason","requestedMemOnHeap":0.0,"encodedId":"word-count-1-1494386305","assignedMemOnHeap":2496.0,"id":"word-count-1-1494386305","uptime":"1d 9h 3m 27s","schedulerInfo":null,"name":"word-count","workersTotal":3,"status":"INACTIVE","requestedMemOffHeap":0.0,"tasksTotal":11,"requestedCpu":0.0,"replicationCount":1,"executorsTotal":11,"uptimeSeconds":119007,"assignedCpu":0.0,"assignedMemOffHeap":0.0,"requestedTotalMem":0.0}],"schedulerDisplayResource":false}



## http://10.128.12.198:8080/api/v1/topology/word-count-1-1494386305


{"assignedTotalMem":2496.0,"window":":all-time","owner":"jason","requestedMemOnHeap":0.0,"encodedId":"word-count-1-1494386305","windowHint":"All time","msgTimeout":30,"user":null,"samplingPct":10,"assignedMemOnHeap":2496.0,"id":"word-count-1-1494386305","configuration":{"topology.builtin.metrics.bucket.size.secs":60,"nimbus.childopts":"-Xmx1024m","ui.filter.params":null,"storm.cluster.mode":"distributed","storm.messaging.netty.client_worker_threads":1,"logviewer.max.per.worker.logs.size.mb":2048,"supervisor.run.worker.as.user":false,"topology.max.task.parallelism":null,"topology.priority":29,"zmq.threads":1,"storm.group.mapping.service":"org.apache.storm.security.auth.ShellBasedGroupsMapping","transactional.zookeeper.root":"\/transactional","topology.sleep.spout.wait.strategy.time.ms":1,"scheduler.display.resource":false,"topology.max.replication.wait.time.sec":60,"drpc.invocations.port":3773,"supervisor.localizer.cache.target.size.mb":10240,"topology.multilang.serializer":"org.apache.storm.multilang.JsonSerializer","storm.messaging.netty.server_worker_threads":1,"nimbus.blobstore.class":"org.apache.storm.blobstore.LocalFsBlobStore","resource.aware.scheduler.eviction.strategy":"org.apache.storm.scheduler.resource.strategies.eviction.DefaultEvictionStrategy","topology.max.error.report.per.interval":5,"storm.thrift.transport":"org.apache.storm.security.auth.SimpleTransportPlugin","zmq.hwm":0,"storm.group.mapping.service.params":null,"worker.profiler.enabled":false,"storm.principal.tolocal":"org.apache.storm.security.auth.DefaultPrincipalToLocal","supervisor.worker.shutdown.sleep.secs":3,"pacemaker.host":"localhost","storm.zookeeper.retry.times":5,"ui.actions.enabled":true,"zmq.linger.millis":5000,"supervisor.enable":true,"topology.stats.sample.rate":0.05,"storm.messaging.netty.min_wait_ms":100,"worker.log.level.reset.poll.secs":30,"storm.zookeeper.port":2181,"supervisor.heartbeat.frequency.secs":5,"topology.enable.message.timeouts":true,"supervisor.cpu.capacity":400.0,"drpc.worker.threads":64,"supervisor.blobstore.download.thread.count":5,"task.backpressure.poll.secs":30,"drpc.queue.size":128,"topology.backpressure.enable":false,"supervisor.blobstore.class":"org.apache.storm.blobstore.NimbusBlobStore","storm.blobstore.inputstream.buffer.size.bytes":65536,"topology.shellbolt.max.pending":100,"drpc.https.keystore.password":"","nimbus.code.sync.freq.secs":120,"logviewer.port":8000,"topology.scheduler.strategy":"org.apache.storm.scheduler.resource.strategies.scheduling.DefaultResourceAwareStrategy","topology.executor.send.buffer.size":1024,"resource.aware.scheduler.priority.strategy":"org.apache.storm.scheduler.resource.strategies.priority.DefaultSchedulingPriorityStrategy","pacemaker.auth.method":"NONE","storm.daemon.metrics.reporter.plugins":["org.apache.storm.daemon.metrics.reporters.JmxPreparableReporter"],"topology.worker.logwriter.childopts":"-Xmx64m","topology.spout.wait.strategy":"org.apache.storm.spout.SleepSpoutWaitStrategy","ui.host":"0.0.0.0","topology.submitter.principal":"","storm.nimbus.retry.interval.millis":2000,"nimbus.inbox.jar.expiration.secs":3600,"dev.zookeeper.path":"\/tmp\/dev-storm-zookeeper","topology.acker.executors":null,"topology.fall.back.on.java.serialization":true,"topology.eventlogger.executors":0,"supervisor.localizer.cleanup.interval.ms":600000,"storm.zookeeper.servers":["localhost"],"nimbus.thrift.threads":64,"logviewer.cleanup.age.mins":10080,"topology.worker.childopts":null,"topology.classpath":null,"supervisor.monitor.frequency.secs":3,"nimbus.credential.renewers.freq.secs":600,"topology.skip.missing.kryo.registrations":false,"drpc.authorizer.acl.filename":"drpc-auth-acl.yaml","pacemaker.kerberos.users":[],"storm.group.mapping.service.cache.duration.secs":120,"topology.testing.always.try.serialize":false,"nimbus.monitor.freq.secs":10,"storm.health.check.timeout.ms":5000,"supervisor.supervisors":[],"topology.tasks":null,"topology.bolts.outgoing.overflow.buffer.enable":false,"storm.messaging.netty.socket.backlog":500,"topology.workers":3,"pacemaker.base.threads":10,"storm.local.dir":"storm-local","worker.childopts":"-Xmx%HEAP-MEM%m -XX:+PrintGCDetails -Xloggc:artifacts\/gc.log -XX:+PrintGCDateStamps -XX:+PrintGCTimeStamps -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=1M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=artifacts\/heapdump","storm.auth.simple-white-list.users":[],"topology.disruptor.batch.timeout.millis":1,"topology.message.timeout.secs":30,"topology.state.synchronization.timeout.secs":60,"topology.tuple.serializer":"org.apache.storm.serialization.types.ListDelegateSerializer","supervisor.supervisors.commands":[],"nimbus.blobstore.expiration.secs":600,"logviewer.childopts":"-Xmx128m","topology.environment":null,"topology.debug":true,"topology.disruptor.batch.size":100,"storm.messaging.netty.max_retries":300,"ui.childopts":"-Xmx768m","storm.network.topography.plugin":"org.apache.storm.networktopography.DefaultRackDNSToSwitchMapping","storm.zookeeper.session.timeout":20000,"drpc.childopts":"-Xmx768m","drpc.http.creds.plugin":"org.apache.storm.security.auth.DefaultHttpCredentialsPlugin","storm.zookeeper.connection.timeout":15000,"storm.zookeeper.auth.user":null,"storm.meta.serialization.delegate":"org.apache.storm.serialization.GzipThriftSerializationDelegate","topology.max.spout.pending":null,"storm.codedistributor.class":"org.apache.storm.codedistributor.LocalFileSystemCodeDistributor","nimbus.supervisor.timeout.secs":60,"nimbus.task.timeout.secs":30,"storm.zookeeper.superACL":null,"drpc.port":3772,"pacemaker.max.threads":50,"storm.zookeeper.retry.intervalceiling.millis":30000,"nimbus.thrift.port":6627,"storm.auth.simple-acl.admins":[],"topology.component.cpu.pcore.percent":10.0,"supervisor.memory.capacity.mb":3072.0,"storm.nimbus.retry.times":5,"supervisor.worker.start.timeout.secs":120,"storm.zookeeper.retry.interval":1000,"logs.users":null,"worker.profiler.command":"flight.bash","storm.scheduler":"org.apache.storm.scheduler.resource.MyJasonScheduler","transactional.zookeeper.port":null,"drpc.max_buffer_size":1048576,"pacemaker.thread.timeout":10,"task.credentials.poll.secs":30,"drpc.https.keystore.type":"JKS","topology.worker.receiver.thread.count":1,"topology.state.checkpoint.interval.ms":1000,"supervisor.slots.ports":[6800,6801,6802,6803],"topology.transfer.buffer.size":1024,"storm.health.check.dir":"healthchecks","topology.worker.shared.thread.pool.size":4,"drpc.authorizer.acl.strict":false,"nimbus.file.copy.expiration.secs":600,"worker.profiler.childopts":"-XX:+UnlockCommercialFeatures -XX:+FlightRecorder","topology.executor.receive.buffer.size":1024,"backpressure.disruptor.low.watermark":0.4,"topology.users":[],"nimbus.task.launch.secs":120,"storm.local.mode.zmq":false,"storm.messaging.netty.buffer_size":5242880,"storm.cluster.state.store":"org.apache.storm.cluster_state.zookeeper_state_factory","worker.heartbeat.frequency.secs":1,"storm.log4j2.conf.dir":"log4j2","ui.http.creds.plugin":"org.apache.storm.security.auth.DefaultHttpCredentialsPlugin","storm.zookeeper.root":"\/storm","topology.submitter.user":"jason","topology.tick.tuple.freq.secs":null,"drpc.https.port":-1,"storm.workers.artifacts.dir":"workers-artifacts","supervisor.blobstore.download.max_retries":3,"task.refresh.poll.secs":10,"storm.exhibitor.port":8080,"task.heartbeat.frequency.secs":3,"pacemaker.port":6699,"storm.messaging.netty.max_wait_ms":1000,"topology.component.resources.offheap.memory.mb":0.0,"drpc.http.port":3774,"topology.error.throttle.interval.secs":10,"storm.messaging.transport":"org.apache.storm.messaging.netty.Context","topology.disable.loadaware.messaging":false,"storm.messaging.netty.authentication":false,"topology.component.resources.onheap.memory.mb":128.0,"topology.kryo.factory":"org.apache.storm.serialization.DefaultKryoFactory","topology.kryo.register":null,"worker.gc.childopts":"","nimbus.topology.validator":"org.apache.storm.nimbus.DefaultTopologyValidator","nimbus.seeds":["localhost"],"nimbus.queue.size":100000,"nimbus.cleanup.inbox.freq.secs":600,"storm.blobstore.replication.factor":3,"worker.heap.memory.mb":768,"logviewer.max.sum.worker.logs.size.mb":4096,"pacemaker.childopts":"-Xmx1024m","ui.users":null,"transactional.zookeeper.servers":null,"supervisor.worker.timeout.secs":30,"storm.zookeeper.auth.password":null,"storm.blobstore.acl.validation.enabled":false,"client.blobstore.class":"org.apache.storm.blobstore.NimbusBlobStore","supervisor.childopts":"-Xmx256m","topology.worker.max.heap.size.mb":768.0,"ui.http.x-frame-options":"DENY","backpressure.disruptor.high.watermark":0.9,"ui.filter":null,"ui.header.buffer.bytes":4096,"topology.min.replication.count":1,"topology.disruptor.wait.timeout.millis":1000,"storm.nimbus.retry.intervalceiling.millis":60000,"topology.trident.batch.emit.interval.millis":500,"storm.auth.simple-acl.users":[],"drpc.invocations.threads":64,"java.library.path":"\/usr\/local\/lib:\/opt\/local\/lib:\/usr\/lib","ui.port":8080,"topology.kryo.decorators":[],"storm.id":"word-count-1-1494386305","topology.name":"word-count","storm.exhibitor.poll.uripath":"\/exhibitor\/v1\/cluster\/list","storm.messaging.netty.transfer.batch.size":262144,"logviewer.appender.name":"A1","nimbus.thrift.max_buffer_size":1048576,"storm.auth.simple-acl.users.commands":[],"drpc.request.timeout.secs":600},"uptime":"1d 9h 5m 41s","schedulerInfo":null,"name":"word-count","workersTotal":3,"status":"INACTIVE","spouts":[{"emitted":105640,"spoutId":"sentence-spout","errorTime":null,"tasks":2,"errorHost":"","failed":0,"completeLatency":"0.000","executors":2,"encodedSpoutId":"sentence-spout","transferred":105640,"errorPort":"","errorLapsedSecs":null,"acked":0,"lastError":"","errorWorkerLogLink":""}],"requestedMemOffHeap":0.0,"debug":false,"visualizationTable":[],"tasksTotal":11,"requestedCpu":0.0,"replicationCount":1,"schedulerDisplayResource":false,"bolts":[{"emitted":0,"errorTime":null,"tasks":2,"errorHost":"","failed":0,"boltId":"report-bolt","executors":2,"processLatency":"0.000","executeLatency":"0.001","transferred":0,"errorPort":"","errorLapsedSecs":null,"acked":0,"encodedBoltId":"report-bolt","lastError":"","executed":507100,"capacity":"0.000","errorWorkerLogLink":""},{"emitted":507080,"errorTime":null,"tasks":2,"errorHost":"","failed":0,"boltId":"split-bolt","executors":2,"processLatency":"0.000","executeLatency":"0.422","transferred":507080,"errorPort":"","errorLapsedSecs":null,"acked":0,"encodedBoltId":"split-bolt","lastError":"","executed":105640,"capacity":"0.000","errorWorkerLogLink":""},{"emitted":507080,"errorTime":null,"tasks":2,"errorHost":"","failed":0,"boltId":"count-bolt","executors":2,"processLatency":"0.000","executeLatency":"0.083","transferred":507080,"errorPort":"","errorLapsedSecs":null,"acked":0,"encodedBoltId":"count-bolt","lastError":"","executed":507100,"capacity":"0.000","errorWorkerLogLink":""}],"executorsTotal":11,"uptimeSeconds":119141,"assignedCpu":0.0,"topologyStats":[{"windowPretty":"10m 0s","window":"600","emitted":0,"transferred":0,"completeLatency":"0","acked":null,"failed":null},{"windowPretty":"3h 0m 0s","window":"10800","emitted":1119800,"transferred":1119800,"completeLatency":"0","acked":null,"failed":null},{"windowPretty":"1d 0h 0m 0s","window":"86400","emitted":1119800,"transferred":1119800,"completeLatency":"0","acked":null,"failed":null},{"windowPretty":"All time","window":":all-time","emitted":1119800,"transferred":1119800,"completeLatency":"0","acked":null,"failed":null}],"assignedMemOffHeap":0.0}


## http://10.128.12.198:8080/api/v1/topology/word-count-1-1494386305/component/count-bolt

{"window":":all-time","boltStats":[{"window":"10800","emitted":507080,"failed":0,"processLatency":"0.000","executeLatency":"0.083","transferred":507080,"acked":0,"executed":507100,"capacity":"0.000","windowPretty":"3h 0m 0s"},{"window":"86400","emitted":507080,"failed":0,"processLatency":"0.000","executeLatency":"0.083","transferred":507080,"acked":0,"executed":507100,"capacity":"0.000","windowPretty":"1d 0h 0m 0s"},{"window":":all-time","emitted":507080,"failed":0,"processLatency":"0.000","executeLatency":"0.083","transferred":507080,"acked":0,"executed":507100,"capacity":"0.000","windowPretty":"All time"},{"window":"600","emitted":0,"failed":0,"processLatency":"0.000","executeLatency":"0.000","transferred":0,"acked":0,"executed":0,"capacity":"0.000","windowPretty":"10m 0s"}],"topologyId":"word-count-1-1494386305","inputStats":[{"component":"split-bolt","encodedComponentId":"split-bolt","stream":"default","executeLatency":"0.083","processLatency":"0.000","executed":507100,"acked":0,"failed":0}],"encodedTopologyId":"word-count-1-1494386305","tasks":2,"encodedId":"count-bolt","profileActionEnabled":false,"windowHint":"All time","user":null,"samplingPct":10,"id":"count-bolt","executors":2,"name":"word-count","executorStats":[{"emitted":211280,"encodedId":"%5B4-4%5D","host":"jason-HP","failed":0,"id":"[4-4]","uptime":"1h 21m 20s","processLatency":"0.000","executeLatency":"0.086","transferred":211280,"port":6800,"workerLogLink":"http:\/\/jason-HP:8000\/log?file=word-count-1-1494386305%2F6800%2Fworker.log","acked":0,"executed":211300,"uptimeSeconds":4880,"capacity":"0.000"},{"emitted":295800,"encodedId":"%5B5-5%5D","host":"jason-HP","failed":0,"id":"[5-5]","uptime":"1h 21m 20s","processLatency":"0.000","executeLatency":"0.082","transferred":295800,"port":6801,"workerLogLink":"http:\/\/jason-HP:8000\/log?file=word-count-1-1494386305%2F6801%2Fworker.log","acked":0,"executed":295800,"uptimeSeconds":4880,"capacity":"0.000"}],"debug":false,"profilingAndDebuggingCapable":true,"componentType":"bolt","componentErrors":[],"topologyStatus":"INACTIVE","outputStats":[{"stream":"default","emitted":507080,"transferred":507080}],"profilerActive":[],"eventLogLink":"http:\/\/:8000\/log?file=word-count-1-1494386305%2F0%2Fevents.log"}




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
