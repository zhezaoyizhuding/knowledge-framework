---
title: 浅析Executor
date: 2018-08-01 23:31:43
categories: Java源码浅析
tags:
- Executor
- Runnable
- Callable
- Future
- FutureTask
---

Executor是JUC包中用于实现异步任务的框架，使用它可以有效降低并发编程的复杂性。整个框架大致可以分为三个部分：用于执行任务的Excutor，任务本身Runnable或者Callable，及异步获取执行结果的Future。下面就通过这仨个部分简单介绍下Executor的整体架构。

### Executor

Executor接口是框架的核心，它下面有 一系列的继承体系，主要实现类有ThreadPoolExecutor和ScheduledThreadPoolExecutor和一个工具类Executors。下面看一下Executor的继承体系，两个实现类的具体实现我们在后续的文章中再详细分析。

{% asset_img Executor继承图.png Executor继承图 %}

下面我们按照这个继承图依次了解一下它们的源码。先看一下最上层的Executor接口，它的源码如下：

```java
public interface Executor {

    /**
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the {@code Executor} implementation.
     *
     * @param command the runnable task
     * @throws RejectedExecutionException if this task cannot be
     * accepted for execution
     * @throws NullPointerException if command is null
     */
    void execute(Runnable command);
}
```

可以看到它内部只有一个待实现的execute方法，用于执行任务。我们也能看到它采用了设计模式中的命令模式，将任务的调用者和处理着解耦分离，我们不需要了解任务是如何执行的，我们只管向里面放入任务即可。Executor有一个子接口ExecutorService提供了更多的功能，下面我们看一下它的源码。

它提供了如下的接口方法：

```java
// 等待线程全部执行结束，成功返回true，超时返回fales
boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
// 批量调用，传入一个任务集合返回一个Future集合，Future中为结果值或者异常
<T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException;
// 上面方法的超时版本，规定时间内，返回已经完成的列表
<T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                  long timeout, TimeUnit unit)
        throws InterruptedException;
// 批量处理，返回任务中任意一个结果
<T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException;
// 上个方法的超时版本
<T> T invokeAny(Collection<? extends Callable<T>> tasks,
                    long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
// 监控方法，判断executor是否被shut down
boolean isShutdown();
// 监控方法，判断是否所有的任务被完成，并且executor已经被关闭。必须在shutdown或者shutdownNow被调用后，这个方法才有可能返回true
boolean isTerminated();
// 关闭executor，不再接受新的task，但是已经进入排队的task会继续执行
void shutdown();
// 立刻关闭executor，不再接受新的task，并且会清除队列中排队的task，尝试中断正在执行的task（但并不保证）。它会返回正在等待的task列表
List<Runnable> shutdownNow();
// 提交Callable型的task
<T> Future<T> submit(Callable<T> task);
// 提交Runnable型的task
Future<?> submit(Runnable task);
// 提交Runnable型的task，并指定返回的结果类型(因为Runnable是不支持泛型的)
<T> Future<T> submit(Runnable task, T result);
```

以上就是ExecutorService接口中提供的可供实现的方法，我们需要重点关注的是它的invoke，shutdown，submit等方法。它为Executor框架制定了基调，后续的实现类再进行自己的实现。本篇博客只是大而全的介绍下Executor框架的结构，具体实现类，比如ThreadPoolExecutor和ScheduledThreadPoolExecutor，笔者打算在后面博客再进行详细介绍。

### Runnable & Callable

Runnable是一个比较古老的任务类型，早在JDK1.0就已经存在，在很长的一段时间类，它都是作为Thread对象的靶对象使用，即通过线程调用它来执行一个任务，它的源码如下：

```java
@FunctionalInterface
public interface Runnable {
    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see     java.lang.Thread#run()
     */
    public abstract void run();
}
```

可以看到它只有一个run方法，用于执行具体的动作，当然，我们看到它上面多了一个注解，这是从Java 8开始对它进行了Lamdba表达式的支持。但是逐渐的它的功能不再足够，比如它无法抛出异常来展示线程执行失败，也无法返回线程执行完成的结果，我们必须通过其他方法来间接的达到这个目的。因此从JDK1.5开始Java提供了一个新的任务类型Callbale来替代它，我们来看一下Callbale的源码。

```java
@FunctionalInterface
public interface Callable<V> {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    V call() throws Exception;
}
```

我们可以看到Callable与Runnable很像，只是将run方法替换成了call方法，不同处在于Callable是支持泛型的，它可以返回任务执行的结果，并且可以抛出异常。

### Future

上面我们介绍了任务的调用者与任务的具体执行者，那么还剩下的一部分就是如何获取任务的执行结果，这样就构成了完整的Executor框架，而获取任务结果就是由Future来完成的，下面看下它的继承结构。

{% asset_img Future继承结构图.png Future继承结构图 %}

下面我们先看看Future接口中有哪些方法，它们的源码如下

```java
// 如果任务还没有执行，取消它；如果已经执行，传入true尝试去中断它，传入false则任务会继续执行完毕。正常取消了线程返回true，没有成功取消或者线程其实已经执行完成，则返回false
boolean cancel(boolean mayInterruptIfRunning);
// 获取任务执行的结果，在实现类FutureTask中这个方法会阻塞，直到获取到结果或者抛出异常
V get() throws InterruptedException, ExecutionException;
// 上面方法的超时版本
V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
// 监控方法，判断任务是否已经被取消，如果cancel方法被调用，则这个方法返回true
boolean isCancelled();
// 监控方法 判断任务是否完成(正常执行完毕，抛出异常或者被取消都算完成)；如果cancel方法被调用，则这个方法返回true
boolean isDone();
```

上面就是Future提供的待实现的接口方法，它的主要实现类是FutureTask类。从上面的继承结构图中我们可以看到FutureTask继承于RunnableFuture，而RunnableFuture继承于Runnable和Future。所以FutureTask同时也具有Runnable的特性，这说明在传入Runnable的地方也可以传入FutureTask，比如上面Executor的execute方法，直接向里面传入FutureTask对象也是一种获取任务结果的方法，可以实现和submit一样的功能。

### 总结

本篇博文就简单介绍下Executor的整体架构，后续博客笔者将继续详细介绍Executor框架中FutureTask，ThreadPooExecutorl，ScheduledThreadPoolExecutor，Executors等成员的具体实现细节。