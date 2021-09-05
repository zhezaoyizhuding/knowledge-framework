---
title: 浅析Executor-6-CompletionService
date: 2018-08-07 22:15:50
categories: Java源码浅析
tags:
- CompletionService
---

CompletionService用于批量任务的处理，它内部内置了一个线程池来执行任务，并使用一个队列来存储任务执行的结果。下面我们来研究一下这个类的内部实现。

### 源码分析

我们先来看一下CompletionService的成员变量，它有三个成员变量，源码如下：

```java
    private final Executor executor;
    private final AbstractExecutorService aes;
    private final BlockingQueue<Future<V>> completionQueue;
```

在这三个成员变量中，executor用于执行任务，completionQueue用于存储任务结果，这两个变量已经足够实现功能。至于aes，笔者在代码中看到只是通过来获取FutureTask的实例，并且这个变量允许为空。

下面看一下构造函数，CompletionService有两个构造函数，源码如下：

```java
public ExecutorCompletionService(Executor executor) {
        if (executor == null)
            throw new NullPointerException();
        this.executor = executor;
        this.aes = (executor instanceof AbstractExecutorService) ?
            (AbstractExecutorService) executor : null;
        this.completionQueue = new LinkedBlockingQueue<Future<V>>();
    }
```

```java
public ExecutorCompletionService(Executor executor,
                                     BlockingQueue<Future<V>> completionQueue) {
        if (executor == null || completionQueue == null)
            throw new NullPointerException();
        this.executor = executor;
        this.aes = (executor instanceof AbstractExecutorService) ?
            (AbstractExecutorService) executor : null;
        this.completionQueue = completionQueue;
    }
```

可以看到两个构造函数中，第一个默认队列是一个无界队列，而第二个可以指定队列的具体实现。构造函数中没什么特殊的地方，只是进行一些赋值操作，并且有一个校验，executor与completionQueue任意一个都不可为空。

下面看一下该类具体的使用，先从提交任务开始，CompletionService也有一个submit方法，源码如下：

```java
    public Future<V> submit(Callable<V> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task);
        executor.execute(new QueueingFuture(f));
        return f;
    }
```

```java
    public Future<V> submit(Runnable task, V result) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task, result);
        executor.execute(new QueueingFuture(f));
        return f;
    }
```

可以看到任务还是放到executor中去执行，但是在此之前它通过newTaskFor得到了一个RunnableFuture，我们看一下这个方法的源码，如下：

```java
    private RunnableFuture<V> newTaskFor(Callable<V> task) {
        if (aes == null)
            return new FutureTask<V>(task);
        else
            return aes.newTaskFor(task);
    }
```

这里只看一下其中一个版本，逻辑都差不多，都只是获取一个FutureTask实例。前面代码中看到它又将这个FutureTask放在了一个QueueingFuture中，我们看一下这个类是什么。

```java
    /**
     * FutureTask extension to enqueue upon completion
     */
    private class QueueingFuture extends FutureTask<Void> {
        QueueingFuture(RunnableFuture<V> task) {
            super(task, null);
            this.task = task;
        }
        protected void done() { completionQueue.add(task); }
        private final Future<V> task;
    }
```

这是CompletionService，可以看到它主要的就是重写了FutureTask的done方法，并在里面进行了一个入队操作。而done方法笔者之前在介绍FutureTask时介绍过，这是一个hook方法（即可被子类重写的protected方法），在任务执行完成时会被调用。所以这个内部类的作用就是进行一个入队操作，将任务的执行结果放入completionQueue中。

下面看一下对任务结果的获取。通过前面的分析我们知道任务的执行结果都放在了completionQueue中，而在这个类的外部我们要想获取任务的执行结果，只需要进行出队操作就行。CompletionService提供了两种类型的出队。

##### take

该方法用于获取队首的元素，如果队列为空，它会一直阻塞，知道获取元素。它的源码如下：

```java
    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }
```

##### poll

轮询，也是用于获取队首的元素，但是如果队列为空，它不会阻塞，而是返回null。

```java
    public Future<V> poll() {
        return completionQueue.poll();
    }
```

它还有另外一个版本，实现了一个长轮询。即如果队列为空，它会阻塞一段时间，如果在这段时间还是没有元素，才会返回null。源码如下:

```java
    public Future<V> poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }
```

这些方法都是对队列方法的复用，要想研究具体的实现，还需要进入到具体队列的源码中分析。这里笔者就不在赘述，感兴趣的对着可以自行研究。

### 总结

CompletionService的实现比较简单，因为都是对其他更底层的框架的复用。如果我们理解了线程池，FutureTask，和队列的实现，再来理解它就很简单了。