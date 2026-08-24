# Because It's Too Easy — v1.0.0 食物新鲜度系统 设计文档

- **日期**: 2026-08-24
- **状态**: 已与作者逐节确认
- **版本**: v1.0.0（仅食物新鲜度；口渴度、体温系统留待后续版本）
- **作者**: 贺阳明 + Claude（结对设计）

---

## 1. 概述

### 1.1 目标

为 Minecraft Java Edition 26.2（Fabric）开发生存难度 mod "Because It's Too Easy"（mod id: `bite`）。
v1.0.0 交付**食物新鲜度/腐坏系统**，堆叠语义采用饥荒（Don't Starve）模式：

- 每种食物有保质期，随游戏时间流逝而腐坏
- 物品图标下方显示新鲜度进度条（耐久条位置，颜色渐变）
- 不同新鲜度的同种食物**可以堆叠**，合并时新鲜度按**数量加权平均**（守恒）
- 吃低新鲜度食物有惩罚；完全变质的食物不可食用

### 1.2 非目标（v1.0.0 明确不做）

- 口渴度、体温系统（v1.x 后续版本）
- 变质食物转换为腐肉（后续可用数据包配方加）
- 冰箱/保鲜容器方块（等体温系统联动）
- 食物纹理随新鲜度变化（灰化等）
- 新食物物品、新方块、新创造模式标签页
- 多加载器支持（仅 Fabric）

### 1.3 运行环境与发布

- 面向 Modrinth + CurseForge 公开发布，MIT 协议
- 国际化：`en_us` + `zh_cn` 双语，所有用户可见文案走翻译键
- 双端 mod（客户端渲染 + 服务端逻辑），fabric.mod.json `environment: "*"`

---

## 2. 工具链与版本锁定（2026-08 研究核实）

| 组件 | 版本 | 备注 |
|---|---|---|
| Minecraft | 26.2 "Chaos Cubed"（2026-06-16 发布） | 当前最新稳定版；26.3 已有快照 |
| Java | **25**（最低） | 本机现有 JDK 8 不满足，需安装 Temurin/Homebrew JDK 25 |
| Fabric Loader | 0.19.3 | 最新 stable |
| Fabric API | 0.158.0+26.2 | 2026-08-18 发布 |
| Fabric Loom | 1.17（模板用 1.17-SNAPSHOT，稳定版 1.17.19） | Gradle 插件 |
| Gradle | 9.5.1（wrapper） | |
| IDE | IntelliJ IDEA 2026.2（已装） | 需装 Minecraft Development 插件（mixin 支持） |

**26.x 时代关键变化**（与旧教程的差异，全部经字节码/官方源验证）：

1. **Yarn 已死**：26.1 起游戏完全去混淆，直接用 Mojang 官方命名（`ServerPlayer`、`Component.translatable`、`Identifier.fromNamespaceAndPath` 等）
2. **Loom 插件换 ID**：用 `net.fabricmc.fabric-loom`（非 remap 版）；依赖声明用普通 `implementation`（非 `modImplementation`）；产物用 `jar` task（非 `remapJar`）；**无 mappings 依赖**
3. **`HudRenderCallback` 已删除**：新 HUD API 为 `HudElementRegistry`（v1.0.0 仅 tooltip/物品条，暂不用 HUD API，温度/口渴版本再用）
4. 项目用 `splitEnvironmentSourceSets()`：`src/main/java`（双端）+ `src/client/java`（仅客户端），客户端类误入服务端成为编译期错误
5. 脚手架来源：fabricmc.net/develop/template 模板生成器或 fabric-example-mod（分支 `26.2`）。**不要用** IDEA 项目向导（官方文档明言模板过时）

---

## 3. 架构总览

```
┌─────────────────────── 服务端（权威） ───────────────────────┐
│                                                              │
│  FreshnessStamper        打标（获得食物时写入出生时间戳）      │
│   ├─ LootTableEvents.MODIFY_DROPS   战利品路径（事件，0 mixin）│
│   └─ FreshnessScanner               懒扫描兜底一切其他来源     │
│      （ServerTickEvents.END_LEVEL_TICK 低频轮询）             │
│                                                              │
│  StackingRules            饥荒式合并（等价性放宽+加权平均）     │
│   └─ ~6 个 mixin（详见 §6 hook 地图）                        │
│                                                              │
│  SpoiledFoodHandler       进食惩罚（Consumable.onConsume mixin）│
│                                                              │
│  ShelfLifeRegistry        保质期表（分类默认值 + JSON 覆盖）    │
└──────────────────────────────────────────────────────────────┘
                            │ 数据流
                 ItemStack 组件（随物品走，天然持久化）
                            │
┌─────────────────────── 客户端（显示） ───────────────────────┐
│  FreshnessBar            物品图标进度条（mixin 到 bar 渲染）   │
│  FreshnessTooltip        tooltip 数值行（ItemComponentTooltip │
│                           ProviderRegistry，0 mixin）          │
└──────────────────────────────────────────────────────────────┘
```

核心决策及依据（研究已验证）：

- **新鲜度 = ItemStack 数据组件**（非 attachment）：组件随物品实例走，存档/死亡掉落/跨维度天然持久化，无需任何额外代码
- **精确时间戳而非每 tick 倒计时**：惰性求值（显示/合并时计算 `now - stamp`），不逐 tick 写组件 → 不触发原版「选中物品组件更新动画」，无 per-tick 性能开销
- **腐坏在服务端权威计算**：进度条与 tooltip 是纯显示

---

## 4. 数据模型

### 4.1 自定义数据组件（2 个）

```java
// bite:freshness —— 每个 stack 一份
public record FreshnessStamp(long creationGameTick) {
    // Codec<Long> → persistent + StreamCodec → networkSynchronized
    // 实现 TooltipProvider（tooltip 渲染，见 §7.2）
}

// bite:shelf_life —— 挂在食物类型的默认组件上（DefaultItemComponentEvents.MODIFY 设置）
public record ShelfLife(long spoilTicks) {
    // spoilTicks < 0 表示永不腐坏（金苹果、腐肉等豁免项）
}
```

注册方式（26.2 验证过的 API）：

```java
Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
    Identifier.fromNamespaceAndPath("bite", "freshness"),
    DataComponentType.<FreshnessStamp>builder()
        .persistent(FreshnessStamp.CODEC)
        .networkSynchronized(FreshnessStamp.STREAM_CODEC)
        .build());
```

> ⚠️ 实现注意：`DataComponentType.Builder` 在 26.2 **没有** `cachingCodec()` 方法（早期研究错误，已修正）。可用方法：`persistent(Codec)`、`networkSynchronized(StreamCodec)`、`cacheEncoding()`、`ignoreSwapAnimation()`。

### 4.2 保质期默认值（按 c:foods 约定标签分类）

Fabric API 26.2 自带 `c:foods/*` 约定标签（`fabric-convention-tags-v2` 模块），直接作为分类输入：

| 分类（c:foods/…） | 默认保质期（游戏天） | 换算 ticks |
|---|---|---|
| `raw_meat`, `raw_fish` | 2 | 48,000 |
| `cooked_meat`, `cooked_fish` | 4 | 96,000 |
| `bread`, `vegetable`, `fruit`, `berry`, `dough` | 6 | 144,000 |
| `soup`, `cookie`, `pie`, `candy` | 3 | 72,000 |
| 其余一切带 `minecraft:food` 组件的物品（兜底） | 7 | 168,000 |
| 豁免（永不腐坏） | ∞ | -1 |

豁免清单 v1 默认：`minecraft:golden_apple`、`minecraft:enchanted_golden_apple`、`minecraft:rotten_flesh`、`minecraft:spider_eye`、`minecraft:poisonous_potato`、`c:foods/golden` 标签全体、`c:foods/food_poisoning` 标签全体。

判定顺序（`ShelfLifeRegistry.resolve(Item)`）：
1. server.json 的 `item_overrides` 命中 → 用覆盖值
2. 豁免清单/标签命中 → -1
3. c:foods 分类标签命中 → 分类默认值
4. 有 `minecraft:food` 组件 → 兜底默认值
5. 都不是（非食物）→ 不处理

### 4.3 新鲜度计算（纯函数，双端共用）

```java
public final class FreshnessMath {
    // 百分比 [0,1]；shelfLife<0 恒为 1
    public static double fraction(long nowTick, FreshnessStamp stamp, ShelfLife shelfLife);
    // 合并：数量加权平均后反解新 stamp（新鲜度守恒）
    public static FreshnessStamp mergeStamps(long nowTick,
        FreshnessStamp s1, long count1, ShelfLife life,
        FreshnessStamp s2, long count2);
    // 等级：FRESH(>0.5)/STALE(0.25-0.5)/OLD(0-0.25)/SPOILED(0)
    public static FreshnessGrade grade(double fraction);
}
```

`mergeStamps` 数学：`f_new = (f1·c1 + f2·c2)/(c1+c2)`，再由 `f_new` 反解 `stamp = now - (1-f_new)·shelfLife`。同种食物 shelfLife 相同，公式良定义。

---

## 5. 打标链路（食物何时开始计时）

| 获得途径 | 机制 | mixin |
|---|---|---|
| 怪物掉落 / 方块掉落 / 钓鱼 / 箱子战利品 | `LootTableEvents.MODIFY_DROPS`（fabric-loot-api-v3）：对 drops 列表里的食物打 `FreshnessStamp(now)` | 0 |
| 合成 / 烹饪产物 | 新 stack 无 stamp → 懒扫描首见打标（语义 = 「出炉即新鲜」） | 0 |
| 村民交易 / 创造模式 / `/give` / 旧存档遗留 | 同上（懒扫描兜底一切来源） | 0 |
| 已打开过的容器里的食物 | 懒扫描周期重扫（持续计时） | 1（accessor） |

**懒扫描**（`FreshnessScanner`）：

- 挂 `ServerTickEvents.END_LEVEL_TICK`，每 100 ticks（5 秒，可配置）一轮
- 扫描范围：① 全部在线玩家背包；② 已加载区块中的容器 BlockEntity（箱子、木桶、熔炉等，跳过战利品表未开箱的 `RandomizableContainerBlockEntity`——Spoiled 的语义，地牢箱里的食物在被发现前不腐坏）；③ 容器实体（潜影盒物品、物品展示框等经由实体路径）
- 枚举已加载区块 BlockEntity 需要 `ChunkMap` accessor mixin（Spoiled 同款，只暴露字段不改行为，稳定性最高的一类 mixin）
- **性能边界**：单 stack 每轮只做 `has(freshness)` 检查（O(1) 组件查询）；无逐 tick 写入。Spoiled mod 用同款模式服务 26.x 用户，性能已验证

**打标规则**：`stamp = level.getGameTime()`（用 game time 而非 day time——day time 每 24000 ticks 循环会回绕）。

---

## 6. 饥荒式堆叠合并（本设计的核心难点）

### 6.1 语义

原版规则：两堆物品可合并 ⟺ item 相同 **且** 组件完全相等（`ItemStack.isSameItemSameComponents`，26.2 字节码验证：item holder 检查 + `Objects.equals(组件映射)`）。

本 mod 规则：两堆食物可合并 ⟺ item 相同 且 shelfLife 相同 且 **两者都未腐坏（fraction > 0）**（新鲜度本身不参与相等判断）；实际合并（数量移动）时重算加权平均 stamp。

### 6.2 mixin hook 地图（研究已验证到 26.2 字节码级）

**A. 等价性放宽（1 处）**

```
目标: ItemStack.isSameItemSameComponents(ItemStack, ItemStack)  [static]
方式: @Inject(at = @At("RETURN"), cancellable = true)
逻辑: 若 vanilla 判 false 且 item 相同、双方都有 freshness 组件、
      shelfLife 相同、双方 fraction > 0 → 改判 true
```

单点覆盖**全部** 26 个原版调用点（容器合并、地面拾取、hoppper、配方匹配……）。副作用评估（研究已枚举全部调用点）：
- 配方匹配放宽（不同新鲜度的食材可互替）→ **期望行为**（坏一点的肉也能做熟）
- 商人支付槽、Vault 插入、熔炉燃料检查等同理放宽 → 对食物无害
- 创造模式标签页分组（`ItemStackLinkedSet`）会把不同新鲜度食物归为一组 → 期望行为

**B. 合并重算（数量移动点，~5 处）**

等价性放宽后，vanilla 在这些点移动数量时 destination 的组件胜出、origin 的时间戳被丢弃（`destination.copyWithCount` + `origin.shrink`，26.2 字节码验证）。必须在这些点写入加权平均：

| # | 目标方法（26.2 Mojang 命名） | 场景 |
|---|---|---|
| 1 | `Slot.safeInsert(ItemStack, int)` | 手动放入槽位 |
| 2 | `Inventory.addResource(int, ItemStack)` | 玩家背包放入（含地面拾取路径） |
| 3 | `AbstractContainerMenu.moveItemStackTo(ItemStack, int, int, boolean)` | Shift 点击转移 |
| 4 | `ItemEntity.merge(ItemStack, ItemStack, int)` | 掉落物互相合并 |
| 5 | `SimpleContainer.moveItemsBetweenStacks(ItemStack, ItemStack)` | 漏斗/容器间转移 |

> ⚠️ 实现注意：`AbstractContainerMenu.doClick` 的签名在 26.x 已变为接收 `ContainerInput` 枚举（旧 `ClickType`），1.20.x 教程的 mixin 描述符不能照抄。

实现模式统一：`@Inject` 在数量移动完成后（或 wrap 对应 `grow`/`shrink` 调用），对 destination stack 执行 `stamp = mergeStamps(now, dest旧值, dest数量增量, life, origin旧值, origin数量)`。

**C. 其他 mixin**

| # | 目标 | 用途 |
|---|---|---|
| 6 | `Consumable.onConsume(Level, LivingEntity, ItemStack)` | 进食完成钩子：按新鲜度施加惩罚（§8） |
| 7 | `ChunkMap` accessor | 懒扫描枚举已加载区块 BlockEntity（§5） |
| 8-9 | 物品条渲染（实现期确认确切目标，见 §7.1） | 进度条显示 |

**mixin 总数：9-11**（等价性 1 + 合并重算 5 + 进食 1 + accessor 1 + 进度条 1-3）。缓解因素：26.x 去混淆后 mixin 目标不再随版本映射变动而失效（Fabric 官方博客确认），剩余风险仅来自 vanilla 真实重构。

### 6.3 已知边界语义（有意决策）

- **腐坏的食物（fraction = 0）不参与合并**：避免新鲜度 0 的老堆污染健康堆（守恒公式会让合并结果也接近 0）。腐坏食物各自成堆，等玩家处理
- 掉落物合并（hook #4）同样走加权平均 → 地上两堆肉会自动合并成平均值堆（饥荒行为）
- 物品展示框/末影人搬运等边缘容器：v1 不特殊处理（跟随等价性放宽的默认行为）

---

## 7. 显示

### 7.1 物品进度条（客户端）

- 位置：物品图标正下方，原版耐久条同一渲染层
- 颜色渐变：`>75%` 绿 `#55FF55` → `50-75%` 黄 `#FFDD55` → `25-50%` 橙 `#FFAA00` → `<25%` 红 `#FF5555` → 腐坏 灰 `#AAAAAA`（与原版 ChatFormatting 色板对齐）
- bar 宽度 = fraction × 13px（耐久条同宽）
- 实现：mixin 到 vanilla bar 渲染提取路径。**确切注入点是本设计唯一标注「实现期验证」的点**：26.2 把 GUI 渲染重构为 render-state 提取制，`genSources` 反编译后确认（候选：`Item.isBarVisible/getBarWidth/getBarColor` 的调用点或新的 ItemRenderState 提取处）。Fabric API 无公开的物品条覆盖 API（已扫描 fabric-item-api-v1 全部导出），mixin 是唯一路径
- 有 vanilla 耐久条的食物（罕见，如其他 mod 的可损耗食物）：进度条不覆盖耐久条——耐久条优先，新鲜度退化为仅 tooltip 显示

### 7.2 Tooltip（客户端）

- 注册方式：`ItemComponentTooltipProviderRegistry`（26.2 推荐：组件 record 实现 `TooltipProvider`，自动渲染进 tooltip，零 mixin）
- 内容（走翻译键 + 占位符）：
  - 正常：`bite.tooltip.freshness`: `"Freshness: %1$s%% (%2$s)"` / `"新鲜度：%1$s%%（%2$s）"`，参数为整数百分比与剩余时间（"约 3.0 天" / "~3.0 days"）
  - 腐坏：`bite.tooltip.spoiled`: `"Spoiled"` / `"已变质"`（灰色）
  - 永不腐坏：不显示行
- 开发期 i18n 热重载：F3+T

---

## 8. 进食效果（服务端权威）

mixin `Consumable.onConsume`（26.2 验证：所有食物进食的统一咽喉点，覆盖玩家与生物）：

| fraction | 营养 | 饱和度 | 额外效果 |
|---|---|---|---|
| > 50% | 100% | 100% | 无 |
| 25-50% | 75% | 75% | 无 |
| >0-25% | 50% | 50% | 30% 概率「饥饿 I」8 秒（对标腐肉机制） |
| 0% | —— | —— | **进食被取消**，聊天栏提示 `bite.msg.spoiled_inedible`（"这食物已经变质了，不能吃"） |

- 营养/饱和度缩放策略：mixin 中在原版营养结算后补偿差值（记录进食前 `FoodData` 快照，结算后按缩放比把食物量/饱和度压回目标值，即 `setFoodLevel(前值 + 营养×缩放)`）；具体接线点随 26.2 进食调用链在实现期适配
- 腐坏判定用进食时刻的服务端计算，不信任客户端
- 生物吃腐坏食物（僵尸等）：v1 一视同仁走相同逻辑（简单）；后续版本可配置
- 配置项：惩罚曲线三档阈值、debuff 概率、腐坏是否绝对禁食（默认禁食）

---

## 9. 配置（手写 Gson JSON，零第三方依赖）

```
config/bite/
├── server.json   # 服务端权威：游戏性
└── client.json   # 客户端本地：显示
```

`server.json` 字段（v1 全量）：

```jsonc
{
  "enabled": true,
  "scan_interval_ticks": 100,
  "default_shelf_life_days": {          // 分类默认（游戏天）
    "raw_meat": 2, "raw_fish": 2,
    "cooked_meat": 4, "cooked_fish": 4,
    "bread": 6, "vegetable": 6, "fruit": 6, "berry": 6, "dough": 6,
    "soup": 3, "cookie": 3, "pie": 3, "candy": 3,
    "fallback": 7
  },
  "item_overrides": {                    // 精确覆盖（-1 = 永不腐坏）
    "minecraft:golden_apple": -1,
    "somemod:sushi": 3
  },
  "spoil_thresholds": { "stale": 0.5, "old": 0.25 },
  "nutrition_scale": { "stale": 0.75, "old": 0.5 },
  "hunger_effect_chance": 0.3,
  "spoiled_inedible": true
}
```

`client.json`：进度条开关、tooltip 格式（百分比/剩余时间二选一或都显示）、颜色渐变开关。

配置读写约 100-150 行（Gson + record），双端各一份。v1 服务端配置**重启生效**（改动频率低，避免热重载的默认组件重算问题）；`/bite reload` 热加载命令留待 v1.1。

> 为什么不用 Cloth Config / YACL / Forge Config API Port：前两者官方定位纯客户端（文档明言不要用于服务端 mod）；FCAP 引入运行时依赖与 TOML 栈，对一个 12 字段的配置过重。研究确认主流生存 mod（Tough As Nails、Homeostatic）均自持配置类。v2 若需要 GUI 配置界面再评估。

---

## 10. 国际化

- 文件：`src/main/resources/assets/bite/lang/en_us.json` + `zh_cn.json`（扁平 JSON；模板的 split source set 下 assets 放 main）
- 代码侧全部 `Component.translatable("bite.xxx", args...)`，占位符用 `%1$s`/`%2$s`（MessageFormat 语义；字面 `%` 转义为 `%%`——tooltip 百分比行必须注意）
- ModMenu 集成键：`modmenu.nameTranslation.bite`、`modmenu.summaryTranslation.bite`、`modmenu.descriptionTranslation.bite`
- 术语表（对齐原版 zh_cn 风格：饥饿/中毒/腐肉）：

| 键 | en_us | zh_cn |
|---|---|---|
| `bite.tooltip.freshness` | Freshness: %1$s%% (%2$s) | 新鲜度：%1$s%%（%2$s） |
| `bite.tooltip.spoiled` | Spoiled | 已变质 |
| `bite.msg.spoiled_inedible` | This food has spoiled and can't be eaten. | 这食物已经变质了，不能吃。 |
| `modmenu.summaryTranslation.bite` | Food spoilage survival mechanics | 食物腐坏生存机制 |

---

## 11. 代码结构

```
~/Code/bite/
├── gradle/wrapper/                  # Gradle 9.5.1
├── build.gradle                      # net.fabricmc.fabric-loom 1.17 + splitEnvironmentSourceSets
├── gradle.properties                 # minecraft_version=26.2, loader_version=0.19.3,
│                                     # fabric_api_version=0.158.0+26.2（无 yarn_mappings 行）
├── src/main/java/com/heyangming/bite/
│   ├── BiteMod.java                  # ModInitializer：组件注册、事件接线、配置加载
│   ├── component/FreshnessStamp.java # record + Codec + StreamCodec + TooltipProvider
│   ├── component/ShelfLife.java      # record + Codec
│   ├── component/BiteComponents.java # DataComponentType 注册处
│   ├── freshness/ShelfLifeRegistry.java  # 分类表 + 覆盖表 + resolve(Item)
│   ├── freshness/FreshnessStamper.java   # 打标 + LootTableEvents 接线
│   ├── freshness/FreshnessScanner.java   # 懒扫描 + END_LEVEL_TICK 接线
│   ├── freshness/FreshnessMath.java      # 纯函数（双端共用，单测友好）
│   ├── freshness/StackingRules.java      # 合并重算逻辑（被 mixin 调用）
│   ├── freshness/SpoiledFoodHandler.java # 进食惩罚逻辑（被 mixin 调用）
│   ├── config/ServerConfig.java
│   └── mixin/                        # 全部 mixin（ Consumable、合并 5 处、accessor…）
├── src/client/java/com/heyangming/bite/client/
│   ├── BiteModClient.java            # ClientModInitializer：tooltip/进度条注册
│   └── render/FreshnessBar.java      # 进度条渲染
├── src/gametest/java/...             # fabricApi { configureTests } 生成的 gametest 源集
├── src/main/resources/
│   ├── fabric.mod.json               # id bite, env "*", depends: fabricloader >=0.19.3,
│   │                                 #   minecraft ~26.2, java >=25, fabric-api *
│   ├── bite.mixins.json
│   └── assets/bite/lang/{en_us,zh_cn}.json
└── docs/superpowers/specs/           # 本文档
```

包名 `com.heyangming.bite`（脚手架前可整体替换为 GitHub 风格 `io.github.<user>.bite`，一次性 sed）。

---

## 12. 测试策略

1. **单元测试**（Fabric Loader JUnit，`src/test/java`）：`FreshnessMath` 全函数（fraction/mergeStamps 反解正确性、腐坏边界、永不腐坏哨兵值）
2. **GameTest**（`fabric-gametest-api-v1`，随 `./gradlew build` 无头执行，服务端权威逻辑全覆盖）：
   - loot 掉落的食物带 stamp
   - 合成产物首见打标
   - 懒扫描给无 stamp 食物打标
   - 两堆不同新鲜度食物经容器转移后合并出加权平均 stamp
   - 吃不同新鲜度食物的营养/debuff/禁食
3. **手动验收**（`./gradlew runClient`）：
   - 创造模式拿食物 → 生存模式观察 tooltip/进度条随时间变化
   - 与朋友联机（e4mc 反向隧道，26.2 支持）验证双端一致
   - F3+T 验证 lang 热重载
4. **调试辅助**：`/bite freshness` 查询手持食物的 stamp/shelfLife/fraction（v1 内置，OP 权限）

---

## 13. 发布（v1.0.0）

- **仓库**：GitHub 公开仓库 `~/Code/bite`（remote 建议名 `because-its-too-easy` 或 `bite`）
- **协议**：MIT（SPDX：`MIT`）
- **fabric.mod.json**：`version 1.0.0`、`environment "*"`、`depends { fabricloader ">=0.19.3", minecraft "~26.2", java ">=25", "fabric-api": "*" }`、`contact`（homepage/sources/issues）、icon 128×128
- **构建发布**：Gradle 插件 Minotaur（`com.modrinth.minotaur` 2.+，`uploadFile` 指向 jar）+ CurseForgeGradle（`net.darkhax.curseforgegradle` 1.3.33）；本地 `publishMods` 式单 task 手动触发（CI 自动化留待需要时）
- **Modrinth 页面**：方形图标、英文正文（附中文段）、依赖声明（Fabric API）、画廊截图（进度条 + tooltip 特写）、版本 changelog。注意：描述需英文（规则要求）、图标不得 AI 生成（需手绘/程序生成）、人工审核 24-48h 起步
- **CurseForge**：项目名/摘要/描述必须英文；首文件上传后才进审核队列；环境标签 Client+Server 双标（2026-07 起强制）

---

## 14. 风险与开放点

| # | 风险/开放点 | 等级 | 缓解 |
|---|---|---|---|
| 1 | 进度条 mixin 的确切注入点（26.2 GUI 重构后） | 中 | 实现期 `genSources` 反编译确认；候选路径明确；AppleSkin 等先例证明可行 |
| 2 | 合并 5 处 mixin 与其他 mod 的容器兼容 | 中 | 全部 hook 点已验证到 26.2 字节码；逐 mixin 配 GameTest |
| 3 | 懒扫描的 BlockEntity 枚举性能（大量加载区块的服务器） | 低 | 5s 一轮 + O(1) 组件检查；Spoiled 同款模式已上线验证 |
| 4 | 26.3 临近（快照已到 -9），可能需要短期适配 | 低 | mixin 目标去混淆后稳定；depends 锁 `~26.2` 不误声明 |
| 5 | 其他 mod 的食物无 c:foods 标签（老 mod） | 低 | 兜底分类 + item_overrides 覆盖表；发布页引导用户报告 |
| 6 | LootTableEvents.MODIFY_DROPS 对未开箱 `RandomizableContainerBlockEntity` 的触发链路未 100% 确认 | 低 | 实现期单测验证；语义影响仅限「地牢食物是否提前计时」 |

---

## 15. 后续版本路线（仅存档，不在 v1.0.0 范围）

- **v1.1**：`/bite reload`、变质转腐肉的数据包配方、腐坏食物合成限制开关
- **v1.2**：体温系统（生物群系/高度/时段/方块热源 → 5 档体感，中暑/失温效果；与新鲜度联动：热环境加速腐坏）——架构沿用 Data Attachment + `HudElementRegistry`/`HudStatusBarHeightRegistry`（自动上移原版状态条）
- **v1.3**：口渴度系统（ exhaustion 驱动消耗、水瓶/直接饮用、脱水效果档位）
- **v2.x**：可选的堆叠放宽升级（跨腐坏边界合并策略）、YACL 配置界面评估

---

## 16. 关键研究结论存档（设计依据溯源）

本设计的非显然决策全部来自 2026-08-23/24 的双工作流研究（185 条经对抗核查的事实，27 个 agent，含 26.2 客户端 jar 字节码级验证）。要点：

- 堆叠等价性：`ItemStack.isSameItemSameComponents` = item 检查 + `Objects.equals(组件)`（jar 验证）；放宽它无 Fabric API 替代（fabric-item-api-v1 全 API 枚举确认）
- 数量移动点与 ground-item merge 的 destination-wins 语义（jar 验证）→ 必须显式加权平均
- `Consumable.onConsume` 是 26.2 统一进食咽喉点（`LivingEntity.eat` 已不存在）
- Fabric API 0.158.0+26.2 **无** crafting-result/pickup/food-consumed 事件（1640 文件全扫描）→ 懒扫描 + loot 事件的组合是唯一免 mixin 打标路径（Spoiled mod 2026-08 仍在维护同款架构）
- `c:foods/*` 约定标签与 `DefaultItemComponentEvents.MODIFY` 存在且适用（fabric-api 26.2 分支源码验证）
- 全部工具链版本号与模板细节（fabricmc.net、FabricMC GitHub、Mojang piston-meta 交叉验证）
- i18n 机制、ModMenu 翻译键、Modrinth/CurseForge 发布规则（官方文档 + API 验证）

（完整研究数据存于会话工作流 transcript；实现期如需复查某条结论的来源 URL，查研究 journal。）
