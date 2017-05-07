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



## 其他


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