## 准备

**MySQL内核版本: 8.0.17**

## 理解 lock 和 latch

### latch

latch是在BTree上定位record的时候对Btree pages加的锁，它一般是在对page中对应record加上lock并且完成访问/修改后就释放，latch的锁区间比lock小很多。

### lock

而 lock 则是数据库 MySQL 中在事务使用的”锁”, 锁定的对象是表或者行.

### lock mode

lock的mode主要有Share(S)和Exclusive(X)【代码中对应LOCK_S和LOCK_X】
lock的gap mode主要有Record lock, Gap lock, Next-key lock【代码中对应LOCK_REC_NOT_GAP, LOCK_GAP, LOCK_ORDINARY】
Record lock是作用在单个record上的记录锁，Gap lock/Next-key lock虽然也是加在某个具体record上，但作用是为了确保record前面的gap不要有其他并发事务插入，InnoDB引入了一个插入意向锁，他的实际类型是 `（LOCK_X | LOCK_GAP | LOCK_INSERT_INTENTION）`=与Gap lock/Next-key lock互斥，如果要插入前检测到插入位置的next record上有lock，则会尝试对这个next record加一个插入意向锁，代表本事务打算给这个gap里插一个新record，如果已经有别的事务给这里上了Gap/Next-key lock，代表它想保护这里，所以当前插入意向锁需要等待相关事务提交才行。这个检测只是单向的，即插入意向锁需等待Gap/Next-key lock释放，而任何锁不用等待插入意向锁释放，否则严重影响这个gap中不冲突的Insert操作并发。
insert加锁和select加锁流程可见第二篇参考文章，这里不再赘述。

## 锁的类型

- [Intention Locks](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-intention-locks):`InnoDB` supports *multiple granularity locking* which permits coexistence of row locks and table locks
- [Record Locks](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-record-locks):A record lock is a lock on an index record
- [Gap Locks](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-gap-locks):A gap lock is a lock on a gap between index records, or a lock on the gap before the first or after the last index record
- [Next-Key Locks](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-next-key-locks):A next-key lock is a combination of a record lock on the index record and a gap lock on the gap before the index record
- ...

## 意向锁

表级别锁的兼容互斥矩阵:

|      | X        | IX         | S          | IS         |
| ---- | -------- | ---------- | ---------- | ---------- |
| X    | Conflict | Conflict   | Conflict   | Conflict   |
| IX   | Conflict | Compatible | Conflict   | Compatible |
| S    | Conflict | Conflict   | Compatible | Compatible |
| IS   | Conflict | Compatible | Compatible | Compatible |

需要注意上图矩阵的`X`, `IX`, `S`, `IS`锁均为表锁，并不代表行锁.

锁的含义:

`X`: 排他锁
`IX`: 意向排他锁
`S`: 共享锁
`IS`: 意向共享锁

在一个事务`trx_t`中用结果`trx_lock_t`来存放事务申请的锁信息, 包括行锁和表锁, 即`trx->lock.trx_locks`和`trx->lock.table_locks`.

MySQL为了支持多粒度的锁, 引入了意向锁，意向锁是一种可以与行锁共存的锁, 例如`SELECT ... FOR SHARE`设置了`IS`意向共享锁, 而`SELECT ... FOR UPDATE`设置了`IX`意向排他锁. 意向锁的上锁原则如下:

- 当一个事务对一个表的某一行记录申请 record 共享锁(行锁), 需要先申请`IS`意向共享锁(表锁).
- 当一个事务对一个表的某一行记录申请 record 排他锁(行锁), 需要先申请`IX`意向排他锁(表锁).

X，IS是表级锁，不会和行级的X，S锁发生冲突, 只会和表级的X，S发生冲突. 行级别的X和S只与其它行锁存在普通的共享、排他规则. 而意向锁的意义是当需要向一张表添加表级X锁时，假如没有意向锁，需要遍历`lock_sys->rec_hash`判断是否与该X锁存在冲突的锁.

## 源码分析

以源码分析的方式来直观的理解意向锁的加锁过程，此处以 update 一条 record 获取 IX 锁为例:

在 IX 锁申请之前，会对当前表(`dict_table_t`)记录的锁信息的兼容情况进行判断(`lock_table_other_has_incompatible()`), 符合兼容矩阵的从而在`row_upd_step()`函数中调用`lock_table()`申请 IX 锁, 表级锁的申请过程如下:

```c++
/* storage/innobase/lock/lock0lock.cc */
/** Creates a table lock object and adds it as the last in the lock queue
 of the table. Does NOT check for deadlocks or lock compatibility.
 @return own: new lock object */
UNIV_INLINE
lock_t *lock_table_create(dict_table_t *table, /*!< in/out: database table in dictionary cache */
                          ulint type_mode, /*!< in: lock mode possibly ORed with LOCK_WAIT */
                          trx_t *trx)      /*!< in: trx */
{
	lock_t*		lock;

	ut_ad(table && trx);
	ut_ad(lock_mutex_own());
	ut_ad(trx_mutex_own(trx));

  /* 检查事务状态. */
	check_trx_state(trx);
	++table->count_by_mode[type_mode & LOCK_MODE_MASK];
  /* For AUTOINC locking we reuse the lock instance only if
  there is no wait involved else we allocate the waiting lock
  from the transaction lock heap. */
	if (type_mode == LOCK_AUTO_INC) {
    /* 对于AUTOINC 锁可以直接复用. */
		lock = table->autoinc_lock;

		table->autoinc_trx = trx;

		ib_vector_push(trx->autoinc_locks, &lock);

	} else if (trx->lock.table_cached < trx->lock.table_pool.size()) {
    /* 假如trx的table_pool有预先申请的table lock. */
		lock = trx->lock.table_pool[trx->lock.table_cached++];
	} else {
    /* 否则通过内存分配一个table lock. */
		lock = static_cast<lock_t*>(
			mem_heap_alloc(trx->lock.lock_heap, sizeof(*lock)));
	}

  /* 设置lock相关的数据变量. */
	lock->type_mode = ib_uint32_t(type_mode | LOCK_TABLE);
	lock->trx = trx;

	lock->un_member.tab_lock.table = table;

	ut_ad(table->n_ref_count > 0 || !table->can_be_evicted);

  /* 插入trx->lock的trx_locks. */
	UT_LIST_ADD_LAST(trx->lock.trx_locks, lock);

	ut_list_append(table->locks, lock, TableLockGetNode());

	if (type_mode & LOCK_WAIT) {
    /* 假如设置了LOCK_WAIT状态，需要设置lock.wait_lock. */
		lock_set_lock_and_trx_wait(lock, trx);
	}

  /* 插入trx->lock的table_locks. */
	lock->trx->lock.table_locks.push_back(lock);

	MONITOR_INC(MONITOR_TABLELOCK_CREATED);
	MONITOR_INC(MONITOR_NUM_TABLELOCK);

	return(lock);
}

/** Sets the wait flag of a lock and the back pointer in trx to lock.
@param[in]  lock  The lock on which a transaction is waiting */
UNIV_INLINE
void lock_set_lock_and_trx_wait(lock_t *lock) {
  auto trx = lock->trx;
  ut_a(trx->lock.wait_lock == NULL);
  ut_ad(lock_mutex_own());
  ut_ad(trx_mutex_own(trx));

  trx->lock.wait_lock = lock;
  trx->lock.wait_lock_type = lock_get_type_low(lock);
  lock->type_mode |= LOCK_WAIT;
}
```

`row_upd_step()`完成申请IX意向排他锁后继续调用`row_upd_clust_step()`, 而`row_upd_clust_step()`调用`lock_clust_rec_modify_check_and_lock()`对修改的 record 申请 X 锁:

```
 ----------------
| row_upd_step() |   /* 申请 IX 锁. */
 ----------------
   |
   |   ----------------
   -> |      ...       |
       ----------------
         |
         |   ----------------------
         -> | row_upd_clust_step() |
             ----------------------
               |
               |   ----------------------------------------
               -> | lock_clust_rec_modify_check_and_lock() |    /* 申请 record 的 X 锁. */
               |   ----------------------------------------
               |
               |   ---------------
               -> |     ...       |
                   ---------------
```

例如此时某一个用户正在使用`lock table`语句锁表，依然会进入`lock_table_other_has_incompatible()`判断表级锁的兼容情况，假如产生冲突，该用户线程则会进入 wait 状态.

````c++
/** Checks if other transactions have an incompatible mode lock request in
 the lock queue.
 @return lock or NULL */
UNIV_INLINE
const lock_t *lock_table_other_has_incompatible(
    const trx_t *trx,          /*!< in: transaction, or NULL if all
                               transactions should be included */
    ulint wait,                /*!< in: LOCK_WAIT if also
                               waiting locks are taken into
                               account, or 0 if not */
    const dict_table_t *table, /*!< in: table */
    lock_mode mode)            /*!< in: lock mode */
{
  const lock_t *lock;

  ut_ad(lock_mutex_own());

  // According to lock_compatibility_matrix, an intention lock can wait only
  // for LOCK_S or LOCK_X. If there are no LOCK_S nor LOCK_X locks in the queue,
  // then we can avoid iterating through the list and return immediately.
  // This might help in OLTP scenarios, with no DDL queries,
  // as then there are almost no LOCK_S nor LOCK_X, but many DML queries still
  // need to get an intention lock to perform their action - while this never
  // causes them to wait for a "data lock", it might cause them to wait for
  // lock_sys->mutex if the operation takes Omega(n).

  if ((mode == LOCK_IS || mode == LOCK_IX) &&
      table->count_by_mode[LOCK_S] == 0 && table->count_by_mode[LOCK_X] == 0) {
    return NULL;
  }

  for (lock = UT_LIST_GET_LAST(table->locks); lock != NULL;
       lock = UT_LIST_GET_PREV(tab_lock.locks, lock)) {
    if (lock->trx != trx && !lock_mode_compatible(lock_get_mode(lock), mode) &&
        (wait || !lock_get_wait(lock))) {
      return (lock);
    }
  }

  return (NULL);
}
````



## 总结

1. MySQL支持的意向锁之间互不排斥，除了IS与S锁兼容外，意向锁会与共享锁/ 排他锁互斥.
2. IX，IS是表级锁，不会和行级的X，S锁发生冲突.

## 参考文档

1. https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-intention-locks
2. https://zhuanlan.zhihu.com/p/412358771

