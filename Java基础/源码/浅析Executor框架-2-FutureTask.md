---
title: 浅析Executor框架-2-FutureTask
date: 2018-08-02 22:36:15
categories: Java源码浅析
tags:
- Future
- FutureTask
---

FutureTask继承于Future，一直到JDK1.8它都是Future的主要实现类，用于获取任务的执行结果。在JDK1.8的注释中是这样介绍这个类的。

> 这个类提供了一个Future的基本实现，它可以开始、取消一个任务（该任务是异步可取消的），或者查询该任务是否完成，并且可以获取任务执行的结果。这个结果只有当任务执行完成后才可被获取；获取任务结果通过它的get方法，并且该方法会被阻塞直到任务执行完成。一旦任务执行完成，它将不能被再次开始或者取消，除非通过runAndReset方法调用该任务。
>
> FutureTask可以去包裹一个Runnable或者Callable对象，因为它实现了Runnable接口，也因此它可以被放在Executor中执行。
>
> 这个类除了作为一个功能完善的类对外提供服务之外，还提供了一些protected方法用于我们自定义一个继承于该类的类，来实现我们需要的功能。

下面我们就简单看一下FutureTask的源码。

### 源码分析

我们先看一下FutureTask的成员变量，源码如下：

```java
/**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    // 任务运行的状态，主要有以下其中
    private volatile int state;
    private static final int NEW          = 0;
    private static final int COMPLETING   = 1;
    private static final int NORMAL       = 2;
    private static final int EXCEPTIONAL  = 3;
    private static final int CANCELLED    = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED  = 6;

    /** The underlying callable; nulled out after running */
    // 任务接口
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    // 任务执行的结果
    private Object outcome; // non-volatile, protected by state reads/writes 
    /** The thread running the callable; CASed during run() */
    // 执行任务的线程
    private volatile Thread runner; 
    /** Treiber stack of waiting threads */
    // 等待队列
    private volatile WaitNode waiters;
```

可以看到FutureTask中主要有上面5个成员变量，它们的用途可以参考注释。注意到WaitNode类型的变量，这是FutureTask的内部类，看命名我们可以猜测它是一个用于存储等待线程的队列，下面我们看一下它的源码。

```java
    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }
```

可以看到它确实就是一个记录线程引用的结构体，采用链表结构实现。

下面看一下FutureTask的构造函数，FutureTask有两个构造函数，可以分别包装Callable型和Runnable型的任务。它们的源码如下：

```java
    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }
```

可以看到这两个构造函数就是传入两种任务类型，并且初始化state为New。细心的读者可以看到第二个构造函数比第一个多传入了一个参数，这是因为Runnable接口不支持泛型，因此需要一个参数来指定它的结果类型。至于任务执行的结果是直接赋给这个引用，还是新建一个新的引用，在笔者看来并不重要，因为我们最终是通过FutureTask的get方法来得到它的。因此这里笔者认为主要的是它的类型，并使用这个类型通过Executors的工厂方法将Runnable转化为Callable。下面我们就看看具体的get方法实现。它的源码如下：

```java
    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }
```

代码中首先判断state是否小于等于COMPLETING，即若任务处于新建或者正在运行的状态时 ，调用awaitDone方法，我们看一下这个方法的实现。它的源码如下：

```java
/**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            // 判断线程是否中断，如果中断，删除当前节点并抛出异常
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            // 满足了这个条件，说明当前任务可能处于四种状态：
            // NORMAL 正确执行完成
            // EXCEPTIONAL 异常
            // CANCELLED 取消
            // INTERRUPTING 或者INTERRUPTED 中断
            // 但无论哪种状态，都说明该任务已经结束，返回当前状态，并做一些现场清理
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            // 任务运行中，当前线程让出CPU，避免资源浪费，因为需要等待任务执行完成
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            // 第一次循环会进入，初始化q
            else if (q == null)
                q = new WaitNode();
            // 第一次循环会进入，构建waiters链表，采用头插法，会形成一个先进后出的栈结构
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            // 判断get方法的版本，因为get方法还有一个超时版本，超时了清理现场，返回状态。在get方法的超时
            // 版本中会根据这个状态判断抛出TimeoutException
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                // 挂起
                LockSupport.parkNanos(this, nanos);
            }
            else
                LockSupport.park(this);
        }
    }
```

上面就是awaitDone的执行流程，下面看一下report方法，它的源码如下：

```java
    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)
            return (V)x;
        if (s >= CANCELLED)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }
```

可以看到这方法只是判断任务是否正常执行，如果是，返回结果；如果不是，抛出异常。

读者根据上面的注释大致可以明白get方法的执行流程，但细心的读者可能会疑惑一个问题，上面的无论是awaitDone方法，还是report方法都是对成员变量的读，那么写是在哪里呢？FutureTask是在哪里给state赋予不同的状态，并将任务结果赋值给outcome的？毕竟在我们使用Future的过程，我们只要调用get方法就能获取到任务的执行结果了，上面的代码流程似乎也没有涉及state和outcome的改变，最让人疑惑的是FutureTask中也没有发布对这些改变的公有方法。

这里就需要理解Executor完整的执行流程，对state修改的入口是从Executor的execute方法开始的，当我们把任务放入execute方法时，它最终会调用到FutureTask的run方法，并在这里执行任务的run方法，并且改变状态。下面我们看一下FutureTask的run方法。它的源码如下：

```java
public void run() {
        // 如果state不是NEW，或者runner不是null，说明任务已经被运行。即该方法已经被调用多了，重复调用
        // 无用
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            // 获取当前任务
            Callable<V> c = callable;
            // 执行任务，并返回结果
            if (c != null && state == NEW) {
                V result;
                // 标志位，标志任务执行过程中是否有异常发生
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                  // 出现异常，清理现场，修改标志位，设置state状态为异常状态
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                // 如果没有异常发生，设置结果及state状态
                if (ran)
                    set(result);
            }
            // 清理现场
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }
```

下面看一下run方法中调用的其他方法。我们先看一下setException方法的源码。如下：

```Java
/**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }
```

如果state还是处于NEW状态，也就是没有被设置为其他状态，则设置当前状态为EXCEPTIONAL。并最终调用finishCompletion方法清理现场。下面我们看一下finishCompletion方法的源码。

```java
/**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            // 判断waiters是否被改变
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                // 清理WaitNode链表栈
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
    }
```

可以看到这个方法就是清理WaitNode，唤醒在get方法中挂起的线程，并将callable置为null。同时还可以看到他调用了一个done方法，我们看一下这个方法的源码，如下。

```java
/**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }
```

可以看到这个方法什么都没干，这其实就是FutureTask提供的一个hooks，只是用于给开发者自定义使用的。如果FutureTask的finishCompletion满足不了我们的要求，那么我们就可以继承FutureTask，并实现done方法，做一些我们希望的处理。

下面看一下run方法中set方法，这是任务正常结束会调用的方法。下面看一下set方法的源码，如下：

```java
/**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }
```

与setException不同的地方就是，它将任务状态设为NORMAL而不是EXCEPTIONAL。

run方法还要最后一个方法handlePossibleCancellationInterrupt，这个方法用于处理中断。它的源码如下，就是一个循环让步。

```java
/**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }
```

到这里run方法就看完了，下面看一下FutureTask的其他发布方法。先看一下cancel方法，它的源码如下：

```java
public boolean cancel(boolean mayInterruptIfRunning) {
        // 判断任务是否处于初始状态，并在此期间没有被改变；如果是，那么改变任务状态，并返回false，因为此时
        // 还没有确定取消
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            // 如果mayInterruptIfRunning为true，中断runner线程。
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }
```

这个方法传入了一个mayInterruptIfRunning参数，用于标识是否中断，true表示中断线程。它的执行流程如上面注释所说的。

FutureTask发布的方法还有两个监控方法，如下：

```java
  public boolean isCancelled() {
        return state >= CANCELLED;
    }
```

```java
public boolean isDone() {
        return state != NEW;
    }
```

从这两个方法中我们可以看到，state的状态大于等于CANCELLED，就可以视为取消。即中断也可视为取消。state不为NEW就可以视为done。

### 总结

到这里FutureTask就介绍完了。本篇博客是边看源码边写成的，且笔者水平有限，难免有些谬误。读者在读到此篇博客时，若有疑惑，当自己反复查证。