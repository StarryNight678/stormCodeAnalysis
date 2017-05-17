# kafaka教程

[kafka中文教程](http://www.orchome.com/kafka/index)




producer：生产者，就是它来生产“鸡蛋”的。

consumer：消费者，生出的“鸡蛋”它来消费。

topic：你把它理解为标签，生产者每生产出来一个鸡蛋就贴上一个标签（topic），消费者可不是谁生产的“鸡蛋”都吃的，这样不同的生产者生产出来的“鸡蛋”，消费者就可以选择性的“吃”了。

broker：就是篮子了。



 bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test


查看消息,查看已创建的topic信息：

> bin/kafka-topics.sh --list --zookeeper localhost:2181
test


## 发送消息


Kafka提供了一个命令行的工具，可以从输入文件或者命令行中读取消息并发送给Kafka集群。每一行是一条消息。
运行producer（生产者）,然后在控制台输入几条消息到服务器。

> bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test 
This is a message
This is another message


## 消费消息

Kafka也提供了一个消费消息的命令行工具，将存储的信息输出出来。

> bin/kafka-console-consumer.sh --zookeeper localhost:2181 --topic test --from-beginning
This is a message
This is another message



## 设置多个broker集群


到目前，我们只是单一的运行一个broker,，没什么意思。对于Kafka,一个broker仅仅只是一个集群的大小, 所有让我们多设几个broker.
首先为每个broker创建一个配置文件:

```
> cp config/server.properties config/server-1.properties 
> cp config/server.properties config/server-2.properties
```


现在编辑这些新建的文件，设置以下属性：

```
config/server-1.properties: 
    broker.id=1 
    listeners=PLAINTEXT://:9093 
    log.dir=/tmp/kafka-logs-1

config/server-2.properties: 
    broker.id=2 
    listeners=PLAINTEXT://:9094 
    log.dir=/tmp/kafka-logs-2
```


`broker.id`是集群中每个节点的唯一且永久的名称，我们修改端口和日志分区是因为我们现在在同一台机器上运行，我们要防止broker在同一端口上注册和覆盖对方的数据。

我们已经运行了zookeeper和刚才的一个kafka节点，所有我们只需要在启动2个新的kafka节点。



> bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 3 --partitions 1 --topic my-replicated-topic


> bin/kafka-topics.sh --describe --zookeeper localhost:2181 --topic my-replicated-topic
Topic:my-replicated-topic    PartitionCount:1    ReplicationFactor:3    Configs:
Topic: my-replicated-topic    Partition: 0    Leader: 1    Replicas: 1,2,0    Isr: 1,2,0


## 使用 Kafka Connect 来 导入/导出 数据


从控制台写入和写回数据是一个方便的开始，但你可能想要从其他来源导入或导出数据到其他系统。对于大多数系统，可以使用kafka Connect，而不需要编写自定义集成代码。Kafka Connect是导入和导出数据的一个工具。它是一个可扩展的工具，运行连接器，实现与自定义的逻辑的外部系统交互。在这个快速入门里，我们将看到如何运行Kafka Connect用简单的连接器从文件导入数据到Kafka主题，再从Kafka主题导出数据到文件，首先，我们首先创建一些种子数据用来测试：

```
echo -e "foo\nbar" > test.txt
```


接下来，我们开始2个连接器运行在独立的模式，这意味着它们运行在一个单一的，本地的，专用的进程。我们提供3个配置文件作为参数。第一个始终是kafka Connect进程，如kafka broker连接和数据库序列化格式，剩下的配置文件每个指定的连接器来创建，这些文件包括一个独特的连接器名称，连接器类来实例化和任何其他配置要求的。



> bin/connect-standalone.sh config/connect-standalone.properties config/connect-file-source.properties config/connect-file-sink.properties


## 使用Kafka Stream来处理数据


Kafka Stream是kafka的客户端库，用于实时流处理和分析存储在kafka broker的数据，这个快速入门示例将演示如何运行一个流应用程序。一个WordCountDemo的例子（为了方便阅读，使用的是java8 lambda表达式）


```java
    KTable wordCounts = textLines
    // Split each text line, by whitespace, into words.
    .flatMapValues(value -> Arrays.asList(value.toLowerCase().split("W+")))

    // Ensure the words are available as record keys for the next aggregate operation.
    .map((key, value) -> new KeyValue<>(value, value))

    // Count the occurrences of each word (record key) and store the results into a table named "Counts".
    .countByKey("Counts")
```















# 第一章：开始



推荐下载scala 2.11版本的。



Topic

Kafka将消息种子(Feed)分门别类，每一类的消息称之为一个主题(Topic).

Producer

发布消息的对象称之为主题生产者(Kafka topic producer)

Consumer

订阅消息并处理发布的消息的种子的对象称之为主题消费者(consumers)

Broker

已发布的消息保存在一组服务器中，称之为Kafka集群。集群中的每一个服务器都是一个代理(Broker). 消费者可以订阅一个或多个主题（topic），并从Broker拉数据，从而消费这些已发布的消息。


![](http://img.orchome.com:8888/group1/M00/00/01/KmCudlf7DsaAVF0WAABMe0J0lv4158.png)


Kafka集群保持所有的消息，直到它们过期， 无论消息是否被消费了。 实际上消费者所持有的仅有的元数据就是这个偏移量，也就是消费者在这个log中的位置。 这个偏移量由消费者控制：正常情况当消费者消费消息的时候，偏移量也线性的的增加。但是实际偏移量由消费者控制，消费者可以将偏移量重置为更老的一个偏移量，重新读取消息。 可以看到这种设计对消费者来说操作自如， 一个消费者的操作不会影响其它消费者对此log的处理。 再说说分区。Kafka中采用分区的设计有几个目的。一是可以处理更多的消息，不受单台服务器的限制。Topic拥有多个分区意味着它可以不受限的处理更多的数据。第二，分区可以作为并行处理的单元，稍后会谈到这一点。





正像传统的消息系统一样，Kafka保证消息的顺序不变。 再详细扯几句。传统的队列模型保持消息，并且保证它们的先后顺序不变。但是， 尽管服务器保证了消息的顺序，消息还是异步的发送给各个消费者，消费者收到消息的先后顺序不能保证了。这也意味着并行消费将不能保证消息的先后顺序。用过传统的消息系统的同学肯定清楚，消息的顺序处理很让人头痛。如果只让一个消费者处理消息，又违背了并行处理的初衷。 在这一点上Kafka做的更好，尽管并没有完全解决上述问题。 Kafka采用了一种分而治之的策略：分区。 因为Topic分区中消息只能由消费者组中的唯一一个消费者处理，所以消息肯定是按照先后顺序进行处理的。但是它也仅仅是保证Topic的一个分区顺序处理，不能保证跨分区的消息先后处理顺序。 所以，如果你想要顺序的处理Topic的所有消息，那就只提供一个分区。

# 第二章：API

# 第三章：kafka的配置

# 第四章：kafka如何设计的

# 第五章：kafka的实现

# 第六章：kafka的常用操作，如扩容，删除和增加topic。

# 第七章：安全

# 第八章：kafka Connect

# 第九章：kafka 流

# 第十章：笔记（kafka命令大全）