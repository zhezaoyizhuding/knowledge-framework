---
title: '浅析Java并发工具类(4) - Exchanger'
date: 2018-07-25 18:01:21
categories: Java源码浅析
tags:
- Exchanger
---

### Exchanger

Exchanger是一个用于线程间交换数据的工具类，它会提供一个同步点，两个线程在这个同步点处交换数据。如果第一个线程调用exchanger方法，它会一直在此等待，直到第二个线程也进入exchanger方法，两个线程在里面交换数据，之后再各自运行下去。它可以用于遗传算法，管道设计，或者其他需要对两个线程的数据进行校对的工作。

##### 用法示例

下面通过JDK的一个示例来看看Exchanger该如何使用，然后再通过源码分析一下它的原理。示例代码如下：

```java
 class FillAndEmpty {
    Exchanger<DataBuffer> exchanger = new Exchanger<DataBuffer>();
    DataBuffer initialEmptyBuffer = ... a made-up type
    DataBuffer initialFullBuffer = ...
 
    class FillingLoop implements Runnable {
      public void run() {
        DataBuffer currentBuffer = initialEmptyBuffer;
        try {
          while (currentBuffer != null) {
            addToBuffer(currentBuffer);
            if (currentBuffer.isFull())
              currentBuffer = exchanger.exchange(currentBuffer);
          }
        } catch (InterruptedException ex) { ... handle ... }
      }
    }
 
    class EmptyingLoop implements Runnable {
      public void run() {
        DataBuffer currentBuffer = initialFullBuffer;
        try {
          while (currentBuffer != null) {
            takeFromBuffer(currentBuffer);
            if (currentBuffer.isEmpty())
              currentBuffer = exchanger.exchange(currentBuffer);
          }
        } catch (InterruptedException ex) { ... handle ...}
      }
    }
 
    void start() {
      new Thread(new FillingLoop()).start();
      new Thread(new EmptyingLoop()).start();
    }
  }
```

可以看到示例中起了两个线程，并通过共享的Exchanger变量来交换它们各自的buffer数据。下面我们通过源码来看看Exchanger数据交换的原理。

##### 源码分析

Exchanger最核心的就是一个exchange方法，在分析这个方法之前我们先看一下Exchanger的成员变量和构造函数。

成员变量：

```java
/**
     * Per-thread state
     */
    private final Participant participant;

    /**
     * Elimination array; null until enabled (within slotExchange).
     * Element accesses use emulation of volatile gets and CAS.
     */
    private volatile Node[] arena;

    /**
     * Slot used until contention detected.
     */
    private volatile Node slot;

    /**
     * The index of the largest valid arena position, OR'ed with SEQ
     * number in high bits, incremented on each update.  The initial
     * update from 0 to SEQ is used to ensure that the arena array is
     * constructed only once.
     */
    private volatile int bound;
```

Exchanger的成员变量主要是上面这几个，其中Participant和Node都是Exchanger的内部类，Node用于存储具体的数据，Participant继承于ThreadLocal，用于存储Node。可以看到上面存在一个Node类型变量，还有一个Node数组，这里就区分了两种情况——竞争情况下和非竞争情况下。bound是用于计算arena中具体的槽位的。

下面看一下Exchanger的构造函数。它的源码如下：

```java
/**
     * Creates a new Exchanger.
     */
    public Exchanger() {
        participant = new Participant();
    }
```

Exchanger只有上面这一个构造函数，可以看到它其实只是创建了一个Participant。

下面看一下Exchanger的两个内部类，它们的源码如下：

```java
    /**
     * Nodes hold partially exchanged data, plus other per-thread
     * bookkeeping. Padded via @sun.misc.Contended to reduce memory
     * contention.
     */
    @sun.misc.Contended static final class Node {
        int index;              // Arena index
        int bound;              // Last recorded value of Exchanger.bound
        int collides;           // Number of CAS failures at current bound
        int hash;               // Pseudo-random for spins
        Object item;            // This thread's current item
        volatile Object match;  // Item provided by releasing thread
        volatile Thread parked; // Set to this thread when parked, else null
    }
```

```java
    /** The corresponding thread local class */
    static final class Participant extends ThreadLocal<Node> {
        public Node initialValue() { return new Node(); }
    }
```

Participant主要就是用于存储线程本地的Node节点，不在多说，这里主要说下Node。它的内部属性注释也有说明，读者如果现在对这些属性的作用不明白也不要紧，在后面我们分析过exchange方法后，再回来看就能明白这些属性的作用了。我们这里看下这个@sun.misc.Contended注解，注释上说它可以减少竞争，那么到底是如何减少的呢？这里就要说一下Java并发中的一些底层知识。我们明白多线程一般在多CPU中才能更好的发挥性能，而在这些多CPU的机器中（事实上现在计算机基本都是多CPU）内存是共享的，而每个CPU还会有自己的本地缓存，缓存的基本单位就是缓存行CacheLine。现代CPU保证了对CacheLine的读写是原子的，每个CacheLine存储的数据是64bit，而在现在的英特尔酷睿等这些CPU是不支持部分填充缓存行的。这就是说如果一个缓存行没有填满，那么下一个连续的数据还会继续从这个缓存行开始填充。这样就会造成一个问题，当我在对第一个数据进行写入时，CPU会锁住这个缓存行，那么对第二个数据的读写操作就没法进行，因为它有部分数据和第一个数据共用了缓存行。因此为了避免这种情况，在Java中的一个办法就是填充无用字节，使连续的两个数据结构不存在共有缓存行的情况，即是空间换时间的一种妥协，有效的减少了竞争的情况。

下面看一下Exchanger中唯一的发布方法，它有两个版本，如下：

```java
public V exchange(V x) throws InterruptedException
public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException
```

可以看到不同之处在于第二个增加了个超时时间，事实上很多并发类中的方法都会 有这么一个重载版本。这里我们只拿其中的第一个来介绍，来理解Exchanger的内部逻辑。它的源码如下：

```java
public V exchange(V x) throws InterruptedException {
        Object v;
        Object item = (x == null) ? NULL_ITEM : x; // translate null args
        if ((arena != null ||
             (v = slotExchange(item, false, 0L)) == null) &&
            ((Thread.interrupted() || // disambiguates null return
              (v = arenaExchange(item, false, 0L)) == null)))
            throw new InterruptedException();
        return (v == NULL_ITEM) ? null : (V)v;
    }
```

可以看到这段代码里主要逻辑是if条件的判断，通过&&符号连接了两个条件。主要逻辑是首先判断arena是否为null，如果为null，执行后面的slotExchange方法；如果arena不为空，则后面的slotExchange方法被短路，继续判断第二个条件，如果当前线程没有中断，则执行arenaExchange。回想之前的构造函数，其中并没有对arena的初始化，所以第一次进入这个方法，arena是为null的。下面看一下slotExchange方法。

```java
/**
     * Exchange function used until arenas enabled. See above for explanation.
     *
     * @param item the item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if either the arena
     * was enabled or the thread was interrupted before completion; or
     * TIMED_OUT if timed and timed out
     */
    private final Object slotExchange(Object item, boolean timed, long ns) {
        Node p = participant.get();
        Thread t = Thread.currentThread();
        if (t.isInterrupted()) // preserve interrupt status so caller can recheck
            return null;

        for (Node q;;) {
            if ((q = slot) != null) {
                if (U.compareAndSwapObject(this, SLOT, q, null)) {
                    Object v = q.item;
                    q.match = item;
                    Thread w = q.parked;
                    if (w != null)
                        U.unpark(w);
                    return v;
                }
                // create arena on contention, but continue until slot null
                if (NCPU > 1 && bound == 0 &&
                    U.compareAndSwapInt(this, BOUND, 0, SEQ))
                    arena = new Node[(FULL + 2) << ASHIFT];
            }
            else if (arena != null)
                return null; // caller must reroute to arenaExchange
            else {
                p.item = item;
                if (U.compareAndSwapObject(this, SLOT, null, p))
                    break;
                p.item = null;
            }
        }

        // await release
        int h = p.hash;
        long end = timed ? System.nanoTime() + ns : 0L;
        int spins = (NCPU > 1) ? SPINS : 1;
        Object v;
        while ((v = p.match) == null) {
            if (spins > 0) {
                h ^= h << 1; h ^= h >>> 3; h ^= h << 10;
                if (h == 0)
                    h = SPINS | (int)t.getId();
                else if (h < 0 && (--spins & ((SPINS >>> 1) - 1)) == 0)
                    Thread.yield();
            }
            else if (slot != p)
                spins = SPINS;
            else if (!t.isInterrupted() && arena == null &&
                     (!timed || (ns = end - System.nanoTime()) > 0L)) {
                U.putObject(t, BLOCKER, this);
                p.parked = t;
                if (slot == p)
                    U.park(false, ns);
                p.parked = null;
                U.putObject(t, BLOCKER, null);
            }
            else if (U.compareAndSwapObject(this, SLOT, p, null)) {
                v = timed && ns <= 0L && !t.isInterrupted() ? TIMED_OUT : null;
                break;
            }
        }
        U.putOrderedObject(p, MATCH, null);
        p.item = null;
        p.hash = h;
        return v;
    }
```

这个方法有些长，而且逻辑也很复杂，因为这个方法并不是独占的，需要考虑多线程的情况。这里我们假使只有两个线程进入到上面这个方法，通过分析他们的执行路劲，来看一看数据是如何交换的。

第一个线程首次进入，首先从participant中得到一个Node变量，这个get方法的调用路径最终会走到Participant中重写的initialValue方法。然后会进入一个for循环，因为是第一次进入slot为null，它会被路由到最后一个else块中。（其中对arena的校验条件是为了兼容多线程的情况，因为此时arena可能还没有被创建，而前面exchange方法中的条件又不是原子的，存在其他线程执行到这里，而arena刚好被创建的情况）继续看看这个else块里做了啥，可以看到它把当前线程持有的数据item塞进了Node中，并通过CAS操作将这个Node变量的值设给成员变量slot，这里要记住一点，这个slot变量是线程共享的。然后就跳出了循环，继续执行。

下面还是一个循环，直到match中有值为止，或者中断，超时，arena被赋予了值；否则，线程将在循环中自旋，自旋的值在多核情况下默认是1024，自旋结束后且此时slot仍然等于p，则线程挂起等待被其他线程唤醒。好的，第一个线程走到这里挂起了，下面我们看看第二个线程。

此时第二个线程进来，slot已经不是null了，所以它会进入第一个if块。在这个语句块中将slot的值置为null，并取出slot中item返回，这个item其实第一个线程携带的数据；而当前线程会将自己携带的数据item放入match中，唤醒之前阻塞的线程。

而第一个线程被唤醒后，此时match有值，它会跳出循环，继续往下执行。将match也设为空，还原现场，只有这个hash值会被复用，最后返回这个match值。

所以我们看到所谓的交换数据，只是通过一个共享的Node类型变量，分别将数据放入它的item和match中，最后返回各自需要的数据。如果只是两个线程的话，整体流程并不复杂，但是因为需要考虑多线程环境下的情况，徒增了很多复杂性。并且在高并发的情况下，可能会存在对这个Node变量争用的情况，因为这个Node是唯一的，所以作者又提供一个争用版本，即arenaExchange方法，采用了一个Node数组来规避竞争，而竞争发生后，所有进入到slotExchange方法的线程会被路由的到arenaExchange方法里。arenaExchange方法的源码如下：

```java
/**
     * Exchange function when arenas enabled. See above for explanation.
     *
     * @param item the (non-null) item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if interrupted; or
     * TIMED_OUT if timed and timed out
     */
    private final Object arenaExchange(Object item, boolean timed, long ns) {
        Node[] a = arena;
        Node p = participant.get();
        for (int i = p.index;;) {                      // access slot at i
            int b, m, c; long j;                       // j is raw array offset
            Node q = (Node)U.getObjectVolatile(a, j = (i << ASHIFT) + ABASE);
            if (q != null && U.compareAndSwapObject(a, j, q, null)) {
                Object v = q.item;                     // release
                q.match = item;
                Thread w = q.parked;
                if (w != null)
                    U.unpark(w);
                return v;
            }
            else if (i <= (m = (b = bound) & MMASK) && q == null) {
                p.item = item;                         // offer
                if (U.compareAndSwapObject(a, j, null, p)) {
                    long end = (timed && m == 0) ? System.nanoTime() + ns : 0L;
                    Thread t = Thread.currentThread(); // wait
                    for (int h = p.hash, spins = SPINS;;) {
                        Object v = p.match;
                        if (v != null) {
                            U.putOrderedObject(p, MATCH, null);
                            p.item = null;             // clear for next use
                            p.hash = h;
                            return v;
                        }
                        else if (spins > 0) {
                            h ^= h << 1; h ^= h >>> 3; h ^= h << 10; // xorshift
                            if (h == 0)                // initialize hash
                                h = SPINS | (int)t.getId();
                            else if (h < 0 &&          // approx 50% true
                                     (--spins & ((SPINS >>> 1) - 1)) == 0)
                                Thread.yield();        // two yields per wait
                        }
                        else if (U.getObjectVolatile(a, j) != p)
                            spins = SPINS;       // releaser hasn't set match yet
                        else if (!t.isInterrupted() && m == 0 &&
                                 (!timed ||
                                  (ns = end - System.nanoTime()) > 0L)) {
                            U.putObject(t, BLOCKER, this); // emulate LockSupport
                            p.parked = t;              // minimize window
                            if (U.getObjectVolatile(a, j) == p)
                                U.park(false, ns);
                            p.parked = null;
                            U.putObject(t, BLOCKER, null);
                        }
                        else if (U.getObjectVolatile(a, j) == p &&
                                 U.compareAndSwapObject(a, j, p, null)) {
                            if (m != 0)                // try to shrink
                                U.compareAndSwapInt(this, BOUND, b, b + SEQ - 1);
                            p.item = null;
                            p.hash = h;
                            i = p.index >>>= 1;        // descend
                            if (Thread.interrupted())
                                return null;
                            if (timed && m == 0 && ns <= 0L)
                                return TIMED_OUT;
                            break;                     // expired; restart
                        }
                    }
                }
                else
                    p.item = null;                     // clear offer
            }
            else {
                if (p.bound != b) {                    // stale; reset
                    p.bound = b;
                    p.collides = 0;
                    i = (i != m || m == 0) ? m : m - 1;
                }
                else if ((c = p.collides) < m || m == FULL ||
                         !U.compareAndSwapInt(this, BOUND, b, b + SEQ + 1)) {
                    p.collides = c + 1;
                    i = (i == 0) ? m : i - 1;          // cyclically traverse
                }
                else
                    i = m + 1;                         // grow
                p.index = i;
            }
        }
    }
```

这个方法笔者这里就不再分析了，一是由于它更加复杂，笔者很多地方也没有弄明白，不能误人子弟；二是我们理解了它的线程间数据交换的机制也已足够，毕竟这个类使用的情况并不多，也多是在两个线程之间的调用，没有必要对它理解的十分透彻。当然感兴趣的读者，也可以仔细分析下这个方法。

### 总结

Exchanger就分析到这了，笔者本身源码也看的磕磕碰碰，很多地方都不是太明白，也有很多地方没有说到。读者若有些不懂得地方可以再看一看其他的博客，最后自己仔细研读下源码，可能会比笔者理解的更加透彻。下面通过一幅笔者从网上盗的图结束这篇博客。这幅图盗于简书的一片博客，笔者实在懒得画图，见谅见谅。该篇博文的地址为[那篇博客](https://www.jianshu.com/p/2840c5c4f368)

{% asset_img Exchanger数据流转图.png Exchanger数据流转图 %}

