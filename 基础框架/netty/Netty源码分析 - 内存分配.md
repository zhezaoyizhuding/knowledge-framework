# 背景

Netty中的内存管理的实现并不是一蹴而就的，它也是参考了jemalloc内存分配器。而jemalloc又借鉴了tcmalloc（出身于Google，通过红黑树来管理内存快和分页，带有线程缓存。对于小的对象来说，直接由线程的局部缓存来完成，大对象那就由自旋锁来减少多线程下的竞争）的设计思路，但是jemalloc设计的更复杂，虽然也有线程缓存的特性，但是jemalloc将内存分配的粒度划分为 `Small`、`Large`、`Huge`三个分类，在空间的占用上比较多，但是在大内存分配的场景，内存碎片就略少 。

虽然有众多的内存分配器，但是它们的核心都是一致的：

- 高效大的内存分配和回收，提升单线程或者多线程场景下的性能；
- 减少内存碎片，包括`内部碎片`和`外部碎片`，提升内存的有效利用率。

这边有个内存碎片的概念，可以介绍下，linux中物理内存会被分成若干个**4k**大小的内存页**Page**，物理内存的分配和回收都是基于**Page**完成的，**内部碎片**就是**Page**内部产生的碎片，**外部碎片**就是各个**Page**之间产生的碎片。

内部碎片：

即使我们只需要很小的内存，系统也至少会分配**4k**大小的**Page**

<img src="https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205011238985.png" alt="image-20220501123758558" style="zoom:50%;" />

外部碎片：

当我们分配大内存的时候，此时一个**Page(4k)**显然不够，此时，系统会分配连续的**Page**才能够满足要求，但是，我们的程序在不断的运行，这些**Page**会被频繁的回收，然后重新分配，难免这些**Page**之间会出现空闲的内存块，这就形成了外部碎片

<img src="https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205011249803.png" alt="image-20220501124926740" style="zoom:50%;" />

对于内存分配的肯定有内存分配的一些算法，这边可以参考我的processon（查了各种资料，然后总结下），这篇主要讲Netty的内存分配；

# 基本概念

Netty内存根据使用的内存位置（堆内heap和堆外direct）和内存是否池化进行分类。

<img src="https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205021238699.png" alt="image-20220502123859568" style="zoom:30%;" />

对于每个线程而言，netty会为之分配一个内存Cache；而在多个线程之间可共享一个Arena。Arena管理着相关内存，包含不同使用率的PoolChunkList、tinySubPagePools及smallSubPagePools来更好地分配内存。

内存根据大小可分为 huge、normal、small、tiny。

Huge

![image-20220502124457511](https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205021244552.png)

初次申请内存，都是按照Chunk来申请，但是为了更高效率的使用内存，在Chunk这个级别下，还定义了Page和SubPage的内存块。

- Chunk : 是Netty向操作系统申请内存的单位，所有的内存分配操作都是基于chunk完成的，默认大小是16M。在分配大小超过8K的内存，会从PoolChunkList中分配内存，或新增Chunk。一个Chunk会被分成2048个Page，是一个完全二叉树。一般每层节点有一个标识，标识当前节点及以下节点是否还有可用节点。

- Page：是Chunk用于管理内存的单位，Netty中的Page的大小为8k，假如需要分配64K的内存，需要在Chunk中选取4个Page进行分配。
- SubPage：负责page内的内存分配，假如我们分配的内存大小远小于Page（8K），直接分配一个Page会造成严重的内存浪费，所以需要将Page划分为多个相同的子块来进行分配，这里的子块就相当于SubPage。SubPage也分为两种不同的规格，在Tiny场景下，最小的划分为16B，然后按16B依次递增；在Small场景下，就分为4种规格，512B、1024B、2048B、4096B。

![image-20220502135035831](https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205021350915.png)



## PoolArena

Netty借鉴了jemalloc中的Arena的设计思想，采用固定数量的多个Arena进行内存分配，Arena的默认数量与CPU的核数有关，通过创建多个Arena来缓解资源竞争的问题，提高了内存分配的效率。线程在首次申请分配内存时，会轮询Arena数量，选择一个固定的Arena，在线程的生命周期内只与该Arena打交道，所以每个线程都保存了Arena的信息，从而提高访问的效率。

PoolArena 的数据结构包含了两个PoolSubPage数组，和六个PoolChunkList,这两个PoolSubPage数组分别存放Tiny和Small类型的内存块，六个PoolChunkList分别存储不同利用率的Chunk，构成一个双向链表。

![image-20220502142055655](https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205021420741.png)



```java
 		//内存使用率为100%的Chunk		
	q100 = new PoolChunkList<T>(this, null, 100, Integer.MAX_VALUE, chunkSize);

			//内存使用率为75～100%的Chunk
        q075 = new PoolChunkList<T>(this, q100, 75, 100, chunkSize);

				//内存使用率为50～100%的Chunk
        q050 = new PoolChunkList<T>(this, q075, 50, 100, chunkSize);

				//内存使用率为25～75%的Chunk
        q025 = new PoolChunkList<T>(this, q050, 25, 75, chunkSize);

				//内存使用率为1～50%的Chunk
        q000 = new PoolChunkList<T>(this, q025, 1, 50, chunkSize);

				//内存使用率为0～25%的Chunk
        qInit = new PoolChunkList<T>(this, q000, Integer.MIN_VALUE, 25, chunkSize);

        q100.prevList(q075);
        q075.prevList(q050);
        q050.prevList(q025);
        q025.prevList(q000);
        q000.prevList(null);
        qInit.prevList(qInit);

```

六种类型的PoolChunkList除了qInit，它们都形成了双向链表.

qInit 用于存储初始化分配的PoolChunk，在第一次内存分配时，PoolChunkList中并没有可用的PoolChunk，所以需要新创建一个PoolChunk并添加到qInit列表中。qInit中的PoolChunk即使内存被完全释放也不会被回收，避免了PoolChunk的重复初始化工作。

内存池的初始阶段线程是没有内存缓存的，所以最开始的内存分配都需要在全局分配区进行分配

无论是TinySubpagePools还是SmallSubpagePools成员在内存池初始化时是不会预置内存的，所以最开始内存分配阶段都会进入PoolArena的allocateNormal方法

```java
 private void allocateNormal(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
   
   		//1. 尝试从现有的Chunk进行分配,
        if (q050.allocate(buf, reqCapacity, normCapacity) || q025.allocate(buf, reqCapacity, normCapacity) ||
            q000.allocate(buf, reqCapacity, normCapacity) || qInit.allocate(buf, reqCapacity, normCapacity) ||
            q075.allocate(buf, reqCapacity, normCapacity)) {
            return;
        }

        // Add a new chunk.2. 尝试创建一个Chuank进行内存分配
        PoolChunk<T> c = newChunk(pageSize, maxOrder, pageShifts, chunkSize);
        boolean success = c.allocate(buf, reqCapacity, normCapacity);
        assert success;
   
   //  	4.将PoolChunk添加到PoolChunkList中
        qInit.add(c);
    }



boolean allocate(PooledByteBuf<T> buf, int reqCapacity, int normCapacity) {
        final long handle;
        if ((normCapacity & subpageOverflowMask) != 0) { // >= pageSize   通过位运算是否大于512k
            handle =  allocateRun(normCapacity);
        } else {
            handle = allocateSubpage(normCapacity);
        }

        if (handle < 0) {
            return false;
        }
        ByteBuffer nioBuffer = cachedNioBuffers != null ? cachedNioBuffers.pollLast() : null;
  
  			//3 . 初始化PooledByteBuf
        initBuf(buf, nioBuffer, handle, reqCapacity);
        return true;
    }
```

分配内存时为什么选择从q050开始



> 1.qinit的chunk利用率低，但不会被回收
> 2.q075和q100由于内存利用率太高，导致内存分配的成功率大大降低，因此放到最后
> 3.q050保存的是内存利用率50%~100%的Chunk，这应该是个折中的选择。这样能保证Chunk的利用率都会保持在一个较高水平提高整个应用的内存利用率，并且内存利用率在50%~100%的Chunk内存分配的成功率有保障
> 4.当应用在实际运行过程中碰到访问高峰，这时需要分配的内存是平时的好几倍需要创建好几倍的Chunk，如果先从q0000开始，这些在高峰期创建的chunk被回收的概率会大大降低，延缓了内存的回收进度，造成内存使用的浪费

## PoolChunkList

PoolChunkList 负责管理多个PoolChunk的生命周期，同一个PoolChunkList中存放了内存相近的PoolChunk，通过双向链表的形式链接在一起，因为PoolChunk经常要从PoolChunkList中删除，而且需要在不同的PoolChunkList中移动，所以双向链表是管理PoolChunk时间复杂度较低的数据结构。

```java
final class PoolChunkList<T> implements PoolChunkListMetric {
    private static final Iterator<PoolChunkMetric> EMPTY_METRICS = Collections.<PoolChunkMetric>emptyList().iterator();
    private final PoolArena<T> arena;
    private final PoolChunkList<T> nextList;//下一个PoolChunkList（使用率更高的）
    private final int minUsage;//最低使用率，低于该值，会移除该chunk，放到preList中
    private final int maxUsage;//最高使用率，高于该值，会移除该chunk，放到nextList中
    private final int maxCapacity;//最大可分配的内存大小，就是用minUsage计算的
    private PoolChunk<T> head;

    // This is only update once when create the linked like list of PoolChunkList in PoolArena constructor.
    private PoolChunkList<T> prevList; //前一个PoolChunkList（使用率更低的）

```

每个PoolChunkList都有内存使用率的上下限：minUsage和maxUsage，当PoolChunk进行内存分配后，如果使用率超过maxUsage，那么PoolChunk会从当前PoolChunkList中删除，并移动到下一个PoolChunkList；同理，PoolChunk中的内存发生释放后，使用率小于minUsage，那么PoolChunk会从当前PoolChunkList中移除，移动到前一个PoolChunk List。

再细看下上面的各个部分的内存使用率会有交叉重叠的部分，这样设计的原因是，因为PoolChunk需要在PoolChunkList中不断的移动，如果每个PoolChunkList的内存使用率的临界值都是恰好衔接的，例如 1%～50%，50%（51%）～70%，如果PoolChunk的使用率在45%~55%之间不停徘徊的话，那么就会导致PoolChunk在两个PoolChunkList不断移动，造成性能损耗。



## PoolChunk

Netty内存的分配和回收都是基于PoolChunk完成的，PoolChunk是真正存储内存数据的地方，每个PoolChunk的默认大小为16M

```java
final class PoolChunk<T> implements PoolChunkMetric {

    final PoolArena<T> arena;

    final T memory; // 存储的数据

    private final byte[] memoryMap; // 满二叉树中的节点是否被分配，数组大小为 4096

    private final byte[] depthMap; // 满二叉树中的节点高度，数组大小为 4096

    private final PoolSubpage<T>[] subpages; // PoolChunk 中管理的 2048 个 8K 内存块

    private int freeBytes; // 剩余的内存大小

    PoolChunkList<T> parent;

    PoolChunk<T> prev;

    PoolChunk<T> next;

    // 省略其他代码

}


```

PoolChunk 我们可以理解为Page（8K）的集合 ，Page只是一种抽象的概念，实际在Netty中Page指的是PoolChunk所管理的子内存块，每个子内存块采用**PoolSubpage**表示

![image-20220502162637539](https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202205021626633.png)



```java
maxOrder = 11;
maxSubpageAllocs = 1 << maxOrder; 

        // Generate the memory map.
        memoryMap = new byte[maxSubpageAllocs << 1];
        depthMap = new byte[memoryMap.length];
        int memoryMapIndex = 1;
        for (int d = 0; d <= maxOrder; ++ d) { // move down the tree one level at a time
            int depth = 1 << d;
            for (int p = 0; p < depth; ++ p) {
                // in each level traverse left to right and set value to the depth of subtree
                memoryMap[memoryMapIndex] = (byte) d;
                depthMap[memoryMapIndex] = (byte) d;
                memoryMapIndex ++;
            }
        }
```

deptMap 用于存放节点所对应的高度。例如第2048个节点depthMap[1025] = 10

memoryMap 用于记录二叉树节点分配的信息，初始值和deptMap是一样的，随着节点被分配，不仅节点的值会改变，而且会递归遍历更新其父节点的值，父节点的值取两个子节点中的最小值。

subpages对应上图中PoolChunk内部的Page0，Page1等。Netty中没有Page的定义，直接使用PoolSubPage表示。当分配的内存小于8k是，PoolChunk中的每个Page节点会被划分成为更小的粒度的内存进行管理，小内存块同样以PoolSubPage管理。

```java
 private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        int d = maxOrder; // subpages are only be allocated from pages i.e., leaves
        synchronized (head) {
            int id = allocateNode(d);
            if (id < 0) {
                return id;
            }

            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;

            int subpageIdx = subpageIdx(id);
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();
        }
    }



PoolSubpage<T> findSubpagePoolHead(int elemSize) {
        int tableIdx;
        PoolSubpage<T>[] table;
        if (isTiny(elemSize)) { // < 512
            tableIdx = elemSize >>> 4;
            table = tinySubpagePools;
        } else {
            tableIdx = 0;
            elemSize >>>= 10;
            while (elemSize != 0) {
                elemSize >>>= 1;
                tableIdx ++;
            }
            table = smallSubpagePools;
        }

        return table[tableIdx];
    }
```

根据代码可以看出，小内存分配的场景下，会首先找到对应的PoolArena，然后根据计算出对应的

tinySubpagePools 或者 smallSubpagePools 数组对应的下标，如果对应数组元素所包含的PoolSubpage链表不存在任何节点，那么将创建新的PoolSubpage加入链表中



## PoolSubpage

```java
final class PoolSubpage<T> implements PoolSubpageMetric {

    final PoolChunk<T> chunk;

    private final int memoryMapIdx; // 对应满二叉树节点的下标

    private final int runOffset; // PoolSubpage 在 PoolChunk 中 memory 的偏移量

    private final long[] bitmap; // 记录每个小内存块的状态

    // 与 PoolArena 中 tinySubpagePools 或 smallSubpagePools 中元素连接成双向链表

    PoolSubpage<T> prev;

    PoolSubpage<T> next;

    int elemSize; // 每个小内存块的大小

    private int maxNumElems; // 最多可以存放多少小内存块：8K/elemSize

    private int numAvail; // 可用于分配的内存块个数

    // 省略其他代码

}



```

PoolSubpage是通过位图bitmap来记录子内存是否已经被使用，bit的取值为0或1

<img src="https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202206251311727.png" alt="image-20220625131149525" style="zoom:50%;" />



那PoolSubPage和PoolArea怎样联系起来的呢

PoolArea在创建是会初始化tinySubpagePools和smallSubpagePools两个PoolSubpage数组，数组的大小分别时32和4

加入我们分配20B大小的内存，会向上取整到32B，从满二叉树的第11层找到一个PoolSubpage节点，并把它分为	8KB/32B = 256个小内存块，然后找到这个PoolSubpage节点对应的PoolArena，然后将PoolSubPage节点与tinySubpagePools[1]对应的head节点链接成双向链表

https://www.processon.com/diagraming/61c2d2ce7d9c08302261e390

如果后续再有32B规格的内存分配时，直接从PoolArena中tinySubpagePools[1]元素的next节点是否存在可用的PoolSubpage，如果存在直接使用该PoolSubpage执行内存分配，提高内存分配的使用效率。



当我们内存释放时，Netty并没有将缓存归还到PoolChunk中，而是使用PoolThreadCache(本地线程缓存)，当下次我们有同样规格的内存分配时，如果缓存有，直接从缓存里面取出当前符合规格的内存

# 内存的分配策略

##### 分配内存大于8k，PoolChunk中采用的Page级别的内存分配策略

假设我们依次申请了 8k，16k ,8k的内存，

1. 首先根据分配内存大小计算二叉树所在节点的高度，然后查找对应高度中是否存在可用节点，如果分配成功则减去已经分配的内存大小得到剩余的可用空间，核心代码如下

```java
private long allocateRun(int normCapacity) {
  			//根据分配内存大小计算树对应的节点高度 maxOrder 为二叉树的最大高度 11. , pageShifts 默认为13
  
        int d = maxOrder - (log2(normCapacity) - pageShifts);
  		//查找对应高度中是否存在可用节点
        int id = allocateNode(d);
        if (id < 0) {
            return id;
        }
        freeBytes -= runLength(id);//减去以分配的内存大小
        return id;
    }
```

第一次在分配8k大小的内存时，计算得到二叉树所在节点高度为11  ， 8k= 2^13.然后从第11层查找可用的Page,下标为2048的节点可以被用于分配内存，即page【0】被分配使用，此时赋值 memoryMap[2048] =12,表示该节点已经不可用,然后递归更新父节点的值，父节点的值取两个子节点的最小值，即memoryMap[1024]=11,memory[512]=10



第二次分配16k内存时，计算得到的节点高度是10，此时1024节点已经分配了一个8K的内存，不满足条件，继续寻找1025节点，此节点并未使用过，满足分配的条件，就将1025的两个子节点分配出去，赋值，memoryMap[2050]=12,memoryMap[2051] = 12,然后在递归更新父节点的值



第三次分配8k大小的内存时，依然从第11层开始查找，发现2048已经使用，2049可以分配，赋值memoryMap【2049】=12，然后递归更新父节点



![image-20220625144300314](https://dong-pdf-preview.oss-cn-shanghai.aliyuncs.com/blog/202206251443375.png)



##### 分配内存小于8k，由PoolSubpage负责管理的内存分配策略

PoolChunk不在分配单独的Page，而是将Page划分为更小的内存块，由PoolSubpage进行管理

```java
private long allocateSubpage(int normCapacity) {
        // Obtain the head of the PoolSubPage pool that is owned by the PoolArena and synchronize on it.
        // This is need as we may add it back and so alter the linked-list structure.

        //根据内存大小找到PoolArena中Subpage数组对应的头节点
        PoolSubpage<T> head = arena.findSubpagePoolHead(normCapacity);
        // 从最底层开始查找
        int d = maxOrder; // subpages are only be allocated from pages i.e., leaves
        synchronized (head) {
            int id = allocateNode(d);//找到一个可用的节点
            if (id < 0) {
                return id;
            }
            //把转化为Subpage的Page给记录下来
            final PoolSubpage<T>[] subpages = this.subpages;
            final int pageSize = this.pageSize;

            freeBytes -= pageSize;
            //pageId 到subpageId的转化， pageId=2048 subpageId = 0
            int subpageIdx = subpageIdx(id);
            PoolSubpage<T> subpage = subpages[subpageIdx];
            if (subpage == null) {
                //创建PoolSubPage，并切分为相同大小的子内存块，然后加入PoolArena对应的双向链表中
                subpage = new PoolSubpage<T>(head, this, id, runOffset(id), pageSize, normCapacity);
                subpages[subpageIdx] = subpage;
            } else {
                subpage.init(head, normCapacity);
            }
            return subpage.allocate();//执行内存分配并返回内存地址
        }
    }
```

如果我们分配20B大小的内存，20B属于tiny场景，按照内存规格的分类，20B需要向上取整到32B。在满二叉树中寻找可用的节点用于内存分配，假如2049节点时可用的，那么返回的ID=2049，然后将pageId转换成了subpageIdx， 2049对应1 ，如果PoolChunk中subpages数组的subpageIdx下标对应的PoolSubpage不存在，那么就新创建一个PoolSubpage，并将PoolSubpage切分为相同大小的子内存块，这边对应的子内存块是32B，然后找到PoolArena中tinySubpagePools数组对应的头节点，32B对应的tinySubpagePools[1]的head节点连接成双向链表，最后执行内存分配返回内存地址。

PoolSubpage 通过位图 bitmap 记录每个内存块是否已经被使用。在上述的示例中，8K/32B = 256，因为每个 long 有 64 位，所以需要 256/64 = 4 个 long 类型的即可描述全部的内存块分配状态，因此 bitmap 数组的长度为 4，从 bitmap[0] 开始记录，每分配一个内存块，就会移动到 bitmap[0] 中的下一个二进制位，直至 bitmap[0] 的所有二进制位都赋值为 1，然后继续分配 bitmap[1]



##### 分配内存小于8k，为了提高内存分配效率，由PoolThreadCache本地线程缓存提供的内存分配

假如我们现在需要分配 32B 大小的堆外内存，会从 MemoryRegionCache 数组 tinySubPageDirectCaches[1] 中取出对应的 MemoryRegionCache 节点，尝试从 MemoryRegionCache 的队列中取出可用的内存块。





# 内存回收

```java
// 见源码。PoolThreadCache  # allocate
```















