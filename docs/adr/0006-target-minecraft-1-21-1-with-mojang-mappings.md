# ADR-0006：以 Minecraft 1.21.1 和 Mojang 映射作为首版基线

- 状态：已接受
- 日期：2026-09-06

## 背景

模组需要通过 Architectury 同时支持 Fabric 与 NeoForge。较新的游戏版本仍依赖 beta 工具链或更高 Java 版本，而 1.21.1 在两个加载器上都有成熟版本线。

## 决策

- Minecraft 1.21.1、Java 21。
- common、Fabric 和 NeoForge 全部使用 Mojang 官方映射。
- 锁定 Loom 1.17.491、Architectury Plugin 3.5.169、Architectury API 13.0.11、Fabric Loader 0.19.5、Fabric API 0.116.17+1.21.1 与 NeoForge 21.1.249。
- 不使用 `SNAPSHOT` 依赖。

## 后果

- 类名与方法名在三个模块中保持一致，减少跨映射维护成本。
- 升级 Minecraft 小版本时必须重新核对 Mixin 锚点、实体导航、区块票据与 FakePlayer 平台桥。
