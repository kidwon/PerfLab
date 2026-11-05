# 本次实现的验证记录

环境：macOS arm64，Homebrew OpenJDK 17.0.20.1。完整验收尚未通过，原因如下。

| 检查 | 结果 |
|---|---|
| `docker compose config --quiet` | 通过 |
| Groovy 3.0.22 解析 settings.gradle / build.gradle / gradle/jmeter.gradle | 语法通过；不等于 Gradle DSL 实际配置成功 |
| 4 个 JMX 的 XML、组件/hashTree 配对、内嵌 Groovy 脚本语法 | 通过；不等于 JMeter 实际加载执行成功 |
| Java 17 编译 DataGenerator | 通过 |
| Java 17 运行生成器 `--count=0` | 按预期拒绝，退出码 1，未连接数据库 |
| 用本机缓存的 Spring 6.2.1 / Jakarta 等 JAR 编译 API、Repository、Service、Generator | 通过；未包含 Boot 应用入口，不是项目固定依赖构建 |
| 用缓存 JUnit 5.11.4 运行 DataGeneratorTest、LabWorkTest | 3 个测试通过；覆盖边界参数、选项解析、可观察的计算与内存分配 |
| Java 17 独立 JVM `-XX:+UseG1GC` / `-XX:+UseZGC`，512 MB 堆及统一日志参数 | 通过；日志确认 Using G1 / Using The Z Garbage Collector；尚未验证 BootRun 实际启动 |
| `./gradlew test --no-daemon` | Wrapper 成功进入下载步骤，因 services.gradle.org DNS 失败而中止 |
| 使用本机缓存 Gradle 8.13 离线重试 test | Gradle 启动因沙箱禁止 socket 而中止（FileLockContentionHandler） |
| `docker info` | 无权访问 Docker Desktop socket |
| PostgreSQL / Flyway / 真实数据生成和追加 / 服务健康 / MVC 测试 / PostgreSQL 集成测试 | 未运行 |
| setupJmeter / 四个真实负载 / JTL 阈值判定 / HTML Dashboard / 服务实际 GC 参数 | 未运行 |

标准 Wrapper JAR 从本机 Gradle 8.13 发行包的 gradle-wrapper-main-8.13.jar 内嵌资源提取；启动脚本使用标准 Gradle Wrapper 脚本。远程下载因 DNS 限制失败，未以自制下载器替代 Wrapper。

请在可联网并有 Docker 权限的终端按 README 完成剩余验收。集成测试仅在显式 `PERFLAB_INTEGRATION=true` 时启用，会重置本机实验库。不要把上述局部检查描述成 `./gradlew test` 或端到端压测通过。
