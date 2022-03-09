top命令查看cpu、内存使用情况，找出使用率高的java进程

top -hp pid 或者ps -mp pid -o THREAD,tid,time查看该进程下线程情况

通过jstack将问题线程ID转成16进制

jstack查看线程堆栈信息：jstack -l | grep 十六进制pid