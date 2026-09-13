# 使用 Architectury 支持双加载器

模组以 Minecraft 1.21.1 为目标，使用 Architectury 共享 Fabric 与 NeoForge 的内容注册和游戏逻辑。Architectury 无法统一拦截现有 `minecraft:zombie` 的 `NATURAL`、`SPAWNER` 与 `TRIAL_SPAWNER` 生成，因此公共模块保留领域行为，Fabric 与 NeoForge 模块只承担各自加载器所需的生成拦截适配。
