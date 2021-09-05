---
title: 浅析Java包装类
date: 2018-03-28 13:45:19
categories: Java源码浅析
tags:
- 包装类
- 自动装箱
- 自动拆箱
---

前阵子笔者被电话面了个试，其间被问到了一个java包装类的问题，让笔者说道说道java包装类的缓存机制。当时笔者一脸懵逼，一个包装类还缓存个奶子欧！最近闲来无事便翻了些博客和java包装类的源码，看看这包装类的缓存机制到底是什么样子的。

### 装箱与拆箱

关于包装类说的最多的就是自动装箱和拆箱，我们先来看看这个。java现在有九种基本类型，int，byte，short，float，double，long，boolean，char和reference。除了引用类型，在JDK的lang包下面存在着其他八种基本类型对应的包装类，分别是Integer，Byte，Short，Float，Double，Long，Boolean，Character。我们来分别看看他们的装箱和拆箱方法。首先我们需要明白在什么情况下会发生拆箱和装箱？在它们和基本类型进行转换或者比较时发生。比如：

```java
// 装箱
Integer i = 10;

//拆箱
int j = 0;
j = i;
```

拆箱和装箱分别会调用这些包装类对应的***value方法和valueOf方法。

##### Integer

Integer是Integer类型的包装类，Integer是一个32位的整数，范围是-2,147,483,648（-2^31）-- 2,147,483,647（2^31 - 1）。我们来看看Integer的装箱方法，源码如下：

```java
public static Integer valueOf(int i) {
    if (i >= IntegerCache.low && i <= IntegerCache.high)
        return IntegerCache.cache[i + (-IntegerCache.low)];
    return new Integer(i);
}
```

我们看到在这个方法中首先会进行一个范围判断，如果在这个范围之外就重新new一个Integer，如果这在范围之内，就直接返回一个对象，而这个对象就是以缓存形式存在的。我们看看Integer的缓存实现，即看一下这个IntegerCache的实现。代码如下：

```java
private static class IntegerCache {
        static final int low = -128;
        static final int high;
        static final Integer cache[];

        static {
            // high value may be configured by property
            int h = 127;
            String integerCacheHighPropValue =
                sun.misc.VM.getSavedProperty("java.lang.Integer.IntegerCache.high");
            if (integerCacheHighPropValue != null) {
                try {
                    int i = parseInt(integerCacheHighPropValue);
                    i = Math.max(i, 127);
                    // Maximum array size is Integer.MAX_VALUE
                    h = Math.min(i, Integer.MAX_VALUE - (-low) -1);
                } catch( NumberFormatException nfe) {
                    // If the property cannot be parsed into an int, ignore it.
                }
            }
            high = h;

            cache = new Integer[(high - low) + 1];
            int j = low;
            for(int k = 0; k < cache.length; k++)
                cache[k] = new Integer(j++);

            // range [-128, 127] must be interned (JLS7 5.1.7)
            assert IntegerCache.high >= 127;
        }

        private IntegerCache() {}
    }
```

可以看到这段代码缓存了一个Integer数组，结合上面的valueOf方法代码，可知道当装箱的int范围在[-128,127]时，直接从这个数组中返回一个对象。下面看看Integer的拆箱方法：

```java
/**
 * Returns the value of this {@code Integer} as an
 * {@code int}.
 */
public int intValue() {
    return value;
}
```

就是直接返回integer内部value属性。

##### Short

Short是short类型的包装类，short是一个16位的整数，范围是-32768（-2^15）-- 32767（2^15 - 1）。我们看一下它的装箱代码：

```java
public static Short valueOf(short s) {
    final int offset = 128;
    int sAsInt = s;
    if (sAsInt >= -128 && sAsInt <= 127) { // must cache
        return ShortCache.cache[sAsInt + offset];
    }
    return new Short(s);
}
```

与Integer的装箱方法其实差不多，也是在装箱的short值在[-128,127]之间时，返回一个缓存的对象。我们再看一下它的缓存实现：

```java
private static class ShortCache {
    private ShortCache(){}

    static final Short cache[] = new Short[-(-128) + 127 + 1];

    static {
        for(int i = 0; i < cache.length; i++)
            cache[i] = new Short((short)(i - 128));
    }
}
```

拆箱算法和Integer一样都是直接返回value值。如下：

```java
public short shortValue() {
    return value;
}
```

##### Byte

Byte是byte的包装类，byte数据类型是8位、有符号的，以二进制补码表示的整数；最小值是 -128（-2^7），最大值是 127（2^7-1）。我们来看看它的装箱代码：

```java
public static Byte valueOf(byte b) {
    final int offset = 128;
    return ByteCache.cache[(int)b + offset];
}
```

```java
private static class ByteCache {
    private ByteCache(){}

    static final Byte cache[] = new Byte[-(-128) + 127 + 1];

    static {
        for(int i = 0; i < cache.length; i++)
            cache[i] = new Byte((byte)(i - 128));
    }
}
```

由于short的范围只有-128 -- 127，所以它的所有的装箱操作都是返回缓存值。拆箱方法：

```java
public byte byteValue() {
    return value;
}
```

##### Long

Long是long的包装类，long 数据类型是 64 位、有符号的以二进制补码表示的整数。最小值是 -9,223,372,036,854,775,808（-2^63）；最大值是 9,223,372,036,854,775,807（2^63 -1）。我们来看看它的装箱方法：

```java
public static Long valueOf(long l) {
    final int offset = 128;
    if (l >= -128 && l <= 127) { // will cache
        return LongCache.cache[(int)l + offset];
    }
    return new Long(l);
}
```

```java
private static class LongCache {
    private LongCache(){}

    static final Long cache[] = new Long[-(-128) + 127 + 1];

    static {
        for(int i = 0; i < cache.length; i++)
            cache[i] = new Long(i - 128);
    }
}
```

和前面的几个包装类大致类似，缓存范围都在[-128,127]之间。拆箱算法如下：

```java
public long longValue() {
    return value;
}
```

##### Float

Float是float的包装类，float是一个32位单精度的浮点数。Float的装箱算法如下：

```java
public static Float valueOf(float f) {
    return new Float(f);
}
```

可见Float的装箱算法是没有缓存的，这也可以理解，毕竟在某个范围内浮点数的个数可以是无穷个，多少内存也不够使。拆箱算法如下：

```java
public float floatValue() {
    return value;
}
```

##### Double

Double是double的包装类，double是一个64位的浮点数，也是java默认的浮点数类型。它的装箱算法如下：

```java
public static Double valueOf(double d) {
    return new Double(d);
}
```

拆箱算法如下：

```java
public double doubleValue() {
    return value;
}
```

可见它的装箱拆箱与float类似。

##### Boolean

Boolean是boolean的包装类，boolean数据类型表示一位的信息，只有两个取值：true 和 false。它的装箱拆箱算法如下：

```java
public static Boolean valueOf(boolean b) {
    return (b ? TRUE : FALSE);
}
```

```java
public boolean booleanValue() {
    return value;
}
```

##### Character

Character是char的包装类，char类型是一个单一的16位Unicode字符，最小值是\u0000（即为0）；最大值是\uffff（即为65535）。它的装箱算法如下：

```java
public static Character valueOf(char c) {
    if (c <= 127) { // must cache
        return CharacterCache.cache[(int)c];
    }
    return new Character(c);
}
```

可见Character也是有缓存的，它的缓存实现如下：

```java
private static class CharacterCache {
    private CharacterCache(){}

    static final Character cache[] = new Character[127 + 1];

    static {
        for (int i = 0; i < cache.length; i++)
            cache[i] = new Character((char)i);
    }
}
```

可见当char对应的Unicode值小于127时，使用缓存，即缓存范围为[0,127]。

### 几个题目

现在我们对java包装类的装箱拆箱和缓存机制有了一定的了解，下面来看一下笔者在网上看到的几个题目。

```java
Integer a = 100;
Integer b = 100;
Integer c = 1000;
Integer d = 1000;

System.out.println(a == b);
System.out.println(c == d);
```

```java
Integer a = new Integer(10);
Integer b = 10;
int c = 10;

System.out.println(a == b);
System.out.println(a == c);
```

读者可以考虑一下上面两段代码的输出。笔者无意间还发现了这样一个题目：

```java
String str1 ="abc";
String str2 ="abc";
System.out.println(str2 == str1);
```

这里我们首先需要理解在java中==只做值的比较，这个运算符的两个操作数的值必须相等，无论是基本类型还是引用类型的值。而另一个用于比较的equals只会用于对象之间的比较，它是一种逻辑上的相等（我们可以简单理解为内容上的相等，当然这也并不恰当，因为这个逻辑是我们可以自由定义的，而不在于内容）。

那么这个输出应该是true还是false呢？答案是极大可能是true。这里可能涉及到编译器和jvm的优化，比如对某些编译器（事实上可能极大多数）而言，当它发现存在两个相同的字符串常量被引用时，那么它只会创建一个字符串常量，而将这两个引用类型都指向这个常量；另一方面，就算编译器没有做这个事情，JVM在运行期也有可能检测到这个事实并做出同样的事情。所以上面的输出是true

当然上面这些其实并没有什么意思（但是很多笔试题喜欢考...），因为不论是JDK的官方文档还是当吹我们刚刚开始学习java时，我们的领路人们都告诉我们对象之间的比较要用equals而不是==。有些规范我们确实应该告诉自己，它是有义务去遵守的，因为这些都是前人的经验，能帮助我们省去很多麻烦。

### 结束语

本篇博客简单介绍了java包装类的自动装箱和拆箱操作，及它们内部的缓存机制。只是做了一些简单的介绍，并没有做其他深入的了解，感兴趣的读者可以自行研究一下Java包装类的源码。笔者是瞅了几眼就不再有心情继续撸下去了，果然还是不适合干技术啊。
