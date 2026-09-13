# 性能验收

本文中的数值是 `0.1.0` 在维护者参考环境上的发布门槛，只证明该环境能够运行固定场景。其他硬件、游戏设置或 Mod 组合上的性能均为 best effort，不保证 TPS、FPS 或实际维持的尸潮数量。

## 环境

- Minecraft 1.21.1、Java 21、Windows 11 Pro
- Intel Core i7-13700K、32 GB 物理内存、服务端堆内存 8 GB
- 模拟距离 8、视距 10、单名玩家
- 不安装额外性能模组
- Fabric 与 NeoForge 分别运行
- 每个场景连续运行十分钟

客户端基准使用 NVIDIA GeForce RTX 4080、1080p、视距 10、无光影；集成显卡不参与验收。

## 目标

- 1000 只 Zombie：服务端 20 TPS，P95 MSPT 不超过 50 ms
- 同时可见约 300 只 Zombie：客户端平均 60 FPS，P95 帧时间不超过 25 ms

区块票据没有数量硬上限，因此服务端指标是维护者参考环境上的固定场景目标，而不是任意实体分布或用户硬件上的无条件保证。其他环境中，性能调节器可以降低远处仿真精度并暂停补充；默认目标 1000 不保证实际同时维持 1000 只 Zombie。

Fabric 与 NeoForge 的两个场景都必须在维护者参考环境上达到目标才发布 `0.1.0`。任一场景不达标时停止发布，并依据性能分析结果重新讨论票据上限或仿真降级。

## 场景

1. 开阔地密集推进：覆盖共享路线、独立寻路、密集推挤与客户端渲染。
2. 围墙与山体基地：覆盖路线失效、64 个活跃挖掘点、方块更新、掉落和权限事件。

每次记录平均与 P95 MSPT；客户端场景同时记录平均 FPS 与 P95 帧时间。

Issue #16 永久记录测试提交、运行时版本、完整测试设置以及每个加载器和场景的指标摘要。JFR、帧时间原始数据、状态快照和完整 `perf/results/` 只在维护者本地保存，用于当次比较和瓶颈定位。

## 可复现世界

两个场景提交固定种子、结构文件和初始化数据，见 `perf/scenarios/`：

| 场景 | 目录 | 种子 | 结构 | 初始化 |
| --- | --- | ---: | --- | --- |
| 开阔地推进 | `perf/scenarios/open-field` | 20211001 | `origin.nbt` 与方向标记 | 超平坦平原、时间 18000、关闭自然刷新与昼夜 |
| 围墙山体基地 | `perf/scenarios/walled-mountain` | 20211001 | `walled_compound.nbt`（含 64 个黑曜石挖掘点）、`mountain.nbt` | 同上，出生点在围墙内 |

完整世界缓存不提交到 Git。开发者本地生成一次，之后每次测试都复制干净基线：

```text
./gradlew generatePerfBaselines
```

缓存目录是 Git 忽略的 `perf/cache/<loader>/<scenario>/world/`。结构 NBT 可用 `python perf/tools/write_structures.py` 重新生成。

## 空载基线

默认测量窗口为 20 秒预热 + 10 分钟（12000 tick），模拟距离 8、视距 10。不要把十分钟运行放进 CI。

```text
./gradlew runIdleServerBaseline
./gradlew runIdleClientBaseline -Pperf.platform=fabric -Pperf.scenario=open-field
```

客户端尸潮渲染优化可用 `-Pperf.clientRenderOptimization=true|false` 单独开关，便于在同一场景、同一硬件上做 A/B 帧时间对照。例如：

```text
./gradlew runIdleClientBaseline -Pperf.platform=fabric -Pperf.scenario=open-field -Pperf.clientRenderOptimization=false
./gradlew runIdleClientBaseline -Pperf.platform=fabric -Pperf.scenario=open-field -Pperf.clientRenderOptimization=true
```

`metrics.json` 会写入 `clientRenderOptimizationEnabled`，同时记录客户端平均 FPS 与 P95 帧时间，因此无需安装额外性能分析 Mod 即可比较该优化的影响。

缩短本地冒烟（PowerShell 给带点号的 `-P` 参数加引号）：

```text
./gradlew runIdleServerBaseline "-Pperf.platform=fabric" "-Pperf.scenario=open-field" "-Pperf.warmupTicks=20" "-Pperf.durationTicks=100"
```

结果写到 `perf/results/<loader>-<scenario>-<mode>-<timestamp>/metrics.json`，并附带 Minecraft 原生 JFR `recording.jfr`。

服务端堆内存默认 8 GB，可用 `-Pperf.xmx=8G` 覆盖；该参数会写成 JVM `-Xmx`。

`generatePerfBaselines` 与 `runIdleServerBaseline` 会按 Fabric → NeoForge 串行运行，避免并行占用 25565 和同一套硬件。Fabric 使用 25565，NeoForge 使用 25566。

## 数据采集

- 服务端使用 Minecraft 原生 JFR（`JvmProfiler`），不安装 Spark 或其他性能分析 Mod。MSPT 平均与 P95 由模组在 `-Dtheyarebillions.perf.mode=idle` 时按 tick 采样写入 `metrics.json`。
- 客户端优先用 PresentMon 记录 `msBetweenPresents`。Gradle JavaExec 客户端默认是 `java.exe`：`perf/tools/capture-presentmon.ps1 -OutputCsv perf/results/frames.csv -DurationSeconds 600`。也可 `-ProcessId <pid>`。脚本使用 `--terminate_after_timed`，到时后退出再汇总。未安装 PresentMon 时，同一 idle 客户端运行会把平均 FPS 与 P95 帧时间写入 `metrics.json`。
- `/theyarebillions status` 记录测试时的尸潮成员、普通 Zombie、玩家群组、活动票据、共享路线、挖掘点和性能调节档位。
