# 性能验收

## 环境

- Minecraft 1.21.1、Java 21
- Ryzen 5 5600、服务端内存 8 GB
- 模拟距离 8、视距 10、单名玩家
- 不安装额外性能模组
- Fabric 与 NeoForge 分别运行
- 每个场景连续运行十分钟

客户端基准使用 GTX 1660、16 GB 内存、1080p、视距 10、无光影。

## 目标

- 1000 只 Zombie：服务端 20 TPS，P95 MSPT 不超过 50 ms
- 同时可见约 300 只 Zombie：客户端平均 60 FPS，P95 帧时间不超过 25 ms

区块票据没有数量硬上限，因此服务端指标是必须持续测试和优化的目标，而不是任意实体分布下的无条件保证。

Fabric 与 NeoForge 的两个场景都必须达到目标才发布 `0.1.0`。任一场景不达标时停止发布，并依据性能分析结果重新讨论票据上限或仿真降级。

## 场景

1. 开阔地密集推进：覆盖共享路线、独立寻路、密集推挤与客户端渲染。
2. 围墙与山体基地：覆盖路线失效、64 个活跃挖掘点、方块更新、掉落和权限事件。

每次记录平均与 P95 MSPT；客户端场景同时记录平均 FPS 与 P95 帧时间。

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

缩短本地冒烟（PowerShell 给带点号的 `-P` 参数加引号）：

```text
./gradlew runIdleServerBaseline "-Pperf.platform=fabric" "-Pperf.scenario=open-field" "-Pperf.warmupTicks=20" "-Pperf.durationTicks=100"
```

结果写到 `perf/results/<loader>-<scenario>-<mode>-<timestamp>/metrics.json`，并附带 Minecraft 原生 JFR `recording.jfr`。

服务端堆内存默认 8 GB，可用 `-Pperf.xmx=8G` 覆盖。

## 数据采集

- 服务端使用 Minecraft 原生 JFR（`JvmProfiler`），不安装 Spark 或其他性能分析 Mod。MSPT 平均与 P95 由模组在 `-Dtheyarebillions.perf.mode=idle` 时按 tick 采样写入 `metrics.json`。
- 客户端优先用 PresentMon 记录 `msBetweenPresents`：`perf/tools/capture-presentmon.ps1 -OutputCsv perf/results/frames.csv -DurationSeconds 600`。未安装 PresentMon 时，同一 idle 客户端运行会把平均 FPS 与 P95 帧时间写入 `metrics.json`。
- `/theyarebillions status` 记录测试时的尸潮成员、普通 Zombie、玩家群组、活动票据、共享路线、挖掘点和性能调节档位。
