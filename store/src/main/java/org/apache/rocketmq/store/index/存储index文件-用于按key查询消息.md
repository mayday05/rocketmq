## IndexFile作用：
用于为生成的索引文件提供访问服务，通过消息Key值查询消息真正的实体内容。在实际的物理存储上，文件名则是以创建时的时间戳命名的，固定的单个IndexFile文件大小约为400M，一个IndexFile可以保存 2000W个索引；

### 1、 IndexHead 数据： 
- beginTimestamp：
该索引文件包含消息的最小存储时间 
- endTimestamp：
该索引文件包含消息的最大存储时间
- beginPhyoffset：
该索引文件中包含消息的最小物理偏移量（commitlog 文件偏移量） 
- endPhyoffset：
该索引文件中包含消息的最大物理偏移量（commitlog 文件偏移量）
- hashSlotCount：
hashslot个数，并不是 hash 槽使用的个数，在这里意义不大，
- indexCount：
已使用的 Index 条目个数

### 2、 Hash 槽： 
一个 IndexFile默认包含500W个Hash槽，每个Hash槽存储的是落在该Hash槽的hashcode最新的Index的索引

### 3、 Index条目列表 
- hashcode：key 的 hashcode
- phyoffset：消息对应的物理偏移量 
- timedif：该消息存储时间与第一条消息的时间戳的差值，小于 0 表示该消息无效 
- preIndexNo：该条目的前一条记录的 Index 索引，hash 冲突时，根据该值构建链表结构