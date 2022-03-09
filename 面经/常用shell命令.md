### 提纲

##### 1，变量

##### 2，循环

##### 3，判断

##### 4，bash脚本模版

* 在哪找
* 如何提高： todo； 正则

##### 5，高频工具

```shell
### 1，正则匹配精度
grep -m1 'detailValue' /home/xiaoju/data1/logs/charging-biz.log |grep -E '\w\\\":[0-9]+\.[0-9]{1,4}'

### 2，前台任务切后台
# 1，切到后台（自动暂停）
ctrl+z
# 2，查看后台列表
jobs
# 3，在后台继续运行
bg 1
# 4，切回前台
fg 1
>>> nohup ./start.sh 2>&1 > start.log &

### 3，所有项目中查找关键字
grep -r --include "*.java" "xxxxxxxx" /opt/ws/didi/wujie

### 4，安装arthas
curl -L https://arthas.aliyun.com/install.sh | sh

### 5，ftp
sftp -oPort=8000 allenzhao@fswap.sys.xiaojukeji.com

### 6，拼参数
cat errmsg.txt | sed "s#^#curl -X POST -H 'Content-Type:application/type' 'http://localhost:9466/test/omsTrade/send' -d '#" | sed "s/$/'\necho 1\nsleep 1\n/" > errmsg.sh

### 7，统计SQL次数
grep 'INSERT INTO ' /opt/doc/common/资料/202110/RDC长期_467749_0820.sql  |awk -F'`' '{sum[$2]+=1}END{for (i in sum) {print i"\t"sum[i]}}'

### 8，日期转换
date -d '2021-10-28' +%s
1635350400
date --date='@1635350400'
Thu Oct 28 00:00:00 CST 2021

### 9，解析json
cat payment_0114_cust.json| jq '.data[0].data[0].rows[].result[0]' |tr -d '"' > payment_0114_cust.csv

### 10，查看oom日志
dmesg -T |grep oom |tail

### 11，异常日志分布
grep ERROR  /home/xiaoju/data1/logs/voucher-service/error.log | awk -F'|' '{print $3}' | cut -c1-500 | sort |uniq -c |sort -k1nr |head -n50

### 12，didi工具
ifind -t didi.wj.cxyx.fms.voucher-service.hnc-v
irun -c "grep ERROR  /home/xiaoju/data1/logs/voucher-service/error.log | awk -F'|' '{print $3}' | cut -c1-500 | head "

### 13，启动脚本
cat /etc/container/init/990-startservice.required.sh

### 20，查询yum工具由哪个包提供
yum  whatprovides *netstat

### 21，查看端口被占用
lsof -nP -i | grep XXX

### 22，验证端口
nc 192.168.3.131 2181

### 23，抓mysql包
>>>>>
#!/bin/bash

tcpdump -i any -s 0 -l -w - dst port 3306 | strings | perl -e '
while(<>) { chomp; next if /^[^ ]+[ ]*$/;
    if(/^(SELECT|UPDATE|DELETE|INSERT|SET|COMMIT|ROLLBACK|CREATE|DROP|ALTER|CALL)/i)
    {
        if (defined $q) { print "$q\n"; }
        $q=$_;
    } else {
        $_ =~ s/^[ \t]+//; $q.=" $_";
    }
}’
<<<<<

### 24，清理mvn项目的targe目录
find . -name pom.xml | xargs -I {} mvn clean -f {}

### 25，查看网络close_wait
netstat -n | awk '/^tcp/ {++S[$NF]} END {for(a in S) print a, S[a]}'  

### 26，生成随机数字字母字符串，生成密码
openssl passwd -stdin < <(echo)

### 27，sed正则截取字符串
grep 'sourceTransferId' /data/logs/busi-api-prod.log | cut -c1-500 |sed 's/^.*\("sourceTransferId":"[^"]*"\).*$/\1/' |sort |uniq -c

### 
```



##### 6，操作案例

* 过control.sh
* rds数据
  - curl
  - jq
  - vs-json-format
  - [json转csv](https://www.bejson.com/json/json2excel/)

##### 7，出题点

###### 过题：

- 6-去空行
- 10-第二列重复
- 14-平均值

###### 关键点：

- 高频命令：man、awk、wc、grep
- 格式化输出：数值 换行

###### java开发：

* jvm命令：jstack, jmap, jdb
* 系统资源：
* 抓包网络：tcpdump

##### 8，坑位

* 协议：BSD、GNU
* 转义
* grep出命令自己
* 适合性（复杂带上下mn行缓存的处理，用python, ruby, java等）



### 附录

1. [每天一个 Linux 命令目录](https://mp.weixin.qq.com/s/2JNQ027tVJ1Nz99ANAHyGA)
2. [Shell 教程](https://www.runoob.com/linux/linux-shell.html)
3. [牛客网](https://www.nowcoder.com/ta/shell?from=baidushell&bd_vid=12713616674231593575)
4. 正则：todo
5. [awk](http://www.gnu.org/software/gawk/manual/gawk.html)
6. 小图： regex-cheatsheet.pdf, gugoole_linux.pdf