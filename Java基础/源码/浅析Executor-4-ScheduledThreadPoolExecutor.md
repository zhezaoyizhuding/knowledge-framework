---
title: 浅析Executor-4-ScheduledThreadPoolExecutor
date: 2018-08-07 22:12:53
categories: Java源码浅析
tags:
- ScheduledThreadPoolExecutor
---

ScheduledThreadPoolExecutor继承于ThreadPoolExecutor，并实现了ScheduledExecutorService接口，它的功能与Timer类似，但是拥有比Timer更高的灵活性和更多的功能，一般推荐使用它来替代Timer，以此实现延迟或者周期性运行的任务。下面看一下它的具体实现。

### 源码分析

我们先从它的构造函数看起，ScheduledThreadPoolExecutor有四个构造函数，可以指定不同的参数，比如核心线程数，线程工厂，拒绝策略等，其内部实现也是复用了ThreadPoolExecutor的对应构造函数，我们来看一下它的源码：

```java
    public ScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
              new DelayedWorkQueue());
    }
```

因为篇幅原因，笔者只选择了一个构造函数展示，其余的构造函数可以分别指定线程工厂或者拒绝策略，但是其他的值是相同的。比如maximumPoolSize均是Integer.MAX_VALUE，说明池中的线程数目不受限制；keepAliveTime为0，说明非核心线程在空闲时立即死亡；最重要的一点是它的队列是自己实现了一个叫DelayedWorkQueue的东西，而不是通过参数传入，这里可以大胆猜测，这个或许就是延迟和周期性的关键。至于事实如何，这里先按下不表，我们之后再看下这个队列的具体实现。

前面我们知道了如何创建一个ScheduledThreadPoolExecutor，下面我们就需要知道如何使用它。使用过线程池我们都知道，在启动线程池时，我们一般调用submit方法，或者直接使用execute方法，那么ScheduledThreadPoolExecutor是不是这样使用的呢？答案是可以这样使用，但事实上我们一般不这么用。至于原因我们看下ScheduledThreadPoolExecutor的这两个方法的源码，如下：

```java
public void execute(Runnable command) {
        schedule(command, 0, NANOSECONDS);
    }
public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, NANOSECONDS);
    }
public Future<?> submit(Runnable task) {
        return schedule(task, 0, NANOSECONDS);
    }
public <T> Future<T> submit(Runnable task, T result) {
        return schedule(Executors.callable(task, result), 0, NANOSECONDS);
    }
```

可以看到ScheduledThreadPoolExecutor重写了从ThreadPoolExecutor继承来得这两个方法，并且将它们的工作委托给了一个schedule方法，而ScheduledThreadPoolExecutor中的schedule方法时公有的，我们可以直接使用。事实上大多数程序员也确实更习惯于直接调用schedule方法，而且这两个方法调用的schedule方法只可以实现延迟任务，要想实现周期性的任务必须调用schedule的变种方法。下面看一下schedule的源码。

### 延迟任务

ScheduledThreadPoolExecutor中实现延迟任务的方法有两个，分别可以接受Runnable和Callable，大致逻辑相同，不同只是在于Runnable需要转换成Callable。我们看一下其中一个方法的源码：

```java
/**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay,
                                           TimeUnit unit) {
        if (callable == null || unit == null)
            throw new NullPointerException();
        RunnableScheduledFuture<V> t = decorateTask(callable,
            new ScheduledFutureTask<V>(callable,
                                       triggerTime(delay, unit)));
        delayedExecute(t);
        return t;
    }
```

可以看着这个方法首先做了个判null，然后调用了一个decorateTask方法返回了一个对象，赋值给了RunnableScheduledFuture，我们看一下这个方法，它的源码如下：

```java
protected <V> RunnableScheduledFuture<V> decorateTask(
        Callable<V> callable, RunnableScheduledFuture<V> task) {
        return task;
    }
```

通过方法名，我们知道这个方法应该起到一个装饰器的作用，但事实上这个方法其实什么都没有做，直接返回了task。这里我们需要知道，这是一个hook方法，可以通过子类重写来实现我们想要的操作。但默认情况下是直接返回。

既然返回了task，那么我们就看看这个task到底是什么，下面我们来了解一下这个ScheduledFutureTask。

### ScheduledFutureTask

这是ScheduledFutureTask的一个内部类，继承于FutureTask，并实现了RunnableScheduledFuture接口，增加了对周期性任务的支持。主要方法一个run方法和一个cancel方法，具体实现也是委托给FutureTask的对应方法，对FutureTask感兴趣的读者可以看看笔者的另外一篇博客[FutureTask](https://thatboy.coding.me/2018/08/02/%E6%B5%85%E6%9E%90Executor%E6%A1%86%E6%9E%B6-2-FutureTask/)

ScheduledFutureTask类的篇幅过长，这里就不在具体介绍了。我们只通过上面的源码看看该如何使用它。通过上面的代码我们能看到，在获取到ScheduledFutureTask后，将它传入了一个delayedExecute方法中。我们看一下这个方法的源码。

```java
/**
     * Main execution method for delayed or periodic tasks.  If pool
     * is shut down, rejects the task. Otherwise adds task to queue
     * and starts a thread, if necessary, to run it.  (We cannot
     * prestart the thread to run the task because the task (probably)
     * shouldn't be run yet.)  If the pool is shut down while the task
     * is being added, cancel and remove it if required by state and
     * run-after-shutdown parameters.
     *
     * @param task the task
     */
    private void delayedExecute(RunnableScheduledFuture<?> task) {
        // 如果池已关闭，拒绝任务
        if (isShutdown())
            reject(task);
        else {
            // 添加任务
            super.getQueue().add(task);
            // 判断池是否关闭，及线程是否可以继续运行，如果不可以，移除并取消任务。
            if (isShutdown() &&
                // 该方法中，在两种情况下下能继续运行，运行状态，或者关闭状态但是需要设置变量为true。
                !canRunInCurrentRunState(task.isPeriodic()) &&
                remove(task))
                task.cancel(false);
            else
                ensurePrestart();
        }
    }
```

我们看一下这个canRunInCurrentRunState方法，它的相关源码如下：

```java
    boolean canRunInCurrentRunState(boolean periodic) {
        return isRunningOrShutdown(periodic ?
                                   continueExistingPeriodicTasksAfterShutdown :
                                   executeExistingDelayedTasksAfterShutdown);
    }
```

```java
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }
```

可以看到若想返回true，则当前状态处于RUNNING，或者处于SHUTDOWN时，且continueExistingPeriodicTasksAfterShutdown或者executeExistingDelayedTasksAfterShutdown的值为true，这两个是ScheduledThreadPoolExecutor的成员变量。

到这里我们还是没有找到这个任务是如何被运行的，是在这个ensurePrestart方法里吗？看名称并不像，并且这个方法中也没有传入task，我们知道运行线程需要调用FutureTask或者它的实现类的run方法，这里就是ScheduledFutureTask的run方法。当然我们看到这个task被放在了一个队列中，而这个队列是成员变量，或许ensurePrestart又从这个队列中获取了task，但好像没有这个必要，毕竟可以直接传入。我们先来看一下这个ensurePrestart方法吧，它的源码如下：

```java
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }
```

可以看到主要是一个addWorker方法，而这个方法继承于ThreadPoolExecutor，我们在[ThreadPoolExecutor](https://thatboy.coding.me/2018/08/05/%E6%B5%85%E6%9E%90ThreadPoolExecutor/) 这篇文章讨论过这个方法。但是与ThreadPoolExecutor不同的是，在ThreadPoolExecutor中是有可能直接向addWorker传入一个FutureTask直接执行它。而在ScheduledFutureTask中不行，我们必须从队列中获取它。下面来看一下ScheduledThreadPoolExecutor中的这个队列。

### DelayedWorkQueue

DelayedWorkQueue是一个优先级队列，有点类似于DelayQueue和PriorityQueue的实现。底层存储结构采用的是一个二叉堆数组，保证了最先被执行的任务一直在队列的顶端。在介绍这个队列之前，我们需要先了解一下二叉堆。在维基百科中对二叉堆的定义为：父节点小于任意一个子节点，且每个节点的左子树和右子树也是一个二叉堆。并且二叉堆可以分为大顶堆和小顶堆，显而易见DelayedWorkQueue中的堆是个小顶堆。下面通过几张图看看小顶堆到底是什么样的，以及它的数组表示形式。

{% asset_img 二叉堆节点图.png 二叉堆节点图 %}

它的数组结构可表示如下:

```java
[1,3,5,7,8,10,13]
```

仔细观察，我们会发现二叉堆数组的一个特性，它非常便于寻找一个节点的子节点和父节点。比如，假设某一个节点的下标为k，那么它的子节点和父节点满足下面这些特性。

- 子节点的下标k：k = 2 * i + 1 或者 k = 2 * i + 2 。（一个是左节点，一个是右节点，二叉堆对左右子节点的大小没有规定）
- 父节点的下标j：j = （i - 1）/ 2。 

介绍完二叉堆，下面我们来看看DelayedWorkQueue的源码，先看一下它的成员变量，源码如下：

```java
private static final int INITIAL_CAPACITY = 16;
        private RunnableScheduledFuture<?>[] queue =
            new RunnableScheduledFuture<?>[INITIAL_CAPACITY];
        private final ReentrantLock lock = new ReentrantLock();
        private int size = 0;

        /**
         * Thread designated to wait for the task at the head of the
         * queue.  This variant of the Leader-Follower pattern
         * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to
         * minimize unnecessary timed waiting.  When a thread becomes
         * the leader, it waits only for the next delay to elapse, but
         * other threads await indefinitely.  The leader thread must
         * signal some other thread before returning from take() or
         * poll(...), unless some other thread becomes leader in the
         * interim.  Whenever the head of the queue is replaced with a
         * task with an earlier expiration time, the leader field is
         * invalidated by being reset to null, and some waiting
         * thread, but not necessarily the current leader, is
         * signalled.  So waiting threads must be prepared to acquire
         * and lose leadership while waiting.
         */
        private Thread leader = null;

        /**
         * Condition signalled when a newer task becomes available at the
         * head of the queue or a new thread may need to become leader.
         */
        private final Condition available = lock.newCondition();
```

可以看到它的初始容量是16，根据经验，这个有初始容量的应该可以自动扩容，因此这个队列是一个无界队列。这个数组queue应该就是那个二叉堆数组，用来存储task。lock用来同步；size是大小，即任务数量；available用于阻塞线程。那么leader是什么呢？根据注释我们知道它就是具体从堆中取出任务的线程，前面说的worker线程在进入队列后，应该便会赋值给它。注释还说了它采用了一个Leader-Follower模式的变种，那这个模式到底是什么呢？

在这个模式中把线程分为三种状态：领导者Leader，追随者Follower，和处理者proccesser。其中Leader是唯一的，它会等待网络IO事件，而Follower很多，它们都在等待成为新的Leader。当一个事件进来时，Leader会首先从Follower中选举出一个新的Leader，然后就去处理这个事件，当处理完成后，它会进入Follower队列中等待再次成为Leader。这就形成了一个闭环来循环处理这些事件。

上面这段定义是笔者从网上搜索得到，读者如果希望更深入的了解它，可以查询相关资料。

下面看一下这个队列的入队和出队操作。

ScheduledThreadPoolExecutor中入队调用的是DelayedWorkQueue的add方法，我们看一下这个方法的源码：

```java
public boolean add(Runnable e) {
            return offer(e);
        }
```

可以看到它把事情委托给了一个offer方法，offer方法的源码如下：

```java
public boolean offer(Runnable x) {
            // 任务不能为null
            if (x == null)
                throw new NullPointerException();
            RunnableScheduledFuture<?> e = (RunnableScheduledFuture<?>)x;
            final ReentrantLock lock = this.lock;
            // 加锁
            lock.lock();
            try {
                int i = size;
                // 如果数组不够用，扩容。这里与HashMap这些的扩容不同，它没有扩容阈值，充满才扩容
                if (i >= queue.length)
                    grow();
                size = i + 1;
                // 第一次入队，直接放在队首。并且将当前任务的索引位存入heapIndex，为了便于取消任务
                if (i == 0) {
                    queue[0] = e;
                    setIndex(e, 0);
                } else {
                    // 加入元素，并调整重新建堆
                    siftUp(i, e);
                }
                // 堆顶元素被替换，即此时加入的任务比之前的堆顶任务有更短的到期时间，此时，重置leader，并
                // 重新选举
                if (queue[0] == e) {
                    leader = null;
                    available.signal();
                }
            } finally {
                lock.unlock();
            }
            return true;
        }
```

下面看一下它的扩容和建堆算法。扩容方法如下：

```java
/**
         * Resizes the heap array.  Call only when holding lock.
         */
        private void grow() {
            int oldCapacity = queue.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1); // grow 50%
            if (newCapacity < 0) // overflow
                newCapacity = Integer.MAX_VALUE;
            queue = Arrays.copyOf(queue, newCapacity);
        }
```

可以看到扩容到1.5倍。下面看一下siftUp的源码，如下：

```java
/**
         * Sifts element added at bottom up to its heap-ordered spot.
         * Call only when holding lock.
         */
        private void siftUp(int k, RunnableScheduledFuture<?> key) {
            // 如果堆中已有元素，进入循环，调整节点位置，直到找到正确的插入位
            while (k > 0) {
                // 找到父节点
                int parent = (k - 1) >>> 1;
                RunnableScheduledFuture<?> e = queue[parent];
                // 与父节点比较到期时间(即谁更早被执行)，如果不小于父节点，说明节点位置不需要调整，
                // 跳出循环
                if (key.compareTo(e) >= 0)
                    break;
                // 如果早于，与父节点调换位置，一直循环，直到找到正确的插入位
                queue[k] = e;
                setIndex(e, k);
                k = parent;
            }
            // 将元素插入找到的正确节点
            queue[k] = key;
            // 更新heapIndex的值
            setIndex(key, k);
        }
```

到这里offer方法介绍完了，下面看一下出队的take方法，它的源码如下：

```java
public RunnableScheduledFuture<?> take() throws InterruptedException {
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            try {
                for (;;) {
                    RunnableScheduledFuture<?> first = queue[0];
                    // 队空，等待
                    if (first == null)
                        available.await();
                    else {
                        // 得到延期时间
                        long delay = first.getDelay(NANOSECONDS);
                        // 到期，出队
                        if (delay <= 0)
                            return finishPoll(first);
                        first = null; // don't retain ref while waiting
                        // leader已经选举出，当前线程等待
                        if (leader != null)
                            available.await();
                        else {
                            Thread thisThread = Thread.currentThread();
                            leader = thisThread;
                            try {
                                // leander等待，直到到达延期时间，执行任务
                                available.awaitNanos(delay);
                            } finally {
                                // 如果不等于null，置为null
                                if (leader == thisThread)
                                    leader = null;
                            }
                        }
                    }
                }
            } finally {
                // 线程返回之前，重新选举leader
                if (leader == null && queue[0] != null)
                    available.signal();
                lock.unlock();
            }
        }
```

DelayedWorkQueue到这里就介绍完了，下面继续上面的任务调度执行，我们来看一下周期性任务和延时任务是有什么区别。我们都知道最终执行任务调用的是ScheduledFutureTask的run方法，我们来看一下它的源码，如下：

```java
/**
         * Overrides FutureTask version so as to reset/requeue if periodic.
         */
        public void run() {
            boolean periodic = isPeriodic();
            if (!canRunInCurrentRunState(periodic))
                cancel(false);
            else if (!periodic)
                ScheduledFutureTask.super.run();
            else if (ScheduledFutureTask.super.runAndReset()) {
                // 计算下次执行时间
                setNextRunTime();
                // 重新周期执行任务
                reExecutePeriodic(outerTask);
            }
        }
```

可以看到在这方法里，它区分的延时任务和周期性任务。如果是延时任务，调用run方法；如果是周期性的，则调用runAndReset方法，并且还执行了两个后置方法。我们看一下这两个方法。

```java
/**
         * Sets the next time to run for a periodic task.
         */
        private void setNextRunTime() {
            long p = period;
            if (p > 0)
                time += p;
            else
                time = triggerTime(-p);
        }
```

通过period设置下次执行时间，对这个变量的介绍如下：

```java
/**
         * Period in nanoseconds for repeating tasks.  A positive
         * value indicates fixed-rate execution.  A negative value
         * indicates fixed-delay execution.  A value of 0 indicates a
         * non-repeating task.
         */
        private final long period;
```

可以看到它有三种取值。

- 0：非周期性任务
- 正数：表明是fixed-rate模式的周期任务，该模式下间隔时间从任务刚开始执行开始计算。
- 负数：表明是fixed-delay模式的周期任务，该模式下间隔时间从任务执行结束后计算。

ScheduledThreadPoolExecutor便是通过这个值来区分任务类型的。

### 总结

ScheduledThreadPoolExecutor就介绍到这里了。本文算是笔者边读源码边做的笔记逻辑有些混乱，读者若有不明白之处或者发现错误疏漏之处，可以在评论中留言，或者查询其他资料来解惑。一家之言不可全信，笔者当自行验证之。ßß