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
