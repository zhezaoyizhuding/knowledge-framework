---
title: 浅析AbstractQueuedSynchronizer
date: 2018-05-07 12:00:28
categories: Java源码浅析
tags:
- AQS
- 锁
- 同步器
---

我们都知道java是支持多线程的，在多线程环境下访问共享的可变的对象时，我们需要同步来实现线程安全，通常我们是通过加锁使其由并行变成串行。而加锁我们会可能会使用呢synchronizer或者lock，而这里面其实存在着很多问题。我们在学习并发时很多书籍或者博客都告诉我们要保证可见性和原子性，java中中为这些提供的支持是volatile和锁。那么我们是否想过这些问题？在多线程环境下为什么数据会不可见？volatile又是如何保证可见性的？锁到底是什么？它又是如何保证操作的原子性和数据的可见性的？synchronizer是java中的关键字，我们可以猜测jvm对它进行了支持使它实现了锁的语义，那么ReentrantLock呢？它只是一个普通的java类，它是如何实现锁的语义的？问题很多，一篇博客很难说完，笔者也不打算在这里赘述，感兴趣的同学可以了解下java内存模型。本文主要讨论下AQS的内部实现以及它是如何实现锁的语义的。

### 概述

AbstractQueuedSynchronizer是java并发框架中一个非常基础的额框架，用来构建锁和其他同步组件，可以说整个java并发框架就是它和volatile共同撑起的。java并发大神Doug Lea在设计这个类时也是希望它能成为实现大部分同步需要的基础。

### 源码分析

AbstractQueuedSynchronizer是一个抽象类，只有一个空的且限定符为protected的构造函数，表明它只希望通过子类继承覆写的方式来使用它。事实上在那些基于AQS的类中确实都是这样使用，但是一般这些并发工具类都是将继承于AQS的子类作为它本身的内部类来使用，将一些需要同步的操作委托给这个内部类。

AQS的内部设计大致可以分为一个状态，两个队列，两类方法来分析，我们下面来仔细看一下。

##### 一个状态

构造函数：

```java
/**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }
```

状态变量：

```java
/**
     * The synchronization state.
     */
    private volatile int state;
```

AQS使用一个状态变量state来表示线程持有锁的状态，0表示未持有，从上面构造函数可以看出初始化时默认为0；1表示持有锁，后续线程将会被放入到一个同步队列中；若大于1，则说明锁被重入(或者在共享模式中被多个线程同时持有)。读者们应该知道，在很多java书中都说明了锁保证了两件事情：可见性与原子性。AQS要实现锁的语义就必须要保证这两件事情，从上面代码可以看到，AQS为了保证state的状态改变被所有线程感知，这里使用volatile来修饰它，保证了它的可见性；至于它如何保证操作的原子性，我们在下面说明，这里先慢慢说下去。看到这里有些读者可能又会想到本文开头所问的那些问题，volatile做了什么，怎么就保证了可见性呢？这里在提一个建议，如果有不明白的同学，去看看java内存模型吧，在很多介绍虚拟机或着并发的书籍的都会见到它，是我们了解java并发不可或缺的知识点，是一些真正有意思的东西。

AQS还有一些其他的成员变量，如下：

```java
/**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;
/**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;
/**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }
```

其中head，tail用于同步队列中的头结点和尾节点，spinForTimeoutThreshold是一个默认的线程自旋时间，其他的是相应变量的内存偏移量，用于原子的更新这些变量。

##### 两个队列

闲话少说，上面介绍了AQS中最主要的一个成员变量，用来标识持有锁的状态，下面介绍一下两个队列，一个同步队列，一个等待队列。在上面介绍了在持有锁后，后续线程将会被塞入到一个同步队列中等待锁的释放，下面我们来看看这个队列。

```java
static final class Node {
        /** Marker to indicate a node is waiting in shared mode */
        static final Node SHARED = new Node();
        /** Marker to indicate a node is waiting in exclusive mode */
        static final Node EXCLUSIVE = null;

        /** waitStatus value to indicate thread has cancelled */
        static final int CANCELLED =  1;
        /** waitStatus value to indicate successor's thread needs unparking */
        static final int SIGNAL    = -1;
        /** waitStatus value to indicate thread is waiting on condition */
        static final int CONDITION = -2;
        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         */
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }
```

上面是笔者在java 8中截取的一段源码，有点长，主要都是注释。jdk源码中的这些注释确实可以帮我们在研究源码的过程中省去很多事情。其实还有一大块对这个内部类的总体介绍的注释，笔者并没有粘在上面，实在是太长了，虽然笔者这业余博客并不在意篇幅，但也没有必要。笔者这里就用本人英语不足四级水平再加上有道词典（这个是主要劳动力，实在是不查不行）通过艰难研读这些注释加代码简单介绍这个类。这里在说点题外话，如何读者里存在英语水平可笔者伯仲之间的，真的要好好学习英语啊，咱们这一行主流框架注释和一些一手的资料文档全是英文，这英语不行看着太痛苦了。最根本问题在于，这看见好看的外国小姐姐，想上去搭讪都不知如何开口，整的笔者现在还是单身！

闲话少说，我们先看下这个类的成员变量。首先是两个Node类型SHARED和EXCLUSIVE，这两个成员变量用来标识线程所处的模式，即共享模式和独占模式。这里就要介绍下锁的两种类型共享锁（也叫读锁），独占锁（也叫写锁）。共享锁可以多个线程同时获取，但是独占锁同一时刻下只能有一个线程持有它，感兴趣的同学可以看看ReetrantReadWriteLock类，它底层也是借助AQS来实现的，当然，事实上这个类的实现要更复杂些。

下面是一个int类型的waitStatus变量标识节点中线程的状态，初始值为0，并且定义了4种状态作为它的取值，分别是CANCELLED，SIGNAL，CONDITION，PROPAGATE。每个状态所代表的含义稍后介绍。

其他成员变量prev和next指向前置节点和后置节点，thread就是节点中等待的线程，nextWaiter变量用于在等待队列中指向下一个节点。上文说了AQS中有两个队列，一个同步队列，一个等待队列。同步队列存放的是在锁上阻塞的线程，等待队列中存放的是在condition上等待的线程。这两个队列的节点类型都是复用的上面的Node类型，便于节点在两个队列之间移动。一个锁对象只会有一个同步队列，但是可能有多个等待队列。

内部两个队列的结构如下：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/AQS内部存储结构图.png" alt="AQS内部存储结构图" style="zoom:30%;" />

可以看到存在两个相连的队列，上面的是同步队列，下面的是等待队列；AQS会持有两个引用，head和tail分别指向同步队列的首节点和尾节点。等待队列其实是从一个condition对象延伸的，AQS持有condition对象的引用，condition对象中存在一个firstWaiter和lastWaiter分别指向等待队列的首节点和尾节点。同步队列和等待队列之间会存在交互，当调用condition.waite时，同步队列的首节点释放锁并移动到等待队列的尾节点在此等待，当调用condition.singal时，等待队列的首节点就会被移动到同步队列中重新去竞争锁。conditionObject的代码就不粘了，有兴趣的同学可以自己去看看源码。

##### 两类方法

下面介绍下AQS中的成员方法，主要分为两类，独占和共享，还有一些是与状态state相关的方法。

独占相关方法：

```java
public final void acquire(int arg)
public final void acquireInterruptibly(int arg)
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
public final boolean release(int arg)
```

共享相关方法：

```java
public final void acquireShared(int arg)
public final void acquireShared(int arg)
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
public final boolean releaseShared(int arg)
```

上面这些是AQS可被使用的模板方法，公有的并使用final定义，无法被重写，还有一些protected定义的用于被子类重写的方法，如下：

独占：

```java
protected boolean tryAcquire(int arg)
protected boolean tryRelease(int arg)
protected boolean isHeldExclusively()
```

共享：

```java
protected int tryAcquireShared(int arg)
protected boolean tryRelease(int arg)
```

由于AQS本质上还是一个抽象类，得跟上具体的子类才有意义。下面通过分析ReentrantLock的实现，来看看锁到底是如何实现，并通过这更好的理解AQS的实现。

### 从一把锁说开去

ReentrantLock中存在一个内部类Sync继承于AQS，但是为了实现公平锁和非公平锁的语义，它下面有实现了两个子类。我们都知道java中synchronize关键字的锁就是非公平锁，而ReentrantLock比它多了一个功能就是选择公平锁，后面再分析代码时我们还会看到ReentrantLock其它的优势。公平锁和非公平锁的区别在于公平锁严格保证了线程持有锁的顺序就是它们请求获取锁的顺序，而非公平锁不保证，它们可以竞争。再高并发情况下非公平锁的性能要远远优于公平锁，除非特殊的需求，一般我们不会使用公平锁。ReentrantLock中默认的也是非公平锁，我们下面的源码分析也是以非公平锁，公平锁的部分不再介绍。

当我们调用Lock.lock()方法，如果是非公平锁它会调用下面这个方法：

```java
/**
         * Performs lock.  Try immediate barge, backing up to normal
         * acquire on failure.
         */
        final void lock() {
            if (compareAndSetState(0, 1))
                setExclusiveOwnerThread(Thread.currentThread());
            else
                acquire(1);
        }
```

首先它会调用compareAndSetState方法，这是个CAS操作，第一个值是预期值，第二个是用于更新的值。它会将这个预期值与内存中真正的值比较，如果相等，则替换为这个更新值，如果不相同，则返回false。读者要把这个方法和java原子类的一些操作区分开，虽然它们底层都是委托给java虚拟机的UnSafe类，但是原子类的方法会一直自旋，直到成功。

所以上面这个方法会先尝试获取锁（判断内存中的state的值是否是0，如果是0，则表示锁还没有被持有，则将它替换为1，即持有锁）。如果获取成功，则把当前线程设置为独占模式线程。方法如下：

```java
/**
     * Sets the thread that currently owns exclusive access.
     * A {@code null} argument indicates that no thread owns access.
     * This method does not otherwise impose any synchronization or
     * {@code volatile} field accesses.
     * @param thread the owner thread
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }
```

这个exclusiveOwnerThread是成员变量，在下面的方法中会判断它的状态。

如果这个锁已经被别的线程持有，则当前线程会调用acquire方法，我们看看这个方法里做了什么。

```java
/**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
```

可以看到在这个方法里它会首先调用tryAcquire方法，传值为1，这个方法是一个可被重写的方法，我们看一下子类的重写实现。

```java
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
```

再次调用了nonfairTryAcquire方法：

```java
/**
         * Performs non-fair tryLock.  tryAcquire is implemented in
         * subclasses, but both need nonfair try for trylock method.
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }
```

这个方法里它首先得到了当前线程的引用及当前的同步状态。然后会再次判断锁是否已经被释放，如果已经被释放了，再次尝试获取锁。这里有的同学可能会问，既然它已经判断state为0，下面为什么还要调用compareAndSetState呢？因为这两个操作之间不是原子操作，在这两个操作之前可能会有其它线程先一步获取到了锁，我们要时刻记得这是在一个多线程环境下。获取锁了直接就返回true，然后再外层的那个方法也会直接返回。

如果锁还没有被释放，它会判断当前线程是否已经是独占线程（之前那个exclusiveOwnerThread变量被用到了），即是否是已经获得锁的线程。如果是，就执行下面的一系列操作，因为java中的锁是可重入的。它会将state加1，再返回true。中间那个判断是为state是int类型的，而java中的int是有范围的，而负值没有意义。事实上在现实情况下运行良好的程序中if (nextc < 0) 的这段应该永远不会执行，它可能会被java虚拟机给优化掉。

如果没有拿到锁，则会返回false，进入到上面那个方法的if语句中。首先会调用addWaiter方法将当前线程入队。

```java
/**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }
```

这个方法首先以当前的线程和所处的模式（独占或者共享），新建一个Node，然后取得tail的引用，判断它是否为null，如果不为空，即已经有人在排着队，那就从后面入队，这里也要注意操作的原子性，代码中使用了compareAndSetTail方法来保证了它。

如果为空，则表示此前无人，调用enq方法初始化。

```java
/**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) { // Must initialize
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }
```

这个方法里的CAS操作就和原子类中的类似，采用一个死循环，使操作必须成功。好了，addWaiter调用完了并返回了当前的Node节点，我们来看看acquireQueued方法里做了什么。

```java
/**
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

这个方法可以看做是一个牢笼，所有的线程再走到这里后，就会一直在里面转啊转的（学名叫自旋），直到获取锁。这是为了避免线程挂起恢复造成的资源浪费。我们来仔细看一下，它会首先判断当前线程的前置节点是否是头结点，如果是，则尝试获取锁；如果获取成功了，把当前节点设置为头结点并返回。否则它会一直空转。我们看到它下面还有一个if语句块用于设置interrupted的状态，它是判断当前线程是否被中断，如果是设置interrupted为true。我们可以把它和doAcquireInterruptibly方法一起看，它的源码如下：

```java
/**
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

我们可以看到它和acquireQueued的不同之处在于，它会直接跑出一个中断异常，而acquireQueued会忽略它。这个方法用来被ReentrantLock的lockInterruptibly方法调用，用来响应中断。可中断也是Lock不同于synchronize的地方，但默认情况下它是忽略中断的。

上面基本上就是锁的获取的代码了，下面看一看释放锁的代码。它的调用流程如下：

```java
public void unlock() {
        sync.release(1);
    }
```

```java
/**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
```

我们可以看到首先它尝试释放锁，如果释放成功，唤醒后继节点。我们看一下tryRelease的代码，如下：

```java
protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }
```

这个方法就是改变了state的状态，所以在Lock中释放锁本质上就是state减一，当然这个方法考虑了重入锁释放的情况。

### 总结

AQS就介绍到这了，关于Condition相关的东西本文就不在继续介绍了，因为实在是有些长了。感兴趣的同学可以自己研究一下，笔者之后如果有时间可能还会写出后续的介绍。本文只是笔者一边看相关资料，一边读源码，边读边写出的东西，难免会有疏漏，读者应自行验证。尤其是本文没有怎么介绍Condition相关的东西，很多东西也说不清楚，特别前文中Node类中的相关状态，笔者自己也没有整的太明白。

研读这些源码只是笔者的一时兴趣，其实在工作中这些东西用的地方并不多，因为我们已经有了很多优秀的并发框架，有完善的线程池设计。其实这或许并不是一件坏事，因为这些东西确实很微妙。我们如果自行管理线程之间的交互，难免会出纰漏，或许在不远的将来，java开发者会完全忘记这些东西。