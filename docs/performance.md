# 性能验收

本文中的数值是 `0.1.0` 在维护者参考环境上的发布门槛，只证明该环境能够运行固定场景。其他硬件、游戏设置或 Mod 组合上的性能均为 best effort，不保证 TPS、FPS 或实际维持的尸潮数量。

## 环境

- Minecraft 1.21.1、Java 21、Windows 11 Pro
- Intel Core i7-13700K、32 GB 物理内存、服务端堆内存 8 GB
- 模拟距离 8、视距 10、单名玩家
- 不安装额外性能模组
- Fabric 与 NeoForge 分别运行

客户端使用 NVIDIA GeForce RTX 4080、1080p、视距 10、无光影；集成显卡不参与验收。

## 发布门槛

### 尸潮形成

Fabric 与 NeoForge 都在开阔地和围墙山体场景中从游戏时间 13000、无尸潮成员开始，使用真实自然种群和真实性能调节器。到 18000 时必须达到 1000 只尸潮成员；其他普通 Zombie 只作为附加负载记录，不能用于凑足目标。

### 稳定服务器负载

相同四个组合从 1000 只尸潮成员开始，预热 20 秒后测量 12000 Tick。每场必须同时满足：

- 十分钟平均尸潮规模至少 990；
- 任一 200-Tick 窗口平均尸潮规模至少 950；
- 结束时尸潮规模至少 990；
- 墙钟平均 TPS 至少 19.9；
- P95 MSPT 不超过 50 ms。

成员可以因原版实体挤压或其他正常原因死亡并得到补充。验收的是稳定尸潮负载，不要求同一批实体始终存活。性能档位只记录、不单独判失败；档位导致数量门槛失败时仍然阻止发布。

### 客户端可见负载

Fabric 与 NeoForge 各运行一次开阔地场景。GameRule 目标设为 300，固定玩家位置、朝向和尸潮分布，测量窗口内约 280–320 只尸潮成员处于开阔视野和客户端渲染范围。预热 20 秒后使用 PresentMon 测量十分钟，平均 FPS 必须至少 60，P95 帧时间不得超过 25 ms。

客户端 Tick 或 `Minecraft.runTick` 耗时只能用于诊断，不能作为发布验收的 FPS 或 Present 间隔。

## 场景

1. 开阔地密集推进：覆盖共享路线、独立寻路、密集推挤与客户端渲染。
2. 围墙与山体基地：覆盖路线失效、64 个活跃挖掘点、方块更新、掉落和权限事件。

Issue #16 永久记录测试提交、运行时版本、完整设置、运行有效性以及每个加载器和场景的指标摘要。JFR、帧时间原始数据、状态快照和完整 `perf/results/` 只在维护者本地保存。

## 可复现世界

两个场景提交固定种子、结构文件和初始化数据，见 `perf/scenarios/`：

| 场景 | 目录 | 种子 | 结构 | 初始化 |
| --- | --- | ---: | --- | --- |
| 开阔地推进 | `perf/scenarios/open-field` | 20211001 | `origin.nbt` 与方向标记 | 超平坦平原、时间 18000、关闭自然刷新与昼夜 |
| 围墙山体基地 | `perf/scenarios/walled-mountain` | 20211001 | `walled_compound.nbt`（含 64 个黑曜石挖掘点）、`mountain.nbt` | 同上，出生点在围墙内 |

形成模式在复制基线后把世界时间重置为 13000 并清空尸潮成员；稳定服务器模式准备 1000 只尸潮成员；客户端模式把 GameRule 目标设置为 300。三个模式复用相同场景，不新增第二套性能 harness。

完整世界缓存不提交到 Git。开发者本地生成一次，之后每次测试都复制干净基线：

```text
./gradlew generatePerfBaselines
```

缓存目录是 Git 忽略的 `perf/cache/<loader>/<scenario>/world/`。结构 NBT 可用 `python perf/tools/write_structures.py` 重新生成。

## 现有工具与后续实现

现有性能 harness 已负责场景生命周期、干净世界复制、JFR、Tick 采样、客户端诊断采样和报告写入。后续子票在同一 seam 上增加形成、稳定服务器和客户端正式模式，以及墙钟 TPS、尸潮规模窗口、运行有效性和最终判定；不创建第二套 harness。

当前基线生成和诊断入口：

```text
./gradlew runIdleServerBaseline
./gradlew runIdleClientBaseline -Pperf.platform=fabric -Pperf.scenario=open-field
```

Fabric 与 NeoForge 串行运行，避免同时占用相同硬件和端口。

在正式模式完成前，现有 `runIdle*` 任务只用于诊断，不能单独证明新的发布门槛通过。缩短时长的 `-Pperf.warmupTicks` 和 `-Pperf.durationTicks` 运行一律是冒烟，不得标记为正式结果。

## 正式运行有效性

每个组合只采用第一次完成的有效运行。只有运行前可判定或由 harness 明确记录的客观条件可以将结果作废：

- 设置与参考环境不符；
- JFR 或 PresentMon 采集失败；
- 干净基线损坏或复制失败；
- 玩家断线；
- Minecraft 或采集进程崩溃。

尸潮规模不足、性能档位下降、TPS/MSPT/FPS/帧时间不达标都是有效失败，不能据此作废并反复重跑。

## 数据采集

- 服务端使用 Minecraft 原生 JFR 和 harness Tick/墙钟采样，记录平均与 P95 MSPT、平均 TPS、尸潮规模窗口、普通 Zombie 总数、死亡/清理/补充原因和性能档位。
- 客户端使用 PresentMon 的 `msBetweenPresents` 记录平均 FPS 与 P95 帧时间。`perf/tools/capture-presentmon.ps1` 负责采集，`perf/tools/summarize_presentmon.py` 负责汇总。
- `/theyarebillions status` 提供尸潮规模、普通 Zombie、玩家群组、活动票据、共享路线、挖掘点和性能档位快照。

2026-09-13 从游戏时间 18000 立即请求目标数量的四场结果继续作为旧流程的有效失败证据，但不能替代新的黄昏形成验收，也不能用其 543–771 只欠载下的 P95 判定 1000 只门槛。

任一正式组合失败即停止 `0.1.0` 发布。若修复已证实的生成、调节器或性能根因后仍不能达到门槛，必须显式修订 Spec、Issue #16 和受影响 ADR，再从头验收；不能在实现中静默降低目标。
