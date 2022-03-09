---
title: 浅析Set
date: 2018-03-22 14:07:35
categories: Java源码浅析
tags:
- Set
- HashSet
- TreeSet
---

Set是java集合框架的重要成员之一，常见的有HashSet，LinkedHashSet，TreeSet等。本篇博客便通过源码简单介绍下这些Set集合。

### 概览

Set是一个不允许重复的集合框架，很多书籍会把它形容成一个罐子。你可以把数据放入罐中，但是当你再想把它取出来时，你就无从下手；只能将所有数据倒出，一个个分辨。也就说你无法随机访问Set中的数据，那么相同的重复数据便没有意义，因为你无法分辨他们，对Set而言他们是完全相同的。下面看一下Set家族的继承结构：

![Set继承结构](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/Set继承结构.png)

可以看到主要的几个实现类都实现了set接口，并且继承了AbstractSet抽象类。AbstractSet这个抽象类中除了实现了基本的equals，hashCode方法，还有一个removeAll方法，它的源码如下：

```java
public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        boolean modified = false;

        if (size() > c.size()) {
            for (Iterator<?> i = c.iterator(); i.hasNext(); )
                modified |= remove(i.next());
        } else {
            for (Iterator<?> i = iterator(); i.hasNext(); ) {
                if (c.contains(i.next())) {
                    i.remove();
                    modified = true;
                }
            }
        }
        return modified;
    }
```

这个方法也没什么说的，它根据传入的集合的大小分成了两种情况，分别采用集合自身的remove方法和当前集合所用的迭代器的方法来循环删除集合的元素。而无论是集合自身的remove方法还是Iterator的具体实现类，都是由AbstractSet的子类决定的。比如在HashSet中它的Iterator就是HashMap中的KeyIterator。

### HashSet

HashSet的底层是采用HashMap实现。要是理解了HashMap，那么HashSet也就很简单了，事实上当笔者写到这时，完全不知道该如何写下去了，因为它的所有操作都是对HashMap中的key的操作。Map的有趣处就在于，你可以把它的key看做Set，values看做List。还是来看看它的源码吧，在这里笔者不会详细介绍到HashMap中的操作，感兴趣的同学可以看看笔者的另外一篇关于hashMap的博客。

先看看HashSet的属性：

```java
static final long serialVersionUID = -5024744406713321676L;

    private transient HashMap<E,Object> map;

    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();
```

可见它只有三个属性，serialVersionUID是为了解决类在序列化时的版本化问题的，事实上所有的集合类都有这个常量属性。那么最后只剩map和PRESENT了，map就是一个HashMap用来存储具体的数据的，前面说了Set和map中的key很像，我们只要用到map中的key就行了，values怎么办呢？索性在里面存个虚拟的无用对象就行了，这就是PRESENT，这个属性就是用来填充values的。

###### add方法

这里重点介绍一下add方法，让我们看看Set为什么不会重复？说到这里可能已经有同学想要打我了，答案就是因为map的key不会重复...。无论是HashMap的Node数组还是TreeMap的红黑树都是通过key来定位位置的，所以相同的key永远都会是同一个位置，实在是没办法重复。但是，其实HashSet中还是做了一些骚操作的。让我们来看看add方法的源码：

```java
public boolean add(E e) {
    return m.put(e, PRESENT)==null;
}
```

我们看见add方法只有一行，就是对HashMap的put方法的返回做了个判空操作。那put方法返回什么呢？map的put方法返回的是在put操作时，value中已经存在的旧值，如果是之前没有值，那就是null。所以我们想象一下这样的场景，当HashSet第一次add时。因为这个key之前没有值，就是null，所以add方法返回true；但是当不是首次add时，此时key对应的value已经存在了一个值PRESENT，后面的所有add操作都是在PRESENT的值之间的替换，永远返回一个Object。所以add的返回值一直是false。但事实上它的操作和true时没有区别。

HashSet这里只介绍一下add方法，其他的一些方法也都是委托给HashMap的相应方法来操作的，笔者这里就不赘述了，想了解的同学可以去看看笔者的另一篇关于HashMap的博客。

### LinkedHashSet

LinkedHashSet继承于HashSet，它的底层操作是委托给LinkedHashMap运作的。我们在看LinkedHashSet的源码时可能只能看到几个构造函数，而所有的构造函数都调用了一个父类的构造函数。这个构造函数的源码如下：

```java
HashSet(int initialCapacity, float loadFactor, boolean dummy) {
    map = new LinkedHashMap<>(initialCapacity, loadFactor);
}
```

这个构造函数虽然是放在HashSet中，但它是包级权限的，而且在HashSet的几个公有的构造方法中并没有调用它，它应该是专门提供给LinkedHashSet使用的。从源码中我们看到这里给map引用指定了一个LinkedHashMap的实体，后面所有使用map的操作都是在LinkedHashMap中操作。

LinkedHashSet的基本操作方法都是继承自HashSet，只是替换了里面的map实体。这里就不介绍了，感兴趣的同学可以看下笔者的一篇关于LinkedHashMap的博客。

### TreeSet

与前面两个Set类似，TreeSet的操作也是委托给Map来做的。相信大家已经猜到了，那就是TreeMap，它所有的增删改查都是在TreeMap，也即是在红黑树上的操作。这里笔者就不做具体介绍了，感兴趣的同学可以看下笔者的一篇关于TreeMap的博客。

### 结束语

本文对一些常见的Set进行了简单的介绍，其实还有一个EnumSet。但笔者还打算写一篇关于Enum的博客，到时打算将EnumSet和EnumMap一起介绍，这里暂且按下不表。最后，还是那句话，由于笔者水平有限且JDk仍在持续迭代，对于博客内容，读者不可全信，应当翻看自己所用版本的JDK或者多查询相关资料相互验证。
