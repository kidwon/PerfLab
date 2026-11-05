# JMeter Performance Lab

本机 Java 性能实验项目：Spring Boot API → PostgreSQL 数据生成 → JMeter 非 GUI 压测 → HTML/JTL → G1 与 ZGC 对比。只压测 `127.0.0.1` / `localhost`。

## 环境与固定版本

- JDK **17**，建议在 IDEA 的 Project SDK 和 Gradle JVM 中都选 17。
- Gradle Wrapper **8.13**（已包含脚本与 Wrapper JAR，无需全局 Gradle）。
- Spring Boot **3.5.7**、dependency-management 插件 **1.1.7**；Spring/JDBC/Flyway/PostgreSQL JDBC/JUnit 版本由该固定 Boot BOM 管理。
- PostgreSQL **16.11**，Docker Desktop / Docker Engine + Compose。
- Apache JMeter **5.6.3**，首次任务自动从 Apache Archive 下载并校验 SHA512，保存在 `build/tools/`。

版本参考：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Apache JMeter 下载与校验](https://jmeter.apache.org/download_jmeter.cgi)。首次构建需要网络访问 Gradle、Maven Central、Gradle Plugin Portal 和 Apache Archive。

## 从零启动

IDEA：File → Open → 选择本目录 `settings.gradle`（或项目目录）→ 作为 Gradle 工程导入。若此前以普通 Java 工程打开，可右键 `build.gradle` → Link Gradle Project。Gradle 设置使用 Wrapper 与 JDK 17。右侧 Gradle 面板可运行 `application/bootRun`、`application/runG1`、`application/runZGC`、`verification/test` 和 JMeter 任务；带 `-P` 参数的命令建议在 Terminal 或 Gradle Run Configuration 中运行。

macOS / Linux 终端 A：

```bash
# JAVA_HOME 必须指向本机实际安装的 JDK 17，以下仅为 Homebrew macOS 示例
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./gradlew --version
docker compose up -d
# 查看数据库 ready 状态后启动服务
docker compose ps
./gradlew bootRun
```

终端 B（同样配置 JDK 17）：

```bash
curl --fail http://127.0.0.1:8080/actuator/health
./gradlew generateData -Pcount=100
./gradlew jmeterSmoke
./gradlew jmeterRead -Pthreads=10 -PrampUp=10 -Pduration=30
./gradlew jmeterMixed -Pthreads=10 -Pduration=30
./gradlew jmeterBottleneck -Pscenario=cpu -Pthreads=5 -Pduration=30
./gradlew test
```

Windows PowerShell 使用 `$env:JAVA_HOME='C:\实际路径\jdk-17'`，把命令中的 `./gradlew` 换为 `.\gradlew.bat`；其余 Gradle 属性相同，不依赖 Shell 包装脚本。

数据库与 API 分别只发布在本机 `5432` 和 `8080`。数据库名和用户为 `perflab`。Compose 使用**本机专用的 trust 认证**，没有提交密码；本机其他程序也能连接该库，仅适用于实验。若自行启用密码认证，使用 `DB_PASSWORD` 环境变量，勿提交真实凭据。数据库数据在 Docker Volume 内，不进 Git。

## 数据生成

```bash
./gradlew generateData -Pcount=10000
./gradlew generateData -Pcount=100000 -PbatchSize=1000 -Pseed=42
# 显式清空订单和商品，再生成：会删除本机 perflab 实验数据
./gradlew generateData -Pcount=1000 -Ptruncate=true -Pseed=42
```

`count` 必填，默认上限 1,000,000；确需改变可显式 `-PmaxCount=2000000`。批次 1..10,000，默认 1,000；seed 默认 42。Java CLI 参数为 `--count=100 --batchSize=1000 --seed=42 --truncate=false`，Gradle 负责映射参数和配置 JDBC 驱动。

生成器仅连接固定的 `jdbc:postgresql://127.0.0.1:5432/perflab`。在一个事务中分批插入，不在内存保存全量数据，失败全部回滚；输出目标、成功条数、耗时、rows/s 或失败原因。事务期间锁定两张实验表，因此应在压测**之前**生成数据。显式 ID 与 `LAB-<id>` SKU 使重复执行默认追加不冲突；重置后的同一 seed 产生相同商品字段和固定范围内的创建时间。首次启动 API 会自动执行 Flyway 建表，之后再生成数据。

## 接口与实验

| 接口 | 示例 / 限制 |
|---|---|
| 商品详情 | `GET /api/products/1`，不存在返回 404 |
| 商品分页 | `GET /api/products?page=0&size=20`，size 1..100 |
| 商品前缀搜索 | `GET /api/products/search?keyword=Product-1`，非空且最多 80 字符；转义 LIKE 通配符 |
| 创建订单 | `POST /api/orders`，JSON `{"productId":1,"quantity":2}`；quantity 1..100；返回 201 和 Location |
| 查询订单 | `GET /api/orders/1` |
| CPU 实验 | `GET /api/lab/cpu?iterations=10000`，最大 5,000,000 |
| 分配实验 | `GET /api/lab/alloc?kb=64`，最大 4,096 KB；实际写入并读取数组，结果返回校验和 |
| SQL 实验 | `GET /api/lab/slow-query?mode=indexed`，仅允许 indexed / scan / sleep |
| JVM 与数据元数据 | `GET /api/lab/runtime`，含实际 JVM 参数、GC 名称、堆上限、商品数 |
| 健康与指标 | `/actuator/health`、`/actuator/metrics`、`/actuator/metrics/http.server.requests` |

`indexed` 为带 `text_pattern_ops` 索引的名称前缀查询；`scan` 为函数 + 前导通配符查询，演示低效访问模式；`sleep` 为固定 `pg_sleep(0.1)`。它们使用独立的固定 SQL，不接受用户 SQL。小数据集上 PostgreSQL 可能主动选择顺序扫描；实际计划由优化器决定，不承诺 indexed 模式始终更快。Hikari 连接有 3 秒 SQL 超时。

## JMeter 参数与报告

```bash
./gradlew setupJmeter
./gradlew jmeterRead -Pthreads=20 -PrampUp=10 -Pduration=60 -PminId=1 -PmaxId=1000
./gradlew jmeterMixed -Pthreads=20 -Pduration=60
./gradlew jmeterBottleneck -Pscenario=alloc -Pkb=256 -Pthreads=5
./gradlew jmeterBottleneck -Pscenario=slow-query -Pmode=scan -Pthreads=5
```

商品 ID 范围必须真实存在且连续。默认 1..100，适合最小闭环；生成 10,000 条后可用 `-PmaxId=10000`，不需导入百万行 CSV。任务预检服务健康、数据数量和范围端点；中间 ID 有空洞仍会产生 404，由断言和错误率检测发现。

| 属性 | 默认 | 范围 / 说明 |
|---|---|---|
| host / port | 127.0.0.1 / 8080 | host 只允许 localhost、127.0.0.1；port 1024..65535 |
| threads | 10（smoke 为 1） | 1..100 |
| rampUp | 10 秒（smoke 为 1） | 1..120 |
| duration | 60 秒（smoke 为 5） | 1..600，不含独立预热 |
| warmUp | 3 秒 | 0..60；预热 1 线程，不写入正式 JTL |
| minId / maxId | 1 / 100 | 正整数，minId ≤ maxId |
| scenario | cpu | cpu / alloc / slow-query |
| iterations / kb | 10000 / 64 | 分别最大 5000000 / 4096 |
| mode | indexed | indexed / scan / sleep |
| maxErrorRate | 0 | 0..1，错误率超过阈值则失败；例如 0.01 为 1% |

每个线程请求前有 100ms 思考时间。`mixed-load` 每轮 4 次商品读 + 1 次创建订单 + 1 次订单查询：商品读与订单事务比例为 80/20，按 HTTP 请求计算为 5 读 / 1 写（约 83/17）。订单查询保留创建时商品 ID，并核对提取的订单 ID。所有请求验证响应码及 JSON 内容。实验接口不代表真实业务性能。

任务实际使用 `java -jar ApacheJMeter.jar -n -t ... -l ... -e -o ...`。下载包、测试日志与报告全部在 `build/`，每次独立目录：

```text
build/jmeter-results/<plan>-<timestamp>-<unique>/
├── metadata.json   # 参数、实际服务 JVM/GC、商品条数、版本、开始时间
├── results.jtl     # CSV 原始样本和断言结果
├── summary.json    # 样本数、错误数、错误率
├── jmeter.log
├── warmup.log
└── html/index.html # 浏览器打开
```

Gradle 会解析 JTL 的 `success` 列（使用标准 CSV 解析器处理带逗号/换行的错误），零样本、进程失败、任何响应断言失败或超过错误率阈值均导致任务失败。默认阈值 0；即使显式放宽错误率阈值，响应断言失败也不会被豁免。不要仅凭 JMeter 的进程退出码判断 API 是否正确。

HTML 的 Statistics / Response Times 显示吞吐量、平均延迟、P95、P99、错误率；P95 表示 95% 的请求耗时不超过此值，P99 同理。JMeter Throughput 为请求/秒；混合场景 TPS 若指业务订单事务，需要看创建订单标签，不能把所有请求吞吐量直接当成订单 TPS。冷启动、预热时间、样本量和错误都影响结果；本项目预热不计入正式统计。默认内置 summariser 在控制台输出汇总，不收集大量响应体。

`.jmx` 可用下载后的 `build/tools/apache-jmeter-5.6.3/bin/jmeter`（Windows 为 `.bat`）在 GUI 中打开。禁用的 Summary Report 仅供小规模 GUI 调试时开启，正式负载使用 Gradle 非 GUI 任务。计划中的请求前置处理器也会检查实际目标域名，拒绝远程目标并停止测试；GUI 调试也只能填写本机地址。

## G1 / ZGC 公平对比

先停止当前服务（Ctrl+C），每次只启动一个 8080 实例：

```bash
# 终端 A
./gradlew runG1
# 终端 B，确认 collectors 与 jvmArguments，GC 选项应出现在服务 JVM
curl --fail http://127.0.0.1:8080/api/lab/runtime
./gradlew jmeterBottleneck -Pscenario=alloc -Pkb=256 -Pthreads=10 -PrampUp=10 -Pduration=60
# 终端 A Ctrl+C 停止后，再运行
./gradlew runZGC
# 终端 B 再检查 runtime，执行完全相同负载
curl --fail http://127.0.0.1:8080/api/lab/runtime
./gradlew jmeterBottleneck -Pscenario=alloc -Pkb=256 -Pthreads=10 -PrampUp=10 -Pduration=60
```

两任务都直接为 BootRun 的 Java 17 子进程传入 `-Xms512m -Xmx512m` 和对应 GC 参数，自动创建日志目录；不依赖 Gradle Daemon 的 GC。日志分别为 `build/logs/g1-gc.log`、`build/logs/zgc-gc.log`，重复运行会轮转，保存对比前及时复制到独立目录。

固定数据量、JDK 发行版、堆大小、线程数、Ramp-up、预热、时长、机器电源模式与后台程序。多次轮换运行，记录 P95/P99、请求吞吐量/订单 TPS、错误率、GC pause；用 Activity Monitor / `top` / `jcmd <服务PID> GC.heap_info` 同时记录 CPU、RSS/堆使用，按日志时间与 JTL 时间关联延迟尖峰。JMeter 与数据库也在本机，会争用 CPU/内存，因此结果只用于学习。

**ZGC 不保证整体性能更高；如果瓶颈在 SQL 或 CPU，换 GC 未必改善 P99。** Java 17 ZGC 的可用性取决于实际 JDK/系统。不要同时设置冲突的 `JAVA_TOOL_OPTIONS` GC 参数；环境变量也可能作用于 Gradle 本身，推荐使用本项目任务。

## 测试与清理

```bash
# 默认单元 / Web MVC 测试，不需要数据库
./gradlew test
# 显式数据库集成测试：会清空本机 perflab 的订单和商品；先启动 Compose
PERFLAB_INTEGRATION=true ./gradlew test --rerun-tasks
# PowerShell: $env:PERFLAB_INTEGRATION='true'; .\gradlew.bat test --rerun-tasks
```

集成测试覆盖 Flyway 启动、生成 100 条、追加 1000 条、SKU 唯一性、重置后 seed 一致、真实商品与订单访问。运行时停止压测，避免重置数据影响其他实验。

服务终端 Ctrl+C 停止；`docker compose stop` 停数据库并保留数据；`docker compose down` 删除容器和网络并保留 Volume。只清空数据可显式 `generateData -Pcount=100 -Ptruncate=true`；完全重置数据库使用 `docker compose down -v`，**会永久删除此 Compose 项目的数据库 Volume**。`./gradlew clean` 清理下载工具、报告与 GC 日志，先备份需要保留的结果。

## 常见故障

- 数据库未启动 / 连接拒绝：`docker compose ps`、`docker compose logs postgres`；等健康后再 `bootRun`。Docker 权限错误需在可访问 Docker Desktop 的本机终端运行。
- 8080 / 5432 被占用：停止已有进程或容器。API 自定义端口可用 `./gradlew bootRun --args='--server.port=8081'`，JMeter 配合 `-Pport=8081`；生成器的实验数据库地址固定，不随端口切换。
- Java 版本错误：检查 `java -version`、`JAVA_HOME` 和 IDEA Gradle JVM，均使用 17。
- 下载失败：检查网络、DNS、企业代理和 Apache Archive；修复后重试 `./gradlew setupJmeter --rerun-tasks`。Gradle Wrapper 首次也需要联网。
- 无数据 / 404：先启动 API 迁移 Schema，再生成至少 100 条；核对 `minId/maxId`，默认追加不会自动重置已有 ID。
- 报告冲突：任务使用毫秒时间加随机标识创建独立目录。手动运行 JMeter 时 `-o` 必须为不存在或空目录；不要复用已有 HTML 目录。
- 断言 / 错误率失败：检查该次 `results.jtl`、`summary.json`、`jmeter.log` 与服务日志，不能靠提高阈值掩盖错误。
- 慢 SQL / GC 变化不明显：增加数据量前确认机器资源；每次只调整一个变量，检查真实 SQL 计划和服务实际 collector。

## 实现与验证状态

```text
build.gradle / settings.gradle / gradlew / gradlew.bat / gradle/wrapper/
gradle/jmeter.gradle                  # 下载、参数限制、执行、元数据、JTL 阈值
src/main/java/com/example/perflab/
  PerformanceLabApplication.java
  api/                               # REST、输入校验、错误映射
  repository/                        # JdbcTemplate 固定 SQL
  service/                           # CPU / 分配实验
  generator/                         # Java 批量数据生成 CLI
src/main/resources/application.yml
src/main/resources/db/migration/V1__schema.sql
src/test/java/com/example/perflab/    # 单元、MVC、显式 PostgreSQL 集成测试
jmeter/plans/                        # smoke / read-load / mixed-load / bottleneck
jmeter/README.md
docker-compose.yml
```

本次生成环境存在网络与 socket 沙箱限制，**尚未完成端到端验收，不能声称全部命令已通过**：

- 已检查：Compose 配置解析、Gradle Groovy 语法、4 个 JMX XML 结构；Wrapper JAR 来自本机 Gradle 8.13 自带的标准嵌入资源。
- 已实际运行：Java 17 的 G1/ZGC JVM 参数和日志写入；生成器 Java 编译与非法 count 拒绝。
- `./gradlew test` 尝试失败：网络 DNS 无法解析 services.gradle.org；使用本机缓存 Gradle 重试仍因沙箱禁止本机 socket 无法启动。
- `docker info` 被 Docker socket 权限拒绝；数据库启动、Flyway、生成/追加、API 健康、完整 MVC/集成测试、JMeter 下载/执行、JTL/HTML、BootRun 实际 GC 传递均**未验证**。请在正常本机终端按上述命令验收。
- 可用缓存依赖的额外编译/单元检查结果见 `VALIDATION.md`，不等同于固定依赖的 Gradle 构建通过。
