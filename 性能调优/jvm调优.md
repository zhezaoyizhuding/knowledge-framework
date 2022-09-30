## 1、概述

JVM调优主要是OOM、fullgc、yanggc的调优。

## 2、OOM

oom严格来说其实算是一种故障，在出现时，jvm将无法再向外提供服务，java进程直接被干掉。由于jvm内存分为多个区域，所以也就有了不同区域的OOM情况。

#### 2.1 堆溢出

这其实就是我们最常见的狭义的内存溢出，通常的OOM基本都是出现在堆区域。它的报错信息如下：

```java
java.lang.OutOfMemoryError: Java heap space
```

##### 原因&优化

- 内存泄露：长久的内存泄露，最终导出内存不足造成OOM，这一般是由于缓存使用不当导致。

内存泄露一般不易发现。除了最初的code review预防外，并没有其他的更好的发现措施，而code review又对开发人员的要求较高。所以这种问题我们一般都是后置发现。比如通过监控发现系统越来越慢，内存稳步提升，或者直接导致了OOM。

解决方案就是dump下内存快照，通过jprofiler等工具分析堆栈信息，找出占用内存较多的对象，在聚焦到代码里分析。

- 短时间内从DB或者其他地方捞出大量对象。常见于导出、批量查询等场景，未做数量限制

限制查询数量，分批查询

- 正常的内存不足。系统并发过高，导致短期内产出大量的垃圾对象，垃圾回收器不堪其重。

升级机器，调大内存。如果jvm超过8G，考虑用G1替代CMS

##### 2.2 元空间溢出

JDK8之前这个地方叫做永久代，后来改成元数据区，同时从堆内移动到了堆外内存。但是字符串常量池从元数据剔除放在了堆里。报错信息：

```java
java.lang.OutOfMemoryError: PermGen space
java.lang.OutOfMemoryError: Metaspace
```

这个地方一般很少溢出，均是操作不当导致的。

- 元空间分配的太少，而java项目又比较大，class放不下。
- 运行时常量池溢出，JDK之前由于字符串常量池是在运行时常量池中，频繁的错误使用String.intern()会导致常量池溢出。

##### 2.3 栈溢出

```java
java.lang.StackOverflowError
java.lang.OutOfMemoryError: unable to create new native thread
```

原因：

- 栈内存设置的太小 -- 扩大栈的内存
- 方法死循环，栈嵌套的太深  -- 慎用递归

##### 2.4 堆外内存溢出

```java
java.lang.OutOfMemoryError: Direct buffer memory
```

原因：

- 大文件或者产生大量文件
- 并发太高，大量的socket

## fullGc

fullgc主要有以下原因导致：

- 手动调用System.gc()
- 老年代空间不足
- 永生代空间不足

其中System.gc不推荐使用，一般都会通过jvm参数**-XX:+ DisableExplicitGC**来禁止。永生代情况也比较少，基本不会发生垃圾回收。所以fullgc的调优主要关注老年代的空间使用情况。

而分析老年代的空间使用情况，就要了解老年代的几种晋升机制。

- 年龄晋升：晋升年龄不同的垃圾回收器配置不同，比如Parallel是15，CMS是6，而且可以通过jvm参数修改
- 大对象直接晋升：大对象的判定阈值也可以通过jvm参数调整
- 老年代分配担保：如果一次yanggc存活对象太多，s区放不下，将会根据是否开启了允许分配担保的配置，来判断是否使用老年代的区域
- 年龄动态判定机制：一批对象的总大小大于这块Survivor区域内存大小的50%(-XX:TargetSurvivorRatio可以指定)，那么此时大于等于这批对象年龄最大值的对象，就可以直接进入老年代。一般在minor gc后触发。

## 参考文档

https://tech.meituan.com/2020/11/12/java-9-cms-gc.html

https://tech.meituan.com/2017/12/29/jvm-optimize.html