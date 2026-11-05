# JMeter 计划

使用项目根目录 Gradle 任务运行；完整参数、报告和限制见 [主 README](../README.md)。

- `smoke.jmx`：健康检查及商品详情，响应码与 JSON 断言。
- `read-load.jmx`：商品详情和分页。
- `mixed-load.jmx`：4 次商品读 + 创建订单 + JSON ID 提取 + 查询订单，约 83% 读请求 / 17% 写请求。
- `bottleneck.jmx`：`scenario=cpu|alloc|slow-query` 条件选择实验接口，不代表真实业务性能。

Gradle 通过 `-J` 传入参数，以独立预热进程运行后再测量。JMeter 只使用随机 ID，不导入百万行请求数据。不要直接修改为外部 host。GUI 仅查看或小规模调试，Summary Report 默认禁用，不收集响应体；正式运行使用非 GUI 任务。

## 从数据库导出请求 CSV

先启动数据库并生成商品，然后运行：

```bash
./gradlew exportJmeterIds
# 最多导出 100 个已存在的 ID
./gradlew exportJmeterIds -Plimit=100
```

输出到 `build/jmeter-data/product-ids.csv`，包含表头 `productId,quantity`，quantity 固定为 1；默认最多 1000 行，上限 100000。数据库为空或导出失败时不会覆盖已有文件。数据库重置后请重新导出。

JMeter GUI 中在线程组下添加 `Config Element → CSV Data Set Config`：

- Filename：选择上述 CSV 的绝对路径。
- File encoding：`UTF-8`。
- Variable Names：留空，自动读取第一行表头。
- Delimiter：`,`。
- Recycle on EOF：`True`。
- Stop thread on EOF：`False`。
- Sharing mode：`All threads`。

本项目计划已有 `Choose product ID` 前置处理器，会随机选择并覆盖 `productId`。使用 CSV 时，需把该处理器最后的 `if (sampler.getName() != 'Read order') { ... }` 随机 ID 赋值块删除，保留前面的本机地址校验。该修改会影响所有使用此计划的运行，包括 Gradle 任务，建议另存为 `*-csv.jmx`。

商品路径使用 `/api/products/${productId}`；订单 JSON 可用 `{"productId":${productId},"quantity":${quantity}}`。保持 CSV 配置在线程组级别，混合计划中的内部读循环不会逐请求更换 CSV 行。
