[TOC]

## IOC

![image-20221005121030331](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005121030331.png)



spring IOC主要分为两块，一个是创建 Bean 容器，一个是初始化 Bean。

###### Spring IoC容器的加载过程

1. 实例化化容器：AnnotationConfigApplicationContext（类/注解/xml各不同）

2. 实例化工厂：DefaultListableBeanFactory

> DefaultListableBeanFactory就是我们所说的容器了，里面放着beanDefinitionMap，beanDefinitionNames，beanDefinitionMap是一个hashMap，beanName作为Key,beanDefinition作为Value，beanDefinitionNames是一个集合，里面存放了beanName。

3. 实例化BeanDefinition读取器： AnnotatedBeanDefinitionReader，其主要做了2件事情

   1. 注册内置BeanPostProcessor 

   2. 注册相关的BeanDefinition 

> BeanDefinition用来描述Bean的，里面存放着关于Bean的一系列信息，比如Bean的作用域，Bean所对应的Class，是否懒加载，是否Primary等等。你可以直接把它看做spring的Bean。

4. 创建BeanDefinition扫描器:ClassPathBeanDefinitionScanner

5. 注册配置类为BeanDefinition： register(annotatedClasses)

6. **refresh()** 

   1. prepareRefresh：从命名来看，就知道这个方法主要做了一些刷新前的准备工作，和主流程关系不大，主要是保存了容器的启动时间，启动标志等。

   2. ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory()：这个方法和主流程关系也不是很大，可以简单的认为，就是把beanFactory取出来而已。XML模式下会在这里读取BeanDefinition

   3. prepareBeanFactory：主要做了如下的操作： 

      1. 设置了一个类加载器 

      2. 设置了bean表达式解析器 

      3. 添加了属性编辑器的支持 

      4. 添加了一个后置处理器：ApplicationContextAwareProcessor，此后置处理器实现了BeanPostProcessor接口 

      5. 设置了一些忽略自动装配的接口 

      6. 设置了一些允许自动装配的接口，并且进行了赋值操作 

      7. 在容器中还没有XX的bean的时候，帮我们注册beanName为XX的singleton bean

   4. postProcessBeanFactory(beanFactory)：空方法，用于后续扩展

   5. invokeBeanFactoryPostProcessors(beanFactory) ：太长

   6. registerBeanPostProcessors(beanFactory)：实例化和注册beanFactory中扩展了BeanPostProcessor的bean。

   7. initMessageSource()：初始化国际化资源处理器

   8. initApplicationEventMulticaster() ： 创建事件多播器 

   9. onRefresh()：模板方法，在容器刷新的时候可以自定义逻辑，不同的Spring容器做不同的事情。

   10. registerListeners()：注册监听器，广播early application events 

   11. finishBeanFactoryInitialization(beanFactory)：实例化所有剩余的（非懒加载）单例比如invokeBeanFactoryPostProcessors方法中根据各种注解解析出来的类，在这个时候都会被初始化。实例化的过程各种BeanPostProcessor开始起作用

   12. getBean(): 初始化Bean

   13. finishRefresh()：refresh做完之后需要做的其他事情。清除上下文资源缓存（如扫描中的ASM元数据）初始化上下文的生命周期处理器，并刷新（找出Spring容器中实现了Lifecycle接口的bean并执行start()方法）。发布ContextRefreshedEvent事件告知对应的ApplicationListener进行响应的操作

###### 初始化Bean -- spring bean的生命周期

1. 实例化Bean对象，这个时候Bean的对象是非常低级的，基本不能够被我们使用，因为连最基本的属性都没有设置，可以理解为连Autowired注解都是没有解析的； 

2. 填充属性，当做完这一步，Bean对象基本是完整的了，可以理解为Autowired注解已经解析完毕，依赖注入完成了； 

3. 如果Bean实现了BeanNameAware接口，则调用setBeanName方法； 

4. 如果Bean实现了BeanClassLoaderAware接口，则调用setBeanClassLoader方法； 

5. 如果Bean实现了BeanFactoryAware接口，则调用setBeanFactory方法； 

6. 调用BeanPostProcessor的postProcessBeforeInitialization方法； 

7. 如果Bean实现了InitializingBean接口，调用afterPropertiesSet方法； 

8. 如果Bean定义了init-method方法，则调用Bean的init-method方法； 

9. 调用BeanPostProcessor的postProcessAfterInitialization方法；当进行到这一步，Bean已经被准备就绪了，一直停留在应用的上下文中，直到被销毁； 

10. 如果应用的上下文被销毁了，如果Bean实现了DisposableBean接口，则调用destroy方法，如果Bean定义了destory-method声明了销毁方法也会被调用。 

## AOP

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005213949881.png" alt="image-20221005213949881" style="zoom:50%;" />

![image-20221005220524374](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005220524374.png)

我们知道，spring中的aop是通过动态代理实现的，那么他具体是如何实现的呢？spring通过一个切面类，在他的类上加入@Aspect注 解，定义一个Pointcut方法，最后定义一系列的增强方法。这样就完成一个对象的切面操作。那么思考一下，按照上述的基础，要实现我们的aop，大致有以下思路： 

1. 找到所有的切面类 

2. 解析出所有的advice并保存 

3. 创建一个动态代理类 

4. 调用被代理类的方法时，找到他的所有增强器，并增强当前的方法 

源码解析：

1. **切面类的解析**：spring通过@EnableAspectJAutoProxy开启aop切面，在注解类上面发现@Import(AspectJAutoProxyRegistrar.class)，AspectJAutoProxyRegistrar实现了ImportBeanDefinitionRegistrar，所以他会通过registerBeanDefinitions方法为我们容器导入beanDefinition。追踪一下源码可以看到最终导入AnnotationAwareAspectJAutoProxyCreator，我们看一下他的类继承关系图，发现它实现了两个重要的接口，**BeanPostProcessor**和InstantiationAwareBeanPostProcessor 

2. **创建代理** ：

   1. **获取advisors:**创建代理之前首先要判断当前bean是否满足被代理， 所以需要**将advisor从之前的缓存中拿出来**和当前bean根据**表达式**进行匹配
   2. **匹配:**根据advisors和当前的bean根据切点表达式进行匹配，看是否符合。
   3. **创建代理:**找到了 和当前Bean匹配的advisor说明满足创建动态代理的条件

   <img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005221220622.png" alt="image-20221005221220622" style="zoom:50%;" />

3. **代理类的调用**。

## 循环依赖

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005221625858.png" alt="image-20221005221625858"  />

三级缓存实现如下：

![image-20221005222010241](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005222010241.png)

流程：

1. 我们尝试去一级缓存(单例缓存池)中去获取对象,一般情况从该map中获取的对象是直接可以使用的)。 IOC容器初始化加载单实例bean的时候第一次进来的时候，该map中一般返回空

2. 若在第一级缓存中没有获取到对象,并且singletonsCurrentlyInCreation这个list包含该beanName，IOC容器初始化加载单实例bean的时候第一次进来的时候 该list中一般返回空,但是循环依赖的时候可以满足该条件

3. 尝试去二级缓存中获取对象(二级缓存中的对象是一个早期对象) 

   > 何为早期对象: 就是bean刚刚调用了构造方法，还来不及给bean的属性进行赋值的对象(纯净态) 就是早期对象

4. 二级缓存中也没有获取到对象,并且allowEarlyReference为true(参数是有上一个方法传递进来的true)，直接从三级缓存中获取 ObjectFactory对象 这个对接就是用来解决循环依赖的关键所在，在ioc后期的过程中,当bean调用了构造方法的时候,把早期对象包裹成一个ObjectFactory暴露到三级缓存中。

5. 通过调用ObjectFactory的getObject()来获取我们的早期对象，并把早期对象放置在二级缓存，包装对象从三级缓存中删除掉。

#### 为什么是三级缓存，而不是两级（为什么需要第二级缓存？）

![image-20221005224416361](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221005224416361.png)

## 事务

基于动态代理实现，不可同一个类中调用，否者注解@Transactional不起作用。

#### 事务传播级别

![image-20221006104200959](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104200959.png)

#### 源码流程

1. 开启事务

   ![image-20221006104529662](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104529662.png)

2. 解析通知者advisor

   ![image-20221006104620480](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104620480.png)

3. 创建动态代理

   ![image-20221006104656719](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104656719.png)

4. 调用

   ![image-20221006104717383](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104717383.png)

## spring扩展点

- **BeanFactoryPostProcessor** 
  - **BeanDefinitionRegistryPostProcessor** 

- **BeanPostProcessor** 

  - **InstantiationAwareBeanPostProcessor** 

  - **AbstractAutoProxyCreator** 

- **@Import** 

  - **ImportBeanDefinitionRegistrar** 

  - **ImportSelector** 

- **Aware** 

- **InitializingBean** 

- **FactoryBean** 

- **SmartInitializingSingleton** 

- **ApplicationListener** 

- **Lifecycle** 

  - **SmartLifecycle** 

  - **LifecycleProcessor** 

- **HandlerInterceptor** 

- **MethodInterceptor** 

## spring mvc执行流程

#### 请求转发流程

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006104831035.png" alt="image-20221006104831035" style="zoom:50%;" />

1. 前端控制器DispatcherServlet 由框架提供

   作用：接收请求，处理响应结果 

2. 处理器映射器HandlerMapping由框架提供 

​		作用：根据请求URL，找到对应的Handler 

3. 处理器适配器HandlerAdapter由框架提供 

​		作用：调用处理器（Handler|Controller）的方法 

4. 处理器Handler又名Controller,后端处理器 

​		作用：接收用户请求数据，调用业务方法处理请求

5. 视图解析器ViewResolver由框架提供 

​		作用：视图解析，把逻辑视图名称解析成真正的物理视图，支持多种视图技术：JSTLView,FreeMarker... 

6. 视图View,程序员开发 

​		作用：将数据展现给用户 

#### 拦截器流程

<img src="https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006105011278.png" alt="image-20221006105011278" style="zoom:50%;" />

- preHandler方法：前置处理方法，在到达handler之前执行。返回false，直接阻断，不会流转到下一个流程。
- posthandler方法：后置处理流程，controller执行之后，视图渲染之前执行。可用于发挥统一响应。
- afterCompletion方法：视图渲染之后执行，可用于处理异常信息，记录操作日志，清理资源等。

## spring boot启动流程

spring boot启动主要分为两部分，一个是@SpringBootApplication注解的自动装配，一个new SpringApplication.run()的执行流程。

#### @SpringBootApplication自动装配

流程图：https://www.processon.com/view/link/5fc0abf67d9c082f447ce49b

@SpringBootApplication -> @EnableAutoConfiguration  -> AutoConfigurationImportSelector -> loadSpringFactories -> 加载spring.factories文件。

1. 判断自动装配开关是否打开。默认`spring.boot.enableautoconfiguration=true`，可在 `application.properties` 或 `application.yml` 中设置
2. 用于获取`EnableAutoConfiguration`注解中的 `exclude` 和 `excludeName`
3. 获取需要自动装配的所有配置类，读取`META-INF/spring.factories`
4. 通过@ConditionalOnXXX筛选配置类

#### new SpringApplication.run()的执行流程

https://www.processon.com/view/link/60d865e85653bb049a4b77ff#map

![image-20221006114102388](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221006114102388.png)

1. 准备环境`Environment`。此时会发送一个`ApplicationEnvironmentPreparedEvent`事件（应用环境准备事件），事件是同步消费的。当事件监听器都被调用完后，`Spring Boot`继续完成环境`Environment`的准备工作，加载`application.yaml`以及所有的`ActiveProfiles`对应的`application-[activeProfile].yaml`配置文件。

2. **创建应用程序上下文**-createApplicationContext。准备`ApplicationContext`容器。我们在`spring.factories`文件中配置的`EnableAutoConfiguration`就是在此时被读取的，并且根据配置的类名加载类，为类生成`BeanDefinition`注册到`bean`工厂中。

3. **刷新上下文（启动核心）**
   3.1 配置工厂对象，包括上下文类加载器，对象发布处理器，beanFactoryPostProcessor
   3.2 注册并实例化bean工厂发布处理器，并且调用这些处理器，对包扫描解析(主要是class文件)
   3.3 注册并实例化bean发布处理器 beanPostProcessor
   3.4 初始化一些与上下文有特别关系的bean对象（创建tomcat服务器）
   3.5 实例化所有bean工厂缓存的bean对象（剩下的）
   3.6 发布通知-通知上下文刷新完成（启动tomcat服务器）

4. **通知监听者-启动程序完成**

## spring cloud启动流程

![image-20221007205511295](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20221007205511295.png)

`Spring Cloud`项目可以在`spring.factories`配置文件中配置一种`BootstrapConfiguration`类，这与`Spring Boot`提供的`EnableAutoConfiguration`类并没有什么区别，只是它们作用在不同的`ApplicationContext`容器中。当项目中添加`Spring Cloud`的依赖时，`SpringApplication`的`run`方法启动的就会是两个容器，即两个`ApplicationContext`。原本的应用启动流程也有所变化。流程大致如下：

- SpringApplication run方法会创建环境变量对象 创建完成会发送ApplicationEnvironmentPreparedEvent事件。

- `Spring Cloud`的`BootstrapApplicationListener`监听`ApplicationEnvironmentPreparedEvent`事件，在监听到事件时开启一个新的`ApplicationContext`容器，我们可以称这个`ApplicationContext`容器为`Spring Cloud`的`Bootstrap`容器。Bootstrap`容器被用来注册`spring.factories`配置文件中配置的所有`BootstrapConfiguration`，并在`Bootstrap`容器初始化完成后将其`Bean`工厂作为原本`Spring Boot`启动的`ApplicationContext`容器的`Bean工厂的父工厂。在创建Bootstrap容器之前会加载bootstrap.[yaml｜props]中的配置，并写入到自己的Environment中，同时会将Bootstrap Conxtent的Enviroment合并到ApplicationContext的Enviroment中。
- 创建完Bootstrap Conxtent后，会重走一遍`Spring Boot`应用的启动流程。而原来`main`方法中调用`SpringApplication`的`run`方法启动`ApplicationContext`容器则会卡在环境准备阶段，等待`Spring Cloud`为其提供父工厂。然后通过判断`Environment`中是否存在`bootstrap`这个`PropertySource`辨别当前容器是否是`Bootstrap`容器，以解决无限监听ApplicationEnvironmentPreparedEvent事件启动新容器的问题。

## 参考文档

https://juejin.cn/post/6844903694039793672

https://zhuanlan.zhihu.com/p/456499577

https://www.daimajiaoliu.com/daima/7b7e2ee54bf7009

https://cloud.tencent.com/developer/article/1658793

https://www.jianshu.com/p/8e93f6d9f397