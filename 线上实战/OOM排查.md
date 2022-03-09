oom排查：

top查看进程使用情况

dmesg查看系统日志，查看java进程被干掉的原因，案发现场

ps  -aux | grep java查看java进程id（或者使用jps，这个是jdk自带的工具）

ps -T -p <pid> 或者 top -H -p <pid>查看进程中线程情况

jstat查看java GC状态

jmap -histo:live pid 统计存活对象的分布情况

**jmap -dump:format=b,file=文件名 [pid] 生成线程转储文件**

**配置虚拟机参数在发生OOM是自动生成dump文件**

**如果jmap有问题（在jdk8之前有bug）可以使用**gcore先生成core dump文件，只有再通过jmap将它（jvm.core）转成heap.hprof文件。

**内存分析工具：***MAT*

*jhat：jvm自带工具*

*内存映像分析工具：***Jprofiler**

**OOM排查主要做两件事：**

- **dump出堆栈信息，再通过专业工具排查**
- 查看GC日志