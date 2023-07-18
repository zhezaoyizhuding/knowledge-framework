---
title: 内存模型概述
date: 2018-11-20 16:40:49
categories: Java虚拟机
tags:
- 内存模型
---

### 并发三大特性

 并发编程Bug的源头:可见性、原子性和有序性问题
 #### 可见性

当一个线程修改了共享变量的值，其他线程能够看到修改的值。Java 内存模型是通过在变量 修改后将新值同步回主内存，在变量读取前从主内存刷新变量值这种依赖主内存作为传递媒介的方法来实现可见性的。 如何保证可见性

- 通过 volatile 关键字保证可见性。
- 通过 内存屏障保证可见性。
-  通过 synchronized 关键字保证可见性。 通过 Lock保证可见性。
-  通过 final 关键字保证可见性

#### 有序性 

即程序执行的顺序按照代码的先后顺序执行。JVM 存在指令重排，所以存在有序性问题。 如何保证有序性

- 通过  volatile 关键字保证可见性。
- 通过  内存屏障保证可见性。
- 通过  synchronized关键字保证有序性。
- 通过  Lock保证有序性。

### 原子性 

一个或多个操作，要么全部执行且在执行过程中不被任何因素打断，要么全部不执行。在Java 中，对基本数据类型的变量的读取和赋值操作是原子性操作(64位处理器)。不采取任 何的原子性保障措施的自增操作并不是原子性的。如何保证原子性

- 通过 synchronized 关键字保证原子性。
- 通过 Lock保证原子性。
- 通过 CAS保证原子性。

### Java内存模型(JMM)

Java虚拟机规范中定义了Java内存模型(Java Memory Model，JMM)，用于屏蔽掉各种硬件和操作系统的内存访问差异，以实现让Java程序在各种平台下都能达到一致的并发效 果，JMM规范了Java虚拟机与计算机内存是如何协同工作的:规定了一个线程如何和何时可 以看到由其他线程修改过后的共享变量的值，以及在必须时如何同步的访问共享变量。JMM 描述的是一种抽象的概念，一组规则，通过这组规则控制程序中各个变量在共享数据区域和私 有数据区域的访问方式，JMM是围绕原子性、有序性、可见性展开的。

![image-20230718151141798](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718151141798.png)

#### as-if-serial

as-if-serial语义的意思是:不管怎么重排序(编译器和处理器为了提高并行度)，(单线 程)程序的执行结果不能被改变。编译器、runtime和处理器都必须遵守as-if-serial语义。为了遵守as-if-serial语义，编译器和处理器不会对存在数据依赖关系的操作做重排序，因为 这种重排序会改变执行结果。但是，如果操作之间不存在数据依赖关系，这些操作就可能被编译 器和处理器重排序。

```java
doublepi=3.14;//A
doubler=1.0;//B
doublearea=pi*r*r;//C
```

A和C之间存在数据依赖关系，同时B和C之间也存在数据依赖关系。因此在最终执行的指令序 列中，C不能被重排序到A和B的前面(C排到A和B的前面，程序的结果将会被改变)。但A和B之 间没有数据依赖关系，编译器和处理器可以重排序A和B之间的执行顺序。

#### happens-before

 从JDK5 开始，JMM使用happens-before的概念来阐述多线程之间的内存可见性。happens-before原则是JMM中非常重要的原则，它是判断数据是否存在竞争、线程是否安全的 主要依据，保证了多线程环境下的可见性。在JMM中，如果一个操作执行的结果需要对另一个操作可见，那么这两个操作之间必须存在happens- before关系。 happens-before和JMM关系如下图:

![image-20230718165427662](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718165427662.png)

happens-before原则非常重要，它是判断数据是否存在竞争、线程是否安全的主要依据，依 靠这个原则，我们解决在并发环境下两操作之间是否可能存在冲突的所有问题。下面我们就一个简单的例子稍微了解下happens-before :

```java
i=1;//线程A执行 
2 j=i;//线程B执行
```

j 是否等于1呢? 假定线程A的操作(i = 1)happens-before线程B的操作(j = i),那么可以 确定线程B执行后j = 1 一定成立，如果他们不存在happens-before原则，那么j = 1 不一定成 立。这就是happens-before原则的威力。

##### happens-before原则定义如下:

1. 如果一个操作happens-before另一个操作，那么第一个操作的执行结果将对第二个操作 可见，而且第一个操作的执行顺序排在第二个操作之前。
2. 两个操作之间存在happens-before关系，并不意味着一定要按照happens-before原则 制定的顺序来执行。如果重排序之后的执行结果与按照happens-before关系来执行的结果 一致，那么这种重排序并不非法。

##### 下面是happens-before规则: 

1. 程序次序规则:一个线程内，按照代码顺序，书写在前面的操作先行发生于书写在后面的操作
2. 锁定规则:一个unLock操作先行发生于后面对同一个锁的lock操作
3. volatile变量规则:对一个变量的写操作先行发生于后面对这个变量的读操作
4. 传递规则:如果操作A先行发生于操作B，而操作B又先行发生于操作C，则可以得出操作A 先行发生于操作C
5. 线程启动规则:Thread对象的start()方法先行发生于此线程的每个一个动作
6. 线程中断规则:对线程interrupt()方法的调用先行发生于被中断线程的代码检测到中断事件 的发生
7. 线程终结规则:线程中所有的操作都先行发生于线程的终止检测，我们可以通过 Thread.join()方法结束、Thread.isAlive()的返回值手段检测到线程已经终止执行
8. 对象终结规则:一个对象的初始化完成先行发生于他的finalize()方法的开始;

我们来详细看看上面每条规则(摘自《深入理解Java虚拟机第12章》): 

- 程序次序规则:一段代码在单线程中执行的结果是有序的。注意是执行结果，因为虚拟机、处理 器会对指令进行重排序。虽然重排序了，但是并不会影响程序的执行结果，所以程序最终执行的结果与顺序执行的结果是一致的。故而这个规则只对单线程有效，在多线程环境下无法保证正确性。 
- 锁定规则:这个规则比较好理解，无论是在单线程环境还是多线程环境，一个锁处于被锁定状 态，那么必须先执行unlock操作后面才能进行lock操作。
- volatile变量规则:这是一条比较重要的规则，它标志着volatile保证了线程可见性。通俗点讲就 是如果一个线程先去写一个volatile变量，然后一个线程去读这个变量，那么这个写操作一定是 happens-before读操作的。

- 传递规则:提现了happens-before原则具有传递性，即A happens-before B , B happens- before C，那么A happens-before C 
- 线程启动规则:假定线程A在执行过程中，通过执行ThreadB.start()来启动线程B，那么线程A对 共享变量的修改在接下来线程B开始执行后确保对线程B可见。 
- 线程终结规则:假定线程A在执行的过程中，通过制定ThreadB.join()等待线程B终止，那么线程B 在终止之前对共享变量的修改在线程A等待返回后可见。

上面八条是原生Java满足Happens-before关系的规则，但是我们可以对他们进行推导出其他满 足happens-before的规则:

1. 将一个元素放入一个线程安全的队列的操作Happens-Before从队列中取出这个元素的操作 
2. 将一个元素放入一个线程安全容器的操作Happens-Before从容器中取出这个元素的操作 
3. 在CountDownLatch上的倒数操作Happens-Before CountDownLatch#await()操作 
4. 释放Semaphore许可的操作Happens-Before获得许可操作 
5. Future表示的任务的所有操作Happens-Before Future#get()操作 
6. 向Executor提交一个Runnable或Callable的操作Happens-Before任务开始执行操作

这里再说一遍happens-before的概念:如果两个操作不存在上述(前面8条 + 后面6条)任一一 个happens-before规则，那么这两个操作就没有顺序的保障，JVM可以对这两个操作进行重排 序。如果操作A happens-before操作B，那么操作A在内存上所做的操作对操作B都是可见的。

下面就用一个简单的例子来描述下happens-before原则:

```java
privateinti=0;
publicvoidwrite(intj){
  i=j;
}
publicintread(){
  return i;
}
```

我们约定线程A执行write()，线程B执行read()，且线程A优先于线程B执行，那么 线程B获得结果是什么?;我们就这段简单的代码一次分析happens­before的规则 (规则5、6、7、8 + 推导的6条可以忽略，因为他们和这段代码毫无关系):

- 由于两个方法是由不同的线程调用，所以肯定不满足程序次序规则;
- 两个方法都没有使用锁，所以不满足锁定规则;
- 变量i不是用volatile修饰的，所以volatile变量规则不满足;
- 传递规则肯定不满足;

所以我们无法通过happens­before原则推导出线程A happens­before线程B，虽然可以确认在时间上线程A优先于线程B指定，但是就是无法确认线程B获得的结果是什 么，所以这段代码不是线程安全的。那么怎么修复这段代码呢?满足规则2、3任一即可。

#### JMM与硬件内存架构的关系

Java内存模型与硬件内存架构之间存在差异。硬件内存架构没有区分线程栈和堆。对于硬 件，所有的线程栈和堆都分布在主内存中。部分线程栈和堆可能有时候会出现在CPU缓存中和 CPU内部的寄存器中。如下图所示，Java内存模型和计算机硬件内存架构是一个交叉关系:

![image-20230718163047763](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718163047763.png)

#### 内存交互操作 

关于主内存与工作内存之间的具体交互协议，即一个变量如何从主内存拷贝到工作内存、如何从工作内存同步到主内存之间的实现细节，Java内存模型定义了以下八种操作来完成: 

- lock(锁定):作用于主内存的变量，把一个变量标识为一条线程独占状态。
-  unlock(解锁):作用于主内存变量，把一个处于锁定状态的变量释放出来，释放后的变量才可以被其他线程锁定。
-  read(读取):作用于主内存变量，把一个变量值从主内存传输到线程的工作内存中，以便随后的load动作使用 
- load(载入):作用于工作内存的变量，它把read操作从主内存中得到的变量值放入工作内存的变量副本中。

- use(使用):作用于工作内存的变量，把工作内存中的一个变量值传递给执行引 擎，每当虚拟机遇到一个需要使用变量的值的字节码指令时将会执行这个操作。

- assign(赋值):作用于工作内存的变量，它把一个从执行引擎接收到的值赋值给 工作内存的变量，每当虚拟机遇到一个给变量赋值的字节码指令时执行这个操作。

- store(存储):作用于工作内存的变量，把工作内存中的一个变量的值传送到主 内存中，以便随后的write的操作。

- write(写入):作用于主内存的变量，它把store操作从工作内存中一个变量的值 传送到主内存的变量中。

![image-20230718163214699](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718163214699.png)

Java内存模型还规定了在执行上述八种基本操作时，必须满足如下规则: 

- 如果要把一个变量从主内存中复制到工作内存，就需要按顺寻地执行read和load操作， 如果把变量从工作内存中同步回主内存中，就要按顺序地执行store和write操作。 但Java内存模型只要求上述操作必须按顺序执行，而没有保证必须是连续执行。

- 不允许read和load、store和write操作之一单独出现

- 不允许一个线程丢弃它的最近assign的操作，即变量在工作内存中改变了之后必须 同步到主内存中。

- 不允许一个线程无原因地(没有发生过任何assign操作)把数据从工作内存同步回 主内存中。

- 一个新的变量只能在主内存中诞生，不允许在工作内存中直接使用一个未被初始化 (load或assign)的变量。即就是对一个变量实施use和store操作之前，必须先执行过 了assign和load操作。

- 一个变量在同一时刻只允许一条线程对其进行lock操作，但lock操作可以被同一条 线程重复执行多次，多次执行lock后，只有执行相同次数的unlock操作，变量才会被解 锁。lock和unlock必须成对出现

- 如果对一个变量执行lock操作，将会清空工作内存中此变量的值，在执行引擎使用 这个变量前需要重新执行load或assign操作初始化变量的值
- 如果一个变量事先没有被lock操作锁定，则不允许对它执行unlock操作;也不允许 去unlock一个被其他线程锁定的变量。

- 对一个变量执行unlock操作之前，必须先把此变量同步到主内存中(执行store和 write操作)。

#### JMM的内存可见性保证 

按程序类型，Java程序的内存可见性保证可以分为下列3类:

- 单线程程序。单线程程序不会出现内存可见性问题。编译器、runtime和处理器会 共同确保单线程程序的执行结果与该程序在顺序一致性模型中的执行结果相同。

- 正确同步的多线程程序。正确同步的多线程程序的执行将具有顺序一致性(程序的 执行结果与该程序在顺序一致性内存模型中的执行结果相同)。这是JMM关注的重点， JMM通过限制编译器和处理器的重排序来为程序员提供内存可见性保证。

- 未同步/未正确同步的多线程程序。JMM为它们提供了最小安全性保障:线程执行 时读取到的值，要么是之前某个线程写入的值，要么是默认值未同步程序在JMM中的执 行时，整体上是无序的，其执行结果无法预知。 JMM不保证未同步程序的执行结果与 该程序在顺序一致性模型中的执行结果一致。

未同步程序在JMM中的执行时，整体上是无序的，其执行结果无法预知。未同步程序在 两个模型中的执行特性有如下几个差异。

- 顺序一致性模型保证单线程内的操作会按程序的顺序执行，而JMM不保证单线程内的操作会按程序的顺序执行，比如正确同步的多线程程序在临界区内的重排序。

- 顺序一致性模型保证所有线程只能看到一致的操作执行顺序，而JMM不保证所有线程能看到一致的操作执行顺序。

- 顺序一致性模型保证对所有的内存读/写操作都具有原子性，而JMM不保证对64位的

long型和double型变量的写操作具有原子性(32位处理器)。

> JVM在32位处理器上运行时，可能会把一个64位long/double型变量的写操作拆分为两个32位的写操 作来执行。这两个32位的写操作可能会被分配到不同的总线事务中执行，此时对这个64位变量的写操 作将不具有原子性。从JSR-133内存模型开始(即从JDK5开始)，仅仅只允许把一个64位 long/double型变量的写操作拆分为两个32位的写操作来执行，任意的读操作在JSR-133中都必须具 有原子性

### volatile的内存语义 

#### volatile的特性

- 可见性:对一个volatile变量的读，总是能看到(任意线程)对这个volatile变量最 后的写入。

- 原子性:对任意单个volatile变量的读/写具有原子性，但类似于volatile++这种复 合操作不具有原子性(基于这点，我们通过会认为volatile不具备原子性)。volatile仅 仅保证对单个volatile变量的读/写具有原子性，而锁的互斥执行的特性可以确保对整个临界区代码的执行具有原子性。 （64位的long型和double型变量，只要它是volatile变量，对该变量的读/写就具有原子性。）

- 有序性:对volatile修饰的变量的读写操作前后加上各种特定的内存屏障来禁止指 令重排序来保障有序性。

在JSR-133之前的旧Java内存模型中，虽然不允许volatile变量之间重排序，但旧的Java内存模型允许 volatile变量与普通变量重排序。为了提供一种比锁更轻量级的线程之间通信的机制，JSR-133专家组 决定增强volatile的内存语义:严格限制编译器和处理器对volatile变量与普通变量的重排序，确保 volatile的写-读和锁的释放-获取具有相同的内存语义。

#### volatile写-读的内存语义

- 当写一个volatile变量时，JMM会把该线程对应的本地内存中的共享变量值刷新到主内存。

- 当读一个volatile变量时，JMM会把该线程对应的本地内存置为无效，线程接下来将从主内存中读取共享变量。

#### volatile可见性实现原理 

##### JMM内存交互层面实现

volatile修饰的变量的read、load、use操作和assign、store、write必须是连续的，即修改后必须立即同步回主内存，使用时必须从主内存刷新，由此保证volatile变量操作对多线程的可见性。 

##### 硬件层面实现

通过lock前缀指令，会锁定变量缓存行区域并写回主内存，这个操作称为“缓存锁定”， 缓存一致性机制会阻止同时修改被两个以上处理器缓存的内存区域数据。一个处理器的缓存回 写到内存会导致其他处理器的缓存无效。

##### volatile在hotspot的实现

 ###### 字节码解释器实现

 JVM中的字节码解释器(bytecodeInterpreter)，用C++实现了JVM指令，其优点是实现相对 简单且容易理解，缺点是执行慢。
bytecodeInterpreter.cpp

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718163814852.png" alt="image-20230718163814852" style="zoom:67%;" />

###### 模板解释器实现

模板解释器(templateInterpreter)，其对每个指令都写了一段对应的汇编代码，启动时将每个 指令与对应汇编代码入口绑定，可以说是效率做到了极致。
 templateTable_x86_64.cpp

###### 在linux系统x86中的实现 

orderAccess_linux_x86.inline.hpp。x86处理器中利用lock实现类似内存屏障的效果。

##### lock前缀指令的作用

1. 确保后续指令执行的原子性。在Pentium及之前的处理器中，带有lock前缀的指令在执 行期间会锁住总线，使得其它处理器暂时无法通过总线访问内存，很显然，这个开销很 大。在新的处理器中，Intel使用缓存锁定来保证指令执行的原子性，缓存锁定将大大降低 lock前缀指令的执行开销。
2. LOCK前缀指令具有类似于内存屏障的功能，禁止该指令与前面和后面的读写指令重排 序。
3. LOCK前缀指令会等待它之前所有的指令完成、并且所有缓冲的写操作写回内存(也就是 将store buffer中的内容写入内存)之后才开始执行，并且根据缓存一致性协议，刷新 store buffer的操作会导致其他cache中的副本失效。

##### 汇编层面volatile的实现 

添加下面的jvm参数查看之前可见性Demo的汇编指令，验证了可见性使用了lock前缀指令。

```shell
‐XX:+UnlockDiagnosticVMOptions‐XX:+PrintAssembly‐Xcomp
```

##### 从硬件层面分析Lock前缀指令

> 《64-ia-32-architectures-software-developer-vol-3a-part-1-manual.pdf》中有如下描述:

> The 32-bit IA-32 processors support locked atomic operations on locations in system memory. These operations are typically used to manage shared data structures (such as semaphores, segment descriptors, system segments, or page tables) in which two or more processors may try simultaneously to modify the same field or flag. The processor uses three interdependent mechanisms for carrying out locked atomic operations:
>
> • Guaranteed atomic operations
>  • Bus locking, using the LOCK# signal and the LOCK instruction prefix
>  • Cache coherency protocols that ensure that atomic operations can be carried out on cached data structures (cache lock); this mechanism is present in the Pentium 4, Intel Xeon, and P6 family processors
>
> 32位的IA-32处理器支持对系统内存中的位置进行锁定的原子操作。这些操作通常用于管 理共享的数据结构(如信号量、段描述符、系统段或页表)，在这些结构中，两个或多个处理器 可能同时试图修改相同的字段或标志。处理器使用三种相互依赖的机制来执行锁定的原子操作:
>
> - 有保证的原子操作
> - 总线锁定，使用LOCK#信号和LOCK指令前缀 
> - 缓存一致性协议，确保原子操作可以在缓存的数据结构上执行(缓存锁);这种机制出现在Pentium 4、Intel Xeon和P6系列处理器中 CPU缓存架构剖析

### 指令重排序 

Java语言规范规定JVM线程内部维持顺序化语义。即只要程序的最终结果与它顺序化情况的结果相等，那么指令的执行顺序可以与代码顺序不一致，此过程叫指令的重排序。 指令重排序的意义:

> JVM能根据处理器特性(CPU多级缓存系统、多核处理器等)适当的对机 器指令进行重排序，使机器指令能更符合CPU的执行特性，最大限度的发挥机器性能。

 在编译器与CPU处理器中都能执行指令重排优化操作

![image-20230718164702082](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718164702082.png)

#### volatile重排序规则

![image-20230718164742134](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718164742134.png)

volatile禁止重排序场景:

- 第二个操作是volatile写，不管第一个操作是什么都不会重排序
- 第一个操作是volatile读，不管第二个操作是什么都不会重排序
- 第一个操作是volatile写，第二个操作是volatile读，也不会发生重排序

JMM内存屏障插入策略

- 在每个volatile写操作的前面插入一个StoreStore屏障 
- 在每个volatile写操作的后面插入一个StoreLoad屏障 
- 在每个volatile读操作的后面插入一个LoadLoad屏障 
- 在每个volatile读操作的后面插入一个LoadStore屏障

![image-20230718164845646](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718164845646.png)

x86处理器不会对读-读、读-写和写-写操作做重排序, 会省略掉这3种操作类型对应的内存 屏障。仅会对写-读操作做重排序，所以volatile写-读操作只需要在volatile写后插入 StoreLoad屏障

![image-20230718164920129](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20230718164920129.png)

#### JVM层面的内存屏障

 在JSR规范中定义了4种内存屏障:

- LoadLoad屏障:(指令Load1; LoadLoad; Load2)，在Load2及后续读取操作要读取的数 据被访问前，保证Load1要读取的数据被读取完毕。
- LoadStore屏障:(指令Load1; LoadStore; Store2)，在Store2及后续写入操作被刷出前， 保证Load1要读取的数据被读取完毕。 
- StoreStore屏障:(指令Store1; StoreStore; Store2)，在Store2及后续写入操作执行前， 保证Store1的写入操作对其它处理器可见。
- StoreLoad屏障:(指令Store1; StoreLoad; Load2)，在Load2及后续所有读取操作执行 前，保证Store1的写入对所有处理器可见。它的开销是四种屏障中最大的。在大多数处理器的 实现中，这个屏障是个万能屏障，兼具其它三种内存屏障的功能。由于x86只有store load可能会重排序，所以只有JSR的StoreLoad屏障对应它的mfence或 lock前缀指令，其他屏障对应空操作

#### 硬件层内存屏障

硬件层提供了一系列的内存屏障 memory barrier / memory fence(Intel的提法)来提供一致性的能力。拿X86平台来说，有几种主要的内存屏障:

- lfence，是一种Load Barrier 读屏障
- sfence, 是一种Store Barrier 写屏障
- mfence, 是一种全能型的屏障，具备lfence和sfence的能力 
- Lock前缀，Lock不是一种内存屏障，但是它能完成类似内存屏障的功能。Lock会对 CPU总线和高速缓存加锁，可以理解为CPU指令级的一种锁。它后面可以跟ADD, ADC, AND, BTC, BTR, BTS, CMPXCHG, CMPXCH8B, DEC, INC, NEG, NOT, OR, SBB, SUB, XOR, XADD, and XCHG等指令。

内存屏障有两个能力:

1. 阻止屏障两边的指令重排序
2. 刷新处理器缓存/冲刷处理器缓存。

对Load Barrier来说，在读指令前插入读屏障，可以让高速缓存中的数据失效，重新从主内存加载数据;对Store Barrier来说，在写指令之后插入写屏障，能让写入缓存的最新数据写 回到主内存。Lock前缀实现了类似的能力，它先对总线和缓存加锁，然后执行后面的指令，最后释放锁 后会把高速缓存中的数据刷新回主内存。在Lock锁住总线的时候，其他CPU的读写请求都会被 阻塞，直到锁释放。不同硬件实现内存屏障的方式不同，Java内存模型屏蔽了这种底层硬件平台的差异，由 JVM来为不同的平台生成相应的机器码。