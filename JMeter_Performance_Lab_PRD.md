# JMeter Performance Lab — PRD / Codex 实施规格

> 版本：v1.0 · 目标：在 IntelliJ IDEA 中学习 JMeter、性能分析和 JVM GC 对比。  
> **范围限制：仅对本机启动的实验服务压测，不对外部系统发起负载。**

## 1. 项目目标

建立一个可独立运行的 Java 性能测试实验工程：

1. IDEA 导入 Gradle 工程后，可以一条命令启动 Spring Boot REST API。
2. 数据生成器接受 `--count` 参数，按需生成指定数量的可重复测试数据，**不在仓库中提交海量生成数据**。
3. 通过 Gradle 命令运行 JMeter 测试计划，支持并发线程数、Ramp-up、持续时间、目标 URL 等参数。
4. 输出 JMeter JTL 原始结果、HTML Dashboard，并能观察吞吐量、平均延迟、P95、P99、错误率。
5. 通过可切换 JVM 参数比较 G1 GC 与 ZGC，学习如何关联 GC 日志与请求延迟。
6. 提供正常、慢 SQL、CPU 密集、内存分配四类实验接口，便于观察瓶颈。

**成功标准：** 在新机器上按照 README 完成 `启动数据库 → 启动服务 → 生成数据 → 执行 JMeter → 查看报告`，不需手工修改代码；默认配置在普通开发笔记本上可以运行。

## 2. 技术栈

| 组件 | 选择 | 备注 |
|---|---|---|
| JDK | Java 17 | 保持与常见企业 Java 环境一致 |
| 构建 | Gradle Wrapper，Groovy DSL | 所有命令通过 `./gradlew` 执行 |
| Web | Spring Boot 3.x（选择兼容 Java 17 的稳定版本） | REST API |
| 数据库 | PostgreSQL 16，Docker Compose | 本地数据库 |
| 数据访问 | Spring Data JDBC 或 JdbcTemplate | 优先透明可理解的 SQL；不要引入复杂 ORM |
| Schema | Flyway | 自动建表、索引与版本管理 |
| 压测 | Apache JMeter（通过 Gradle 任务下载/安装或受控调用） | 固定一个已验证的稳定版本；JMeter 在非 GUI 模式执行 |
| 数据生成 | Java CLI（同一 Gradle 工程内） | `--count`、`--batch-size`、`--seed` 等参数 |
| 测试 | JUnit 5、Spring Boot Test | 单元与集成测试 |
| 监控 | Spring Boot Actuator、Micrometer | `/actuator/health`、`/actuator/metrics` |

**版本策略：** Codex 实现时选用彼此兼容、仍可获取的稳定版本，明确锁定版本并写入 README；不得使用动态版本（如 `latest.release`）。不要把实验结果或 GC 差异当作生产环境结论。

## 3. 推荐项目结构

```text
jmeter-performance-lab/
├── README.md
├── PRD.md
├── settings.gradle
├── build.gradle
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── docker-compose.yml
├── .gitignore
├── src/main/java/com/example/perflab/
│   ├── PerformanceLabApplication.java
│   ├── api/                  # REST controllers
│   ├── service/              # 业务及模拟瓶颈
│   ├── repository/           # SQL 操作
│   ├── model/                # DTO / 数据模型
│   └── generator/            # 测试数据生成 CLI
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/         # Flyway SQL
├── src/test/java/
├── jmeter/
│   ├── plans/
│   │   ├── smoke.jmx
│   │   ├── read-load.jmx
│   │   ├── mixed-load.jmx
│   │   └── bottleneck.jmx
│   └── README.md
├── scripts/                  # 必要时用于跨平台包装
└── build/                    # 测试结果和报告，gitignored
```

## 4. API 设计

所有 API 均只绑定本机开发环境；默认服务端口 `8080`。

| 方法 | 路径 | 用途 | 预期 |
|---|---|---|---|
| GET | `/api/products/{id}` | 单条记录查询 | 200 或 404 |
| GET | `/api/products?page=0&size=20` | 分页查询 | 200；分页参数限制 |
| GET | `/api/products/search?keyword=...` | 关键词查询 | 200；演示索引作用 |
| POST | `/api/orders` | 创建测试订单 | 201；验证请求体 |
| GET | `/api/orders/{id}` | 查询订单 | 200 或 404 |
| GET | `/api/lab/cpu?iterations=...` | CPU 计算实验 | 参数设安全上限 |
| GET | `/api/lab/alloc?kb=...` | 内存分配实验 | 单次大小设安全上限 |
| GET | `/api/lab/slow-query?mode=...` | 慢 SQL 实验 | 使用受控查询模式，不拼接用户 SQL |
| GET | `/actuator/health` | 服务就绪检查 | 200 |

要求：
- 商品表至少包含 `id`、`sku`、`name`、`category`、`price`、`created_at`；订单表至少包含 `id`、`product_id`、`quantity`、`created_at`。
- 为正常查询建立合理索引，慢查询实验可使用独立的明确开关或专用查询，不能默认影响正常接口。
- 数据生成及压测不得在普通开发配置中无限制占用 CPU、内存或磁盘。
- 对所有输入做合法性检查，返回清晰错误码；不要把异常堆栈直接返回给客户端。

## 5. 数据生成工具（核心需求）

**必须实现 Java CLI，数量由用户指定。** 推荐 Gradle 任务：

```bash
./gradlew generateData -Pcount=10000
./gradlew generateData -Pcount=100000 -PbatchSize=1000 -Pseed=42
./gradlew generateData -Pcount=1000 -Ptruncate=true
```

实现要求：
- `count` 必填，正整数；设置合理上限（默认最多 1,000,000，可配置），避免误操作。
- `batchSize` 默认 1000，批量插入 PostgreSQL；避免逐条插入及把全部对象放进内存。
- `seed` 可选；相同 seed、相同初始数据库状态下生成相同字段数据。
- `truncate` 默认 `false`；为 `true` 时需显式请求，清空实验数据后重新生成；仅允许本地实验数据库。
- 使用可读、合理的数据分布：商品类别、价格区间、名称、SKU；SKU 保证唯一，重复执行默认追加时不冲突。
- 生成器输出：目标条数、成功插入条数、耗时、吞吐量、失败原因。
- 对于大数据量使用 JDBC Batch；失败时明确报告，不悄悄吞异常。
- 生成数据与 JMeter 请求数据分开：JMeter 可通过查询预先存在的 ID 范围或使用独立小型 CSV 输入，避免要求把 100 万行全部导入 JMeter 内存。

## 6. JMeter 测试计划

所有 `.jmx` 文件都应能在 JMeter GUI 中打开查看、编辑，但**实际负载测试默认使用非 GUI 模式**。

### 6.1 smoke.jmx
- 1 个线程、短时间运行。
- 验证 `/actuator/health` 和基本 GET API。
- 响应码断言、基本 JSON 字段断言。

### 6.2 read-load.jmx
- 重点测试商品详情、分页查询。
- 参数化线程数、Ramp-up、持续时间、host、port。
- 用可配置 ID 范围选择已生成的商品；应避免大量 404 影响结果。

### 6.3 mixed-load.jmx
- 读写混合：商品读取 + 创建订单 + 查询订单。
- 使用 JSON Extractor 提取创建订单返回的 ID，并在后续请求中引用。
- 提供合理的读写比例（例如 80/20），并在文档中说明。

### 6.4 bottleneck.jmx
- 分别运行 CPU、内存分配、慢 SQL 场景。
- 默认参数必须保守；通过显式参数提高实验负载。
- 明确标注“实验接口，不代表真实业务性能”。

### 6.5 JMeter 通用规范
- 使用 Thread Group、HTTP Request Defaults、HTTP Header Manager、Response Assertion、Summary/Aggregate 结果配置等标准组件。
- 不能只检查 HTTP 200；订单请求应验证关键 JSON 内容。
- 每个场景有适度的 warm-up，正式报告应说明 warm-up 是否计入统计。
- 通过 `-J` 或 Gradle 属性传入参数，不直接改 `.jmx` 才能切换线程数。
- 默认线程数 10、Ramp-up 10 秒、持续时间 60 秒；限制误用极端并发参数。
- 不启用 GUI 监听器收集大量响应体；避免压测机自身成为瓶颈。

## 7. Gradle 任务和 CLI 体验

以下命令必须可用（实现时可定义自定义 Gradle Task）：

```bash
# 启动依赖
 docker compose up -d

# 启动 API
./gradlew bootRun

# 生成测试数据（另开 IDEA Terminal）
./gradlew generateData -Pcount=10000

# 烟雾测试
./gradlew jmeterSmoke

# 读取压测
./gradlew jmeterRead -Pthreads=20 -PrampUp=10 -Pduration=60

# 混合压测
./gradlew jmeterMixed -Pthreads=20 -Pduration=60

# 瓶颈实验
./gradlew jmeterBottleneck -Pscenario=cpu -Pthreads=5

# 单元测试
./gradlew test
```

JMeter 任务需要：
- 自动获取固定版本的 JMeter，或提供一次性 `setupJmeter` 任务；不得依赖用户事先手工安装全局 JMeter。
- 使用 `jmeter -n -t <plan> -l <jtl> -e -o <reportDir>` 的非 GUI 模式。
- 每次执行创建独立结果目录，如 `build/jmeter-results/read-<timestamp>/`，避免 HTML 报告目录非空导致失败。
- 记录本次执行的线程数、持续时间、JDK 版本、GC 参数、JMeter 版本、生成数据量等元数据。
- 当断言失败、JMeter 执行错误或错误率超过配置阈值时，任务应以非零退出码结束；注意 JMeter 的进程退出码本身不一定反映业务断言失败，需解析结果并执行阈值检查。
- HTML Dashboard 中可查看吞吐量、响应时间分位数和错误率；README 说明报告位置。

## 8. JVM GC 实验

必须提供 G1 GC 和 ZGC 的可重复启动方式，例如通过环境变量 `JAVA_TOOL_OPTIONS` 或明确的 Gradle 参数注入到 `bootRun` 子进程：

```bash
# 示例：在不同终端分别启动，不要同时启动两个占用 8080 的实例
JAVA_TOOL_OPTIONS='-Xms512m -Xmx512m -XX:+UseG1GC -Xlog:gc*:file=build/logs/g1-gc.log:time,uptime,level,tags' ./gradlew bootRun

JAVA_TOOL_OPTIONS='-Xms512m -Xmx512m -XX:+UseZGC -Xlog:gc*:file=build/logs/zgc-gc.log:time,uptime,level,tags' ./gradlew bootRun
```

Codex 应实现更方便的 `runG1` / `runZGC` 任务或 `scripts/run-g1.sh` / `scripts/run-zgc.sh`，并验证 **GC 选项确实作用于 Spring Boot JVM，而非仅 Gradle Daemon**。日志目录须自动创建。Java 17 的 ZGC 具体支持及 JVM 参数以实际发行版验证为准。

对比实验应固定：数据量、线程数、Ramp-up、时长、JDK、Heap Size、机器环境。收集：P95、P99、TPS、错误率、CPU、内存、GC 暂停时间。README 必须说明：**ZGC 不保证整体性能更高；如果瓶颈在 SQL 或 CPU，换 GC 未必改善 P99。**

## 9. 文档要求

`README.md` 必须用中文写明：
1. 前置环境：JDK 17、Docker、IDEA；Gradle 通过 Wrapper。
2. IDEA 导入、终端运行、Gradle 面板运行的步骤。
3. 从零开始的完整命令序列，包括两个终端分别启动服务和运行压测。
4. 如何指定数据条数、并发数、Ramp-up、测试时长。
5. 如何查看 HTML 报告、JTL、GC 日志；如何理解 TPS、P95、P99 和错误率。
6. G1 vs ZGC 的公平对比步骤及限制。
7. 常见故障：数据库未启动、端口占用、JMeter 下载失败、没有测试数据、报告目录冲突。
8. 停止服务、清理实验数据及 Docker Volume 的安全步骤。

## 10. 非功能与安全要求

- 默认监听 `localhost`，数据库端口仅暴露到本机。
- 默认只允许压测 `127.0.0.1` / `localhost`；如果指定其他地址，直接拒绝并提示仅供本地实验。
- 不在 Git 中提交数据库密码、生成的百万条数据、JTL、HTML 报告、GC 日志或下载的 JMeter 包。
- 使用 `.gitignore` 忽略 `build/`、IDEA 缓存、环境文件等。
- 尽量兼容 macOS/Linux，Windows 通过 `gradlew.bat`；Shell 包装脚本不是唯一运行入口。
- 测试数据仅使用虚构内容，不使用真实个人信息。
- 保持代码简单、可读，避免过度抽象和微服务化。

## 11. 验收测试

- [ ] `./gradlew test` 通过。
- [ ] `docker compose up -d` 后服务可以启动，健康检查成功。
- [ ] `generateData -Pcount=100` 后数据库新增 100 条商品数据。
- [ ] `generateData -Pcount=1000` 可再次追加，SKU 不冲突。
- [ ] `-Pseed=42` 在重置数据库后可重复生成相同测试字段。
- [ ] `jmeterSmoke` 成功，且实际执行了响应断言。
- [ ] `jmeterRead -Pthreads=10 -Pduration=30` 输出有效 JTL 和 HTML 报告。
- [ ] `jmeterMixed` 能创建订单、提取 ID 并查询该订单。
- [ ] 修改线程数和持续时间无需编辑 `.jmx`。
- [ ] G1 和 ZGC 模式都能启动，日志中能确认实际使用的 GC。
- [ ] 所有实验都不会默认访问外部目标或生成超量数据。
- [ ] README 中的所有命令已实际执行验证；无法执行的项目明确标记，不能声称已验证。

## 12. Codex 执行指令（直接复制给 Codex）

```text
请阅读当前目录的 JMeter_Performance_Lab_PRD.md，按照 PRD 创建完整可运行的 Gradle Java 17 + Spring Boot + PostgreSQL + JMeter 性能测试实验项目。

要求：
1. 先检查当前目录文件，不覆盖已有用户文件；若非空且存在冲突，先说明。
2. 先列出简短实施计划，再实际创建全部源代码、Gradle Wrapper、Docker Compose、Flyway、JMeter .jmx、数据生成 CLI 和 README。
3. 优先实现可运行的最小闭环：启动服务 → 生成 100 条数据 → JMeter smoke → JTL/HTML 报告，然后扩展其他场景。
4. 不要只写伪代码或 TODO；所有任务必须有真实实现。
5. 运行 Gradle 测试和可运行的验收命令，修复发现的问题；若网络或 Docker 不可用，明确报告哪些项目未验证。
6. 固定依赖版本，确保 Java 17 兼容；重点验证 JMeter 的 Gradle 集成、报告生成、断言失败判定和 JVM GC 参数传递。
7. 只允许对本机实验服务压测，默认参数保持低风险。
8. 完成后给出实际目录结构、运行命令、验证结果和已知限制。
```

## 13. 推荐学习顺序

第一轮：生成 1 万条数据，10 线程执行 `read-load`，熟悉 HTML 报告。  
第二轮：增加到 50 / 100 线程，观察吞吐量、P95、P99 是否恶化。  
第三轮：对比有索引与低效查询，定位数据库瓶颈。  
第四轮：使用内存分配场景，比较 G1 与 ZGC 的 GC 日志和延迟。  
第五轮：总结一次完整的「测量 → 定位 → 改善 → 复测」案例，作为日语面试素材。
