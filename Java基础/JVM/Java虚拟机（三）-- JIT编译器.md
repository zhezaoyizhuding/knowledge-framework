---
title: Java虚拟机中的优化
date: 2018-11-14 16:40:34
categories: Java虚拟机
tags:
- 编译优化
- 运行优化
- 语法糖
---

在校园里，当我们初学Java语言时，我们的老师或许都曾经告诉过我们：Java是一种解释型语言，所以它的效率要比C/C++语言要差，要更慢。这句话并没有错误，但是随着时间的发展，它开始慢慢变得不再准确。Java虚拟机的开发者们对虚拟机进行了大量的优化，使它在运行起来或许已经不比C++语言慢多少了。

对虚拟机的优化分为早起优化（也叫编译期优化），和晚期优化（也叫运行期优化），下面Java虚拟机在这两个时间点主要做了哪些优化。

### 早期优化

这里的早期其实是一个不太确定的时间点，因为Java虚拟机中的编译其实存在两个过程：一个是将源码编译成字节码文件，另一个是将字节码编译成机器码（其实还有些虚拟机中的编译器直接将源码编译成机器码，比如AOT编译器）。有的同学可能会疑问，Java不是一门解释型语言吗？为什么还会有将字节码编译成机器码的过程，应该是解释读取字节码才对吧。事实上，确实是如此，Java程序在运行时，字节码是被解释执行的，但现在的虚拟机中增加了一个即时编译器（即JIT编译器），在Java程序运行一段时间后，其中的常用路径的字节码会被这个编译器编译成机器码，以提升运行效率。

即时编译器其实主要是在运行期的优化，也就是后文将要说的晚期优化。而早期优化其实就是一些语法糖，这些都是通过Javac编译器实现的。

##### Javac编译器

Javac编译器是用来编译Java语言的编译器，笔者在上篇博客提到过现在在Java虚拟机中除了可以运行Java语言，还可以运行其他的很多语言，比如Groovy，而每一个语言都会有一个自己对应的编译器将它编译成Class文件，Javac编译器就是专门用来编译Java语言的。Javac编译器是通过Java语言编写的，感兴趣的同学可以看看它的源码，下面看一下Java语言编译的过程。

Java的编译过程大致可以分为三个步骤：

- 解析和填充符号表：该过程进行词法分析、语法分析生成抽象语法树，并填充符号表，供之后的语义分析使用。
- 插入式注解处理器的注解处理：对编译器的注解进行处理，通过它来读取，修改，添加语法树。
- 分析和字节码生成：语义分析，解语法糖，字节码生成

##### 语法糖

几乎各种语言都会或多或少提供一些语法糖来方便程序的开发，虽然这些语法糖无法提升运行时的效率，但是却可以方便程序的开发以及提升代码的可读性。但是也有些观点认为太多的语法糖不一定都是有益的，因为它会隐藏代码实现的细节，所以了解语法糖背后的实现是有必要的。下面介绍一下Java中常见的语法糖。

###### 字符串相加

或许有些童鞋使用过“+”号来连接字符串的操作，之前的老师或者前辈们或许都曾劝诫过我们不要这样使用，因为这样的语句会生成多个字符串对象，造成不必要的内存浪费，应该使用StringBuider或者StringBuffer来替换。但事实上，现代虚拟机（比如笔者写这篇博客时JDK11都听说要出来了，笔者自己用的也是Java8）中这个字符串连接符号就是一个语法糖，在编译时，Javac会自动把它替换成StringBuider或者StringBuffer。

例如，有这样一个Java源文件，如下：

```java
public class Demo_1 {
    public String example() {
        String str1 = "first string";
        String str2 = "second string";
        String str3 = str1 + str2;
        return str3;
    }
}
```

我们在用Javac将它编译成Class文件，再使用反编译工具（比如jad）将它重新还原成java源文件，就会变成下面这个样子。

```java
public class Demo_1
{
    public Demo_1()
    {
    }
    public String example()
    {
        String str1 = "first string";
        String str2 = "second string";
        String str3 = (new StringBuilder()).append(str1).append(str2).toString();
        return str3;
    }
}
```

可以看到“+”连接符边替换成了StringBuilder的append操作。

###### 自动装箱、拆箱

了解包装类的童鞋应该知道自动装箱和拆箱其实就是调用包装类的饿××.valueOf 方法和××.××val方法。例如下面这段代码。

```java
public class Demo_2 {

    public void demo() {
        int a = 11;
        Integer b = a;
        int c = a + b;
    }
}
```

反编译后：

```java
public class Demo_2
{
    public Demo_2()
    {
    }
    public void demo()
    {
        int a = 11;
        Integer b = Integer.valueOf(a);
        int c = a + b.intValue();
    }
}
```

可以看到装箱时调用了Integer的valueOf方法，拆箱时调用了它的intValue方法。

###### 循环遍历

java中有一种foreach循环，使用的是集合的迭代器。如下：

```java
public class Demo_3 {

    public void demo() {
        List<String> strings = new ArrayList<>();
        strings.add("frist");
        strings.add("second");
        strings.add("third");
        for (String s : strings) {
            s = s + "test";
        }
    }
}
```

反编译后

```java
public class Demo_3
{
    public Demo_3()
    {
    }
    public void demo()
    {
        List strings = new ArrayList();
        strings.add("frist");
        strings.add("second");
        strings.add("third");
        for(Iterator iterator = strings.iterator(); iterator.hasNext();)
        {
            String s = (String)iterator.next();
            s = (new StringBuilder()).append(s).append("test").toString();
        }
    }
}
```

###### 变长参数

变长参数解语法糖后实质是个数组，如下：

```java
public class Demo_4 {
    public void demo(String...args) {
        args[0] = "first";
        args[1] = "second";
        args[2] = "third";
    }
}
```

解语法糖后

```java
public class Demo_4
{
    public Demo_4()
    {
    }
    public transient void demo(String args[])
    {
        args[0] = "first";
        args[1] = "second";
        args[2] = "third";
    }
}
```

###### 条件编译

消除分支不成立的代码块。如下：

```java
public class Demo_5 {

    public void demo() {
        if (true) {
            System.out.println("first");
        } else {
            System.out.println("second");
        }
    }
}
```

解语法糖后：

```java
public class Demo_5
{
    public Demo_5()
    {
    }
    public void demo()
    {
        System.out.println("first");
    }
}
```

###### 泛型

泛型最初生根发芽于C++语言中的模板，后来延伸到Java与C#语言中，但是在Java与C#中泛型的实现是不同的。C#中的泛型是通过CLR实现的，是真实的类型，比如在C#中，List<Integer>与List<String>就是完全不同的类型，无论是在 源码期，编译期，还是运行期。这种实现方式被称为类型膨胀，产生的泛型被称为真实泛型。

而在Java，List<Integer>与List<String>在编译后就是同一个类型List，多余的类型会被擦除，并使用强制类型转换替代。看到这里很多同学就会明白了，Java中的泛型其实

就是一颗语法糖，它也只能减少强制类型转换错误的发生，在运行期它们是没有区别的。这种实现泛型的方式被称为类型擦除，实现的泛型被称为伪泛型。例如：

```java
public class Demo_6 {

    public void demo_1(List<String> list) {
        list.add("first");
        String str = list.get(0);
        System.out.println("List<String> list");
    }

    public void demo_2(List<Integer> list) {
        System.out.println("List<Integer> list");
    }
}
```

反编译 后：

```java
public class Demo_6
{
    public Demo_6()
    {
    }
    public void demo_1(List list)
    {
        list.add("first");
        String str = (String)list.get(0);
        System.out.println("List<String> list");
    }
    public void demo_2(List list)
    {
        System.out.println("List<Integer> list");
    }
}
```

细心的同学可能发现了，反编译后，如果两个方法的方法名相同，那么它们的签名应该也是相同的，所以这样的泛型是无法重载的。

###### 枚举

Java中的枚举其实也是依靠语法糖实现的，本质上还是一个Class类。例如：

```java
public enum Demo_7 {

    FIRST("first"),
    SECOND("second"),
    THIRD("third");

    private String name;
    Demo_7(String name) {
        this.name = name;
    }
}
```

反编译后：

```java
public final class Demo_7 extends Enum
{

    public static Demo_7[] values()
    {
        return (Demo_7[])$VALUES.clone();
    }

    public static Demo_7 valueOf(String name)
    {
        return (Demo_7)Enum.valueOf(com/example/demo/SyntacticSugar/Demo_7, name);
    }

    private Demo_7(String s, int i, String name)
    {
        super(s, i);
        this.name = name;
    }

    public static final Demo_7 FIRST;
    public static final Demo_7 SECOND;
    public static final Demo_7 THIRD;
    private String name;
    private static final Demo_7 $VALUES[];

    static 
    {
        FIRST = new Demo_7("FIRST", 0, "first");
        SECOND = new Demo_7("SECOND", 1, "second");
        THIRD = new Demo_7("THIRD", 2, "third");
        $VALUES = (new Demo_7[] {
            FIRST, SECOND, THIRD
        });
    }
}
```

具体的我就不再介绍了，感兴趣的读者可以看看笔者的另一篇博客[浅析枚举](https://thatboy.coding.me/2018/03/26/%E6%B5%85%E6%9E%90%E6%9E%9A%E4%B8%BE/) 。

### 晚期优化

前面提到过，当有些代码路劲的代码执行的很频繁时，虚拟机就会把这些代码标记为热点代码，为了提升这些代码的执行效率，虚拟机会把这些代码编译成机器码，并进行各种优化（在机器码或者字节码到机器码的中间状态中），而这个过程便是通过一个叫做即时编译器的（JIT编译器）东西完成的。下面我们就来了解一下这个即时编译器，本文的虚拟机特指HotSpot虚拟机。

##### 分层编译策略 

HotSpot虚拟机中是采用分层编译策略来执行代码，具体分为三层，如下：

- 第0层：程序解释执行，解释器不开启性能监控功能呢，可触发第一次编译。
- 第1层：也称为C1编译（HotSpot中的即时编译器），将字节码编译成本地代码，并进行简单可靠的优化，如有必要将加入性能监控的逻辑。
- 第2层（或第2层以上）：也称为C2 编译，也是讲字节码转为机器码，但会启用一些编译耗时较长的优化，甚至会根据性能监控信息进行一些不可靠的激进优化（如果监控到优化失败，则会回退到解释执行，在HotSpot中解释执行作为激进优化失败的逃生门）。

##### 热点代码

前面提到热点代码会被编译器编译成机器码，那么这个热点代码到底是什么？事实上，热点代码主要分为两类，如下：

- 被多次调用的方法
- 被多次执行的循环体

热点代码说是分为两类，但实际上，第二种的循环体在被编译时，也是编译整个循环体所在的方法，而不是单独的循环体。这种在方法执行过程中编译的编译方式，被称为栈上替换（简称OSR编译），因为方法栈帧还是栈上，但是方法被替代了。

细心的读者可能会有些困惑，上面在定义热点代码时，用了“多次”这个字眼，这不是一个严谨的术语。“多次”到底是多少次呢？虚拟机有自己的机制来区分什么条件下的一段代码才是热点代码，这种机制叫做热点探测。虚拟机中的热点探测主要分为以下两种：

- 基于采样的热点探测：该方法会检测各个线程的栈顶，如果发现某个或某些方法经常出现在栈顶，那这个方法就是热点方法。该种方法的好处是简单高效，还可以很容易获得方法调用关系，缺点是很难精确的确认一个方法的热度，容易受到线程阻塞或者别的外界因素的影响。
- 基于计数的热点探测：这种方法会为每个方法或者循环体建立计数器，统计方法的执行次数，如果执行次数超过一定的阈值就认为它是热点方法。这种方法缺点是实现起来麻烦一些，需要为每个方法建立并维护计数器，并且不能获取方法的调用关系，优点是统计结果相对来说更加精确和严谨。

在HotSpot虚拟机中使用的是第二种方式，它为每个方法准备了两种计数器：方法调用计数器与回边计数器。

方法调用计数器顾名思义就是统计方法调用的次数，当一个方法被调用时，会首先检查该方法是否存在一个呗JIT编译过的版本，如果存在，则优先使用编译过的版本来执行。如果没有，则将该方法的调用计数器加1，然后判断方法调用计数器与回边计数器之和是够超过方法调用计数器的阈值，如果已超过阈值，则会向JIT提交一个该方法的编译请求（如果不做任何设置，则编译过程是异步的，即该次方法调用不会等待编译请求完成，而是直接解释执行）。方法的执行流程大致如下图所示：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/方法调用计时器执行过程.png" alt="方法调用计时器执行过程" style="zoom:33%;" />

值得注意的是默认情况下，方法调用计数器统计的并不是方法调用的绝对次数，而是一段时间内的相对次数，如果超过了这个时间段，而方法的调用次数还没有超过阈值，则该统计次数会减半，这个叫做热度的衰减，可以通过虚拟机参数来关闭热度的衰减。

回边计数器统计的是一个方法中循环体执行的次数，当解释器遇到一条回边指令（控制流向后跳转的指令）时，会先查找想要执行的代码片段是否存在已经编译好的版本。如果有，则优先执行已编译好的代码，如果没有，就把回边计数器的值加1，然后判断回边计数器和方法调用计数器的和是否超过回边计数器的阈值。如果超过阈值，则会提交一个OSR编译请求，并且把回边计数器的值降低一些，以便继续在解释器中执行循环，等待编译输出编译结果。它的大致流程如下图所示：

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/回边计数器执行过程.png" alt="回边计数器执行过程" style="zoom:33%;" />

与方法调用计数器不同的是，回边计数器没有热度衰减的过程，它统计的就是循环体执行的绝对次数。

##### 编译优化技术

虚拟机团队在即时编译器中增加了很多优化技术，这些优化技术发生在字节码编译成机器码或者是中间代码的过程中。这些优化技术很多，我们常听的或许有方法内联，常量消除，空值消除，公共表达式消除等，可能还有并发相关的锁粗化，锁消除。这些都是在即时编译器中的优化技术，因为优化很多，并且实现很复杂，笔者也只是知其然不知其所以然，就不做详细介绍了。感兴趣的同学可以查看《深入理解Java虚拟机》书中的编译优化章节，里面会有一张优化技术表及常见优化举例。或者也可以查看该链接[即时编译器优化技术表](https://wiki.openjdk.java.net/display/HotSpot/PerformanceTacticIndex) 。

### 总结

本文简单介绍了虚拟机从源码到机器码的过程中做了哪些事情，希望读者能通过此篇博文能对虚拟机有更清楚的了解。本文是笔者提炼《深入理解Java虚拟机》中的一些章节而成，陈述比较简单。很多东西都没有详细介绍到，如果笔者希望有更深入的了解，可以阅读《深入理解Java虚拟机》和《Java虚拟机规范》等书籍。