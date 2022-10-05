## 架构

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005152157599.png" alt="image-20221005152157599" style="zoom:50%;" />

#### Tomcat处理请求过程

Tomcat通过Endpoint组件接收socket连接，接收到⼀个socket连接后会执⾏如下步骤 

1. 第⼀次从socket中获取数据到InputBuffer中，BIO对应的是InternalInputBuffer，⽗类是AbstractInputBuffer 

2. 然后基于InputBuffer进⾏解析数据 

3. 先解析请求⾏，把请求⽅法，请求uri，请求协议等封装到org.apache.coyote.Request对象中 

4. org.apache.coyote.Request中的属性都是MessageBytes类型，直接可以理解为字节类型，因为从socket中获取的数据都是字节，在解析过程中不⽤直接把字节转成字符串，并且MessageBytes虽然表示字节，但是它并不会真正的存储字节，还是使⽤ByteChunk基于InputBuffer中的字节数组来进⾏标记，标记字节数组中的哪个⼀个范围表示请求⽅法，哪个⼀个范围表示请求uri等等。 

5. 然后解析头，和解析请求⾏类似 

6. 解析完请求头后，就基于请求头来初始化⼀些参数，⽐如Connection是keepalive是close，⽐如是否有Content-length，并且对于的⻓度是多少等等，还包括当前请求在处理请求体时应该使⽤哪个InputFilter。 

7. 然后将请求交给容器 

8. 容器再将请求交给具体的servlet进⾏处理 

9. servlet在处理请求的过程中会利⽤response进⾏响应，返回数据给客户端，⼀个普通的响应过程会把数据先写⼊⼀个缓冲区，当调⽤flush，或者close⽅法时会把缓冲区中的内容发送给socet，下⾯有⼀篇单独的⽂章讲解tomcat响应请求过程 

10. servlet处理完请求后，先会检查是否需要把响应数据发送给socket 

11. 接着看当前请求的请求体是否处理结束，是否还有剩余数据，如果有剩余数据需要把这些数据处理掉，以便能够获取到下⼀个请求的数据 

12. 然后回到第⼀步开始处理下⼀个请求

#### Tomcat线程模型

在Tomcat7中，默认为BIO，可以通过如下配置改为NIO

```xml
<Connector port="8080" protocol="org.apache.coyote.http11.Http11Ni oProtocol" 
           connectionTimeout="20000" redirectPort="8443" />
```

##### BIO 

BIO的模型⽐较简单。 

![image-20221005175356696](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005175356696.png)

1. JioEndpoint中的Acceptor线程负责循环阻塞接收socket连接 

2. 每接收到⼀个socket连接就包装成SocketProcessor扔进线程池Executor中，SocketProcessor是⼀个Runnable 

3. SocketProcessor负责从socket中阻塞读取数据，并且向socket中阻塞写⼊数据 

Acceptor线程的数量默认为1个，可以通过acceptorThreadCount参数进⾏配置。线程池Executor是可以配置的，比如：

```xml
<Executor name="tomcatThreadPool" namePrefix="catalina-exec-" maxThreads="150" minSpareThreads="4"/> 
<Connector port="8080" protocol="org.apache.coyote.http11.Http11Ni oProtocol" connectionTimeout="20000" redirectPort="8443" executor="tomcatThreadPool"/>
```

从上⾯的配置可以看到，每个Connector可以对应⼀个线程池，默认情况下，Tomcat中每个Connector都会创建⼀个⾃⼰的线程池，并且该线程池的默认值为：

1. 最⼩线程数量为10 

2. 最⼤线程数量为200 

如果两个Connector配置的executor是⼀样的话，就表示这两个Connector公⽤⼀个线程池。使⽤BIO来处理请求时，我们可以总结⼀下： 

1. 当请求数量⽐较⼤时，可以提⾼Acceptor线程的数量，提⾼接收请求的速率 

2. 当请求⽐较耗时是，可以提⾼线程池Executor的最⼤线程数量 

##### NIO

NIO最⼤的特性就是⾮阻塞，⾮阻塞接收socket连接，⾮阻塞从socket中读取数据，⾮阻塞从将数据写到socket中。但是在Tomcat7中，只有在从socket中读取请求⾏，请求头数据时是⾮阻塞的，在读取请求体是阻塞的，响应数据时也是阻塞的。为什么不全是⾮阻塞的呢？因为Tomcat7对应Servlet3.0，Servlet3.0规范中没有考虑NIO。Servlet3.1中才会有NIO相关的定义，而Servlet3.1是在Tomcat8中实现的。

![image-20221005175935075](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005175935075.png)

⾸先我们来看⼀下Tomcat7中使⽤NIO处理请求的基本流程： 

1. 利⽤Acceptor来阻塞获取socket连接，NIO中叫socketChannel 

2. 接收到socketChannel后，需要将socketChannel绑定到⼀个Selector中，并注册读事件，另外，基于NIO还需要⼀个线程来轮询Selector中是否存在就绪事件，如果存在就将就绪事件查出来，并处理该事件，在Tomcat中⽀持多个线程同时查询是否存在就绪事件，该线程对象为Poller，每个Poller中都包含⼀个Selector，这样每个Poller线程就负责轮询⾃⼰的Selector上就绪的事件，然后处理事件。

3. 当Acceptro接收到⼀个socketChannel后，就会将socketChannel注册到某⼀个Poller上，确定Polloer的逻辑⾮常简单，假设现在有3个Poller，编号为1,2,3，那么Tomcat接收到的第⼀个socketChannel注册到1号Poller上，第⼆个socketChannel注册到2号Poller上，第三个socketChannel注册到3号Poller上，第四个socketChannel注册到1号Poller上，依次循环。 

4. 在某⼀个Poller中，除开有selector外，还有⼀个ConcurrentLinkedQueue队列events，events表示待执⾏事件，⽐如Tomcat要socketChannel注册到selector上，但是Tomcat并没有直接这么做，⽽是先⾃⼰⽣成⼀个PollerEvent，然后把PollerEvent加⼊到队列events中，然后这个队列中的事件会在Poller线程的循环过程中真正执⾏ 

5. 上⾯说了，Poller线程中需要循环查询selector中是否存在就绪事件，⽽Tomcat在真正查询之前会先看⼀下events队列中是否存在待执⾏事件，如果存在就会先执⾏，这些事件表示需要向selector上注册事件，⽐如注册socketChannel的读事件和写事件，所以在真正执⾏events队列中的事件时就会真正的向selector上注册事件。所以只有先执⾏events队列中的PollerEvent，Poller线程才能有机会从selector中查询到就绪事件 

6. 每个Poller线程⼀旦查询到就绪事件，就会去处理这些事件，事件⽆⾮就是读事件和写事件 

7. 处理的第⼀步就是获取当前就绪事件对应的socketChannel，因为我们要向socketChannel中读数据或写数据 

8. 处理的第⼆步就是把socketChannel和当前要做的事情（读或写）封装为SocketProcessor对象 

9. 处理的第三步就是把SocketProcessor扔进线程池进⾏处理 

10. 在SocketProcessor线程运⾏时，就会从socketChannel读取数据（假设当前处理的是读事件），并且是⾮阻塞读 

11. 既然是⾮阻塞读，⼤概的⼀个流程就是，某⼀个Poller中的selector查询到了⼀个读就绪事件，然后交给⼀个SocketProcessor线程进⾏处理，SocketProcessor线程读取数据之后，如果发现请求⾏和请求头的数据都已经读完了，并解析完了，那么该SocketProcessor线程就会继续把解析后的请求交给Servlet进⾏处理，Servlet中可能会读取请求体，可能会响应数据，⽽不管是读请求体还是响应数据都是阻塞的，直到Servlet中的逻辑都执⾏完后，SocketProcessor线程才会运⾏结束。假如SocketProcessor读到了数据之后，发现请求⾏或请求头的数据还没有读完，那么本次读事件处理完毕，需要Poller线程再次查询到就绪读事件才能继续读数据，以及解析数据 

12. 实际上Tomcat7中的⾮阻塞读就只是在读取请求⾏和请求体数据时才是⾮阻塞的，⾄于请求体的数据，是在Servlet中通过inputstream.read()⽅法获取时才会真正的去获取请求体的数据，并且是阻塞的。 

在Tomcat7，虽然有NIO，但是不够彻底，相⽐如BIO，优点仅限于能利⽤较少的线程同时接收更多的请求，但是在真正处理请求时，想⽐如BIO并没有太多的优势，如果在处理⼀个请求时既不⽤读取请求，也不需要响应很多的数据那么NIO模式还是会拥有更⼤的吞吐量，所以如果要优化的话，将BIO改成NIO也是可以的。 

#### Tomcat中类加载器架构

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005202705817.png" alt="image-20221005202705817" style="zoom:50%;" />

Tomcat 拥有不同的⾃定义类加载器，以实现对各种资源库的控制。⼀般来说，Tomcat 主要⽤类加载器解决以下 4 个问题。 

- 同⼀个Tomcat中，各个Web应⽤之间各⾃使⽤的Java类库要互相隔离。 

- 同⼀个Tomcat中，各个Web应⽤之间可以提供共享的Java类库。 

- 为了使Tomcat不受Web应⽤的影响，应该使服务器的类库与应⽤程序的类库互相独⽴。 

- Tomcat⽀持热部署。 

在 Tomcat中，最重要的⼀个类加载器是 Common 类加载器，它的⽗类加载器是应⽤程序类加载器，负责加载 $CATALINA_ BASE/lib、$CATALINA_HOME/lib 两个⽬录下所有的.class ⽂件与.jar ⽂件。Tomcat中⼀般会有多个WebApp类加载器-WebAppClassLoader ，每个类加载器负责加载⼀个 Web 程序。它的⽗类加载器是Common类加载器。由于每个 Web 应⽤都有⾃⼰的 WebApp 类加载器，很好地使多个 Web 应⽤程序之间互相隔离且能通过创建新的 WebApp类加载器达到热部署。这种类加载器结构能有效使 Tomcat 不受 Web 应⽤程序影响，⽽ Common 类加载器的存在使多个 Web 应⽤程序能够互相共享类库。 

#### Tomcat⽣命周期

Tomcat架构是⼀种树状的层级管理结构，组件会有⾃⼰的⽗节点，也可能会有⾃⼰的孩⼦节点，每个节点都是组件，每个组件都有⽣命周期，为了管理⽅便，⼦节点的⽣命周期都是交由⽗节点来管理的。每个组件⽣命周期的管理主要由⼀个接⼝org.apache.catalina.Lifecycle和⼀个枚举org.apache.catalina.LifecycleState来表示。 

###### Lifecycle 

org.apache.catalina.Lifecycle接⼝定义了组件所有执⾏的动作，核⼼的有三个： 

1. init()，组件进⾏初始化 

2. start()，启动组件 

3. stop()，停⽌组件 

4. destroy()，销毁组件 

5. getState()，获取组件当前状态 

###### ⽣命周期流转 

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005203159922.png" alt="image-20221005203159922" style="zoom:50%;" />

1. 所有状态都能转变为FAILED 

2. ⼀个组件在STARTING_PREP、STARTING、STARTED状态调⽤start()⽅法不会产⽣影响 

3. ⼀个组件在NEW状态调⽤start()⽅法时，会先调⽤init()⽅法 

4. ⼀个组件在STOPPING_PREP、STOPPING、STOPPED状态调⽤stop⽅法不会产⽣影响 

5. ⼀个组件在NEW状态调⽤stop()⽅法是，会将状态直接改为STOPPED。当组件⾃⼰启动失败去停⽌时，需要将⼦组件也进⾏停⽌，尽管某些⼦组件还没有启动。 

6. 其他状态相互转换都会抛异常 

7. 合法的状态转换发⽣时都会触发相应的LifecycleEvent事件，⾮合法的转换不会触发事件。 

#### Tomcat事件监听

Tomcat中每个组件的状态会发送变化，变化的时候会抛出⼀些事件，Tomcat⽀持定义事件监听器来监听并消费这些事件。 实现事件监听功能的类为org.apache.catalina.util.LifecycleBase。每个组件都会继承这个类。该类中有⼀个属性： List<LifecycleListener> lifecycleListeners ; 该属性⽤来保存事件监听器，也就是说每个组件拥有⼀个事件监听器列表。

#### Tomcat启动过程

- 解析server.xml 
- 初始化
- 启动

#### Tomcat热部署与热加载

热部署和热加载是类似的，都是在不重启Tomcat的情况下，使得应⽤的最新代码⽣效。热部署表示重新部署应⽤，它的执⾏主体是Host，表示主机。热加载表示重新加载class，它的执⾏主体是Context，表示应⽤。

###### 热加载

我们可以在Context上配置reloadable属性为true，这样就表示该应⽤开启了热加载功能，默认是false。热加载触发的条件是：WEB-INF/classes⽬录下的⽂件发⽣了变化，WEB-INF/lib⽬录下的jar包添加、删除、修改都会触发热加载。 

热加载⼤致流程为： 

1. 设置当前Context不能接受以及处理请求标志为true 

2. 停⽌当前Context 

3. 启动当前Context 

4. 设置当前Context不能接受以及处理请求标志为false 

###### 热部署

BackgroundProcessor线程第六步会发出⼀个PERIODIC_EVENT事件，⽽HostConfig监听了此事件，当接收到此事件后就会执⾏热部署的检查与操作。对于⼀个⽂件夹部署的应⽤，通常会检查以下资源是否发⽣变动： 

- tomcat-7/webapps/应⽤名.war 

- tomcat-7/webapps/应⽤名 

- tomcat-7/webapps/应⽤名/META-INF/context.xml 

- tomcat-7/conf/Catalina/localhost/应⽤名.xml 

- tomcat-7/conf/context.xml 

对于⼀个War部署的应⽤，会检查以下资源是否发⽣变动： 

- tomcat-7/webapps/应⽤名.war 

- tomcat-7/conf/Catalina/localhost/应⽤名.xml 

- tomcat-7/conf/context.xml 

对于⼀个描述符部署的应⽤，会检查以下资源是否发⽣变动： 

- tomcat-7/conf/Catalina/localhost/应⽤名.xml 

- 指定的DocBase⽬录 

- tomcat-7/conf/context.xml 

⼀旦这些⽂件或⽬录发⽣了变化，就会触发热部署，当然热部署也是有开关的，在Host上，默认是开启的。这⾥需要注意的是，对于⼀个⽬录是否发⽣了变化，Tomcat只判断了这个⽬录的修改时间是否发⽣了变化，所以和热加载是不冲突的，因为热加载监听的是WEB-INF/classes和WEB-INF/lib⽬录，⽽热部署监听的是应⽤名那⼀层的⽬录。在讲热部署的过程之前，我们要先讲⼀下应⽤部署的优先级，对于⼀个应⽤，我们可以在四个地⽅进⾏定义：

1. server.xml中的context节点 

2. /tomcat-7/conf/Catalina/localhost/应⽤名.xml 

3. /tomcat-7/webapps/应⽤名.war 

4. /tomcat-7/webapps/应⽤名 

优先级就是上⾯所列的顺序，意思是同⼀个应⽤名，如果你在这个四个地⽅都配置了，那么优先级低的将不起作⽤。因为Tomcat在部署⼀个应⽤的时候，会先查⼀下这个应⽤名是否已经被部署过了。 

热部署的过程： 

如果发⽣改变的是⽂件夹，⽐如/tomcat-7/webapps/应⽤名，那么不会做什么事情，只是会更新⼀下记录的修改时间，这是因为这个/tomcat-7/webapps/应⽤名⽬录下的⽂件，要么是jsp⽂件，要么是其他⽂件，⽽Tomcat只会管jsp⽂件，⽽对于jsp⽂件如果发⽣了修改，jsp⾃带的机制会处理修改的。如果发⽣改变的是/tomcat-7/conf/Catalina/localhost/应⽤名.xml⽂件，那么就是先undeploy，然后 再deploy，和热加载其实类似。对于undeploy就不多说了，就是讲当前应⽤从host从移除，这就包括了当前应⽤的停⽌和销毁，然后还会从已部署列表中移除当前应⽤，然后调⽤deployApps()就可以重新部署应⽤了。

#### Spring Boot启动扫描Servlet

1. @ServletComponentScan负责扫描@WebServlet，每个Servlet对应⼀个ServletContextInitializer(接⼝)，对应的实现类是ServletRegistrationBean 

2. SpringApplication.run(Application.class)⾥⾯会去创建⼀个ServletWebServerApplicationContext，最终会调⽤该类的onRefresh⽅法 

3. 调⽤createWebServer⽅法创建并启动Tomcat 

4. 在创建的Tomcat的过程中会创建⼀个TomcatStarter，并且在创建TomcatStarter时将ServletContextInitializer传进去，TomcatStarter实现了ServletContainerInitializer接⼝，该接⼝是Servlet规范中的接⼝ 

5. 启动Tomcat 

6. 启动Tomcat后，Tomcat会负责调⽤TomcatStarter中的onStartup⽅法 

7. 循环调⽤每个ServletContextInitializer的onStartup⽅法，并且把servletContext传给ServletContextInitializer 

8. 最终在ServletRegistrationBean中将ServletRegistrationBean对应的Servlet添加到servletContext中 