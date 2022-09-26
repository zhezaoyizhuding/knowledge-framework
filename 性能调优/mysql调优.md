## 概述

mysql调优主要是慢sql的调优，而慢sql的产生大多数情况下都是索引简历的不合理。本文主要介绍下explain的使用，以及几种常见场景SQL的优化。

## Explain

使用EXPLAIN关键字可以模拟优化器执行SQL语句，分析你的查询语句或是结构的性能瓶颈。在 select 语句之前增加 explain 关键字，MySQL 会在查询上设置一个标记，执行查询会返回执行计划的信息，而不是执行这条SQL
**注意: **如果 from 中包含子查询，仍会执行该子查询，将结果放入临时表中。

参考官方文档:https://dev.mysql.com/doc/refman/5.7/en/explain-output.html

![image-20220926160318455](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926160318455.png)

explain中的列

-  id列
   id列的编号是 select 的序列号，有几个 select 就有几个id，并且id的顺序是按 select 出现的顺序增长的。 id列越大执行优先级越高，id相同则从上往下执行，id为NULL最后执行。

- select_type列
   select_type 表示对应行是简单还是复杂的查询。 
  - simple:简单查询。查询不包含子查询和union
  - primary:复杂查询中最外层的 select
  - subquery:包含在 select 中的子查询(不在 from 子句中)
  - derived:包含在 from 子句中的子查询。MySQL会将结果存放在一个临时表中，也称为派生表(derived的英文含义)
  - union:在 union 中的第二个和随后的 select

- table列
   这一列表示 explain 的一行正在访问哪个表。当 from 子句中有子查询时，table列是 <derivenN> 格式，表示当前查询依赖 id=N 的查询，于是先执行 id=N 的查询。当有 union 时，UNION RESULT 的 table 列的值为<union1,2>，1和2表示参与 union 的 select 行id。

- type列
   这一列表示关联类型或访问类型，即MySQL决定如何查找表中的行，查找数据行记录的大概范围。 依次从最优到最差分别为:system > const > eq_ref > ref > range > index > ALL **一般来说，得保证查询达到range级别，最好达到ref** 
  - NULL: mysql能够在优化阶段分解查询语句，在执行阶段用不着再访问表或索引。例如:在索引列中选取最小值，可以单独查找索引来完成，不需要在执行时访问表
  - const, system: mysql能对查询的某部分进行优化并将其转化成一个常量(可以看show warnings 的结果)。用于 primary key 或 unique key 的所有列与常数比较时，所以表最多有一个匹配行，读取1次，速度比较快。system是 const的特例，表里只有一条元组匹配时为system
  - eq_ref: primary key 或 unique key 索引的所有部分被连接使用 ，最多只会返回一条符合条件的记录。这可能是在 const 之外最好的联接类型了，简单的 select 查询不会出现这种 type。
  - ref: 相比 eq_ref，不使用唯一索引，而是使用普通索引或者唯一性索引的部分前缀，索引要和某个值相比较，可能会找到多个符合条件的行。
  - range:范围扫描通常出现在 in(), between ,> ,<, >= 等操作中。使用一个索引来检索给定范围的行。
  - index:扫描全索引就能拿到结果，一般是扫描某个二级索引，这种扫描不会从索引树根节点开始快速查找，而是直接对二级索引的叶子节点遍历和扫描，速度还是比较慢的，这种查询一般为使用覆盖索引，二级索引一般比较小，所以这 种通常比ALL快一些。
  - ALL:即全表扫描，扫描你的聚簇索引的所有叶子节点。通常情况下这需要增加索引来进行优化了。

- possible_keys列
   这一列显示查询可能使用哪些索引来查找。
   explain 时可能出现 possible_keys 有列，而 key 显示 NULL 的情况，这种情况是因为表中数据不多，mysql认为索引对此查询帮助不大，选择了全表查询。如果该列是NULL，则没有相关的索引。在这种情况下，可以通过检查 where 子句看是否可以创造一个适当的索引来提 高查询性能，然后用 explain 查看效果。

- key列
   这一列显示mysql实际采用哪个索引来优化对该表的访问。
   如果没有使用索引，则该列是 NULL。如果想强制mysql使用或忽视possible_keys列中的索引，在查询中使用 force index、ignore index。

- key_len列
   这一列显示了mysql在索引里使用的字节数，通过这个值可以算出具体使用了索引中的哪些列。 举例来说，film_actor的联合索引 idx_film_actor_id 由 film_id 和 actor_id 两个int列组成，并且每个int是4字节。通过结果中的key_len=4可推断出查询使用了第一个列:film_id列来执行索引查找。

  key_len计算规则如下: 

  - 字符串，char(n)和varchar(n)，5.0.3以后版本中，n均代表字符数，而不是字节数，如果是utf-8，一个数字或字母占1个字节，一个汉字占3个字节 
    - char(n):如果存汉字长度就是 3n 字节
    - varchar(n):如果存汉字则长度是 3n + 2 字节，加的2字节用来存储字符串长度，因为varchar是变长字符串

  - 数值类型 

    - tinyint:1字节

    - smallint:2字节 
    - int:4字节 
    - bigint:8字节  

  - 时间类型  

    - date:3字节

    - timestamp:4字节

    - datetime:8字节

如果字段允许为 NULL，需要1字节记录是否为 NULL。索引最大长度是768字节，当字符串过长时，mysql会做一个类似左前缀索引的处理，将前半部分的字符提取出来做索引。

- ref列 

  这一列显示了在key列记录的索引中，表查找值所用到的列或常量，常见的有:const(常量)，字段名(例:film.id)

- rows列 

  这一列是mysql估计要读取并检测的行数，注意这个不是结果集里的行数。

- Extra列
   这一列展示的是额外信息。常见的重要值如下:

  - Using index:使用覆盖索引 覆盖索引定义:mysql执行计划explain结果里的key有使用索引，如果select后面查询的字段都可以从这个索引的树中 获取，这种情况一般可以说是用到了覆盖索引，extra里一般都有using index;覆盖索引一般针对的是辅助索引，整个 查询结果只通过辅助索引就能拿到结果，不需要通过辅助索引树找到主键，再通过主键去主键索引树里获取其它字段值

  - Using where:使用 where 语句来处理结果，并且查询的列未被索引覆盖

  - Using index condition:查询的列不完全被索引覆盖，where条件中是一个前导列的范围;

  - Using temporary:mysql需要创建一张临时表来处理查询。出现这种情况一般是要进行优化的，首先是想到用索引来优化。

  - Using filesort: 将用外部排序而不是索引排序，数据较小时从内存排序，否则需要在磁盘完成排序。这种情况下一 般也是要考虑使用索引来优化的。

  - Select tables optimized away:使用某些聚合函数(比如 max、min)来访问存在索引的某个字段时

## SQL优化

sql优化主要还是索引的优化，其次还有排序、分页、join等场景。

#### 索引

1. 全值匹配

   尽量用全值匹配，因为范围查询可能不会走索引。比如联合索引第一个字段用范围可能不会走索引

2. 最左前缀法则 

   如果索引了多列，要遵守最左前缀法则。指的是查询从索引的最左前列开始并且不跳过索引中的列。

3. 不在索引列上做任何操作(计算、函数、(自动or手动)类型转换)，会导致索引失效而转向全表扫描

4. 存储引擎不能使用索引中范围条件右边的列

5. 尽量使用覆盖索引(只访问索引的查询(索引列包含查询列))，减少 select * 语句

6. mysql在使用不等于(!=或者<>)，not in ，not exists 的时候无法使用索引会导致全表扫描
    < 小于、 > 大于、 <=、>= 这些，mysql内部优化器会根据检索比例、表大小等多个因素整体评估是否使用索引
7. is null,is not null 一般情况下也无法使用索引

8. like以通配符开头('$abc...')mysql索引失效会变成全表扫描操作

   问题:解决like'%字符串%'索引不被使用的方法? 

   - 使用覆盖索引，查询字段必须是建立覆盖索引字段
   - 如果不能使用覆盖索引则可能需要借助搜索引擎

9. 字符串不加单引号索引失效（隐式类型转换）

10. 少用or或in，用它查询时，mysql不一定使用索引，mysql内部优化器会根据检索比例、表大小等多个因素整体评估是否使用索引。一般表数据量比较大的情况会走索引，在表记录不多的情况下会选择全表扫描

![image-20220926164508143](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926164508143.png)

like KK%相当于=常量，%KK和%KK% 相当于范围。

##### mysql如何选择索引

虽然我们根据经验建了索引，但是一条语句mysql到底走不走索引，选择走哪个索引其实是不能完全确定的，它和扫描的数据行数息息相关。我们可以通过trace工具来查看mysql选择索引的过程。开启trace工具会影响mysql性能，所以只能临时分析sql使用，用完之后立即关闭

```shell
mysql> set session optimizer_trace="enabled=on",end_markers_in_json=on; ‐‐开启trace
```

```shell
mysql> select * from employees where name > 'a' order by position;
mysql> SELECT * FROM information_schema.OPTIMIZER_TRACE;

5 查看trace字段: 
6{
7 "steps": [ 
8{
9 "join_preparation": { ‐‐第一阶段:SQL准备阶段，格式化sql
10 "select#": 1,
11 "steps": [
12 {
13 "expanded_query": "/* select#1 */ select `employees`.`id` AS `id`,`employees`.`name` AS `name`,`emp
oyees`.`age` AS `age`,`employees`.`position` AS `position`,`employees`.`hire_time` AS `hire_time` from
`employees` where (`employees`.`name` > 'a') order by `employees`.`position`"
14 }
15 ] /* steps */
16 } /* join_preparation */
17 },
18 {
19 "join_optimization": { ‐‐第二阶段:SQL优化阶段
20 "select#": 1,
21 "steps": [
22 {
23 "condition_processing": { ‐‐条件处理
24 "condition": "WHERE",
25 "original_condition": "(`employees`.`name` > 'a')",
26 "steps": [
27 {
28 "transformation": "equality_propagation",
29 "resulting_condition": "(`employees`.`name` > 'a')"
30 },
31 {
32 "transformation": "constant_propagation",
33 "resulting_condition": "(`employees`.`name` > 'a')"
34 },
{
36 "transformation": "trivial_condition_removal",
37 "resulting_condition": "(`employees`.`name` > 'a')"
38 }
39 ] /* steps */
40 } /* condition_processing */
41 },
42 {
43 "substitute_generated_columns": {
44 } /* substitute_generated_columns */
45 },
46 {
47 "table_dependencies": [ ‐‐表依赖详情
48 {
49 "table": "`employees`",
50 "row_may_be_null": false,
51 "map_bit": 0,
52 "depends_on_map_bits": [
53 ] /* depends_on_map_bits */
54 }
55 ] /* table_dependencies */
56 },
57 {
58 "ref_optimizer_key_uses": [
59 ] /* ref_optimizer_key_uses */
60 },
61 {
62 "rows_estimation": [ ‐‐预估表的访问成本
63 {
64 "table": "`employees`",
65 "range_analysis": {
66 "table_scan": { ‐‐全表扫描情况
67 "rows": 10123, ‐‐扫描行数
68 "cost": 2054.7 ‐‐查询成本
69 } /* table_scan */,
70 "potential_range_indexes": [ ‐‐查询可能使用的索引
71 {
72 "index": "PRIMARY", ‐‐主键索引
73 "usable": false,
74 "cause": "not_applicable"
75 },
76 {
77 "index": "idx_name_age_position", ‐‐辅助索引
78 "usable": true,
79 "key_parts": [
80 "name",
81 "age",
82 "position",
83 "id"
84 ] /* key_parts */
85 }
86 ] /* potential_range_indexes */,
87 "setup_range_conditions": [
] /* setup_range_conditions */,
89 "group_index_range": {
90 "chosen": false,
91 "cause": "not_group_by_or_distinct"
92 } /* group_index_range */,
93 "analyzing_range_alternatives": { ‐‐分析各个索引使用成本
94 "range_scan_alternatives": [
95 {
96 "index": "idx_name_age_position",
97 "ranges": [
98 "a < name" ‐‐索引使用范围
99 ] /* ranges */,
100 "index_dives_for_eq_ranges": true,
101 "rowid_ordered": false, ‐‐使用该索引获取的记录是否按照主键排序
102 "using_mrr": false,
103 "index_only": false, ‐‐是否使用覆盖索引
104 "rows": 5061, ‐‐索引扫描行数
105 "cost": 6074.2, ‐‐索引使用成本
106 "chosen": false, ‐‐是否选择该索引
107 "cause": "cost"
108 }
109 ] /* range_scan_alternatives */,
110 "analyzing_roworder_intersect": {
111 "usable": false,
112 "cause": "too_few_roworder_scans"
113 }
114 }
115 }
116 }
117 ]
118 },
119 {
120 "considered_execution_plans": [
121 {
/* analyzing_roworder_intersect */
/* analyzing_range_alternatives */
/* range_analysis */
/* rows_estimation */
122 "plan_prefix": [
123 ] /* plan_prefix */,
124 "table": "`employees`",
125 "best_access_path": { ‐‐最优访问路径
126 "considered_access_paths": [ ‐‐最终选择的访问路径
127 {
128 "rows_to_scan": 10123,
129 "access_type": "scan", ‐‐访问类型:为scan，全表扫描
130 "resulting_rows": 10123,
131 "cost": 2052.6,
132 "chosen": true, ‐‐确定选择
133 "use_tmp_table": true
134 }
135 ] /* considered_access_paths */
136 } /* best_access_path */,
137 "condition_filtering_pct": 100,
138 "rows_for_plan": 10123,
139 "cost_for_plan": 2052.6,
140 "sort_cost": 10123,
"new_cost_for_plan": 12176,
142 "chosen": true
143 }
144 ] /* considered_execution_plans */
145 },
146 {
147 "attaching_conditions_to_tables": {
148 "original_condition": "(`employees`.`name` > 'a')",
149 "attached_conditions_computation": [
150 ] /* attached_conditions_computation */,
151 "attached_conditions_summary": [
152 {
153 "table": "`employees`",
154 "attached": "(`employees`.`name` > 'a')"
155 }
156 ] /* attached_conditions_summary */
157 } /* attaching_conditions_to_tables */
158 },
159 {
160 "clause_processing": {
161 "clause": "ORDER BY",
162 "original_clause": "`employees`.`position`",
163 "items": [
164 {
165 "item": "`employees`.`position`"
166 }
167 ] /* items */,
168 "resulting_clause_is_simple": true,
169 "resulting_clause": "`employees`.`position`"
170 } /* clause_processing */
171 },
172 {
173 "reconsidering_access_paths_for_index_ordering": {
174 "clause": "ORDER BY",
175 "steps": [
176 ] /* steps */,
177 "index_order_summary": {
178 "table": "`employees`",
179 "index_provides_order": false,
180 "order_direction": "undefined",
181 "index": "unknown",
182 "plan_changed": false
183 } /* index_order_summary */
184 } /* reconsidering_access_paths_for_index_ordering */
185 },
186 {
187 "refine_plan": [
188 {
189 "table": "`employees`"
190 }
191 ] /* refine_plan */
192 }
] /* steps */
194 } /* join_optimization */
195 },
196 {
197 "join_execution": { ‐‐第三阶段:SQL执行阶段
198 "select#": 1,
199 "steps": [
200 ] /* steps */
201 } /* join_execution */
202 }
203 ] /* steps */
204 }
205
206 结论:全表扫描的成本低于索引扫描，所以mysql最终选择全表扫描 207
208 mysql> select * from employees where name > 'zzz' order by position;
209 mysql> SELECT * FROM information_schema.OPTIMIZER_TRACE;
210
211 查看trace字段可知索引扫描的成本低于全表扫描，所以mysql最终选择索引扫描 212
213 mysql> set session optimizer_trace="enabled=off"; ‐‐关闭trace
```

##### 索引设计原则

1. 代码先行，索引后上
2. 联合索引尽量覆盖条件，使用覆盖索引和索引下推
3. 不要在小基数字段上建立索引，小基数字段是指区分度比较低的
4. 长字符串我们可以采用前缀索引
5. where与order by冲突时优先where
6. 基于慢sql查询做优化

#### 排序优化

排序的优化主要有三点：

- 利用索引的有序性来避免排序
- 想办法减少结果集大小（限定结果集），降低辅助文件的数量
- 减少字段数量，避免mysql采用rowId排序（双路排序），该种排序会回表

##### 排序优化示例

Case1:

![image-20220926193020167](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193020167.png)

分析: 利用最左前缀法则:中间字段不能断，因此查询用到了name索引，从key_len=74也能看出，age索引列用 在排序过程中，因为Extra字段里没有using filesort

Case 2:

![image-20220926193041027](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193041027.png)

分析: 从explain的执行结果来看:key_len=74，查询使用了name索引，由于用了position进行排序，跳过了 age，出现了Using filesort。

Case 3:

![image-20220926193118983](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193118983.png)
 查找只用到索引name，age和position用于排序，无Using filesort。

Case 4:

![image-20220926193140268](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193140268.png)

分析:和Case 3中explain的执行结果一样，但是出现了Using filesort，因为索引的创建顺序为 name,age,position，但是排序的时候age和position颠倒位置了。

Case 5:

![image-20220926193157900](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193157900.png)

分析:与Case 4对比，在Extra中并未出现Using filesort，因为age为常量，在排序中被优化，所以索引未颠倒， 不会出现Using filesort。

Case 6:

![image-20220926193217746](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193217746.png)

分析:虽然排序的字段列与索引顺序一样，且order by默认升序，这里position desc变成了降序，导致与索引的 排序方式不同，从而产生Using filesort。Mysql8以上版本有降序索引可以支持该种查询方式。

Case 7:

![image-20220926193242659](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193242659.png)

分析:对于排序来说，多个相等条件也是范围查询

Case 8:

![image-20220926193311645](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193311645.png)

可以用覆盖索引优化

![image-20220926193324857](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926193324857.png)

###### 优化总结

1. MySQL支持两种方式的排序filesort和index，Using index是指MySQL扫描索引本身完成排序。index效率高，filesort效率低。

2. order by满足两种情况会使用Using index。

   - order by语句使用索引最左前列。

   - 使用where子句与order by子句条件列组合满足索引最左前列。 

3. 尽量在索引列上完成排序，遵循索引建立(索引创建的顺序)时的最左前缀法则。
4. 如果order by的条件不在索引列上，就会产生Using filesort。
5. 能用覆盖索引尽量用覆盖索引
6. group by与order by很类似，其实质是先排序后分组，遵照索引创建顺序的最左前缀法则。对于group by的优化如果不需要排序的可以加上order by null禁止排序。注意，where高于having，能写在where中 的限定条件就不要去having限定了。

###### Using filesort文件排序原理详解

mysql中排序主要分为单路排序（全字段排序），双路排序（rowId排序）

- 单路排序:是一次性取出满足条件行的所有字段，然后在sort buffer中进行排序;用trace工具可 以看到sort_mode信息里显示< sort_key, additional_fields >或者< sort_key, packed_additional_fields >

- 双路排序(又叫回表排序模式):是首先根据相应的条件取出相应的排序字段和可以直接定位行 数据的行 ID，然后在 sort buffer 中进行排序，排序完后需要再次取回其它需要的字段;用trace工具 可以看到sort_mode信息里显示< sort_key, rowid >

MySQL 通过比较系统变量 max_length_for_sort_data(默认1024字节) 的大小和需要查询的字段总大小来 判断使用哪种排序模式。

- 如果 字段的总长度小于max_length_for_sort_data ，那么使用 单路排序模式; 
- 如果 字段的总长度大于max_length_for_sort_data ，那么使用 双路排序模式。

#### 分页查询优化

很多时候我们业务系统实现分页功能可能会用如下sql实现

```shell
mysql> select * from employees limit 10000,10;
```

表示从表 employees 中取出从 10001 行开始的 10 行记录。看似只查询了 10 条记录，实际这条 SQL 是先读取 10010 条记录，然后抛弃前 10000 条记录，然后读到后面 10 条想要的数据。因此要查询一张大表比较靠后的数据，执行效率是非常低的。

###### 1、根据自增且连续的主键排序的分页查询

首先来看一个根据自增且连续主键排序的分页查询的例子:

```sql
mysql> select * from employees limit 90000,5;
```

![image-20220926195753622](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926195753622.png)

该 SQL 表示查询从第 90001开始的五行数据，没添加单独 order by，表示通过主键排序。我们再看表 employees ，因 为主键是自增并且连续的，所以可以改写成按照主键去查询从第 90001开始的五行数据，如下:

```sql
mysql> select * from employees where id > 90000 limit 5;
```

![image-20220926195823150](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926195823150.png)

查询的结果是一致的。我们再对比一下执行计划:

```sql
mysql> EXPLAIN select * from employees limit 90000,5;
mysql> EXPLAIN select * from employees where id > 90000 limit 5;
```

![image-20220926195852374](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926195852374.png)

显然改写后的 SQL 走了索引，而且扫描的行数大大减少，执行效率更高。

但是，这条改写的SQL 在很多场景并不实用，因为表中可能某些记录被删后，主键空缺，导致结果不一致。另外如果原 SQL 是 order by 非主键的字段，按照上面说的方法改写会导致两条 SQL 的结果不一致。所以这种改写得满 足以下两个条件:

- 主键自增且连续
- 结果是按照主键排序的

###### 2、根据非主键字段排序的分页查询

再看一个根据非主键字段排序的分页查询，SQL 如下:

![image-20220926200051201](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926200051201.png)

发现并没有使用 name 字段的索引(key 字段对应的值为 null)，具体原因上节课讲过:

扫描整个索引并查找到没索引的行(可能要遍历多个索引树)的成本比扫描全表的成本更高，所以优化器放弃使用索引。 知道不走索引的原因，那么怎么优化呢? **其实关键是让排序时返回的字段尽可能少，所以可以让排序和分页操作先查出主键**，然后根据主键查到对应的记录，SQL 改写如下

```sql
mysql> select * from employees e inner join (select id from employees order by name limit 90000,5) ed on e.id = ed.id;
```

![image-20220926200302306](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926200302306.png)

需要的结果与原 SQ 一致，执行时间减少了一半以上，我们再对比优化前后sql的执行计划:

![image-20220926200321527](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926200321527.png)

原 SQL 使用的是 filesort 排序，而优化后的 SQL 使用的是索引排序。

#### Join关联查询优化

一般情况下我们都推荐去join，通过业务关联来避免在mysql中执行join语句，因为join语句的性能是不稳定的。但是如果无法去除，或者去除成本很高，尽量用小表去驱动大表。（如果可以，也可以通过其他存储工具，比如es来替换它）

mysql的表关联常见有两种算法

- Nested-Loop Join 算法

- Block Nested-Loop Join 算法

###### 1、嵌套循环连接 Nested-Loop Join(NLJ) 算法（如果关联字段有索引，优先选择该算法）

一次一行循环地从第一张表(称为驱动表)中读取行，在这行数据中取到关联字段，根据关联字段在另一张表(被驱动表)里取出满足条件的行，然后取出两张表的结果合集。

```sql
mysql> EXPLAIN select * from t1 inner join t2 on t1.a= t2.a;
```

![image-20220926201548013](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926201548013.png)

从执行计划中可以看到这些信息:

- 驱动表是 t2，被驱动表是 t1。先执行的就是驱动表(执行计划结果的id如果一样则按从上到下顺序执行sql);优化器一般会优先选择小表做驱动表。所以使用 inner join 时，排在前面的表并不一定就是驱动表。

- 当使用left join时，左表是驱动表，右表是被驱动表，当使用right join时，右表时驱动表，左表是被驱动表， 当使用join时，mysql会选择数据量比较小的表作为驱动表，大表作为被驱动表。

- 使用了 NLJ算法。一般 join 语句中，如果执行计划 Extra 中未出现 Using join buffer 则表示使用的 join 算 法是 NLJ。

上面sql的大致流程如下:

1. 从表 t2 中读取一行数据(如果t2表有查询过滤条件的，会从过滤结果里取出一行数据); 
2. 从第 1 步的数据中，取出关联字段 a，到表 t1 中查找;
3. 取出表 t1 中满足条件的行，跟 t2 中获取到的结果合并，作为结果返回给客户端;
4. 重复上面 3 步

整个过程会读取 t2 表的所有数据(扫描100行)，然后遍历这每行数据中字段 a 的值，根据 t2 表中 a 的值索引扫描 t1 表 中的对应行(扫描100次 t1 表的索引，1次扫描可以认为最终只扫描 t1 表一行完整数据，也就是总共 t1 表也扫描了100 行)。因此整个过程扫描了 200 行。 如果被驱动表的关联字段没索引，使用NLJ算法性能会比较低(下面有详细解释)，mysql会选择Block Nested-Loop Join 算法。

###### 2、 基于块的嵌套循环连接 Block Nested-Loop Join(BNL)算法

把驱动表的数据读入到 join_buffer 中，然后扫描被驱动表，把被驱动表每一行取出来跟 join_buffer 中的数据做对比。

```sql
1 mysql>EXPLAIN select * from t1 inner join t2 on t1.b= t2.b;
```

![image-20220926202202191](https://yusheng-picgo.oss-cn-beijing.aliyuncs.com/picgo/image-20220926202202191.png)

Extra 中 的Using join buffer (Block Nested Loop)说明该关联查询使用的是 BNL 算法。 上面sql的大致流程如下:

1. 把 t2 的所有数据放入到 join_buffer 中
2. 把表 t1 中每一行取出来，跟 join_buffer 中的数据做对比 
3. 返回满足 join 条件的数据

整个过程对表 t1 和 t2 都做了一次全表扫描，因此扫描的总行数为10000(表 t1 的数据总量) + 100(表 t2 的数据总量) = 10100。并且 join_buffer 里的数据是无序的，因此对表 t1 中的每一行，都要做 100 次判断，所以内存中的判断次数是 100 * 10000= 100 万次。
 这个例子里表 t2 才 100 行，要是表 t2 是一个大表，join_buffer 放不下怎么办呢?

join_buffer 的大小是由参数 join_buffer_size 设定的，默认值是 256k。如果放不下表 t2 的所有数据话，策略很简单， 就是分段放。
 比如 t2 表有1000行记录， join_buffer 一次只能放800行数据，那么执行过程就是先往 join_buffer 里放800行记录，然 后从 t1 表里取数据跟 join_buffer 中数据对比得到部分结果，然后清空 join_buffer ，再放入 t2 表剩余200行记录，再次从 t1 表里取数据跟 join_buffer 中数据对比。所以就多扫了一次 t1 表。

###### 被驱动表的关联字段没索引为什么要选择使用 BNL 算法而不使用 Nested-Loop Join 呢?

如果上面第二条sql使用 Nested-Loop Join，那么扫描行数为 100 * 10000 = 100万次，这个是磁盘扫描。 很显然，用BNL磁盘扫描次数少很多，相比于磁盘扫描，BNL的内存计算会快得多。 因此MySQL对于被驱动表的关联字段没索引的关联查询，一般都会使用 BNL 算法。如果有索引一般选择 NLJ 算法，有索引的情况下 NLJ 算法比 BNL算法性能更高

###### 对于关联sql的优化

-  关联字段加索引，让mysql做join操作时尽量选择NLJ算法 
- 小表驱动大表，写多表连接sql时如果明确知道哪张表是小表可以用straight_join写法固定连接驱动方式，省去mysql优化器自己判断的时间。straight_join只适用于inner join，并不适用于left join，right join。(因为left join，right join已经代表指 定了表的执行顺序)尽可能让优化器去判断，因为大部分情况下mysql优化器是比人要聪明的。使用straight_join一定要慎重，因 为部分情况下人为指定的执行顺序并不一定会比优化引擎要靠谱。

###### 小表定义

在决定哪个表做驱动表的时候，应该是两个表按照各自的条件过滤，过滤完成之后，计算参与 join 的各个字段的总数据 量，数据量小的那个表，就是“小表”，应该作为驱动表。 

#### count(*)优化

对于mysql的统计而言，count(*)已经是最优的方式，但是我们可以通过辅助表或者辅助系统来优化它。

- 字段有索引: count(*)≈count(1)>count(字段)>count(主键 id) 

  字段有索引，count(字段)统计走二级索引，二级索引存储数据比主键索引少，所以count(字段)>count(主键 id)

- 字段无索引: count(*)≈count(1)>count(主键 id)>count(字段) 

  字段没有索引count(字段)统计走不了索引， count(主键 id)还可以走主键索引，所以count(主键 id)>count(字段)，count(1)跟count(字段)执行过程类似，不过count(1)不需要取出字段统计，就用常量1做统计，count(字段)还需要取出字段，所以理论上count(1)比count(字段)会快一点。

count(*)是例外，mysql并不会把全部字段取出来，而是专门做了优化，不取值，按行累加，效率很高，所以不需要用 count(列名)或count(常量)来替代 count(*)。 为什么对于count(id)，mysql最终选择辅助索引而不是主键聚集索引?因为二级索引相对主键索引存储数据更少，检索 性能应该更高，mysql内部做了点优化(应该是在5.7版本才优化)。