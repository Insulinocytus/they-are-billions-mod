# 自动化测试

## 持续集成

GitHub Actions 对 Ready PR 的最新提交运行三个并行检查：

- **Unit Tests**：验证不依赖运行中 Minecraft 世界的通用逻辑。
- **Fabric Validation**：依次执行 Fabric Platform Build 与 JAR Packaging Checks、Server Integration Tests 和 Client Startup Smoke Tests。
- **NeoForge Validation**：依次执行 NeoForge Platform Build 与 JAR Packaging Checks、Server Integration Tests 和 Client Startup Smoke Tests。

Draft PR 不运行检查；Ready PR 后续每次 push 会重新验证最新提交。也可以通过 `workflow_dispatch` 手动运行完整验证。合并到 `main` 后不重复测试、不构建或上传 artifact；未来需要发布时再增加 GitHub Release workflow。

三个检查均使用 Gradle 依赖缓存。Unit Tests 最多运行 10 分钟；每个平台的 Validation 最多运行 15 分钟，其中 Client Startup Smoke Tests 最多运行 3 分钟。Fabric 与 NeoForge 即使其中一个失败，另一个也会继续完成。

性能基准不在共享 CI 中判定通过或失败；它使用固定硬件和 `docs/performance.md` 中的可复现世界手动运行。`generatePerfBaselines`、`runIdleServerBaseline` 和 `runIdleClientBaseline` 只在开发者机器上执行。Unit Tests 仍验证场景文件与 MSPT/P95 计算。

## Unit Tests

当前使用 JUnit 验证模组版本握手、性能工具的设置/采样/场景文件/汇总计算、确定性尸潮规划，以及尸潮标记生命周期与原版生成接管范围。

## JAR Packaging Checks

Fabric 与 NeoForge 的 Platform Build 分别检查最终 JAR 是否存在，并确认其中包含对应加载器的模组元数据和 `LICENSE`。检查读取 runner 中的本地产物，不上传 artifact。

## Server Integration Tests

Fabric 与 NeoForge 分别运行 GameTest Server（`runGameTestServer`）。当前覆盖验证模组能生成带持久化尸潮标记的原版 Zombie，以及尸潮身份、命名脱离、死亡掉落和显式生成不被接管。

## Client Startup Smoke Tests

Fabric 与 NeoForge 分别启动客户端，确认日志报告模组已加载后结束进程。该检查只验证客户端入口、Mixin 和资源可以完成初始化，不验证 UI 或玩法行为。
