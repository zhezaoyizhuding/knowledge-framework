## 1、概述

JVM调优（也可以直接叫gc调优）主要是OOM、fullgc、yanggc的调优。

![img](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/cac9845d64448288663cdee901b2797f312301.png)

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

- 短时间内从DB或者其他地方捞出大量对象。常见于导出、批量查询、无where条件等场景，未做数量限制

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
- 运行时常量池溢出，JDK8之前运行时常量池还在方法区中，频繁的错误使用String.intern()会导致常量池溢出。（JDK 8之后就是在堆中，显示的是堆溢出）

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

## fullGc（major gc）

什么时候可能会触发STW的Full GC呢？ 

- Perm空间不足； 
- CMS GC时出现promotion failed和concurrent mode failure（concurrent mode failure发生的原因一般是CMS正在进行gc，但是由于老年代空间不足，需要尽快回收老年代里面的不再被使用的对象，这时停止所有的线程，同时终止CMS，直接进行Serial Old GC）（老年代空间不足）
-  统计得到的Young GC晋升到老年代的平均大小大于老年代的剩余空间，或者在未配置“-XX:-HandlePromotionFailure”的情况下年轻代所有对象之和大于老年代剩余可用空间。(老年代空间不足)
- 主动触发Full GC（执行jmap -histo:live [pid]）或者System.gc()来避免碎片问题。

其中System.gc不推荐使用，一般都会通过jvm参数**-XX:+ DisableExplicitGC**来禁止。永生代情况也比较少，基本不会发生垃圾回收。所以fullgc的调优主要关注老年代的空间使用情况。

而分析老年代的空间使用情况，就要了解老年代的几种晋升机制。

- 年龄晋升：晋升年龄不同的垃圾回收器配置不同，比如Parallel是15，CMS是6，而且可以通过jvm参数修改
- 大对象直接晋升：大对象的判定阈值也可以通过jvm参数调整
- 老年代分配担保：如果一次yanggc存活对象太多，s区放不下，将会根据是否开启了允许分配担保的配置，来判断是否使用老年代的区域
- 年龄动态判定机制：一批对象的总大小大于这块Survivor区域内存大小的50%(-XX:TargetSurvivorRatio可以指定)，那么此时大于等于这批对象年龄最大值的对象，就可以直接进入老年代。一般在minor gc后触发。

**gc调优最常见的就是新生代和老年代的比例设置的不合理，导致对象过早晋升，一般可以通过jstat或者arthas等成熟工具观察jvm gc情况，如果老年代回收比例过大，则很可能发生了过早晋升。可适当调大新生代的大小。一般老年代调到活跃对象的3倍即可，其他的jvm内存都可以给新生代**

![image-20221001230639014](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221001230639014.png)

## yanggc（minor gc）

适当调大新生代大小，防止对象过早晋升。

## 火焰图

![image-20230713161016897](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230713161016897.png)

https://www.infoq.cn/article/a8kmnxdhbwmzxzsytlga

https://www.ruanyifeng.com/blog/2017/09/flame-graph.html

https://zhuanlan.zhihu.com/p/402188023

## 参考文档

https://tech.meituan.com/2020/11/12/java-9-cms-gc.html

https://tech.meituan.com/2017/12/29/jvm-optimize.html

https://www.jianshu.com/p/4d59698030f1

