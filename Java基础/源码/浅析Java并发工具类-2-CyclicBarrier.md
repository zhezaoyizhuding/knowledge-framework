---
title: 浅析Java并发工具类(2) - CyclicBarrier
date: 2018-07-23 22:18:41
categories: Java源码浅析
tags:
- CyclicBarrier
---

CyclicBarrier的字面意思是可循环使用（Cyclic）的屏障（Barrier）,它的功能是让一组线程到达一个屏障（也就叫同步点）时被阻塞，直到最后一个线程到达屏障时，屏障才会打开，所有被屏障拦截的线程继续运行。值得一提的是，所谓的循环是指CyclicBarrier中的count是可以重置的(这点不同于CountDownLatch)，因此它可以被循环调用。下面我们通过源码来分析一下这个类。

#### 源码分析

CyclicBarrier类代码并不多，布局比较简单，我们先来看一下它有哪些成员变量。它的成员变量如下：

```java
/** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** The number of parties */
    private final int parties;
    /* The command to run when tripped */
    private final Runnable barrierCommand;
    /** The current generation */
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     */
    private int count;
```

CyclicBarrier主要有上面这些成员变量，我们通过注释和猜测大致可以猜出它们各自的用途。

- lock：保护下面的成员变量，比如count。CyclicBarrier的特性决定它肯定在多线程环境下被使用，这是就需要对它进行同步。
- trip：一个Condition类型的变量，用于线程之间的交互。
- parties：可以理解为线程的总数
- barrierCommand：一个任务型变量，在所有的parties线程执行完毕后执行，这个变量是可选的，在构造CyclicBarrier时我们可以选择使用或者不使用它。
- generation：Generation是CyclicBarrier的内部成员类。前面我们说过CyclicBarrier是可以重置的，而这个重置依靠的就是这个Generation，每次重置都是一个新的Generation。它有一个成员变量broken，用于标识这个CyclicBarrier是否已经损坏。
- count：还没到达CyclicBarrier point的线程数，每当一个线程到达，count就减一。

下面我们看一下CyclicBarrier的构造函数，CyclicBarrier有两个构造函数，可以选择是否创建barrierCommand。它们的源码如下：

```java
/**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }
```

我们可以看到构造函数中主要是对三个变量parties，count，barrierCommand的赋值，barrierCommand可以传入一个Runnable也可以传null；而parties和count的初始值相等。

介绍完成员变量和构造函数，我们来看一下CyclicBarrier的主要运行逻辑。CyclicBarrier对外发布的最主要的方法是下面两个：

```java
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }
```

```java
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }
```

而他们都依托于一个私有的dowait方法，下面我们来看看这个方法的逻辑。

```java
/**
     * Main barrier code, covering the various policies.
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();

            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }

            int index = --count;
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        command.run();
                    ranAction = true;
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            for (;;) {
                try {
                    if (!timed)
                        trip.await();
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                if (g != generation)
                    return index;

                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }
```

可以看到为了保证线程安全，它首先加了一把锁，而这个锁就是前面介绍的成员变量lock所指向的对象。然后获得了generation的引用，标明下面有一波操作要开始了，首先校验broken是否为true，如果为true，表示屏障已经破了，下面的逻辑的就没必要在运行了，抛出BrokenBarrierException。但默认broken是false的，所以第一个线程进来时不会走这条路劲。我们继续往下看，它又校验了当前线程是否被中断了，如果被中断了调用breakBarrier方法，并抛出InterruptedException。我们来看一下breakBarrier方法，它的源码如下：

```java
    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }
```

我们看到这个方法主要干了三件事，设置broken为true，重置count的值，唤醒其他线程。所以它的主要功能是当有一个线程中断时，唤醒其他线程（下面我们会看到每个进入dowait方法线程都会在一个await方法上阻塞），然后这些线程在醒来后，会去校验broken的值，然后唰唰的全抛出BrokenBarrierException，这就实现了CyclicBarrier的all-or-none的功能。all-or-none看名字我们也知道它表示这些线程要么全都成功，要么全都失败，类似一个事务。

我们继续往下看。它计算了一个index值，用于标识当前是第几个线程进入这个方法。如果是最后一个，即index等于0时，进入到if块中。在这个if块中主要干了两件事。一是启动barrierCommand线程，我们可以在里面整合屏障前所有子线程的结果。当然如果barrierCommand为null，即在创建时没有指定的话，就什么都不做。如果存在barrierCommand的话，它还设置了一个标识ranAction，和finally一起起到了一个catch的作用，来判定barrierCommand是否成功运行。如果没有，打破这个屏障，使它最终失败。另一件事就是barrierCommand运行成功，调用nextGeneration方法，我们看下nextGeneration的源码。

```java
    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    private void nextGeneration() {
        // signal completion of last generation
        trip.signalAll();
        // set up next generation
        count = parties;
        generation = new Generation();
    }
```

我们看到它唤醒了其他所有线程，重置了count的值，并新建了一个generation，标明第一代已经成功运行，可以开始下一代了。即重置为运行前的初始状态，实现了CyclicBarrier的可重置性。

继续往下看，下面是一个死循环，所有屏障前的线程都在此等待，直到broken, interrupted, timed out（抛出对应异常）。或者barrierCommand执行完成，新建generation后，即成功运行。进一步保证了CyclicBarrier的all-or-none特性。

### 用法示例

下面抠了CyclicBarrier中的用法示例，代码如下：

```java
class Solver {
    final int N;
    final float[][] data;
    final CyclicBarrier barrier;
 
    class Worker implements Runnable {
      int myRow;
      Worker(int row) { myRow = row; }
      public void run() {
        while (!done()) {
          processRow(myRow);
 
          try {
            barrier.await();
          } catch (InterruptedException ex) {
            return;
          } catch (BrokenBarrierException ex) {
            return;
          }
        }
      }
    }
 
    public Solver(float[][] matrix) {
      data = matrix;
      N = matrix.length;
      Runnable barrierAction =
        new Runnable() { public void run() { mergeRows(...); }};
      barrier = new CyclicBarrier(N, barrierAction);
 
      List<Thread> threads = new ArrayList<Thread>(N);
      for (int i = 0; i < N; i++) {
        Thread thread = new Thread(new Worker(i));
        threads.add(thread);
        thread.start();
      }
 
      // wait until done
      for (Thread thread : threads)
        thread.join();
    }
  }}
```

在这个示例里，每一个线程处理一个row，并且在处理完后等待，直到所有的rows被处理完成，然后调用barrierAction合并这些结果，如果合并完成，则通知子线程结束。

### 总结

CyclicBarrier就介绍到这了，当然它还有一些公有的辅助方法，这里就不在一一介绍，有兴趣的同学的可以看看它的源码，这些辅助方法逻辑都比较简单，它的最主要的方法就是上面介绍的dowait方法。本文是笔者一边看源码一边书写，难免有些疏漏，望读者斧正。

