## 五种网络IO模型

## 1. 概念说明

为了便于理解后面的内容，我们先来了解一些概念。

### 1.1 Socket

Socket 中文翻译为套接字，是计算机网络中进程间进行双向通信的端点的抽象。一个 Socket 代表了网络通信的一端，是由操作系统提供的进程间通信机制。

- 在操作系统中，通常会为应用程序提供一组应用程序接口，称为 Socket 接口（Socket API）。应用程序可以通过 Socket 接口，来使用网络 Socket，以进行数据的传输。
- 一个 Socket 由IP地址和端口组成，即：Socket 地址 = IP地址 : 端口号。
- 在同一台计算机上，TCP 协议与 UDP 协议可以同时使用相同的端口（Port），而互不干扰。
- 要想实现网络通信，至少需要一对 Socket，其中一个运行在客户端，称之为 Client Socket；另一个运行在服务器端，称之为 Server Socket。
- Socket 之间的连接过程可以分为三个步骤：（1）服务器监听；（2）客户端连接；（3）连接确认。

![Socket](https://segmentfault.com/img/remote/1460000039898782)

### 1.2 Socket 缓冲区

每个 Socket 被创建后，都会在内核中分配两个缓冲区：输入缓冲区和输出缓冲区。

- 通过 Socket 发送数据并不会立即向网络中传输数据，而是先将数据写入到输出缓冲区中，再由 TCP 协议将数据从输出缓冲区发送到目标主机。
- 通过 Socket 接收数据也是如此，也是从输入缓冲区中读取数据，而不是直接从网络中读取。

![Socket缓冲区](https://segmentfault.com/img/remote/1460000039898783)

### 1.3 用户空间、内核空间、系统调用

操作系统的进程空间可以分为用户空间（User Space）和内核空间（Kernel Space），它们需要不同的执行权限。

- 大多数系统交互式操作需要在内核空间中运行，比如设备 IO 操作。
- 我们的应用程序运行在用户空间，是不具备系统级的直接操作权限的。如果应用程序想要访问系统核心功能，必须通过系统调用（System Call）来完成。比如调用`recv()`函数，会将输入缓冲区中的内容拷贝到用户缓冲区。
- 系统调用运行在内核空间，是操作系统为应用程序提供的接口。

![用户空间、内核空间、系统调用](https://segmentfault.com/img/remote/1460000039898784)

下面列举了一些 Linux 操作系统中的系统调用接口（部分函数后面章节会用到）：

- socketcall socket系统调用
- socket 建立socket
- bind 绑定socket到端口
- connect 连接远程主机
- accept 响应socket连接请求
- send 通过socket发送信息
- sendto 发送UDP信息
- recv 通过socket接收信息
- recvfrom 接收UDP信息
- listen 监听socket端口
- select 对多路同步IO进行轮询
- shutdown 关闭socket上的连接
- sigaction 设置对指定信号的处理方法

### 1.4 阻塞与非阻塞

阻塞与非阻塞，用于描述调用者在等待返回结果时的状态。

- 阻塞：调用者发起请求后，会一直等待返回结果，这期间当前线程会被挂起（阻塞）。
- 非阻塞：调用者发起请求后，会立刻返回，当前线程也不会阻塞。该调用不会立刻得到结果，调用者需要定时轮询查看处理状态。

### 1.5 同步与异步

而同步与异步，用于描述调用结果的返回机制（或者叫通信机制）。

- 同步：调用者发起请求后，会一直等待返回结果，即由调用者主动等待这个调用结果。
- 异步：调用者发起请求后，会立刻返回，但不会立刻得到这个结果，而是由被调者在执行结束后主动通知（如 Callback）调用者。

## 2. 五种 IO 模型

IO 模型是指：用什么样的通道或者说是通信模式进行数据的传输，这很大程序上决定了程序通信的性能。

Linux 系统为我们提供五种可用的 IO 模型：阻塞式 IO 模型、非阻塞式 IO 模型、IO 多路复用模型、信号驱动 IO 模型和异步 IO 模型。

### 2.1 阻塞式 IO 模型

阻塞式 IO （Blocking IO）：应用进程从发起 IO 系统调用，至内核返回成功标识，这整个期间是处于阻塞状态的。

![阻塞式 IO 模型](https://segmentfault.com/img/remote/1460000039898785)

### 2.2 非阻塞式 IO 模型

非阻塞式IO（Non-Blocking IO）：应用进程可以将 Socket 设置为非阻塞，这样应用进程在发起 IO 系统调用后，会立刻返回。应用进程可以轮询的发起 IO 系统调用，直到内核返回成功标识。

![非阻塞式 IO 模型](https://segmentfault.com/img/remote/1460000039898786)

### 2.3 IO 多路复用模型

IO 多路复用（IO Multiplexin）：可以将多个应用进程的 Socket 注册到一个 Select（多路复用器）上，然后使用一个进程来监听该 Select（该操作会阻塞），Select 会监听所有注册进来的 Socket。只要有一个 Socket 的数据准备好，就会返回该Socket。再由应用进程发起 IO 系统调用，来完成数据读取。

![IO 多路复用模型](https://segmentfault.com/img/remote/1460000039898787)

### 2.4 信号驱动 IO 模型

信号驱动 IO（Signal Driven IO）：可以为 Socket 开启信号驱动 IO 功能，应用进程需向内核注册一个信号处理程序，该操作并立即返回。当内核中有数据准备好，会发送一个信号给应用进程，应用进程便可以在信号处理程序中发起 IO 系统调用，来完成数据读取了。

![信号驱动 IO 模型](https://segmentfault.com/img/remote/1460000039898788)

### 2.5 异步 IO 模型

异步 IO（Asynchronous IO）： 应用进程发起 IO 系统调用后，会立即返回。当内核中数据完全准备后，并且也复制到了用户空间，会产生一个信号来通知应用进程。

![异步 IO 模型](https://segmentfault.com/img/remote/1460000039898789)

## 3. 总结

从上述五种 IO 模型可以看出，应用进程对内核发起 IO 系统调用后，内核会经过两个阶段来完成数据的传输：

- 第一阶段：等待数据。即应用进程发起 IO 系统调用后，会一直等待数据；当有数据传入服务器，会将数据放入内核空间，此时数据准备好。
- 第二阶段：将数据从内核空间复制到用户空间，并返回给应用程序成功标识。

![五种 IO 模型对比](https://segmentfault.com/img/remote/1460000039898790)

前四种模型的第二阶段是相同的，都是处于阻塞状态，其主要区别在第一阶段。而异步 IO 模型则不同，应用进程在这两个阶段是完全不阻塞的。

| IO 模型      | 第一阶段       | 第二阶段 |
| ------------ | -------------- | -------- |
| 阻塞式IO     | 阻塞           | 阻塞     |
| 非阻塞式IO   | 非阻塞         | 阻塞     |
| IO多路程复用 | 阻塞（Select） | 阻塞     |
| 信号驱动式IO | 异步           | 阻塞     |
| 异步IO       | 异步           | 异步     |

## Reactor模式

针对传统阻塞I/O服务模型的缺点，解决方案一般有两个

1. 基于 I/O 复用模型：多个连接共用一个阻塞对象，应用程序只需要在一个阻塞对象等待，无需阻塞等待所有连接。当某个连接有新的数据可以处理时，操作系统通知应用程序，线程从阻塞状态返回，开始进行业务处理。
2. 基于线程池复用线程资源：不必再为每个连接创建线程，将连接完成后的业务处理任务分配给线程进行处理，一个线程可以处理多个连接的业务。

I/O复用结合线程池，就是Reactor模式基本设计思想，如图所示：

![image.png](https://segmentfault.com/img/bVcTDY8)

## 单Reactor单线程

<img src="https://segmentfault.com/img/bVcTDZr" alt="image.png" style="zoom:150%;" />

优点：模型简单，没有多线程、进程通信、竞争的问题，全部都在一个线程中完成
缺点：

- 性能问题，只有一个线程，无法完全发挥多核 CPU 的性能。Handler 在处理某个连接上的业务时，整个进程无法处理其他连接事件，很容易导致性能瓶颈。
- 可靠性问题，线程意外终止，或者进入死循环，会导致整个系统通信模块不可用，不能接收和处理外部消息，造成节点故障

使用场景：客户端的数量有限，业务处理非常快速，比如 Redis在业务处理的时间复杂度 O(1) 的情况

## 单Reactor多线程

![preview](https://segmentfault.com/img/bVcTDZJ/view)

**工作流程**

1、Reactor对象通过select监听客户端请求事件，收到事件后，通过dispatch进行分发。

2、如果建立连接请求，则Acceptor通过accept处理连接请求，然后创建一个Handler对象处理完成连接后的各种事件。

3、如果不是连接请求，则由reactor分发调用连接对应的handler来处理。

4、handler只负责相应事件，不做具体的业务处理，通过read读取数据后，会分发给后面的worker线程池的某个线程处理业务。

5、worker线程池会分配独立线程完成真正的业务，并将结果返回给handler。

6、handler收到响应后，通过send分发将结果返回给client。

**优点：可以充分利用多核cpu的处理能力**

**缺点：多线程数据共享和访问比较复杂，rector处理所有的事件的监听和响应，在单线程运行，在高并发应用场景下，容易出现性能瓶颈。**

## **主从Reactor多线程**

![preview](https://segmentfault.com/img/bVcTDZT/view)

**工作流程**

1、Reactor主线程MainReactor对象通过select监听连接事件，收到事件后，通过Acceptor处理连接事件。

2、当Acceptor处理连接事件后，MainReactor将连接分配给SubAcceptor。

3、SubAcceptor将连接加入到连接队列进行监听，并创建handler进行各种事件处理。

4、当有新事件发生时，SubAcceptor就会调用对应的handler进行各种事件处理。

5、handler通过read读取数据，分发给后面的work线程处理。

6、work线程池分配独立的work线程进行业务处理，并返回结果。

7、handler收到响应的结果后，再通过send返回给client。

优点：父线程与子线程的数据交互简单职责明确，父线程只需要接收新连接，子线程完成后续的业务处理。Reactor 主线程只需要把新连接传给子线程，子线程无需返回数据。
缺点：编程复杂度较高
结合实例：这种模型在许多项目中广泛使用，包括 Nginx 主从 Reactor 多进程模型，Memcached 主从多线程，Netty 主从多线程模型也进行了支持

## Redis中reactor模型

![image-20220514152121306](/Users/zhengrui/Library/Application Support/typora-user-images/image-20220514152121306.png)

在这个模型中，Redis服务器用主线程执行I/O多路复用程序、文件事件分派器以及事件处理器。而且，尽管多个文件事件可能会并发出现，Redis服务器是顺序处理各个文件事件的。

Redis服务器主线程的执行流程在Redis.c的main函数中体现，而关于处理文件事件的主要的有这几行：

```c
int main(int argc, char **argv) {
	...
	initServer();
	...
	aeMain();
	...
	aeDeleteEventLoop(server.el);
	return 0;
}
```

在initServer()中，建立各个事件处理器；在aeMain()中，执行事件处理循环；在aeDeleteEventLoop(server.el)中关闭停止事件处理循环；最后退出。

## Netty中的reactor模型

Netty主要是基于主从Reactor多线程模式做了一定的改进，其中主从Reactor都由单线程一个变成了多线程。
<img src="https://segmentfault.com/img/bVcTD0m" alt="截屏2020-11-24 上午10.05.32.png"  />
Server 端包含 1 个 Boss的NioEventLoopGroup 和 1 个 Worker的NioEventLoopGroup。NioEventLoopGroup 相当于 1 个事件循环组，这个组里包含多个事件循环 NioEventLoop，每个 NioEventLoop 包含 1 个 Selector 和 1 个事件循环线程。每个Boss的NioEventLoop循环执行的任务包含 3 步：

1. 轮询 Accept 事件；
2. 处理 Accept I/O 事件，与 Client 建立连接，生成 NioSocketChannel，并将 NioSocketChannel 注册到某个 Worker NioEventLoop 的 Selector 上；
3. 处理任务队列中的任务，runAllTasks。任务队列中的任务包括用户调用eventloop.execute或schedule执行的任务，或者其他线程提交到该eventloop的任务。

每个 Worker NioEventLoop 循环执行的任务包含 3 步：

1. 轮询 Read、Write 事件；
2. 处理 I/O 事件，即 Read、Write 事件，在 NioSocketChannel 可读、可写事件发生时进行处理；
3. 处理任务队列中的任务，runAllTasks。

## Preactor模式

Proactor 模式整体与Reactor 模式一致，区别就在于Proactor模式将所有I/O操作都交给主线程和内核来处理，工作线程仅仅负责业务逻辑。模型如下：

![img](http://www.cmsblogs.com/images/group/sike-java/sike-nio/nio-20211031100002.jpg)

- **Procator Initiator**：负责创建Handler和Procator，并将Procator和Handler都通过Asynchronous operation processor注册到内核。
- **Handler**：执行业务流程的业务处理器。
- **Asynchronous operation processor**：负责处理注册请求，并完成IO操作。完成IO操作后会通知Procator。
- **Procator**：根据不同的事件类型回调不同的handler进行业务处理。

这里需要注意的是： **Proactor关注的不是就绪事件，而是完成事件，这是区分Reactor模式的关键点**。

然而可惜的是，Linux下的异步 I/O 是不完善的，`aio` 系列函数是由 `POSIX` 定义的异步操作接口，不是真正的操作系统级别支持的，而是在用户空间模拟出来的异步，并且仅仅支持基于本地文件的 `aio` 异步操作，网络编程中的 `socket`是不支持的，这也使得基于 Linux 的高性能网络程序都是使用 Reactor 方案。

而 Windows 里实现了一套完整的支持 `socket` 的异步编程接口，这套接口就是 `IOCP`，是由操作系统级别实现的异步 I/O，真正意义上异步 I/O，因此在 Windows 里实现高性能网络程序可以使用效率更高的 Proactor 方案。

**优缺点**

- 优点
  - 性能确实是强大，效率也高
- 缺点
  - 复杂。性能好，效率高，东西是好东西，但是使用起来就是复杂。
  - 操作系统支持。上面提到过，Linux系统对异步IO支持不是很好，不是很完善

## Proactor 模式与Reactor 模式

Proactor模式与Reactor模式 的区别有如下几点：

- Reactor 模式注册的是文件描述符的就绪事件。当Reactor 模式有事件发生时，它需要判断当前事件是读事件还是写事件，然后在调用系统的`read`或者`write`将数据从内核中拷贝到用户数据区，然后进行业务处理。
- Proactor模式注册的则是完成事件。即发起异步操作后，操作系统将在内核态完成I/O并拷贝数据到用户提供的缓冲区中，完成后通知Proactor进行回调，用户只需要处理后续的业务即可。
- Reactor模式实现`同步I/O多路分发`
- Proactor模式实现`异步I/O分发`。

## select poll epoll kqueue

此外，在UNIX系统上，一切皆文件。套接字也不例外，每一个套接字都有对应的fd（即文件描述符）。我们简单看看这几个系统调用的原型。

#### 1、select

```c
select(int nfds, fd_set *r, fd_set *w, fd_set *e, struct timeval *timeout)
```

**基本原理：** select 函数监视的文件描述符分3类，分别放在writefds、readfds、和exceptfds三个集合中。调用后select函数会阻塞，直到有描述符就绪（有数据 可读、可写、或者有except），或者超时（timeout指定等待时间，如果立即返回设为null即可），函数返回。当select函数返回后，可以通过遍历fdset，来找到就绪的描述符。

基本流程，如图所示：![img](http://www.loujunkai.club/network/select.jpg)

select目前几乎在所有的平台上支持，其**良好跨平台支持**也是它的一个优点。select的一个缺点在于单个进程能够监视的文件描述符的数量存在最大限制，在Linux上一般为**1024**，可以通过修改宏定义甚至重新编译内核的方式提升这一限制，但是这样也会造成效率的降低。

select本质上是通过设置或者检查存放fd标志位的数据结构来进行下一步处理。这样所带来的**缺点**是：

- 1、select最大的缺陷就是单个进程所打开的FD是有一定限制的，它由FD_SETSIZE设置，默认值是1024。 　　一般来说这个数目和系统内存关系很大，具体数目可以cat /proc/sys/fs/file-max察看。**32位机默认是1024个。64位机默认是2048**.
- 2、这3个集合在返回时会被内核修改，因此我们每次调用时都需要重新初始化
- 3、对socket进行扫描时是线性扫描，即采用**轮询的方法，效率较低**。
  　　当套接字比较多的时候，每次select()都要通过遍历FD_SETSIZE个Socket来完成调度，不管哪个Socket是活跃的，都遍历一遍。这会浪费很多CPU时间。如果能给套接字注册某个回调函数，当他们活跃时，自动完成相关操作，那就避免了轮询，这正是epoll与kqueue做的。
- 4、需要维护一个用来存放大量fd的数据结构，这样会使得用户空间和内核空间在传递该结构时复制开销大。

#### 2、poll

```c
poll(struct pollfd *fds, int nfds, int timeout)

struct pollfd {
	int fd;
	short events;
	short revents;
}
```

**基本原理：**poll本质上和select没有区别，它将用户传入的数组拷贝到内核空间，然后查询每个fd对应的设备状态，如果设备就绪则在设备等待队列中加入一项并继续遍历，如果遍历完所有fd后没有发现就绪设备，则挂起当前进程，直到设备就绪或者主动超时，被唤醒后它又要再次遍历fd。这个过程经历了多次无谓的遍历。

poll解决了select的头两个问题，没有最大连接数的限制以及不再需要每次都初始化就绪集合。

**它没有最大连接数的限制，原因是它是基于链表来存储的**，但是同样有一个**缺点**：

- 1、大量的fd的数组被整体复制于用户态和内核地址空间之间，而不管这样的复制是不是有意义。
- 2、poll还有一个特点是“水平触发”，如果报告了fd后，没有被处理，那么下次poll时会再次报告该fd。

注意：从上面看，select和poll都需要在返回后，通过遍历文件描述符来获取已经就绪的socket。事实上，同时连接的大量客户端在一时刻可能只有很少的处于就绪状态，因此随着监视的描述符数量的增长，其效率也会线性下降。

#### 3、epoll

```c
int epoll_create(int size);
int epoll_ctl(int epfd, int op, int fd, struct epoll_event *event);
int epoll_wait(int epfd, struct epoll_event *events, int maxevents, int timeout);
```

epoll_create: 创建一个epoll句柄，实际结构是一个用于存储socket事件（fd）的红黑树，以及一个用于存储就绪事件的就绪链表。

epoll_ctl：对红黑树进行插入、修改、删除操作。插入是会给fd注册回调函数，当该文件描述符上有数据就绪时，自动调用回调函数将该描述符加入就绪队列

epoll_wait：轮训就绪链表，根据LT或者ET模式，将该就绪事件通知给用户

epoll是在2.6内核中提出的，是之前的select和poll的增强版本。相对于select和poll来说，epoll更加灵活，没有描述符限制。epoll使用一个文件描述符管理多个描述符，将用户关系的文件描述符的事件存放到内核的一个事件表中，这样在用户空间和内核空间的copy只需一次。

**基本原理：**epoll支持**水平触发和边缘触发**，最大的特点在于边缘触发，它只告诉进程哪些fd刚刚变为就绪态，并且只会通知一次。
还有一个特点是，epoll使用“事件”的就绪通知方式，通过epoll_ctl注册fd，一旦该fd就绪，内核就会采用类似callback的**回调机制**来激活该fd，epoll_wait便可以收到通知。

##### epoll的优点：

- 1、没有最大并发连接的限制，能打开的FD的上限远大于1024（1G的内存上能监听约10万个端口）。
- 2、效率提升，不是轮询的方式，不会随着FD数目的增加效率下降。 　　只有活跃可用的FD才会调用callback函数；即epoll最大的优点就在于它**只管你“活跃”的连接**，而跟连接总数无关，因此在实际的网络环境中，epoll的效率就会远远高于select和poll。
- 3、内存拷贝，利用mmap()文件映射内存加速与内核空间的消息传递；即epoll使用mmap减少复制开销。

epoll对文件描述符的操作有两种模式：LT（level trigger）和ET（edge trigger）。LT模式是默认模式，LT模式与ET模式的区别如下：

- LT模式：当epoll_wait检测到描述符事件发生并将此事件通知应用程序，应用程序可以**不立即**处理该事件。下次调用epoll_wait时，会再次响应应用程序并通知此事件。
- ET模式：当epoll_wait检测到描述符事件发生并将此事件通知应用程序，应用程序必须**立即**处理该事件。如果不处理，下次调用epoll_wait时，不会再次响应应用程序并通知此事件。

#### 1、LT模式

　　LT(level triggered)是缺省的工作方式，并且同时支持block和no-block socket。在这种做法中，内核告诉你一个文件描述符是否就绪了，然后你可以对这个就绪的fd进行IO操作。如果你不作任何操作，内核还是会继续通知你的。

#### 2、ET模式

ET(edge-triggered)是高速工作方式，只支持no-block socket。在这种模式下，当描述符从未就绪变为就绪时，内核通过epoll告诉你。然后它会假设你知道文件描述符已经就绪，并且不会再为那个文件描述符发送更多的就绪通知，直到你做了某些操作导致那个文件描述符不再为就绪状态了(比如，你在发送，接收或者接收请求，或者发送接收的数据少于一定量时导致了一个EWOULDBLOCK 错误）。但是请注意，如果一直不对这个fd作IO操作(从而导致它再次变成未就绪)，内核不会发送更多的通知(only once)。

ET模式在很大程度上减少了epoll事件被重复触发的次数，因此效率要比LT模式高。epoll工作在ET模式的时候，必须使用非阻塞套接口，以避免由于一个文件句柄的阻塞读/阻塞写操作把处理多个文件描述符的任务饿死。

#### 3、在select/poll中

在select/poll中，进程只有在调用一定的方法后，内核才对所有监视的文件描述符进行扫描，而epoll事先通过epoll_ctl()来注册一个文件描述符，一旦基于某个文件描述符就绪时，内核会采用类似callback的回调机制，迅速激活这个文件描述符，当进程调用epoll_wait()时便得到通知。(此处去掉了遍历文件描述符，而是通过监听回调的的机制。这正是epoll的魅力所在。)

**注意：**如果没有大量的idle-connection或者dead-connection，epoll的效率并不会比select/poll高很多，但是当遇到大量的idle-connection，就会发现epoll的效率大大高于select/poll。

#### 4、kqueue

epoll是Linux中的实现，kqueue则是在FreeBSD的实现。

```c
int kqueue(void);
int kevent(int kq, const struct kevent *changelist, int nchanges, struct kevent *eventlist, int nevents, const struct timespec *timeout);
```

与epoll相同的是，kqueue创建一个context；与epoll不同的是，kqueue用kevent代替了epoll_ctl和epoll_wait。

epoll和kqueue解决了select存在的问题。通过它们，我们可以高效的通过系统调用来获取多个套接字的读/写事件，从而解决一个线程处理多个连接的问题。

### 三、select、poll、epoll区别

#### 1、支持一个进程所能打开的最大连接数

| 方式   | 描述                                                         |
| :----- | :----------------------------------------------------------- |
| select | 单个进程所能打开的最大连接数由 FD_SETSIZE 定义，其大小是32个整数的大小（在32位机器上，大小就是32*32，在64位机器上，大小就是32*64），当然我们可以对其进行修改，然后重新编译内核，但是性能可能会受影响，这需要进一步的测试。 |
| poll   | poll本质上和select没有区别，但是它没有最大连接数的限制，原因是它是基于链表的。 |
| epoll  | 虽然连接数有限制，但是很大，1G内存的机器上可以打开10万左右的连接，2G内存的机器上可以打开20万左右的连接。 |

#### 2、FD剧增后带来的IO效率问题

| 方式   | 描述                                                         |
| :----- | :----------------------------------------------------------- |
| select | 因为每次调用时都会对连接进行线性遍历，所有随着FD的增加会造成遍历速度慢的“线性下降性能问题”。 |
| poll   | 同上                                                         |
| epoll  | 因为epoll内核中实现是根据每个fd上面的callback函数实现的，只有活跃的socket才会主动调用callback，所以在活跃的socket较少的情况下，使用epoll没有前两者线性下降的性能问题，但是所有socket都很活跃的情况下，可能会有性能问题。 |

#### 3、消息传递方式

| 方式   | 描述                                               |
| :----- | :------------------------------------------------- |
| select | 内核需要将消息传递到内核空间，都需要内核拷贝动作。 |
| poll   | 同上                                               |
| epoll  | epoll通过内核和用户空间共享一块内存来实现。        |

综上，在选择select，poll，epoll时要根据具体的使用场合以及这三种方式的自身特点：

- 1、表面上看epoll的性能最好，但是在连接数少并且连接都十分活跃的情况下，select和poll的性能可能比epoll好，毕竟epoll的通知机制需要很多函数回调。
- 2、select低效是因为每次它都需要轮询。但低效也是相对的，视情况而定，也可通过良好的设计改善。

## 参考文档

https://www.zhihu.com/question/26943938

https://juejin.cn/post/6892687008552976398

https://cloud.tencent.com/developer/article/1488120

https://zhuanlan.zhihu.com/p/394676109

https://segmentfault.com/a/1190000040392205

