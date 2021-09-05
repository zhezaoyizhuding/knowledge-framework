---
title: 浅析Executor-5-Executors
date: 2018-08-07 22:13:30
categories: Java源码浅析
tags:
- Executors
---

Executors是Executor框架中的一个工厂类，用它我们可以创建特定的线程池，线程工厂，以及将Runnable转换为Callable等。下面我们通过源码来仔细看看这个类的工厂方法们。

### ExecutorService相关

Executors中有很多返回ExecutorService的方法，用于返回一个个不同类型的线程池，下面我们分别看下。

- newSingleThreadExecutor

创建一个单线程的Executor。源码如下：

```java
    public static ExecutorService newSingleThreadExecutor() {
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>()));
    }
```

```java
public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
       
```

可以看到它返回的真实类型其实不是ThreadPoolExecutor，而是它的一个包装类，增加了一个finalize方法，队列采用的是无界队列LinkedBlockingQueue。

- newFixedThreadPool

创建指定线程数量的线程池。源码如下：

```java
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>());
    }
```

```java
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>(),
                                      threadFactory);
    }
```

可以看到要创建固定数量的线程池，将corePoolSize和maximumPoolSize设为相等就行了。

- newCachedThreadPool

创建一个可缓存的线程池，调用execute将重用以前构造的线程（如果线程可用）。如果现有线程没有可用的，则创建一个新线程并添加到池中。终止并从缓存中移除那些已有60秒钟未被使用的线程。源码如下（每种线程池都会有一个指定线程工厂的版本，后面就不在列出源码了）：

```java
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }
```

可以看到它将corePoolSize设置为0，即池中没有常驻线程，maximumPoolSize最大值，即线程数量任意。最后使用一个同步队列来存储任务，事实上该队列不会缓存任务，而是直接将它将给线程执行。

- newWorkStealingPool

用于工作密取的线程池。源码如下：

```java
    public static ExecutorService newWorkStealingPool(int parallelism) {
        return new ForkJoinPool
            (parallelism,
             ForkJoinPool.defaultForkJoinWorkerThreadFactory,
             null, true);
    }
```

这个使用的很少，但事实上在多CPU环境下，工作密取得性能更好。它存储任务采用的是多个双端队列，当某个队列任务跑空时，线程可以从其他的队列尾端偷取任务执行。

### ScheduledExecutorService相关

Executors中还要一些创建定时任务的线程池，比如：

- newSingleThreadScheduledExecutor

创建一个单线程的定时任务线程池。源码如下：

```java
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new DelegatedScheduledExecutorService
            (new ScheduledThreadPoolExecutor(1));
    }
```

返回的真实类型也是ScheduledThreadPoolExecutor的一个包装类。事实上真正任务还是委托给ScheduledThreadPoolExecutor在干的，笔者也不明白它这包装一层的好处在哪。

- ScheduledThreadPoolExecutor

创建定时任务线程池。源码如下：

```java
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }
```

创建一个指定核心线程数的线程池，ScheduledThreadPoolExecutor笔者会在后续的文章中继续介绍。

### Callable相关

Executors中还要一些将Runnable转换为Callable的方法，如下面。

```java
    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<T>(task, result);
    }
```

```java
    public static Callable<Object> callable(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<Object>(task, null);
    }
```

在使用线程池时，如果传入的任务类型是Runnable，事实上它会最终使用这两个方法将它转换为Callable。可以看到它采用了一个适配器来转换Runnable，我们看一下这个适配器。源码如下：

```java
/**
     * A callable that runs given task and returns given result
     */
    static final class RunnableAdapter<T> implements Callable<T> {
        final Runnable task;
        final T result;
        RunnableAdapter(Runnable task, T result) {
            this.task = task;
            this.result = result;
        }
        public T call() {
            task.run();
            return result;
        }
    }
```

可以看到它继承于Callable，依照适配器模式设计而成，事实上就是作为Runnable的代理。

### 总结

Executors就介绍到这里，Executors只是一个工具类，我们只需要知道它有哪些工厂方法，能干那些事就行了，不需要太过深入了解它的原理，当然事实上也没有太多可以深读的东西。

