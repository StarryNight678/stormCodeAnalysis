# API总结

```java
Map<String, SchedulerAssignment> mss = cluster.getAssignments();
            for (String s : mss.keySet()) {
                System.out.println("getExecutorToSlot:");
                System.out.println(s + " " + mss.get(s).getExecutorToSlot());
                System.out.println("getSlotToExecutors:");
                System.out.println(s + " " + mss.get(s).getSlotToExecutors());
            }
```


##  getExecutorToSlot:

word-count-1-1494747487 {[7, 7]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [5, 5]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [12, 12]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [3, 3]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [10, 10]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [1, 1]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [8, 8]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [6, 6]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [13, 13]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [4, 4]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [11, 11]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [2, 2]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803, [9, 9]=138efdd0-6fe2-435c-b0c7-78c2e3820682:6803}

##  getSlotToExecutors:

word-count-1-1494747487 {138efdd0-6fe2-435c-b0c7-78c2e3820682:6803=[[7, 7], [5, 5], [12, 12], [3, 3], [10, 10], [1, 1], [8, 8], [6, 6], [13, 13], [4, 4], [11, 11], [2, 2], [9, 9]]}



## executorToComponent:

{[7, 7]=count-bolt, [5, 5]=__metricsorg.apache.storm.metric.LoggingMetricsConsumer, [12, 12]=split-bolt, [3, 3]=__acker, [10, 10]=sentence-spout, [1, 1]=__acker, [8, 8]=report-bolt, [6, 6]=count-bolt, [13, 13]=split-bolt, [4, 4]=__metricsorg.apache.storm.metric.HttpForwardingMetricsConsumer, [11, 11]=sentence-spout, [2, 2]=__acker, [9, 9]=report-bolt}



```java

```


```java

```

```java

```

```java

```

