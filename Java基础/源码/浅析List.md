---
title: 浅析List
date: 2018-07-18 22:28:31
categories: Java源码浅析
tags: 
- List
- ArrayList
- LinkedList
- Vector
---

List是我们工作中用的最多的集合框架之一，这片博客简单介绍下List主要的两个实现类ArrayList和LinkedList，以及一个不再使用的很古老的集合类Vector。

### ArrayList

在JDK中的源码中的类注释对ArrayList的功能进行介绍。翻译过来主要有一下几点。

- 它是一个实现了List结构的动态数组，拥有List接口的所有操作，底层实现还是借助一个数组，可以存储null。
- ArrayList的性能很高，它的很多操作的实现复杂度都是常数时间，其他的操作也都是在线性时间内，拥有比LinkedList更好的性能。
- ArrayList有一个成员变量capacity，可以自动扩容，但是如果可以预估元素的总量，应该指定capacity的初始值，减少扩容操作。
- 它不是线程安全的，在多线程情况下对它进行修改时需要进行外部同步，可以使用Collections.synchronizedList来封装它。（其实我们很少这么干，因为JDK5开始concurrent包下提供了它的替代类）
- 在一个迭代器中，必须通过迭代器的add/remove方法对它修改，而不能是ArrayList本身的方法，否则它将抛出一个ConcurrentModificationException异常。

下面我们来看一下ArrayList的继承结构，下面是通过IDEA中Diagram工具生成的继承结构图：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/ArrayList继承结构图.png" alt="ArrayList继承结构图" style="zoom:50%;" />

可以看到它继承了AbstractList，实现了List以获取List的相应操作；并实现了RandomAccess，Cloneable，Serializable接口，这些接口都是 一些标志性接口，JDK中存在一些标志性接口，它们仅仅用于标记它们的实现类具备某项功能，而不会附加一些必须实现的操作。比如上面这三个接口就标明ArrayList是可随机存取的，可被克隆（JDK的一般都是浅拷贝）的，并且可被序列化的。很多框架在某些类进行某项操作之前会检查她是否具备相应功能，比如在之前古老的java web中，我们的model类，如果要在网络中传输，我们就需要它实现Serializable接口，否则将抛出异常。

##### 看看源码

ArrayList的成员变量：

```java
 /**
     * Default initial capacity.
     */
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * Shared empty array instance used for empty instances.
     */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /**
     * Shared empty array instance used for default sized empty instances. We
     * distinguish this from EMPTY_ELEMENTDATA to know how much to inflate when
     * first element is added.
     */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    /**
     * The array buffer into which the elements of the ArrayList are stored.
     * The capacity of the ArrayList is the length of this array buffer. Any
     * empty ArrayList with elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA
     * will be expanded to DEFAULT_CAPACITY when the first element is added.
     */
    transient Object[] elementData; // non-private to simplify nested class access

    /**
     * The size of the ArrayList (the number of elements it contains).
     *
     * @serial
     */
    private int size;
```

作为一个集合类，它主要的也是三个成员变量：初始大小DEFAULT_CAPACITY，标识集合中元素数量的size，以及真正用来存储数据的数据结构elementData。

ArrayList的构造函数：

```java 
/**
     * Constructs an empty list with the specified initial capacity.
     *
     * @param  initialCapacity  the initial capacity of the list
     * @throws IllegalArgumentException if the specified initial capacity
     *         is negative
     */
    public ArrayList(int initialCapacity) {
        if (initialCapacity > 0) {
            this.elementData = new Object[initialCapacity];
        } else if (initialCapacity == 0) {
            this.elementData = EMPTY_ELEMENTDATA;
        } else {
            throw new IllegalArgumentException("Illegal Capacity: "+
                                               initialCapacity);
        }
    }

    /**
     * Constructs an empty list with an initial capacity of ten.
     */
    public ArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    /**
     * Constructs a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     */
    public ArrayList(Collection<? extends E> c) {
        elementData = c.toArray();
        if ((size = elementData.length) != 0) {
            // c.toArray might (incorrectly) not return Object[] (see 6260652)
            if (elementData.getClass() != Object[].class)
                elementData = Arrays.copyOf(elementData, size, Object[].class);
        } else {
            // replace with empty array.
            this.elementData = EMPTY_ELEMENTDATA;
        }
    }
```

ArrayL主要有三个构造函数，可以分别默认创建，指定初始大小创建，以及从一个已经存在的集合拷贝而来。

下面看看对ArrayList操作方法。在我们创建一个ArrayList后我们需要向里面添加元素，这时就需要调用它的add方法，add方法的源码如下：

```java
    /**
     * Appends the specified element to the end of this list.
     *
     * @param e element to be appended to this list
     * @return <tt>true</tt> (as specified by {@link Collection#add})
     */
    public boolean add(E e) {
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }
```

可以看到add方法就是将元素按照索引放入elementData数组中，并增加size的值。当然在前面会校验是否是要扩容，我们来看看ensureCapacityInternal这个方法。

```java
    private void ensureCapacityInternal(int minCapacity) {
        ensureExplicitCapacity(calculateCapacity(elementData, minCapacity));
    }
```

```java
    private static int calculateCapacity(Object[] elementData, int minCapacity) {
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            return Math.max(DEFAULT_CAPACITY, minCapacity);
        }
        return minCapacity;
    }
```

```java
    private void ensureExplicitCapacity(int minCapacity) {
        modCount++;

        // overflow-conscious code
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }
```

上面就是它的调用流程，我们看到在calculateCapacity，它会判断elementData是否是DEFAULTCAPACITY_EMPTY_ELEMENTDATA，其实就是判断该ArrayList是以哪种方式创建的（可看上面三个构造函数）。如果它是以默认方式创建的，那么进入if语句，返回一个当前需要的容量。这里我们也能看到ArrayList的大小是一种懒加载的方式（集合中大多都是这种方式），即在真正放入元素时才开辟空间。

calculateCapacity其实就是返回一个当前需要的容量，然后在ensureExplicitCapacity中扩容，这个方法里我们看到还有一个modCount成员变量，这个变量是ArrayList继承AbstractList得到的，用于记录ArrayList被修改的次数。真正扩容的操作在grow方法中，它的源码如下：

```java
/**
     * Increases the capacity to ensure that it can hold at least the
     * number of elements specified by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        elementData = Arrays.copyOf(elementData, newCapacity);
    }
```

我们可以看到ArrayList每次扩容后的大小为oldCapacity + (oldCapacity >> 1)，即原来的1.5倍。

下面我们看一下remove方法，它的源码如下：

```java
/**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If the list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * <tt>i</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (int index = 0; index < size; index++)
                if (elementData[index] == null) {
                    fastRemove(index);
                    return true;
                }
        } else {
            for (int index = 0; index < size; index++)
                if (o.equals(elementData[index])) {
                    fastRemove(index);
                    return true;
                }
        }
        return false;
    }
```

可以看到删除时分为了两种情况，把null当做特殊情况单独划为一类，因为List中是可以存储null的。主要的删除操作在fastRemove方法中，我们看看这个方法。

```java
    private void fastRemove(int index) {
        modCount++;
        int numMoved = size - index - 1;
        if (numMoved > 0)
            System.arraycopy(elementData, index+1, elementData, index,
                             numMoved);
        elementData[--size] = null; // clear to let GC do its work
    }
```

我们看到这个方法传入的是需要删除的元素的索引，里面也分为了两种情况，如果这个元素就在末尾，则直接将它设为null，索引前移一位；如果不是，调用System.arraycopy方法，这个方法在这里的效果就是将当前索引之后的元素前移一位。使删除后的List依然紧凑。

除了add和remove方法，List常用的可能还有set和get方法，这俩方法根据传入的索引找到相应的元素，再对它进行一些操作。其实add和remove还有一个依托索引的重载版本，这里就不在详细介绍了，它们的定义如下。

```java
public E get(int index)
public E set(int index, E element)
public void add(int index, E element)
public E remove(int index)
```

从Java 8 开始集合类中增加了一些函数性接口，用于对Lamdba表达式的支持。如List中的forEach用于集合的循环遍历，还有共同的一个steam方法可以实现集合的过滤，映射及其他复杂的操作。这里就不再介绍了，感兴趣的同学可以去学习下Lamdba相关的知识。

### LinkedList

LinkedList是List的另一个实现，底层采用的是一个双端链表。在插入和删除效率上，LinkedList比ArrayList更加优秀。因为ArrayList比LinkedList多维持的一个索引，这虽然给它提供了随机访问的特性，但也额外增加了一点负担，最重要的是在数据超过界限时，ArrayList需要进行扩容操作，而LinkedList不需要，它只需要在尾部增加一个节点就行了（所以在队列中一般喜欢叫底层为数组的为有界队列，而链表为无界队列）。同时在删除时，我么前面介绍了ArrayList是将当前索引的后续数据整个前移一位，这就造成了很大的负担，而LinkedList只需要更改下指针的指向就行了。下面我们来看一下它的源码。

我们先看看它的继承结构图，如下：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/LinkedList继承结构图.png" alt="LinkedList继承结构图" style="zoom:50%;" />

与ArrayList不同的是它实现了Deque，这是一个双端队列接口，赋予了LinkedList双端队列的一些功能；还有个不同处是它继承了AbstractSequentialList而不是AbstractList，当然AbstractSequentialList其实就是AbstractList的子类，但是它装饰了更多的功能。

它的主要成员变量如下：

```java
transient int size = 0;

    /**
     * Pointer to first node.
     * Invariant: (first == null && last == null) ||
     *            (first.prev == null && first.item != null)
     */
    transient Node<E> first;

    /**
     * Pointer to last node.
     * Invariant: (first == null && last == null) ||
     *            (last.next == null && last.item != null)
     */
    transient Node<E> last;
```

相比于ArrayList，LinkedList的成员变量比较少，一个标识节点数量的size，以及分别指向首节点和尾节点的first和last。

再看看它的构造函数。

```java
    public LinkedList() {
    }

    /**
     * Constructs a list containing the elements of the specified
     * collection, in the order they are returned by the collection's
     * iterator.
     *
     * @param  c the collection whose elements are to be placed into this list
     * @throws NullPointerException if the specified collection is null
     */
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }
```

LinkedList的构造函数只有两个，分别是默认创建和根据已有集合创建。

简单看下它的add和remove方法。add方法的源码如下：

```java
/**
     * Appends the specified element to the end of this list.
     *
     * <p>This method is equivalent to {@link #addLast}.
     *
     * @param e element to be appended to this list
     * @return {@code true} (as specified by {@link Collection#add})
     */
    public boolean add(E e) {
        linkLast(e);
        return true;
    }
```

```java
/**
     * Links e as last element.
     */
    void linkLast(E e) {
        final Node<E> l = last;
        final Node<E> newNode = new Node<>(l, e, null);
        last = newNode;
        if (l == null)
            first = newNode;
        else
            l.next = newNode;
        size++;
        modCount++;
    }
```

我们可以看到其实就是直接在链表末尾加一个节点，使last指向它，然后调整指针使前一个节点指向这个节点，如果是第一个元素，就把first指向它。最后修改size和modCount的值。

它的remove方法如下：

```java
/**
     * Removes the first occurrence of the specified element from this list,
     * if it is present.  If this list does not contain the element, it is
     * unchanged.  More formally, removes the element with the lowest index
     * {@code i} such that
     * <tt>(o==null&nbsp;?&nbsp;get(i)==null&nbsp;:&nbsp;o.equals(get(i)))</tt>
     * (if such an element exists).  Returns {@code true} if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param o element to be removed from this list, if present
     * @return {@code true} if this list contained the specified element
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }
```

```java
/**
     * Unlinks non-null node x.
     */
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;
        final Node<E> next = x.next;
        final Node<E> prev = x.prev;

        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
            x.prev = null;
        }

        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
            x.next = null;
        }

        x.item = null;
        size--;
        modCount++;
        return element;
    }
```

我们可以看到，remove方法就是遍历链表，找到需要删除的节点，然后调用unLink方法将它删除，unLink方法就是考虑各种情况改变下指针的指向，并把节点中的元素置为空。

LinkedList就介绍到这里，下面看下一个古老的集合类Vector。

### Vector

Vector是一个非常古老的集合类，早在JDK1.2就已存在，但是JDK1.5开始，JDk中增加了一些新的集合类后，比如上面的ArrayList和LinkedList。Vector就基本上不再使用，虽然JDK的开发者们对它也进行了兼容。

Vector的实现与ArrayList很像，事实上ArrayList就是用于在单线程环境中替换它的，它与ArrayList的不同之处在于Vector是线程安全的，它的一些操作方法都使用synchronized关键字进行了同步，使得它在多线程环境下可以安全使用，但它的性能并不能让人满意，虽然JVM从JDK1.5开始对synchronized进行了优化，但在高并发情况下依然差强人意。所以大多数情况下我们都不会使用它，而是使用concurrent包下的CopyOnWriteArrayList来替代它。

所以读者们，这个类我们就不在进行详细介绍了，忘记JDK中还有这个类吧。

### ConcurrentModificationException问题

在java集合的循环遍历中如果使用不当可能会出现ConcurrentModificationException，看名字我们可能觉得它是并发修改异常，事实上在单线程环境也可能出现这个问题，当然在多线程环境中更可能出现。但是在多线程环境我们就不该使用这些集合因为它们本来就是线程不安全，我们应该使用concurrent包下的相应集合类来替换它。下面我们来看一下在单线程环境中为什么会出现这个问题。

通过迭代器对集合进行遍历或者使用foreach循环，如while (iterator.hasNext())或者for (String string : strings)实际上都是java集合中的迭代器实现。后者是一种语法糖，在通过javac编译解析后本质上还是迭代器，而迭代器迭代集合时会调用下面这个方法。

```java
public E next() {
            checkForComodification();
            int i = cursor;
            if (i >= size)
                throw new NoSuchElementException();
            Object[] elementData = ArrayList.this.elementData;
            if (i >= elementData.length)
                throw new ConcurrentModificationException();
            cursor = i + 1;
            return (E) elementData[lastRet = i];
        }
```

前面说过我们只考虑单线程情况下的异常问题，而在单线程下事实上抛出异常的情况是在checkForComodification方法中，我们来看一下这个方法的实现。

```java
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
```

它其实就是比较了modCount和expectedModCount两个的值，如果不相等就会抛出这个异常，而modCount我们前面介绍过，在调用List的add和remove都会使这个值加1，而expectedModCount是在迭代器定义的，初始值就是modCount的值。

```java
private class Itr implements Iterator<E> {
        int cursor;       // index of next element to return
        int lastRet = -1; // index of last element returned; -1 if no such
        int expectedModCount = modCount;
    .....
}
```

所以它们在一开始还是相等的，但是在调用add或着remove后modCount被修改了，而expectedModCount没有，所以就会抛出这个异常。

那在单线程环境中如何避免它呢，我们可以通过调用迭代器的remove方法，它的方法如下：

```java
public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            checkForComodification();

            try {
                ArrayList.this.remove(lastRet);
                cursor = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException ex) {
                throw new ConcurrentModificationException();
            }
        }
```

可以看到这个方法中在remove之后重新矫正了expectedModCount的值，使得它们满足相等。



