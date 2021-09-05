---
title: 浅析LinkedHashMap
date: 2018-03-21 14:56:44
categories: Java源码浅析
tags:
- LinkedHashMap
- 源码
- 底层实现
---

正常情况下HashMap已经足够我们使用了。但在某些场景下，我们可能需要保证数据插入集合的有序性，使得我们在遍历时，数据能按照我们插入时的顺序输出。这种情况下，HashMap就满足不了要求了。JDK对此情况做出了补偿，提供了一个新的集合类LinkedHashMap。该类继承于HashMap，但在底层存储结构上做出些改变，它在table数组（键值对数组）中的数据之间使用一个双向链表来维持数据的有序性。LinkedHashMap的存储结构大致可描述如下：

{% asset_img LinkedHashMap底层存储结构.png LinkedHashMap底层存储结构 %}

它的节点结构如下：

```java
static class Entry<K,V> extends HashMap.Node<K,V> {
    Entry<K,V> before, after;
    Entry(int hash, K key, V value, Node<K,V> next) {
        super(hash, key, value, next);
    }
}
```

可见于HashMap相比，LinkedHashMap的Node结构多了两个指针，分别指向它的前一个节点和后一个节点。要注意的是，这里的前后并不是table数组中索引位置的前后，而是一种逻辑上的先后，因为table数组中的索引是hash得到。所以上面的存储结构图其实并不准确（这里请读者见谅，图确实很难画），两个用指针相连的节点在table数组可能并不是挨着的。

LinkedHashMap的迭代顺序可以有两种，它使用了一个booblean类型的变量accessOrder来定义，源码种定义如下：

```java
/**
 * The iteration ordering method for this linked hash map: <tt>true</tt>
 * for access-order, <tt>false</tt> for insertion-order.
 *
 * @serial
 */
final boolean accessOrder;
```

通过上面注释可知：若accessOrder为false，则链表中的数据保持插入时的顺序；若accessOrder为true，则采用访问的顺序来作为遍历顺序。这句话给如何理解呢？通过翻看源码发现，当accessOrder为true时，每次在访问链表，即调用get方法时，都会把查找的这个Node节点移动到链表的尾端。因此此时，链表中数据顺序就不再是插入时的顺序。默认情况下accessOrder为false，LinkedHashMap提供了一个构造方法来设置accessOrder的值。下面是LinkedHashMap提供的构造方法：

```java
public LinkedHashMap() {
    super();
    accessOrder = false;
}
```

```java
public LinkedHashMap(int initialCapacity) {
    super(initialCapacity);
    accessOrder = false;
}
```

```java
public LinkedHashMap(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
    accessOrder = false;
}
```

```java
public LinkedHashMap(int initialCapacity,
                     float loadFactor,
                     boolean accessOrder) {
    super(initialCapacity, loadFactor);
    this.accessOrder = accessOrder;
}
```

```java
public LinkedHashMap(Map<? extends K, ? extends V> m) {
    super();
    accessOrder = false;
    putMapEntries(m, false);
}
```

引入accessOrder其实是为了LRU算法作支持，LinkedHashMap中有一个removeEldestEntry方法，在LinkedHashMap的默认实现中它是返回恒定false的，但是它给予了我们重写的权限。因此我们可以继承LInkedHashMap并重写上面这个方法来实现一个最近最少使用的缓存（比如我们可以指定LinkedHashMap的节点数量，即缓存大小，当达到这个界限时每有新的数据进来，我们就处理掉最老的节点）

### LinkedHashMap内部操作逻辑

这里通过分析get，put和remove方法来介绍下LinkedHashMap内部的操作逻辑。

###### get方法

LinkedHashMap对HashMap的get进行了重写，源码如下：

```java
public V get(Object key) {
    Node<K,V> e;
    if ((e = getNode(hash(key), key)) == null)
        return null;
    if (accessOrder)
        afterNodeAccess(e);
    return e.value;
}
```

这段代码上面部分的逻辑和HashMap是一样的，只是在后面加了个accessOrder的状态判断，若这个变量的值为true，则调整链表顺序，将查询到节点放在链表的末尾。源码如下：

```java
void afterNodeAccess(Node<K,V> e) { // move node to last
        LinkedHashMap.Entry<K,V> last;
        if (accessOrder && (last = tail) != e) {
            LinkedHashMap.Entry<K,V> p =
                (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        }
    }
```

###### put方法

LinkedHashMap的put方法继承自HashMap，只是实现了其中的afterNodeAccess和afterNodeInsertion方法。我们在翻看HashMap源码时，可能会看到三个空函数，这三个函数就是为了LinkedHashMap回调使用的。源码如下：

```java
// Callbacks to allow LinkedHashMap post-actions
void afterNodeAccess(Node<K,V> p) { }
void afterNodeInsertion(boolean evict) { }
void afterNodeRemoval(Node<K,V> p) { }
```

在LinkedHashMap中对这三个方法进行了重写，以完成在相应操作结束后对底层链表进行重建。下面看一下HashMap中put方法：

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

上面的put操作笔者就不再说了，感兴趣的同学可以看下笔者的另外一篇博客，是关于HashMap的底层源码实现的。下面我们来看一下LinkedHashMap中的afterNodeInsertion方法（afterNodeAccess前面说过了，在get方法里也调用了它）。

```java
void afterNodeInsertion(boolean evict) { // possibly remove eldest
    LinkedHashMap.Entry<K,V> first;
    if (evict && (first = head) != null && removeEldestEntry(first)) {
        K key = first.key;
        removeNode(hash(key), key, null, false, true);
    }
}
```

可见该方法中又回调了removeNode方法，而这个方法继承自HashMap。但是通过翻看removeEldestEntry方法的源码，可以发现事实上if中的代码根本不会执行，因为该方法永远返回false（上面提过这个方法是为了实现LRU算法的）。源码如下：

```java
protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
    return false;
}
```

###### remove方法

LinkedHashMap也是调用HashMap的remove来实现删除操作。源码如下：

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

通过源码可知，在remove时，会先搜索到这个节点所在位置，然后判断他的类型，主要有三种情况。如果是红黑树（JDK1.8新增），则调用removeTreeNode方法删除这个节点（由于红黑树的删除操作有些复杂，需要旋转并改变节点的颜色，使之重新符合红黑树的定义，这里就不做介绍了）；如果node==p，这种情况即是该节点上没有冲突，此时node.next为null，所以此时搜索到节点会被设置为null；第三种情况就是节点是链表结构，此时将当前节点的下一个节点放在前一个节点的指针下，其实就是链表的删除操作。

如果是HashMap只进行上面的操作就行了，但是由于LinkedHashMap实现afterNodeRemoval方法，我们来看一些这个方法干了些啥。

```java
void afterNodeRemoval(Node<K,V> e) { // unlink
    LinkedHashMap.Entry<K,V> p =
        (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
    p.before = p.after = null;
    if (b == null)
        head = a;
    else
        b.after = a;
    if (a == null)
        tail = b;
    else
        a.before = b;
}
```

翻看源码发现，其实该方法就是一个链表删除操作。因为LinkedHashMap底层的table数组中的数据之间使用链表来维持顺序，再删除这个节点后，还需要重置前后指针，排除这个节点。

### 结束语

本文从底层存储结构和一些操作的逻辑对LinkedHashMap进行了简单介绍。囿于笔者自己水平限制，所写可能有些许不当之处。读者不可全信，应当自己验证之，同时JDK一直在更新，今天的正确放在明天可能就不再适用。
