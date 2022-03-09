---
title: 浅析Java并发工具类(3) - Semaphore
date: 2018-07-25 14:05:12
categories: Java源码浅析
tags:
- Semaphore
---

Semaphore是将要介绍的第三个并发工具类，用于限制访问某个公共资源的线程数量。它底层依托于AQS的实现，采用一个许可证permits来限制访问的线程的数量，只有获取许可证的线程才可访问该资源。假如只允许10个线程同时访问该资源，那就设置10个permits，如果10个permits都被占用，那么其他线程只能等待，直到有些permits被释放。下面看看它的源码实现。

### 源码分析

Semaphore只有一个成员变量sync，它的类型是Semaphore的一个内部类，继承于AQS。这点有点类似于ReentrantLock的实现，不同点在于ReentrantLock使用的是AQS的独占模式，而Semaphore使用的是它的共享模式。与ReentrantLock相同的是，Semaphore也提供了公平锁和非公平锁两个版本可供选择。下面看一下Sync的源码：

```java
/**
     * Synchronization implementation for semaphore.  Uses AQS state
     * to represent permits. Subclassed into fair and nonfair
     * versions.
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;

        Sync(int permits) {
            setState(permits);
        }

        final int getPermits() {
            return getState();
        }

        final int nonfairTryAcquireShared(int acquires) {
            for (;;) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }

        protected final boolean tryReleaseShared(int releases) {
            for (;;) {
                int current = getState();
                int next = current + releases;
                if (next < current) // overflow
                    throw new Error("Maximum permit count exceeded");
                if (compareAndSetState(current, next))
                    return true;
            }
        }

        final void reducePermits(int reductions) {
            for (;;) {
                int current = getState();
                int next = current - reductions;
                if (next > current) // underflow
                    throw new Error("Permit count underflow");
                if (compareAndSetState(current, next))
                    return;
            }
        }

        final int drainPermits() {
            for (;;) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0))
                    return current;
            }
        }
    }
```

可以看到所谓的许可证permits，即是AQS中的state。主要提供了获取锁，释放锁，减少permits数量及清空permits的方法。它有两个实现类，对应公平锁和非公平锁的实现。源码如下：

公平锁

```java
/**
     * Fair version
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }

        protected int tryAcquireShared(int acquires) {
            for (;;) {
                if (hasQueuedPredecessors())
                    return -1;
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 ||
                    compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }
```

非公平锁

```java
/**
     * NonFair version
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }

        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }
```

公平锁和非公平锁在实现上其实就是公平锁多了一个校验，判断它是否存在前置节点，如果存在的话，则不允许获取锁。在功能上，公平锁可以有效遏制线程饥饿现象；而非公平锁有更大的吞吐率。使用时可以根据需求选择，默认实现也是非公平的。

下面看看它的构造函数。Semaphore有两个构造函数，源码如下：

```java
/**
     * Creates a {@code Semaphore} with the given number of
     * permits and nonfair fairness setting.
     *
     * @param permits the initial number of permits available.
     *        This value may be negative, in which case releases
     *        must occur before any acquires will be granted.
     */
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    /**
     * Creates a {@code Semaphore} with the given number of
     * permits and the given fairness setting.
     *
     * @param permits the initial number of permits available.
     *        This value may be negative, in which case releases
     *        must occur before any acquires will be granted.
     * @param fair {@code true} if this semaphore will guarantee
     *        first-in first-out granting of permits under contention,
     *        else {@code false}
     */
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
```

主要是指定许可证permits的数量，第二个构造函数可以选择是否使用公平锁。

下面看一看Semaphore的逻辑实现，Semaphore是一个典型的代理实现，它底层的所有实现都是委托给内部类Sync，最终依托于AQS共享模式的实现。它的方法主要分为两类，获取锁和释放锁还有一些用于监控的辅助方法。本文并不想介绍AQS的内部实现，因此这些方法就没法详细分析，这里只对它的公有方法进行简单列举。若对AQS感兴趣，可以看一下笔者的另一篇博客[浅析AbstractQueuedSynchronizer](https://thatboy.coding.me/2018/05/07/%E6%B5%85%E6%9E%90AbstractQueuedSynchronizer/)。

获取锁：

```java
    public void acquire() throws InterruptedException 
    public void acquire(int permits) throws InterruptedException
    public void acquireUninterruptibly()
    public void acquireUninterruptibly(int permits)
```

尝试获取一次锁：

```java
public boolean tryAcquire()
public boolean tryAcquire()
public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException
public boolean tryAcquire(long timeout, TimeUnit unit)
        throws InterruptedException
```

释放锁：

```java
public void release()
public void release(int permits)
```

其他重要方法：

```java
// 返回可用许可证数目
public int availablePermits()
// 清除所有许可证
public int drainPermits()
```

### 使用示例

下面是笔者在Semaphore的注释示例中抠出的代码，从这段代码中我们看看Semaphore的用法。代码如下：

```java
 class Pool {
    private static final int MAX_AVAILABLE = 100;
    private final Semaphore available = new Semaphore(MAX_AVAILABLE, true);
 
    public Object getItem() throws InterruptedException {
      available.acquire();
      return getNextAvailableItem();
    }
 
    public void putItem(Object x) {
      if (markAsUnused(x))
        available.release();
    }
 
    // Not a particularly efficient data structure; just for demo
 
    protected Object[] items = ... whatever kinds of items being managed
    protected boolean[] used = new boolean[MAX_AVAILABLE];
 
    protected synchronized Object getNextAvailableItem() {
      for (int i = 0; i < MAX_AVAILABLE; ++i) {
        if (!used[i]) {
           used[i] = true;
           return items[i];
        }
      }
      return null; // not reached
    }
 
    protected synchronized boolean markAsUnused(Object item) {
      for (int i = 0; i < MAX_AVAILABLE; ++i) {
        if (item == items[i]) {
           if (used[i]) {
             used[i] = false;
             return true;
           } else
             return false;
        }
      }
      return false;
    }
  }}
```

这是一段简单的demo代码，可能并不能实现某项功能，但从中我们可以分析出Semaphore该如何使用。上面这段代码中items可以视为一类资源，比如连接池。示例假如有100个资源，并且采用的是公平锁来防止线程饥饿。getItem方法用于获取资源，putItem用于释放资源，我们看到在每次获取资源时，都需要通过Semaphore获取个许可证；释放时也需要同时将这个许可证释放，以便供后续线程调用。当然示例中将获取资源，释放资源的操作委托给了另外两个方法，并通过一个布尔变量标识资源是否已经被获取。

### 结束语

Semaphore就介绍到这里了，事实上这些工具类我们用到的地方并不多，就是使用也是用来设计其他的并发类。但是翻看源码可以让我了解大神的设计思想，也是一件乐事。





