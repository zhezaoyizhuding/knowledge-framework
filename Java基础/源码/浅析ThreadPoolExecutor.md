---
title: 浅析Executor-3-ThreadPoolExecutor
date: 2018-08-05 22:34:59
categories: Java源码浅析
tags:
- ThreadPoolExecutor
---

本文是笔者浅析Executor框架系列第三篇，主要介绍下Exector的核心实现类ThreadPoolExecutor，该类便是我们常说的线程池的实现。至于线程池的好处有很多，比如复用，统一管理，批量处理等等，这里不再赘述。本文主要探讨ThreadPoolExecutor的具体实现，看一看一个任务放入线程池后是如何被执行的。下面具体看看它们的实现。

### ThreadPoolExecutor

我们首先看一下如何创建一个线程池，ThreadPoolExecutor有四个发布的构造函数，其中三个会调用第四个，只是指定不同的默认实现。因此我们这里只看下第四个构造函数的实现，来了解一下构建一个线程池所需要的参数。它的源码如下：

```java
public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }
```

下面介绍一下各个参数的含义：

- corePoolSize：核心线程池大小。在线程池被创建后，池里是没有线程的，只有当有任务进来时才会创建线程去执行任务（当然也可以调用prestartCoreThread或者prestartAllCoreThreads去预先创建线程）。而核心线程大小表示当池中线程少于这个值时，每有任务进来，都会创建一个新的线程去执行它；当线程数量等于或者大于这个值时，即池里没有空闲线程，此时会把任务暂存在任务队列中。此外，核心线程也表示池中常驻的线程，即当没有需要执行的任务时，这些线程不会被清除。（当然你也可以设置allowCoreThreadTimeOut(true)使核心线程在超出keepAliveTime后被清除，默认是false）
- maxinumPoolSize：池中存在的最大线程数量。当核心线程都在忙碌，并且任务队列也已经满时；此时，再有新的任务进来，线程池就会临时创建线程去执行这个任务。
- keepAliveTime：线程的保活时间，默认只有当池中线程数量超出核心线程数时该参数才会起作用，即默认该参数只对超出核心线程数目的线程起作用。
- unit：时间单位，会转换为纳秒
- workQueue：任务队列。根据情况的选择，有多种队列，这个下文再介绍。
- threadFactory：线程工厂，用于创建线程
- handler：任务拒绝策略。当线程数量达到maxinumPoolSize或者线程池被关闭时，此时线程池将无法再接收任务，线程池采用RejectedExecutionHandler来进行任务拒绝策略选择。

从上面的构造函数我们也能看到这些参数需要满足的条件，如下：

- corePoolSize不能小于0；maximumPoolSize不能小于0，且maximumPoolSize必须大于corePoolSize；keepAliveTime不能小于0
- workQueue，threadFactory，handler不能同时为null。

可以看到这个构造函数中还有一个acc变量，据注释的说明，这是一个会在调用finalizer时使用的上下文对象。

除开上面四个参数，后面三个参数都是可选的对象，这里我们再仔细介绍下这三个参数都有哪些选择。

workQueue：

- SynchronousQueue：同步队列。它不会保存提交的任务，而是直接将任务交给线程执行，若当前没有空闲线程，则会创建一个新的线程来执行任务。因此使用这个队列时，通常会将maxinumPoolSize设置的非常大。
- ArrayBlockingQueue：有界队列。基于数组，在创建时必须指定大小。
- LinkedBlockingQueue：无界队列。基于链表，如果没有指定大小，默认值为Integer.MAX_VALUE。

这些队列的具体实现由于篇幅过大，笔者打算在后续的文章中单独进行介绍。

threadFactory：

threadFactory如果没有指定的话会采用一个默认实现，这个默认实现的源码如下：

```java
    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }
```

```java
DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }
```

可以看到这个默认实现中，指定了线程的线程组和线程名的前缀。当然我们也可以通过实现ThreadFactory来定制线程的一些特性，比如线程名，线程组，优先级，守护线程状态等。这个实现类我们可以自己实现，也可以直接使用一些通用框架的实现类，比如apache-commons中的BasicThreadFactory类。

handler：

当线程池的任务缓存队列已满并且线程池中的线程数目达到maximumPoolSize时，或者线程池被关闭，如果还有任务到来就会采取任务拒绝策略，通常有以下四种策略：

- ThreadPoolExecutor.AbortPolicy:丢弃任务并抛出RejectedExecutionException异常。默认策略。
- ThreadPoolExecutor.DiscardPolicy：也是丢弃任务，但是不抛出异常。
- ThreadPoolExecutor.DiscardOldestPolicy：丢弃队列最前面的任务，然后重新尝试执行任务（重复此过程）
- ThreadPoolExecutor.CallerRunsPolicy：由调用线程处理该任务

这四个是ThreadPoolExecutor的内部类，皆实现了RejectedExecutionHandler接口，并重写了它的rejectedExecution方法，定制了不同的策略。下面看下这四个类的具体实现。

AbortPolicy的源码如下：

```java
/**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }
```

可以看到该实现类的rejectedExecution方法中只是抛出了RejectedExecutionException。

DiscardPolicy：

```java
/**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }
```

rejectedExecution方法中什么都没做。

```java
public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
```

可以看到这个rejectedExecution方法中，首先判断线程池是否被关闭，如果没有，则将队列的首元素出队，并执行当前任务。

CallerRunsPolicy：

```java
/**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }
```

可以看到在这个类的rejectedExecution方法中，如果线程池没有关闭，则直接在当前线程执行该任务。

上面我们了解了如何构造一个线程池，以及它各种构件的选择，下面我们来看一下线程池执行的过程。我们知道线程池在执行时一般调用的是submit方法或者直接调用execute方法，但事实上最终都会调用到execute方法，submit方法的三个版本如下：

```java
/**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<Void> ftask = newTaskFor(task, null);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task, result);
        execute(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }
```

可以看到它们实际上都是构造了一个RunnableFuture类型变量，然后传入execute方法中。事实上传入的都是一个FutureTask对象，我们看一下其中一个newTaskFor的实现就可明白，其他的newTaskFor版本类似，只是调用了FutureTask不同的构造方法，它的源码如下：

```java
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }
```

下面我们就重点看一下execute方法的实现，它的源码如下：

```java
public void execute(Runnable command) {
        // 任务不能为null
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        // 得到ctl中的值，该值是一个32位数，前三位表示线程状态，后29位表示线程数目。初始值为0
        int c = ctl.get();
       // workerCountOf(c)获取c的后29位，即比较池中的线程是否达到了corePoolSize
        if (workerCountOf(c) < corePoolSize) {
            // 没有达到，新建线程
            if (addWorker(command, true))
                return;
            // 更新c的值
            c = ctl.get();
        }
        // 线程池处于RUNNING状态，且任务队列未满
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            // 线程池已经关闭，且队列未空
            if (! isRunning(recheck) && remove(command))
                reject(command);
            // 若线程数量为0，新建线程
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        // 若队列已满，路由到此；若新建线程失败，采用拒绝策略
        else if (!addWorker(command, false))
            reject(command);
    }
```

execute的执行流程大致就如上面注释所说，下面我们看一下execute所涉及的其他方法。首先看看这个ctl.get()到底会得到一个什么，我们分析下面这些源码：

```java
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    // 当创建线程池后，初始时，线程池处于RUNNING状态；此时，能接受新提交的任务，并且也能处理阻塞队列中的任务
    private static final int RUNNING    = -1 << COUNT_BITS;
    // 关闭状态，不再接受新提交的任务，但正在执行的任务和阻塞队列中已保存的任务会继续执行完毕。在线程池处于
    // RUNNING状态时，调用shutdown()方法会使线程池进入到该状态
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    // 不再接受新的任务，并且会清除任务队列中任务和尝试中断当前正在运行的任务。在线程池处于RUNNING
    // 或SHUTDOWN状态时，调用 shutdownNow() 方法会使线程池进入到该状态
    private static final int STOP       =  1 << COUNT_BITS;
    // 如果所有的任务都已终止了，工作线程为0，任务队列为空，线程池进入该状态，并且之后会调用 terminated() 
    // 方法进入TERMINATED 状态
    private static final int TIDYING    =  2 << COUNT_BITS;
    // 在terminated() 方法执行完后进入该状态，默认terminated()方法中什么也没有做
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }
```

这里又是一个用到位运算的巧妙设计，作者将32位二进制数拆成两部分，前三位表示线程池状态，后29位表示线程数目。我们来一步步分析，逐步计算出这些常量的值。

- Integer.SIZE：这是Integer中的一个常量，值为32。
- COUNT_BITS：显而易见，比Integer.SIZE少3，值为29。
- CAPACITY：1左移29位减一，即为2^29 - 1，表示为二进制即为00011111111111111111111111111111。
- RUNNING：-1的二进制表示为11111111111111111111111111111111，左移29位为11100000000000000000000000000000。
- SHUTDOWN：0无论咋移还是0哈，所以它的二进制表示为00000000000000000000000000000000
- STOP：1的二进制表示为00000000000000000000000000000001，左移29位为00100000000000000000000000000000。
- TIDYING：2的二进制表示为00000000000000000000000000000010，左移29位为01000000000000000000000000000000。
- TERMINATED：3的二进制表示为00000000000000000000000000000011，左移29位为01100000000000000000000000000000。

仔细看下上面这些常量的二进制表示我们可以发现，容量的前三位为0，而状态的后29位为0。所以我们最终可以得出是上面的结论：前三位表示线程状态，后二十九位表示线程数量。

我们再看一下后面三个静态方法，既然CAPACITY为00011111111111111111111111111111，取反则为11100000000000000000000000000000，那么在runStateOf方法中无论c的值是什么，返回的都是头三位相与的结果；而在方法workerCountOf中返回的就是后29位相与的结果；ctlOf方法返回线程状态和线程数目的并集。

现在我们就可以计算出ctl的值了，即为RUNNING的值，-1 << 29。回到上面的execute方法workerCountOf(c)的初始值便为0，事实上我们只需要知道它表示线程池中的线程数量即可。

下面看一下addWorker的实现，它的源码如下：

```java
private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            // 获取池状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            // 如果线程池已经关闭，且后面三个条件至少有一个不满足，则返回false
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN &&
                   firstTask == null &&
                   ! workQueue.isEmpty()))
                return false;

            for (;;) {
                // 得到当前线程数目
                int wc = workerCountOf(c);
                // 超出最大容量，或者超出当前模式的容量，返回false
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                // 增加成功，跳出循环
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }
        // 标志位，标志worker是否添加成功
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            // 新建一个worker，这可以看成线程和任务的集合体
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 加锁
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        // 添加worker
                        workers.add(w);
                        int s = workers.size();
                        // 更新最大线程数目，这个值应该表示曾经最大，用于监控
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    // 启动线程，执行任务
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                // 失败，移除当前worker
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

其中Worker是ThreadPoolExecutor的内部类，它包裹了Thread和FutureTask，既是任务也会任务的调用者。我们看一下它的实现。下面是它的构造函数源码：

```java
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }
```

可以看到thread中放入的便是Worker本身，当调用thread的start方法时，线程启动，调用Worker的run方法。它的源码如下：

```java
/** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);
        }
```

它调用了一个runWorker方法，它的源码如下：

```java
final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            // 如果task，调用getTask从队列中获取
            while (task != null || (task = getTask()) != null) {
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }
```

这个方法中最终调用到了FutureTask的run方法，而在FutureTask的run方法中，最终会执行Callable的call方法，任务就这样被执行起来了。这里需要注意的是Worker并不是每次都持有FutureTask，如果没有持有，它会调用getTask从队列中获取。FutureTask的run方法笔者在之前的博客中介绍过了，这里不再赘述。

### 总结

ThreadPoolExecutor的源码就介绍到这里，由于笔者水平有限，有些地方笔者也没有完成搞清楚，但大致流程倒是理通了。其中不明之处读者可以自行查证，到时希望在评论里留言一二，告知笔者，这里万分感谢了。这是笔者Executor源码分析的第三篇，后续笔者会继续介绍Executor中其他的成员。