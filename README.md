# Because It's Too Easy

生存模式太容易了。/ Survival was too easy.

食物会随时间腐坏：图标下的进度条告诉你还剩多少时间，tooltip 显示精确数值。

## 特性 / Features
- 🥩 食物保质期：生肉 2 天、熟食 4 天、面包/作物 6 天（可配置）
- 📊 新鲜度进度条（物品图标下方）
- 🧮 饥荒式堆叠：不同新鲜度的食物可堆叠，合并取加权平均（守恒）
- 🤢 吃不新鲜的食物：营养打折，低新鲜度概率饥饿 debuff；完全变质不可食用
- ℹ️ v1.0.0 进食惩罚仅作用于玩家；生物（僵尸等）进食 mob parity 计划在 v1.1 实现
- 🌐 中英双语（en_us / zh_cn）
- 🔧 全部数值可在 `config/bite/server.json` 配置；其他 mod 的食物自动按 `c:foods` 分类兼容

## 要求 / Requirements
Minecraft 26.2 · Fabric Loader ≥0.19.3 · Fabric API

## English
Food spoils over time. A bar under each item tracks freshness; tooltips show exact
values. Stacks merge Don't-Strive-style: weighted-average freshness is conserved.
Eating stale food costs nutrition and may apply Hunger; spoiled food is inedible.

> **v1.0.0 scope:** Eating penalties apply to players only. Mob parity (mobs eating
> spoiled food under the same rules) is planned for v1.1.

## Icon
The bundled `assets/bite/icon.png` is a procedurally-generated placeholder gradient
(fresh-green → spoil-red), **not** an AI-generated image — compliant with Modrinth's
policy against AI-generated artwork. Replace it with a hand-drawn or commissioned
icon before publishing to Modrinth/CurseForge.
