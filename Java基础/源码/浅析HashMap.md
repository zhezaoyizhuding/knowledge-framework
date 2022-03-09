---
title: 浅析HashMap
date: 2018-03-19 10:41:22
categories: Java源码浅析
tags:
- HashMap
- 源码
- 底层实现
- 原理
---

JDK1.8开始HashMap的源码有了一些改变，这篇博客主要分别介绍下JDK1.7和JDK1.8中HashMap的底层实现，并比较一下二者的不同。

### JDK1.7中的HashMap

HashMap是我们很常用的集合框架，对它的用法我们都很熟悉。但是如果要更好的使用它，我们可能需要理解HashMap的内部实现，同时这也是面试比较常问的一个问题。下面的源码使用的是Java SE Development Kit 7u80，主要介绍下HashMap的底层存储结构，扩容机制和一些常用的方法。

#### JDK1.7中的HashMap的存储结构

在JDk1.7中或者之前的一些版本中，HashMap的底层存储结构都是采用“数组+链表”的形式。数组的每一节被称为桶（bucket），每个bucket存储这一个或多个entry（Map中的每一个键值对），如果有冲突，冲突的地方开一个链表，链式存储这些entry，所以性能最差的HashMap最终会退化成一个链表。其中Entry的结构如下：

```java
static class Entry<K,V> implements Map.Entry<K,V> {
        final K key;
        V value;
        Entry<K,V> next;
        int hash;

        /**
         * Creates new entry.
         */
        Entry(int h, K k, V v, Entry<K,V> n) {
            value = v;
            next = n;
            key = k;
            hash = h;
        }
```

key,value存储相应的值，next指向bucket中下一个entry。

JDK1.7中HashMap的存储结构大致可表述为下图：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/JDK1.7中HashMap存储结构.png" alt="JDK1.7中HashMap存储结构" style="zoom: 67%;" />

table数组中的索引通过key的hashCode再取模获得。获取hashCode的hash算法如下(对hashCode进行rehash--高位参与运算，尽量减少冲突)：

```java
final int hash(Object k) {
    int h = hashSeed;
    if (0 != h && k instanceof String) {
        return sun.misc.Hashing.stringHash32((String) k);
    }

    h ^= k.hashCode();

    // This function ensures that hashCodes that differ only by constant multiples at each bit position have a bounded number of collisions (approximately 8 at default load factor).
    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
}
```

获取数组的索引的方法如下：

```java
static int indexFor(int h, int length) {
    // assert Integer.bitCount(length) == 1 : "length must be a non-zero power of 2";
    return h & (length-1);
}
```

#### 扩容

HashMap的成员变量中有一个负载因子loadFactor，值为0.75f。还有一个阈值变量threshold，这个变量的值为capacity * loadFactor。源码中定义如下：

```java
/**
     * The next size value at which to resize (capacity * load factor).
     * @serial
     */
    // If table == EMPTY_TABLE then this is the initial capacity at which the
    // table will be created when inflated.
    int threshold;

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    final float loadFactor;
```

当table数组中所存储的bucket数大于等于这个值，并且当前位置的bucket已经有值时就会发生扩容。源码如下：

```java
void addEntry(int hash, K key, V value, int bucketIndex) {
    if ((size >= threshold) && (null != table[bucketIndex])) {
        resize(2 * table.length);
        hash = (null != key) ? hash(key) : 0;
        bucketIndex = indexFor(hash, table.length);
    }

    createEntry(hash, key, value, bucketIndex);
}
```

```java
void resize(int newCapacity) {
    Entry[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity == MAXIMUM_CAPACITY) {
        threshold = Integer.MAX_VALUE;
        return;
    }

    Entry[] newTable = new Entry[newCapacity];
    transfer(newTable, initHashSeedAsNeeded(newCapacity));
    table = newTable;
    threshold = (int)Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
}
```

扩容时会新建一个原先数组两倍的新数组，将原先数组的数据重新hash到新的数组，如果原先数组的某个bucket是链表，并且重新hash的位置仍然相同，则链表顺序会反转（因为采用的是头插法）。rehash后重设table引用和threshold的值。源码如下：

```java
/**
    * Transfers all entries from current table to newTable.
    */
   void transfer(Entry[] newTable, boolean rehash) {
       int newCapacity = newTable.length;
       for (Entry<K,V> e : table) {
           while(null != e) {
               Entry<K,V> next = e.next;
               if (rehash) {
                   e.hash = null == e.key ? 0 : hash(e.key);
               }
               int i = indexFor(e.hash, newCapacity);
               e.next = newTable[i];
               newTable[i] = e;
               e = next;
           }
       }
   }
```

同时我们要知道上面的addEntry方法是在put方法中调用的，HashMap在初始化时并没有创建table数组（只是初始化一些基本的属性），而是在真正放入数据才开始创建，并在这里判断是否需要扩容。

#### HashMap的一些常用方法

###### get方法

我们是如何通过key找到所需的value值的呢？当我们调用HashMap的get方法时，首先我们会判断key是否为null，如果为空，则定位到table[0]（所有的key为null的entry都会放在这里）；如果不为空并且table的size不为0，此时会根据key的hashcode找到entry所在的bucket（因此HashMap的key最好为不可变对象），并遍历链表通过key的equals方法找到最终的entry。返回它的value值。相关代码如下：

```java
public V get(Object key) {
        if (key == null)
            return getForNullKey();
        Entry<K,V> entry = getEntry(key);

        return null == entry ? null : entry.getValue();
    }
```

```java
private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null)
                return e.value;
        }
        return null;
    }
```

```java
final Entry<K,V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }

        int hash = (key == null) ? 0 : hash(key);
        for (Entry<K,V> e = table[indexFor(hash, table.length)];
             e != null;
             e = e.next) {
            Object k;
            if (e.hash == hash &&
                ((k = e.key) == key || (key != null && key.equals(k))))
                return e;
        }
        return null;
    }
```

###### put方法

在调用put方法时，会首先判断table数组是否已经初始化，如果没有，则初始化table表；如果已经初始化则判断key是否为null，如果为null，则将它放在table[0]中（替换之前的旧值）；如果key不为null，则取key的hashCode值找到具体的bucket位置，如果该位置没有值（即为null），则新建entry（调用上面列出的addEntry方法）；如果该位置已有值，即发生了hash冲突，则遍历链表，通过key的equals方法找到具体的entry；如果没有找到这样的entry，则在尾部新增一个entry节点。相关代码如下：

```java
public V put(K key, V value) {
        if (table == EMPTY_TABLE) {
            inflateTable(threshold);
        }
        if (key == null)
            return putForNullKey(value);
        int hash = hash(key);
        int i = indexFor(hash, table.length);
        for (Entry<K,V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }

        modCount++;
        addEntry(hash, key, value, i);
        return null;
    }
```

初始化：

```java
private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        int capacity = roundUpToPowerOf2(toSize);

        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Entry[capacity];
        initHashSeedAsNeeded(capacity);
    }

final boolean initHashSeedAsNeeded(int capacity) {
        boolean currentAltHashing = hashSeed != 0;
        boolean useAltHashing = sun.misc.VM.isBooted() &&
                (capacity >= Holder.ALTERNATIVE_HASHING_THRESHOLD);
        boolean switching = currentAltHashing ^ useAltHashing;
        if (switching) {
            hashSeed = useAltHashing
                ? sun.misc.Hashing.randomHashSeed(this)
                : 0;
        }
        return switching;
    }
```

放入空key对象：

```java
/**
     * Offloaded version of put for null keys
     */
    private V putForNullKey(V value) {
        for (Entry<K,V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }
        modCount++;
        addEntry(0, null, value, 0);
        return null;
    }
```

### JDK1.8中HashMap

从JDK1.8开始，HashMap的底层实现做了一些改变。首先增加了一个Node类替换之前的Entry类（实际上就是改了个命名，具体实现没有变，这个命名比之前的确实要更加贴切）。同时新增了一个TreeNode类，这是因为1.8中的HashMap当冲突的链表节点大于等于8时，就会转化为红黑树，以优化链表查询。Node和TreeNode的结构如下：

```java
static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;
        final K key;
        V value;
        Node<K,V> next;

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
        .....
}
```

```java
/**
     * Entry for Tree bins. Extends LinkedHashMap.Entry (which in turn
     * extends Node) so can be used as extension of either regular or
     * linked node.
     */
    static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
        TreeNode<K,V> parent;  // red-black tree links
        TreeNode<K,V> left;
        TreeNode<K,V> right;
        TreeNode<K,V> prev;    // needed to unlink next upon deletion
        boolean red;
        TreeNode(int hash, K key, V val, Node<K,V> next) {
            super(hash, key, val, next);
        }
        ......
}
```

HashMap的存储结构可表述如下：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/JDK1.8中HashMap存储结构.png" alt="JDK1.8中HashMap存储结构" style="zoom:67%;" />

#### 扩容

JDK1.8中扩容操作比1.7中做了些优化，首先它将初始化操作也整合在了扩容方法里面，当然这不是重点；重点是在扩容时它不需要再调用hash方法重新计算新表中索引位置。它采用二次幂的扩展，使得扩展后的bucket位置要么是原位置，要么是原位置加上OldCap（即老表的长度）（这个具体是如何实现的，笔者也不是很明白，看的云里雾里。参考了网上的博客，原因可能是源码中优化了hash方法中的高位运算，使得在扩容时，它的mask范围在高位多了1个bit位，而这个bit位如果为0，则扩展后的元素位置为原位置，若这个bit为1，则新位置为原位置加上OldCap。因此在扩容时不需要再重新hash，只需要观察原先的hash值新增的bit位是0还是1即可。同时因为新增的bit为是0还是1是随机的，因此可以很好的将之前的冲突重新离散。）。还有一点优化是如果之前冲突的节点在扩展后仍然冲突，与1.7中不同的是，1.8中的链表不会反转。源码如下：

```java
/**
     * Initializes or doubles table size.  If null, allocates in
     * accord with initial capacity target held in field threshold.
     * Otherwise, because we are using power-of-two expansion, the
     * elements from each bin must either stay at same index, or move
     * with a power of two offset in the new table.
     *
     * @return the table
     */
    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else { // preserve order
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }
```

#### HashMap的一些方法

JDK1.8中get和put方法的逻辑和1.7中的变化也不大。

###### get方法

get方法在1.7和1.8中改变不大，多了个逻辑，当索引到的bucket类型为TreeNode时，查找红黑树。get方法源码如下：

```java
public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }
```

```java
final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & hash]) != null) {
            if (first.hash == hash && // always check first node
                ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            if ((e = first.next) != null) {
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```

###### put方法

逻辑变化不大。多了个当链表的节点数大于等于8时，转化为红黑树。源码如下：

```java
public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }
```

```java
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }
```

###### remove方法

remove方法也加入了红黑树的逻辑，当冲突节点数小于6，会再退化成链表

```java
public V remove(Object key) {
        Node<K,V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ?
            null : e.value;
    }
```

```java
final Node<K,V> removeNode(int hash, Object key, Object value,
                               boolean matchValue, boolean movable) {
        Node<K,V>[] tab; Node<K,V> p; int n, index;
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (p = tab[index = (n - 1) & hash]) != null) {
            Node<K,V> node = null, e; K k; V v;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                node = p;
            else if ((e = p.next) != null) {
                if (p instanceof TreeNode)
                    node = ((TreeNode<K,V>)p).getTreeNode(hash, key);
                else {
                    do {
                        if (e.hash == hash &&
                            ((k = e.key) == key ||
                             (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        p = e;
                    } while ((e = e.next) != null);
                }
            }
            if (node != null && (!matchValue || (v = node.value) == value ||
                                 (value != null && value.equals(v)))) {
                if (node instanceof TreeNode)
                    ((TreeNode<K,V>)node).removeTreeNode(this, tab, movable);
                else if (node == p)
                    tab[index] = node.next;
                else
                    p.next = node.next;
                ++modCount;
                --size;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }
```

```java
/**
         * Removes the given node, that must be present before this call.
         * This is messier than typical red-black deletion code because we
         * cannot swap the contents of an interior node with a leaf
         * successor that is pinned by "next" pointers that are accessible
         * independently during traversal. So instead we swap the tree
         * linkages. If the current tree appears to have too few nodes,
         * the bin is converted back to a plain bin. (The test triggers
         * somewhere between 2 and 6 nodes, depending on tree structure).
         */
        final void removeTreeNode(HashMap<K,V> map, Node<K,V>[] tab,
                                  boolean movable) {
            int n;
            if (tab == null || (n = tab.length) == 0)
                return;
            int index = (n - 1) & hash;
            TreeNode<K,V> first = (TreeNode<K,V>)tab[index], root = first, rl;
            TreeNode<K,V> succ = (TreeNode<K,V>)next, pred = prev;
            if (pred == null)
                tab[index] = first = succ;
            else
                pred.next = succ;
            if (succ != null)
                succ.prev = pred;
            if (first == null)
                return;
            if (root.parent != null)
                root = root.root();
            if (root == null
                || (movable
                    && (root.right == null
                        || (rl = root.left) == null
                        || rl.left == null))) {
                tab[index] = first.untreeify(map);  // too small
                return;
            }
            TreeNode<K,V> p = this, pl = left, pr = right, replacement;
            if (pl != null && pr != null) {
                TreeNode<K,V> s = pr, sl;
                while ((sl = s.left) != null) // find successor
                    s = sl;
                boolean c = s.red; s.red = p.red; p.red = c; // swap colors
                TreeNode<K,V> sr = s.right;
                TreeNode<K,V> pp = p.parent;
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                }
                else {
                    TreeNode<K,V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left)
                            sp.left = p;
                        else
                            sp.right = p;
                    }
                    if ((s.right = pr) != null)
                        pr.parent = s;
                }
                p.left = null;
                if ((p.right = sr) != null)
                    sr.parent = p;
                if ((s.left = pl) != null)
                    pl.parent = s;
                if ((s.parent = pp) == null)
                    root = s;
                else if (p == pp.left)
                    pp.left = s;
                else
                    pp.right = s;
                if (sr != null)
                    replacement = sr;
                else
                    replacement = p;
            }
            else if (pl != null)
                replacement = pl;
            else if (pr != null)
                replacement = pr;
            else
                replacement = p;
            if (replacement != p) {
                TreeNode<K,V> pp = replacement.parent = p.parent;
                if (pp == null)
                    root = replacement;
                else if (p == pp.left)
                    pp.left = replacement;
                else
                    pp.right = replacement;
                p.left = p.right = p.parent = null;
            }

            TreeNode<K,V> r = p.red ? root : balanceDeletion(root, replacement);

            if (replacement == p) {  // detach
                TreeNode<K,V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    if (p == pp.left)
                        pp.left = null;
                    else if (p == pp.right)
                        pp.right = null;
                }
            }
            if (movable)
                moveRootToFront(tab, r);
        }
```

### 结束语

HashMap可以说是是使用最频繁的一个容器之一，了解它的底层实现有助于我们更好的使用它，在map中的数据很大的情况下，恰当的使用可能比不当的使用具有很大的性能提升。本博客是笔者通过研读JDk中的源码和翻看相关的博客和书籍整合而成。受笔者自身水平限制，可能会有些错谬之处，读者不可全信，当多查询相关资料或者自己阅读源码验证一下。最后，如有不当之处，敬请斧正。
