---
title: 浅析ThreadLocal
date: 2018-04-23 20:31:54
categories: Java源码浅析
tags:
- ThreadLocal
---

在一次面试被问到了ThreadLocal，其间被问到了一个问题：ThreadLocal有什么缺陷。笔者不确定的回答：内存泄漏？面试官随即问道了：为什么会造成内存泄漏？笔者懵逼了，不知道该如何回答。下面在这片博客中通过源码（JDK1.8）来简单了解一下ThreadLocal的内部实现，好好看一下它为什么会造成内存泄露，以及在什么情况下会造成内存泄露。

### 概要

我们都知道ThreadLcoal可以将一个变量与当前线程绑定，使这个变量变成线程私有，以此来实现线程安全。通常我们会用它来避免多个函数或组件之间公用变量传递的麻烦。下面我们看一下javaDoc中对ThreadLocal的介绍。

```java
/**
 * This class provides thread-local variables.  These variables differ from
 * their normal counterparts in that each thread that accesses one (via its
 * {@code get} or {@code set} method) has its own, independently initialized
 * copy of the variable.  {@code ThreadLocal} instances are typically private
 * static fields in classes that wish to associate state with a thread (e.g.,
 * a user ID or Transaction ID).
 *
 * <p>For example, the class below generates unique identifiers local to each
 * thread.
 * A thread's id is assigned the first time it invokes {@code ThreadId.get()}
 * and remains unchanged on subsequent calls.
 * <pre>
 * import java.util.concurrent.atomic.AtomicInteger;
 *
 * public class ThreadId {
 *     // Atomic integer containing the next thread ID to be assigned
 *     private static final AtomicInteger nextId = new AtomicInteger(0);
 *
 *     // Thread local variable containing each thread's ID
 *     private static final ThreadLocal&lt;Integer&gt; threadId =
 *         new ThreadLocal&lt;Integer&gt;() {
 *             &#64;Override protected Integer initialValue() {
 *                 return nextId.getAndIncrement();
 *         }
 *     };
 *
 *     // Returns the current thread's unique ID, assigning it if necessary
 *     public static int get() {
 *         return threadId.get();
 *     }
 * }
 * </pre>
 * <p>Each thread holds an implicit reference to its copy of a thread-local
 * variable as long as the thread is alive and the {@code ThreadLocal}
 * instance is accessible; after a thread goes away, all of its copies of
 * thread-local instances are subject to garbage collection (unless other
 * references to these copies exist).
 *
 * @author  Josh Bloch and Doug Lea
 * @since   1.2
 */
```

上面注释对ThreadLocal介绍的还是比较详细的，与我们了解的也差不多。我们主要看下最后一句，它说每个线程都会持有一个它的ThreadLocal变量副本的引用，如果这个线程一直存活，那么这个ThreadLocal实例就是可达的。这句话其实就透露了ThreadLocal内存泄露的其中一个原因，这里我们先按下不表，先来看看ThreadLocal的内部实现。

### 源码分析

ThreadLocal只有一个构造方法，同时也没有其他的工厂方法，我们先来看一下它的这个构造方法。

```java
/**
 * Creates a thread local variable.
 * @see #withInitial(java.util.function.Supplier)
 */
public ThreadLocal() {
}
```

可以看到这个构造方法是个空方法，只是用于new一个ThreadLocal对象，并没有进行数据的装配和底层存储结构的构造，这点上和HashMap差不多，采用了一种懒加载的方式，只有在真正在使用的时候才进行相关结构的构造。我们都知道ThreadLocal核心的有三个方法set，get，和remove方法，那么它底层结构的初始化应该是在调用这些方法时进行的。我们来看看这些方法的源码。

##### set方法

set方法的源码如下：

```java
public void set(T value) {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
}
```

我们看到它首先获得了一个当前线程的引用，并把它传递进了一个getMap函数，我们来看看这个getMap函数。

```java
/**
 * Get the map associated with a ThreadLocal. Overridden in
 * InheritableThreadLocal.
 *
 * @param  t the current thread
 * @return the map
 */
ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}
```

可见这个方法只是返回一个当前线程持有的ThreadLocalMap对象，这里我们要明白一点，当前线程其实是持有这个ThreadLocalMap的引用的。下面再回到上面的set方法，它先得到当前线程持有的ThreadLocalMap对象，而在刚开始这个map肯定是空的，所以它会调用createMap方法，我们来看看这个方法。

```java
/**
 * Create the map associated with a ThreadLocal. Overridden in
 * InheritableThreadLocal.
 *
 * @param t the current thread
 * @param firstValue value for the initial entry of the map
 */
void createMap(Thread t, T firstValue) {
    t.threadLocals = new ThreadLocalMap(this, firstValue);
}
```

这里面new了一个ThreadLocalMap对象，并把它赋给了当前线程持有的一个threadLocals。那么这个ThreadLocalMap是什么呢？它其实是ThreadLocal的静态内部类，ThreadLocal的数据存储和核心操作都是委托给它来实现的。看到这里或许有些同学会疑惑，既然只是存储变量副本，Set好像也能实现，为什么不用Set而用Map呢？毕竟Map要比Set复杂的多。这里我们就需要明白，这个Map是属于Thread而不是ThreadLocal的（虽然它是ThreadLocal的内部类），一个Thread可以有很多个ThreadLocal来存储多个变量，但它只会有一个ThreadLocalMap。

好了，下面我们来看一下上面这个ThreadLocalMap的构造方法里做了些什么。

```java
ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
    table = new Entry[INITIAL_CAPACITY];
    int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
    table[i] = new Entry(firstKey, firstValue);
    size = 1;
    setThreshold(INITIAL_CAPACITY);
}
```

首先它new了一个Entry类型的数组，初始容量是INITIAL_CAPACITY，这个值默认是16，我们再来看看Entry是什么。源码如下：

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    /** The value associated with this ThreadLocal. */
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

Entry是ThreadLocalMap的静态内部类，其实就是一个用来封装数据的结构体，类似于map中的桶的概念。这里需要注意的一点是，在这里ThreadLocalMap的key被包裹成了一个弱引用。我们再回到上面的构造方法，可以看到它new了一个数组后，然后对这个key进行了一顿操作，又是hash又是取模的，这个具体我们下文再说，这里我们只需要知道它最终得到一个索引值作为上面数组的下标，通过这个我们可以索引到具体数据的位置。下面就是new一个Entry方法上面的数据中并初始化size和threshold。这里面size表示数组中entry的数目，threshold表示ThreadLocalMap扩容的阈值，这里面与HashMap不同的是这个阈值并不是table的length，而是length的三分之二，扩容时倒是和HashMap相同，充满在阈值的四分之三时扩容。

上面这个初始化的问题说完了，下面看看具体的set方法。通过上面的set方法我们知道如果这个ThreadLocalMap没有初始化，调用set方法会先初始化它；如果已经初始化了，再调用set方法会委托给ThreadLocalMap的set方法处理。这个set方法的源码如下：

```java
private void set(ThreadLocal<?> key, Object value) {

            // We don't use a fast path as with get() because it is at
            // least as common to use set() to create new entries as
            // it is to replace existing ones, in which case, a fast
            // path would fail more often than not.

            Entry[] tab = table;
            int len = tab.length;
            int i = key.threadLocalHashCode & (len-1);

            for (Entry e = tab[i];
                 e != null;
                 e = tab[i = nextIndex(i, len)]) {
                ThreadLocal<?> k = e.get();

                if (k == key) {
                    e.value = value;
                    return;
                }

                if (k == null) {
                    replaceStaleEntry(key, value, i);
                    return;
                }
            }

            tab[i] = new Entry(key, value);
            int sz = ++size;
            if (!cleanSomeSlots(i, sz) && sz >= threshold)
                rehash();
        }
```

我们看到它是通过key的threadLocalHashCode找到具体的slot的，我们就来看看这个threadLocalHashCode。它的相关代码如下：

```java
private final int threadLocalHashCode = nextHashCode();

    /**
     * The next hash code to be given out. Updated atomically. Starts at
     * zero.
     */
    private static AtomicInteger nextHashCode =
        new AtomicInteger();

    /**
     * The difference between successively generated hash codes - turns
     * implicit sequential thread-local IDs into near-optimally spread
     * multiplicative hash values for power-of-two-sized tables.
     */
    private static final int HASH_INCREMENT = 0x61c88647;

    /**
     * Returns the next hash code.
     */
    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }
```

我们看到它其实是通过AtomicInteger实现一个原子的递增（其实熟悉AtomicInteger源码的同学应该会知道它其实就是一个CAS操作，至于CAS操作是什么，感兴趣的同学可以看看相关的书籍和博客，我们这里可以把它理解成一个粒度非常小的锁，小到只能保证单个元素），递增的间隔是一个常数0x61c88647，这个数是多少呢？换算成十进制是1640531527。那为什么是这个数呢？笔者为此查了很多博客，并且最终锁定了一篇文章[Why 0x61c88647?](https://www.javaspecialists.eu/archive/Issue164.html)。这是个老外写的文章，笔者读了之后得出了结论---英语真的难读。言归正传，期间说到了这个数，作者把它称作golden number，这翻译叫黄金数是吧？看到这笔者当时就发散了思维，黄金分割点、黄金比例身材、章子怡....汪峰那个老王八蛋。好吧，又扯远了。在这篇文章中作者说道了选这个数的原因，为了保证数据的跳跃分布。因为用hash就会存在冲突，这个数保证在冲突时它的下一个槽位存在数据的可能性更低（下文我们会看到这个map处理冲突的方法是一种线性检测的方式）。那它是如何保证数据跳跃分布的呢？上面的set方法中我们可以看到它取索引的计算方式是key.threadLocalHashCode & (len-1)，即是这个数与entry数组的长度减一做按位与运算，而数组的长度是2的N次方（初始值是16，并且扩容时是2倍扩容），细心的同学应该已经发现了，这样的话，这个运算的值其实就是取threadLocalHashCode的低N位（也相当于对len取模，但位运算的效率更高）。最后得到的结果是连续set的两个数之间间距为7，并且均匀分布。下面是我用python做的一个实验。

```python
>>> HASH_INCREMENT = 0x6188647
>>> def magic_hash(n):
...     for i in range(n):
...         nextHashCode = i * HASH_INCREMENT
...         print(nextHashCode & (n - 1),end = ' ')
...     print()
...
>>> magic_hash(16)
0 7 14 5 12 3 10 1 8 15 6 13 4 11 2 9
>>> magic_hash(32)
0 7 14 21 28 3 10 17 24 31 6 13 20 27 2 9 16 23 30 5 12 19 26 1 8 15 22 29 4 11 18 25
>>> magic_hash(64)
0 7 14 21 28 35 42 49 56 63 6 13 20 27 34 41 48 55 62 5 12 19 26 33 40 47 54 61 4 11 18 25 32 39 46 53 60 3 10 17 24 31 38 45 52 59 2 9 16 23 30 37 44 51 58 1 8 15 22 29 36 43 50 57
```

可见结果为从0开始，间距为7，非常均匀。这里我们要知道当第一个线程进来时，它是将数据存储在0这个位置的，因为getAndAdd方式是先get再add，它会先返回默认值0。当然这只是第一个线程，后续线程进来时就不会为0。因为虽然每一个线程都会有自己的ThreadLocalMap，但是对于外部类ThreadLocal是共享的，它的静态成员nextHashCode也是共享的。

继续看上面的set方法，我们来看一下在得到这个索引后，它如何获取具体的value及如何处理冲突的。可以看到下面是一个for循环，在循环中它从这个索引开始向右遍历，熟悉hash的同学应该知道这其实就是解决hash冲突几种方法中开放定址法中的线性探测。如果冲突的话它会一直循环，直到找到一个为null的slot，并在此创建一个新的entry。但是在循环中会遇到两种情况：如果找到了这个key（没有冲突），重写它的value值返回；还有一种情况，我们前面说过这个entry的key是一个弱引用，它可能会被GC给回收，所以如果这个key被GC回收的话为了避免内存泄露，我们需要回收它对应的value值。这里调用了一个replaceStaleEntry方法，我们看看它的内部实现。

```java
private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
            Entry[] tab = table;
            int len = tab.length;
            Entry e;

            // Back up to check for prior stale entry in current run.
            // We clean out whole runs at a time to avoid continual
            // incremental rehashing due to garbage collector freeing
            // up refs in bunches (i.e., whenever the collector runs).
            int slotToExpunge = staleSlot;
            for (int i = prevIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = prevIndex(i, len))
                if (e.get() == null)
                    slotToExpunge = i;

            // Find either the key or trailing null slot of run, whichever
            // occurs first
            for (int i = nextIndex(staleSlot, len);
                 (e = tab[i]) != null;
                 i = nextIndex(i, len)) {
                ThreadLocal<?> k = e.get();

                // If we find key, then we need to swap it
                // with the stale entry to maintain hash table order.
                // The newly stale slot, or any other stale slot
                // encountered above it, can then be sent to expungeStaleEntry
                // to remove or rehash all of the other entries in run.
                if (k == key) {
                    e.value = value;

                    tab[i] = tab[staleSlot];
                    tab[staleSlot] = e;

                    // Start expunge at preceding stale entry if it exists
                    if (slotToExpunge == staleSlot)
                        slotToExpunge = i;
                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                    return;
                }

                // If we didn't find stale entry on backward scan, the
                // first stale entry seen while scanning for key is the
                // first still present in the run.
                if (k == null && slotToExpunge == staleSlot)
                    slotToExpunge = i;
            }

            // If key not found, put new entry in stale slot
            tab[staleSlot].value = null;
            tab[staleSlot] = new Entry(key, value);

            // If there are any other stale entries in run, expunge them
            if (slotToExpunge != staleSlot)
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
        }
```

这方法有点长，逻辑也有点复杂，通过代码和注释，它其实主要是将找到的这个失效的entry与真正要找的entry替换位置，并且如果一直没找到想要找的entry（key已失效或者根本就不存在），那么就在当前位置重新新建一个key。这个方法有个副作用，就是它会清除沿途遇到的key的null的entry。通过expungeStaleEntry方法和cleanSomeSlots方法，这个具体就不再说了，情形有些多比较复杂。但我们需要明白这个回收是启发式的，它只会在调用set方法，并且遭遇了一个key为null的entry时，才会触发这个回收方法。所以很多前辈都告诫过我们在使用ThreadLocal时要记得调用remove方法。

在上面set方法的最后还有个rehash，如果这个table中不存在key为null的entry并且entry的数目大于threshold时就会触发rehash，rehash方法如下：

```java
/**
 * Re-pack and/or re-size the table. First scan the entire
 * table removing stale entries. If this doesn't sufficiently
 * shrink the size of the table, double the table size.
 */
private void rehash() {
    expungeStaleEntries();

    // Use lower threshold for doubling to avoid hysteresis
    if (size >= threshold - threshold / 4)
        resize();
}
```

在rehash时如果entry的数目大于等于threshold的四分之三，就会发生扩容，扩容时2倍扩容，并重新hash索引。代码如下：

```java
/**
 * Double the capacity of the table.
 */
private void resize() {
    Entry[] oldTab = table;
    int oldLen = oldTab.length;
    int newLen = oldLen * 2;
    Entry[] newTab = new Entry[newLen];
    int count = 0;

    for (int j = 0; j < oldLen; ++j) {
        Entry e = oldTab[j];
        if (e != null) {
            ThreadLocal<?> k = e.get();
            if (k == null) {
                e.value = null; // Help the GC
            } else {
                int h = k.threadLocalHashCode & (newLen - 1);
                while (newTab[h] != null)
                    h = nextIndex(h, newLen);
                newTab[h] = e;
                count++;
            }
        }
    }

    setThreshold(newLen);
    size = count;
    table = newTab;
}
```

#### get方法

上面分析了一下set方法，下面我们看看get方法。它的入口方法如下：

```java
public T get() {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null) {
        ThreadLocalMap.Entry e = map.getEntry(this);
        if (e != null) {
            @SuppressWarnings("unchecked")
            T result = (T)e.value;
            return result;
        }
    }
    return setInitialValue();
}
```

它也是委托给了ThreadLocalMap的一个getEntry方法，它的源码如下：

```java
private Entry getEntry(ThreadLocal<?> key) {
    int i = key.threadLocalHashCode & (table.length - 1);
    Entry e = table[i];
    if (e != null && e.get() == key)
        return e;
    else
        return getEntryAfterMiss(key, i, e);
}
```

这里为了提高性能，如果直接命中就当场返回，如果没有命中，调用getEntryAfterMiss方法。它的源码如下：

```java
private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
    Entry[] tab = table;
    int len = tab.length;

    while (e != null) {
        ThreadLocal<?> k = e.get();
        if (k == key)
            return e;
        if (k == null)
            expungeStaleEntry(i);
        else
            i = nextIndex(i, len);
        e = tab[i];
    }
    return null;
}
```

这里也是通过一个线性探测的方法找到具体的值，并且会清除沿途遇到的无效的entry。回到上面的get方法，如果我们在没有调用set方法的情况下，调用了get方法，它会调用一个setInitialValue方法。它的源码如下：

```java
/**
 * Variant of set() to establish initialValue. Used instead
 * of set() in case user has overridden the set() method.
 *
 * @return the initial value
 */
private T setInitialValue() {
    T value = initialValue();
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
    return value;
}
```

这其实是set方法的一个变种，官方注释是说为了避免用户重写set方法。

#### remove方法

我们来看看最后一个remove方法，它的源码如下：

```java
public void remove() {
    ThreadLocalMap m = getMap(Thread.currentThread());
    if (m != null)
        m.remove(this);
}
```

ThreadLocalMap的remove方法如下：

```java
private void remove(ThreadLocal<?> key) {
    Entry[] tab = table;
    int len = tab.length;
    int i = key.threadLocalHashCode & (len-1);
    for (Entry e = tab[i];
         e != null;
         e = tab[i = nextIndex(i, len)]) {
        if (e.get() == key) {
            e.clear();
            expungeStaleEntry(i);
            return;
        }
    }
}
```

也就是遍历找到这个key，将它对应的key和entry清除。

### 内存泄露的原因

ThreadLocal为什么会在使用不当的情况下会造成内存泄露呢？看了上面的分析我们很容易能想到的一个原因是ThreadLocal自身对entry的回收是启发式的，只有在我们手动调用set或者get方法，并且在遭遇一个无效的entry时才会调用，所以如果不手动调用remove方法还是会造成一定的内存泄露。但这还不是最大的问题，最大的问题是ThreadLocal是和线程对象绑定的，它具有和线程对象一样的生命周期。下面是我在网上找的Thread和ThreadLocal之间的引用链图：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/threadlocal.jpg" alt="threadlocal" style="zoom:67%;" />

Thread和entry之间存在一个引用链，因此只要Thread存在，则GC就不会回收Entry，哪怕它的key已经被回收。但是也有人说Thread在执行完毕自动就会被销毁，那么这个map也就会被回收。确实如此，如果Thread被回收了，map也会被回收，而我们如果单起一个线程的话，执行完后，它确实会被销毁。然而，在现实中存在一个叫线程池的技术，而我们为了方便管理和复用更乐意去使用它。而在池中，Thread在使用后是不会被回收的。这就造成了ThreadLocal的内存泄漏。在后端开发中要尤其注意ThreadLocal的使用，因为后台常用的Tomcat容器在处理http请求时采用的就是线程池技术。

### 结束语

ThreadLocal的分析就到这里了，本文对主要的源码进行了分析，但是更详细的实现细节并没有深究，并且可能存在理解不当的情况，因此读者在读到这片文章时不可全信，应当自己读读源码或者找些其他博客资料相互映照。
