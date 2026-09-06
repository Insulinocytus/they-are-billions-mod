# 自动化测试

## 持续集成

GitHub Actions 使用 Java 21 构建 common、Fabric 与 NeoForge，运行单元测试与两个加载器的 GameTest，并确认两个加载器的发布 JAR 均能产出。

性能基准不在共享 CI 中判定通过或失败；它使用固定硬件和 `docs/performance.md` 中的可复现世界手动运行。`generatePerfBaselines`、`runIdleServerBaseline` 和 `runIdleClientBaseline` 只在开发者机器上执行。CI 仍运行场景文件与 MSPT/P95 计算的单元测试。

## 单元测试

首版使用 JUnit 测试不依赖运行中 Minecraft 世界的纯逻辑：

- 玩家连通分组与预算转移
- 夜间线性数量目标
- 性能调节器升降档
- 路线缓存失效与接入点选择
- 挖掘参与者、全局名额与两秒进度清零
- 区块票据引用计数

## GameTest

Fabric 与 NeoForge 各运行最小平台 GameTest，验证尸潮生成及标记、FakePlayer 破坏事件、白天清理、命名脱离和区块票据申请释放。本地可用 `:fabric:runGametest` 与 `:neoforge:runGameTestServer`。
