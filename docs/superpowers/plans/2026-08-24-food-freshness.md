# Because It's Too Easy v1.0.0 食物新鲜度系统 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Minecraft 26.2 (Fabric) 上实现食物新鲜度/腐坏系统：量化保质期、物品进度条、饥荒式加权平均堆叠、进食惩罚、双语、可发布。

**Architecture:** 新鲜度 = ItemStack 数据组件 `bite:freshness`（出生游戏刻）；保质期 = `bite:shelf_life` 默认组件。打标走 loot 事件 + 懒扫描（免 mixin）；堆叠走等价性放宽单点 mixin + 5 处合并重算 mixin；进食惩罚走 `ItemStack.finishUsingItem` mixin + `UseItemCallback` 事件拦截；显示走物品条 mixin + `TooltipProvider`。服务端权威，客户端纯显示。

**Tech Stack:** MC 26.2 / Fabric Loader 0.19.3 / Fabric API 0.158.0+26.2 / Loom `net.fabricmc.fabric-loom` 1.17 / Gradle 9.5.1 / Java 25 / 无第三方运行时依赖（配置用 Gson）。

**Spec:** `docs/superpowers/specs/2026-08-24-food-freshness-design.md`（本计划从 spec 出发，执行者需同时读 spec）

## Global Constraints

- MC `26.2`；`java >=25`；`fabricloader >=0.19.3`；`fabric-api 0.158.0+26.2`；Gradle wrapper 9.5.1（模板自带，勿改）
- Loom 插件 id 必须是 `net.fabricmc.fabric-loom`（非 remap 版）；依赖声明用普通 `implementation`（**禁止** `modImplementation`）；产物是 `jar` task（**禁止** `remapJar`）；**没有** yarn/mappings 依赖
- 包名 `com.eamon.bite`；mod id `bite`；仓库本地 `~/Code/bite`
- 26.x 用 Mojang 官方命名（`Identifier.fromNamespaceAndPath`、`Component.translatable`、`ServerLevel` 等）；**不使用** `HudRenderCallback`（已删除）、`modImplementation`、`yarn_mappings` 等旧教程写法
- 所有用户可见文案必须走翻译键（`assets/bite/lang/en_us.json` + `zh_cn.json`），禁止硬编码字符串；字面 `%` 在 lang 值中转义为 `%%`
- 客户端代码只在 `src/client/java`（splitEnvironmentSourceSets），服务端权威逻辑只在 `src/main/java`
- 不引入第三方配置库/渲染库；许可证 MIT
- 本机命令行的 `java` 是 JDK 8（工作用，勿动）；**所有 gradlew 命令一律** `JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew ...`
- 提交信息用 conventional commits（feat/test/chore/docs），每任务至少一提交

---

### Task 1: 环境与项目脚手架

**Files:**
- Create: 整个项目骨架（从 fabric-example-mod 分支 `26.2` 克隆改名）
- Modify: `gradle.properties`、`build.gradle`、`src/main/resources/fabric.mod.json`

**Interfaces:**
- Produces: 可构建的空 mod 项目，mod id `bite`，入口类 `com.eamon.bite.BiteMod` / `com.eamon.bite.client.BiteModClient`，mixin 配置 `bite.mixins.json` + `bite.client.mixins.json`

- [ ] **Step 1: 安装 JDK 25**

```bash
brew install --cask temurin@25
/usr/libexec/java_home -v 25   # 应输出 /Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
```
若 cask 不存在，fallback：`brew install openjdk@25`，然后按 brew 输出做 symlink（brew 会提示 `sudo ln -sfn ...`），再验证 `/usr/libexec/java_home -v 25`。
验证共存无害：`java -version` 仍显示 1.8（PATH 未变）。

- [ ] **Step 2: 拉取官方 26.2 模板并合入项目**

```bash
cd ~/Code
git clone --depth 1 -b 26.2 https://github.com/FabricMC/fabric-example-mod.git /tmp/bite-scaffold
rsync -a --exclude='.git' /tmp/bite-scaffold/ ~/Code/bite/
rm -rf /tmp/bite-scaffold
cd ~/Code/bite
```

- [ ] **Step 3: 全局改名（example → bite）**

先确认模板里的实际命名（`cat src/main/resources/fabric.mod.json`、`ls src/main/java -R`），然后按实际值执行 sed。以模板常见命名为例：

```bash
cd ~/Code/bite
# 包与类
mkdir -p src/main/java/com/eamon/bite src/client/java/com/eamon/bite/client
git mv src/main/java/com/example/ExampleMod.java src/main/java/com/eamon/bite/BiteMod.java 2>/dev/null || true
git mv src/client/java/com/example/client/ExampleModClient.java src/client/java/com/eamon/bite/client/BiteModClient.java 2>/dev/null || true
rm -rf src/main/java/com/example src/client/java/com/example
grep -rl 'com\.example' src build.gradle gradle.properties settings.gradle 2>/dev/null | xargs sed -i '' 's/com\.example\.ExampleMod/com.eamon.bite.BiteMod/g; s/com\.example\.client\.ExampleModClient/com.eamon.bite.client.BiteModClient/g; s/com\.example/com.eamon/g'
# mod id / 资源文件名
grep -rl 'example-mod\|examplemod\|modid' src build.gradle gradle.properties settings.gradle 2>/dev/null | xargs sed -i '' 's/example-mod/bite/g; s/examplemod/bite/g; s/modid/bite/g'
mv src/main/resources/example-mod.mixins.json src/main/resources/bite.mixins.json 2>/dev/null || true
mv src/client/resources/example-mod.client.mixins.json src/client/resources/bite.client.mixins.json 2>/dev/null || true
# 若模板自带示例 mixin 类（如 ExampleMixin），删除其 java 文件并从 bite.mixins.json 的 "mixins" 数组移除条目
```

- [ ] **Step 4: 更新 gradle.properties 与 fabric.mod.json**

`gradle.properties` 关键值（版本号来自 spec §2，模板已自带，核对即可）：

```properties
minecraft_version=26.2
loader_version=0.19.3
fabric_api_version=0.158.0+26.2
loom_version=1.17-SNAPSHOT
mod_version=1.0.0
maven_group=com.eamon
archives_base_name=bite
```

`src/main/resources/fabric.mod.json` 改为（保留模板的 schemaVersion/entrypoints 结构）：

```json
{
  "schemaVersion": 1,
  "id": "bite",
  "version": "${version}",
  "name": "Because It's Too Easy",
  "description": "Survival is too easy. Food spoils, and so will your plans.",
  "authors": ["Eamon"],
  "contact": { "homepage": "https://github.com/LinZui520/bite", "sources": "https://github.com/LinZui520/bite", "issues": "https://github.com/LinZui520/bite/issues" },
  "license": "MIT",
  "icon": "assets/bite/icon.png",
  "environment": "*",
  "entrypoints": {
    "main": ["com.eamon.bite.BiteMod"],
    "client": ["com.eamon.bite.client.BiteModClient"]
  },
  "mixins": [
    "bite.mixins.json",
    { "config": "bite.client.mixins.json", "environment": "client" }
  ],
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*"
  }
}
```

入口类内容（先放最小实现，让项目可编译）：

```java
// src/main/java/com/eamon/bite/BiteMod.java
package com.eamon.bite;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiteMod implements ModInitializer {
    public static final String MOD_ID = "bite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Because It's Too Easy initialized");
    }
}
```

```java
// src/client/java/com/eamon/bite/client/BiteModClient.java
package com.eamon.bite.client;

import net.fabricmc.api.ClientModInitializer;

public class BiteModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
    }
}
```

- [ ] **Step 5: 构建验证（无头）**

```bash
cd ~/Code/bite
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
Expected: `BUILD SUCCESSFUL`，产物 `build/libs/bite-1.0.0.jar`。
首次运行会下载 MC 依赖（数分钟）。失败时看 `--stacktrace` 输出；若报 mixin 配置引用了不存在的类，回到 Step 3 清理 mixins json 内容（`"mixins": []`、`"client": []` 即可）。

- [ ] **Step 6: runClient 冒烟（手动，需人观察）**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew runClient
```
Expected: 游戏窗口打开到标题界面，日志出现 `Because It's Too Easy initialized`，Mod 列表里能看到 bite。关闭窗口即可。

- [ ] **Step 7: 提交**

```bash
cd ~/Code/bite && git add -A && git commit -m "chore: 26.2 fabric 脚手架（模板改名 bite/com.eamon.bite）"
```

---

### Task 2: FreshnessMath 纯函数（TDD）

**Files:**
- Create: `src/main/java/com/eamon/bite/freshness/FreshnessGrade.java`、`src/main/java/com/eamon/bite/freshness/FreshnessMath.java`
- Test: `src/test/java/com/eamon/bite/freshness/FreshnessMathTest.java`
- Modify: `build.gradle`（加 JUnit）

**Interfaces:**
- Produces: `FreshnessMath.fraction(long now, FreshnessStamp stamp, ShelfLife life) -> double`；`FreshnessMath.mergeStamps(long now, FreshnessStamp s1, long count1, FreshnessStamp s2, long count2, ShelfLife life) -> FreshnessStamp`；`FreshnessMath.grade(double fraction) -> FreshnessGrade`；`FreshnessGrade` 枚举 `FRESH/STALE/OLD/SPOILED`
- 依赖 Task 3 的 record？**否** —— 本任务先定义最小 record 骨架（Task 3 再补 Codec），避免前向依赖

- [ ] **Step 1: 加 JUnit 依赖**

`build.gradle` 的 `dependencies` 块追加：

```gradle
dependencies {
    // ...模板已有内容...
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```
并在文件末尾追加：

```gradle
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: 定义两个 record 骨架 + 枚举**

```java
// src/main/java/com/eamon/bite/component/FreshnessStamp.java
package com.eamon.bite.component;

/** 打标时刻的游戏刻（game time，非 day time）。Task 3 会补充 CODEC/STREAM_CODEC/TooltipProvider。 */
public record FreshnessStamp(long creationGameTick) {
}
```

```java
// src/main/java/com/eamon/bite/component/ShelfLife.java
package com.eamon.bite.component;

/** 保质期（游戏刻）。负数 = 永不腐坏/非食物。 */
public record ShelfLife(long spoilTicks) {
    public static final ShelfLife NEVER = new ShelfLife(-1);
}
```

```java
// src/main/java/com/eamon/bite/freshness/FreshnessGrade.java
package com.eamon.bite.freshness;

/** 新鲜度档位（机制侧 4 档；进度条颜色另有 5 档映射，见客户端）。 */
public enum FreshnessGrade {
    FRESH, STALE, OLD, SPOILED
}
```

- [ ] **Step 3: 写失败测试**

```java
// src/test/java/com/eamon/bite/freshness/FreshnessMathTest.java
package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FreshnessMathTest {
    private static final ShelfLife LIFE = new ShelfLife(1000L);

    @Test
    void freshItemAtNowHasFullFraction() {
        assertEquals(1.0, FreshnessMath.fraction(5000, new FreshnessStamp(5000), LIFE), 1e-9);
    }

    @Test
    void fullyAgedItemHasZeroFraction() {
        assertEquals(0.0, FreshnessMath.fraction(6000, new FreshnessStamp(5000), LIFE), 1e-9);
    }

    @Test
    void fractionClampsBeyondBounds() {
        assertEquals(1.0, FreshnessMath.fraction(4000, new FreshnessStamp(5000), LIFE), 1e-9); // 未到保质期开始（未来戳）
        assertEquals(0.0, FreshnessMath.fraction(99999, new FreshnessStamp(0), LIFE), 1e-9);
    }

    @Test
    void neverSpoilAlwaysFull() {
        assertEquals(1.0, FreshnessMath.fraction(99999, new FreshnessStamp(0), ShelfLife.NEVER), 1e-9);
    }

    @Test
    void gradeThresholds() {
        assertEquals(FreshnessGrade.FRESH, FreshnessMath.grade(0.51));
        assertEquals(FreshnessGrade.STALE, FreshnessMath.grade(0.5));
        assertEquals(FreshnessGrade.STALE, FreshnessMath.grade(0.26));
        assertEquals(FreshnessGrade.OLD, FreshnessMath.grade(0.25));
        assertEquals(FreshnessGrade.OLD, FreshnessMath.grade(0.01));
        assertEquals(FreshnessGrade.SPOILED, FreshnessMath.grade(0.0));
    }

    @Test
    void mergeWeightedAverageDonStarveStyle() {
        // 1 个 100% + 1 个 50% → 75%
        long now = 5000;
        FreshnessStamp fresh = new FreshnessStamp(now);
        FreshnessStamp half = new FreshnessStamp(now - 500);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, fresh, 1, half, 1, LIFE);
        assertEquals(0.75, FreshnessMath.fraction(now, merged, LIFE), 1e-6);
    }

    @Test
    void mergeWeightedByCounts() {
        // 3 个 100% + 1 个 0%（临界未腐坏，fraction 极小正数）→ (3+ε)/4
        long now = 5000;
        FreshnessStamp fresh = new FreshnessStamp(now);
        FreshnessStamp almostSpoiled = new FreshnessStamp(now - 999);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, fresh, 3, almostSpoiled, 1, LIFE);
        assertEquals(0.75, FreshnessMath.fraction(now, merged, LIFE), 1e-3);
    }

    @Test
    void mergeNeverSpoilReturnsFreshOperand() {
        long now = 5000;
        FreshnessStamp a = new FreshnessStamp(1);
        FreshnessStamp b = new FreshnessStamp(2);
        assertEquals(b, FreshnessMath.mergeStamps(now, a, 1, b, 1, ShelfLife.NEVER));
    }

    @Test
    void mergeRoundTripsThroughFraction() {
        long now = 123456;
        FreshnessStamp s1 = new FreshnessStamp(now - 100);
        FreshnessStamp s2 = new FreshnessStamp(now - 900);
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, s1, 7, s2, 5, LIFE);
        double expected = (FreshnessMath.fraction(now, s1, LIFE) * 7 + FreshnessMath.fraction(now, s2, LIFE) * 5) / 12.0;
        assertEquals(expected, FreshnessMath.fraction(now, merged, LIFE), 1e-6);
    }
}
```

- [ ] **Step 4: 跑测试确认失败**

```bash
cd ~/Code/bite && JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
```
Expected: 编译失败 `cannot find symbol: FreshnessMath`。

- [ ] **Step 5: 实现 FreshnessMath**

```java
// src/main/java/com/eamon/bite/freshness/FreshnessMath.java
package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;

/** 新鲜度纯函数。双端共用；时间一律 game time。 */
public final class FreshnessMath {
    private FreshnessMath() {}

    /** 百分比 [0,1]。life.spoilTicks < 0 恒为 1。 */
    public static double fraction(long now, FreshnessStamp stamp, ShelfLife life) {
        if (life.spoilTicks() <= 0) return 1.0;
        double f = 1.0 - (double) (now - stamp.creationGameTick()) / life.spoilTicks();
        return Math.clamp(f, 0.0, 1.0);
    }

    /** 饥荒式合并：新鲜度按数量加权平均，反解出新时间戳（守恒）。 */
    public static FreshnessStamp mergeStamps(long now, FreshnessStamp s1, long count1, FreshnessStamp s2, long count2, ShelfLife life) {
        if (life.spoilTicks() <= 0) return s2;
        double f1 = fraction(now, s1, life);
        double f2 = fraction(now, s2, life);
        double merged = (f1 * count1 + f2 * count2) / (count1 + count2);
        long age = Math.round((1.0 - merged) * life.spoilTicks());
        return new FreshnessStamp(now - age);
    }

    public static FreshnessGrade grade(double fraction) {
        if (fraction <= 0.0) return FreshnessGrade.SPOILED;
        if (fraction <= 0.25) return FreshnessGrade.OLD;
        if (fraction <= 0.5) return FreshnessGrade.STALE;
        return FreshnessGrade.FRESH;
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
```
Expected: 全部 PASS。

- [ ] **Step 7: 提交**

```bash
git add -A && git commit -m "feat: FreshnessMath 新鲜度纯函数与档位（TDD）"
```

---

### Task 3: 数据组件注册 + FreshnessClock

**Files:**
- Modify: `src/main/java/com/eamon/bite/component/FreshnessStamp.java`（补 Codec/StreamCodec）
- Create: `src/main/java/com/eamon/bite/component/BiteComponents.java`、`src/main/java/com/eamon/bite/freshness/FreshnessClock.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（注册调用 + 时钟接线）

**Interfaces:**
- Consumes: Task 2 的 record
- Produces: `BiteComponents.FRESHNESS: DataComponentType<FreshnessStamp>`、`BiteComponents.SHELF_LIFE: DataComponentType<ShelfLife>`、`FreshnessClock.now() -> long`（双端每 tick 更新的 game time 缓存）

- [ ] **Step 1: 补全 record 的序列化器**

```java
// src/main/java/com/eamon/bite/component/FreshnessStamp.java
package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;

public record FreshnessStamp(long creationGameTick) {
    public static final Codec<FreshnessStamp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("creation_tick").forGetter(FreshnessStamp::creationGameTick)
    ).apply(instance, FreshnessStamp::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreshnessStamp> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, FreshnessStamp::creationGameTick, FreshnessStamp::new);
}
```

```java
// src/main/java/com/eamon/bite/component/ShelfLife.java
package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ShelfLife(long spoilTicks) {
    public static final ShelfLife NEVER = new ShelfLife(-1);

    public static final Codec<ShelfLife> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("spoil_ticks").forGetter(ShelfLife::spoilTicks)
    ).apply(instance, ShelfLife::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfLife> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, ShelfLife::spoilTicks, ShelfLife::new);
}
```

- [ ] **Step 2: 注册类**

```java
// src/main/java/com/eamon/bite/component/BiteComponents.java
package com.eamon.bite.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import static com.eamon.bite.BiteMod.MOD_ID;

public final class BiteComponents {
    public static final DataComponentType<FreshnessStamp> FRESHNESS = register("freshness", FreshnessStamp.CODEC, FreshnessStamp.STREAM_CODEC);
    public static final DataComponentType<ShelfLife> SHELF_LIFE = register("shelf_life", ShelfLife.CODEC, ShelfLife.STREAM_CODEC);

    private BiteComponents() {}

    private static <T> DataComponentType<T> register(String path, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, path),
            DataComponentType.<T>builder().persistent(codec).networkSynchronized(streamCodec).build());
    }
}
```

> ⚠️ 编译期检查点：若 `Identifier` 的包名不对（26.2 可能是 `net.minecraft.resources.Identifier` 或 `net.minecraft.util.Identifier`），用 IDE 自动修复 import；`DataComponentType.Builder` 在 26.2 **没有** `cachingCodec()` 方法，只有 `persistent/networkSynchronized/cacheEncoding/ignoreSwapAnimation`。

- [ ] **Step 3: FreshnessClock**

```java
// src/main/java/com/eamon/bite/freshness/FreshnessClock.java
package com.eamon.bite.freshness;

/** 双端每 tick 更新的 game time 缓存。供无 Level 上下文的静态钩子（等价性放宽、物品条渲染）读取。 */
public final class FreshnessClock {
    private static volatile long now;

    private FreshnessClock() {}

    public static void update(long gameTime) { now = gameTime; }
    public static long now() { return now; }
}
```

- [ ] **Step 4: BiteMod 接线**

```java
// src/main/java/com/eamon/bite/BiteMod.java
package com.eamon.bite;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.freshness.FreshnessClock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BiteMod implements ModInitializer {
    public static final String MOD_ID = "bite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BiteComponents.FRESHNESS.getClass(); // 触发静态注册
        ServerTickEvents.END_SERVER_TICK.register(server ->
            FreshnessClock.update(server.overworld().getGameTime()));
        LOGGER.info("Because It's Too Easy initialized");
    }
}
```

```java
// src/client/java/com/eamon/bite/client/BiteModClient.java
package com.eamon.bite.client;

import com.eamon.bite.freshness.FreshnessClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class BiteModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null) FreshnessClock.update(client.level.getGameTime());
        });
    }
}
```

- [ ] **Step 5: 构建验证**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat: bite:freshness / bite:shelf_life 数据组件与 FreshnessClock"
```

---

### Task 4: 配置（Gson，TDD）

**Files:**
- Create: `src/main/java/com/eamon/bite/config/ServerConfig.java`、`src/main/java/com/eamon/bite/config/ClientConfig.java`
- Test: `src/test/java/com/eamon/bite/config/ServerConfigTest.java`

**Interfaces:**
- Produces: `ServerConfig.load(Path) -> ServerConfig`（缺省写默认文件）、`ServerConfig.get() -> ServerConfig`、字段见下；`ClientConfig.load(Path)`。字段名即 JSON 键。
- 字段（spec §9）：`enabled`、`scanIntervalTicks`、`shelfLifeDays: Map<String,Integer>`（键含 `raw_meat/raw_fish/cooked_meat/cooked_fish/bread/vegetable/fruit/berry/dough/soup/cookie/pie/candy/default`）、`itemOverrides: Map<String,Integer>`、`staleThreshold=0.5`、`oldThreshold=0.25`、`nutritionScaleStale=0.75`、`nutritionScaleOld=0.5`、`hungerEffectChance=0.3`、`hungerEffectDurationTicks=160`、`spoiledInedible=true`

- [ ] **Step 1: 失败测试**

```java
// src/test/java/com/eamon/bite/config/ServerConfigTest.java
package com.eamon.bite.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @Test
    void parsesFullJson() {
        String json = """
            {
              "enabled": true,
              "scan_interval_ticks": 50,
              "shelf_life_days": {"raw_meat": 2, "default": 7},
              "item_overrides": {"minecraft:golden_apple": -1, "somemod:sushi": 3},
              "stale_threshold": 0.5,
              "old_threshold": 0.25,
              "nutrition_scale_stale": 0.75,
              "nutrition_scale_old": 0.5,
              "hunger_effect_chance": 0.3,
              "hunger_effect_duration_ticks": 160,
              "spoiled_inedible": true
            }
            """;
        ServerConfig cfg = ServerConfig.fromJson(json);
        assertTrue(cfg.enabled());
        assertEquals(50, cfg.scanIntervalTicks());
        assertEquals(2, cfg.shelfLifeDays().get("raw_meat"));
        assertEquals(-1, cfg.itemOverrides().get("minecraft:golden_apple"));
        assertEquals(3, cfg.itemOverrides().get("somemod:sushi"));
        assertTrue(cfg.spoiledInedible());
    }

    @Test
    void missingFieldsFallBackToDefaults() {
        ServerConfig cfg = ServerConfig.fromJson("{}");
        assertTrue(cfg.enabled());
        assertEquals(100, cfg.scanIntervalTicks());
        assertEquals(6, cfg.shelfLifeDays().get("bread"));
        assertTrue(cfg.itemOverrides().containsKey("minecraft:golden_apple"));
        assertEquals(0.75, cfg.nutritionScaleStale());
    }

    @Test
    void roundTrips() {
        ServerConfig cfg = ServerConfig.fromJson(ServerConfig.DEFAULT.toJson());
        assertEquals(cfg, ServerConfig.fromJson(cfg.toJson()));
    }

    @Test
    void defaultOverridesIncludeVanillaExemptions() {
        Map<String, Integer> ov = ServerConfig.DEFAULT.itemOverrides();
        assertEquals(-1, ov.get("minecraft:golden_apple"));
        assertEquals(-1, ov.get("minecraft:enchanted_golden_apple"));
        assertEquals(-1, ov.get("minecraft:rotten_flesh"));
        assertEquals(-1, ov.get("minecraft:spider_eye"));
        assertEquals(-1, ov.get("minecraft:poisonous_potato"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
```
Expected: 编译失败 `cannot find symbol: ServerConfig`。

- [ ] **Step 3: 实现 ServerConfig**

```java
// src/main/java/com/eamon/bite/config/ServerConfig.java
package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** 服务端权威配置。v1 重启生效（spec §9）。 */
public record ServerConfig(
    boolean enabled,
    int scanIntervalTicks,
    Map<String, Integer> shelfLifeDays,
    Map<String, Integer> itemOverrides,
    double staleThreshold,
    double oldThreshold,
    double nutritionScaleStale,
    double nutritionScaleOld,
    double hungerEffectChance,
    int hungerEffectDurationTicks,
    boolean spoiledInedible
) {
    public static final ServerConfig DEFAULT = createDefault();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ServerConfig instance = DEFAULT;

    public static ServerConfig get() { return instance; }

    private static ServerConfig createDefault() {
        Map<String, Integer> shelf = new LinkedHashMap<>();
        shelf.put("raw_meat", 2); shelf.put("raw_fish", 2);
        shelf.put("cooked_meat", 4); shelf.put("cooked_fish", 4);
        shelf.put("bread", 6); shelf.put("vegetable", 6); shelf.put("fruit", 6);
        shelf.put("berry", 6); shelf.put("dough", 6);
        shelf.put("soup", 3); shelf.put("cookie", 3); shelf.put("pie", 3); shelf.put("candy", 3);
        shelf.put("default", 7);
        Map<String, Integer> overrides = new TreeMap<>();
        overrides.put("minecraft:golden_apple", -1);
        overrides.put("minecraft:enchanted_golden_apple", -1);
        overrides.put("minecraft:rotten_flesh", -1);
        overrides.put("minecraft:spider_eye", -1);
        overrides.put("minecraft:poisonous_potato", -1);
        return new ServerConfig(true, 100, shelf, overrides,
            0.5, 0.25, 0.75, 0.5, 0.3, 160, true);
    }

    public static ServerConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        ServerConfig d = DEFAULT;
        Map<String, Integer> shelf = raw.shelf_life_days == null ? d.shelfLifeDays : raw.shelf_life_days;
        if (!shelf.containsKey("default")) shelf.put("default", 7);
        return new ServerConfig(
            orDefault(raw.enabled, d.enabled),
            orDefault(raw.scan_interval_ticks, d.scanIntervalTicks),
            shelf,
            raw.item_overrides == null ? d.itemOverrides : raw.item_overrides,
            orDefault(raw.stale_threshold, d.staleThreshold),
            orDefault(raw.old_threshold, d.oldThreshold),
            orDefault(raw.nutrition_scale_stale, d.nutritionScaleStale),
            orDefault(raw.nutrition_scale_old, d.nutritionScaleOld),
            orDefault(raw.hunger_effect_chance, d.hungerEffectChance),
            orDefault(raw.hunger_effect_duration_ticks, d.hungerEffectDurationTicks),
            orDefault(raw.spoiled_inedible, d.spoiledInedible));
    }

    private static int orDefault(Integer v, int d) { return v == null ? d : v; }
    private static double orDefault(Double v, double d) { return v == null ? d : v; }
    private static boolean orDefault(Boolean v, boolean d) { return v == null ? d : v; }

    public String toJson() {
        Raw raw = new Raw();
        raw.enabled = enabled; raw.scan_interval_ticks = scanIntervalTicks;
        raw.shelf_life_days = shelfLifeDays; raw.item_overrides = itemOverrides;
        raw.stale_threshold = staleThreshold; raw.old_threshold = oldThreshold;
        raw.nutrition_scale_stale = nutritionScaleStale; raw.nutrition_scale_old = nutritionScaleOld;
        raw.hunger_effect_chance = hungerEffectChance; raw.hunger_effect_duration_ticks = hungerEffectDurationTicks;
        raw.spoiled_inedible = spoiledInedible;
        return GSON.toJson(raw);
    }

    /** 从 config/bite/server.json 加载；文件缺失时写默认值。 */
    public static ServerConfig load(Path configDir) {
        Path file = configDir.resolve("bite").resolve("server.json");
        try {
            if (Files.exists(file)) {
                instance = fromJson(Files.readString(file));
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT.toJson());
                instance = DEFAULT;
            }
        } catch (IOException e) {
            BiteMod.LOGGER.error("Failed to load server config, using defaults", e);
            instance = DEFAULT;
        }
        return instance;
    }

    /** Gson 反序列化骨架（snake_case JSON 键）。 */
    private static final class Raw {
        Boolean enabled;
        Integer scan_interval_ticks;
        Map<String, Integer> shelf_life_days;
        Map<String, Integer> item_overrides;
        Double stale_threshold;
        Double old_threshold;
        Double nutrition_scale_stale;
        Double nutrition_scale_old;
        Double hunger_effect_chance;
        Integer hunger_effect_duration_ticks;
        Boolean spoiled_inedible;
    }
}
```

`ClientConfig` 同模式，字段：`showBar=true`、`showTooltip=true`、`tooltipStyle="percent_and_time"`（可选 `percent` / `time`）。文件 `config/bite/client.json`。实现省略号处照 ServerConfig 复制模式（字段少，直接写全）：

```java
// src/main/java/com/eamon/bite/config/ClientConfig.java
package com.eamon.bite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.eamon.bite.BiteMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ClientConfig(boolean showBar, boolean showTooltip, String tooltipStyle) {
    public static final ClientConfig DEFAULT = new ClientConfig(true, true, "percent_and_time");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ClientConfig instance = DEFAULT;

    public static ClientConfig get() { return instance; }

    public static ClientConfig fromJson(String json) {
        Raw raw = GSON.fromJson(json, Raw.class);
        if (raw == null) return DEFAULT;
        String style = raw.tooltip_style == null ? DEFAULT.tooltipStyle : raw.tooltip_style;
        if (!style.equals("percent") && !style.equals("time") && !style.equals("percent_and_time")) style = DEFAULT.tooltipStyle;
        return new ClientConfig(
            raw.show_bar == null ? DEFAULT.showBar : raw.show_bar,
            raw.show_tooltip == null ? DEFAULT.showTooltip : raw.show_tooltip,
            style);
    }

    public String toJson() {
        Raw raw = new Raw();
        raw.show_bar = showBar; raw.show_tooltip = showTooltip; raw.tooltip_style = tooltipStyle;
        return GSON.toJson(raw);
    }

    public static ClientConfig load(Path configDir) {
        Path file = configDir.resolve("bite").resolve("client.json");
        try {
            if (Files.exists(file)) {
                instance = fromJson(Files.readString(file));
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT.toJson());
                instance = DEFAULT;
            }
        } catch (IOException e) {
            BiteMod.LOGGER.error("Failed to load client config, using defaults", e);
            instance = DEFAULT;
        }
        return instance;
    }

    private static final class Raw {
        Boolean show_bar;
        Boolean show_tooltip;
        String tooltip_style;
    }
}
```

在 `BiteMod.onInitialize()` 开头加：`ServerConfig.load(FabricLoader.getInstance().getConfigDir());`
在 `BiteModClient.onInitializeClient()` 开头加：`ClientConfig.load(FabricLoader.getInstance().getConfigDir());`
（import `net.fabricmc.loader.api.FabricLoader`。）

- [ ] **Step 4: 跑测试确认通过**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
```
Expected: 全 PASS。

- [ ] **Step 5: 提交**

```bash
git add -A && git commit -m "feat: server/client 配置（Gson，零依赖）"
```

---

### Task 5: ShelfLifeRegistry + 默认组件接线

**Files:**
- Create: `src/main/java/com/eamon/bite/freshness/ShelfLifeRegistry.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（注册 DefaultItemComponentEvents）
- Test: 由 Task 6 的 gametest 覆盖（tag 查询需要真实注册表，不做纯单测）

**Interfaces:**
- Consumes: `ServerConfig`（Task 4）
- Produces: `ShelfLifeRegistry.resolveShelfLifeTicks(Item) -> long`（游戏刻；-1 = 永不腐坏/非食物）；`ShelfLifeRegistry.categoryOf(Item) -> String`（调试用）

- [ ] **Step 1: 实现 ShelfLifeRegistry**

```java
// src/main/java/com/eamon/bite/freshness/ShelfLifeRegistry.java
package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;

import java.util.List;

/** 保质期解析：item_overrides > c:foods 分类 > 兜底 default。返回游戏刻；-1 = 永不/非食物。 */
public final class ShelfLifeRegistry {
    public static final long DAY = 24000L;

    /** 顺序即优先级（先命中先用）。 */
    private static final List<String> CATEGORIES = List.of(
        "raw_meat", "raw_fish", "cooked_meat", "cooked_fish",
        "bread", "vegetable", "fruit", "berry", "dough",
        "soup", "cookie", "pie", "candy");

    private ShelfLifeRegistry() {}

    public static long resolveShelfLifeTicks(Item item) {
        String id = describe(item);
        Integer override = ServerConfig.get().itemOverrides().get(id);
        if (override != null) return override < 0 ? -1 : override * DAY;
        if (!isFood(item)) return -1;
        String cat = categoryOf(item);
        Integer days = ServerConfig.get().shelfLifeDays().get(cat);
        if (days == null) days = ServerConfig.get().shelfLifeDays().get("default");
        if (days == null || days < 0) return -1;
        return days * DAY;
    }

    public static String categoryOf(Item item) {
        for (String cat : CATEGORIES) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "foods/" + cat));
            if (item.builtInRegistryHolder().is(tag)) return cat;
        }
        return "default";
    }

    private static boolean isFood(Item item) {
        return item.components().has(DataComponents.FOOD)
            || item.components().has(DataComponents.CONSUMABLE);
    }

    private static String describe(Item item) {
        return ResourceKey.create(Registries.ITEM,
            net.minecraft.core.Registry.getElementKey?? 
    }
}
```

⚠️ 最后一处 `describe` 需要运行时确认 —— 26.2 的注册表 ID 获取 API 因「注册表 ID 拆分」重构而变（spec §2 风险表 #5 相关）。用 genSources 确认后选其一：

```java
// 候选 A（若 Registries.ITEM 的 keyOf/getKey 存在）：
private static String describe(Item item) {
    return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
}
// 候选 B（若走 ItemIds 拆分类）：
private static String describe(Item item) {
    return net.minecraft.world.item.ItemIds.keyOf(item).toString(); // 名字以 genSources 为准
}
```

- [ ] **Step 2: DefaultItemComponentEvents 接线（默认保质期组件）**

在 `BiteMod.onInitialize()` 中追加：

```java
DefaultItemComponentEvents.MODIFY.register(event -> event.modify(
    ShelfLifeRegistry::isFoodItemForDefaults,
    (item, components) -> {
        long ticks = ShelfLifeRegistry.resolveShelfLifeTicks(item);
        if (ticks > 0) components.set(BiteComponents.SHELF_LIFE, new ShelfLife(ticks));
    }));
```

`ShelfLifeRegistry` 追加：

```java
public static boolean isFoodItemForDefaults(Item item) { return isFood(item); }
```

⚠️ 编译期检查点：`DefaultItemComponentEvents.MODIFY` 的 handler 形参与 `event.modify` 的重载签名（`modify(Item, BiConsumer)` 还是 `modify(Predicate<Item>, ModifyConsumer)`）以 Fabric API 26.2 源码为准 —— `./gradlew genSources` 后在 External Libraries 里搜 `DefaultItemComponentEvents` 对齐形参。语义目标固定：**所有食物 item 的默认组件获得 `bite:shelf_life`**。若 `components` 参数类型是 `DataComponentMap.Builder`，`set` 写法不变。

- [ ] **Step 3: 构建验证 + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
git add -A && git commit -m "feat: 保质期解析（c:foods 分类 + 覆盖表）与默认组件接线"
```

---

### Task 6: GameTest 基建 + FreshnessStamper（loot 打标）

**Files:**
- Modify: `build.gradle`（configureTests）
- Create: `src/gametest/java/com/eamon/bite/test/FreshnessGameTests.java`、`src/gametest/resources/fabric.mod.json`（若 DSL 未自动生成则手写）、`src/main/resources/data/bite/loot_table/gametest/food.json`
- Create: `src/main/java/com/eamon/bite/freshness/FreshnessStamper.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（loot 事件注册）

**Interfaces:**
- Consumes: `BiteComponents`（Task 3）
- Produces: `FreshnessStamper.stamp(ItemStack stack, long now)`（幂等：已有 stamp 不覆盖；非食物/永不腐坏跳过）
- Produces: gametest 基建（后续任务的测试都写进 `FreshnessGameTests`）

- [ ] **Step 1: 配置 gametest 源集**

`build.gradle` 追加：

```gradle
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "bite-gametest"
        eula = true
    }
}
```

`src/gametest/resources/fabric.mod.json`（若 DSL 生成则改为如下内容）：

```json
{
  "schemaVersion": 1,
  "id": "bite-gametest",
  "version": "${version}",
  "environment": "*",
  "entrypoints": {
    "fabric-gametest": ["com.eamon.bite.test.FreshnessGameTests"]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "fabric-api": "*",
    "bite": "*"
  }
}
```

- [ ] **Step 2: 测试用 loot table（确定性掉面包）**

`src/main/resources/data/bite/loot_table/gametest/food.json`：

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:bread" }
      ]
    }
  ]
}
```

- [ ] **Step 3: FreshnessStamper**

```java
// src/main/java/com/eamon/bite/freshness/FreshnessStamper.java
package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/** 打标（幂等）：给食物写入出生时间戳。 */
public final class FreshnessStamper {
    private FreshnessStamper() {}

    /** 已有 stamp / 非食物 / 永不腐坏 → 不动。 */
    public static void stamp(ItemStack stack, long now) {
        if (stack.isEmpty() || stack.has(BiteComponents.FRESHNESS)) return;
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (life == null) life = new ShelfLife(ShelfLifeRegistry.resolveShelfLifeTicks(stack.getItem()));
        if (life.spoilTicks() <= 0) return;
        stack.set(BiteComponents.FRESHNESS, new FreshnessStamp(now));
    }
}
```

`BiteMod.onInitialize()` 追加 loot 事件：

```java
LootTableEvents.MODIFY_DROPS.register((key, context, drops) -> {
    long now = context.getLevel().getGameTime();
    for (ItemStack drop : drops) FreshnessStamper.stamp(drop, now);
});
```

（import `net.fabricmc.fabric.api.loot.v3.LootTableEvents`。）

- [ ] **Step 4: 第一个 gametest（loot 打标）**

```java
// src/gametest/java/com/eamon/bite/test/FreshnessGameTests.java
package com.eamon.bite.test;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootContextParamSets;
import net.minecraft.world.level.storage.loot.LootContextParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

public class FreshnessGameTests implements FabricGameTest {

    @net.fabricmc.fabric.api.gametest.v1.GameTest
    public void lootDropsGetStamped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("bite", "gametest/food")));
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, helper.absolutePos(BlockPos.containing(1, 1, 1)).getCenter())
            .create(LootContextParamSets.CHEST);
        List<ItemStack> drops = new ArrayList<>();
        table.getRandomItems(params, drops::add);

        Assertions.assertFalse(drops.isEmpty(), "loot table should drop bread");
        for (ItemStack drop : drops) {
            FreshnessStamp stamp = drop.get(BiteComponents.FRESHNESS);
            Assertions.assertNotNull(stamp, "loot drop should be stamped");
            Assertions.assertEquals(level.getGameTime(), stamp.creationGameTick());
        }
        helper.succeed();
    }
}
```

⚠️ 编译期检查点：`reloadableRegistries().getLootTable` 的调用链若在 26.2 改名（候选：`server.getLootData().getLootTable`），以 genSources/IDE 自动补全为准；`@GameTest` 用 **Fabric 的** `net.fabricmc.fabric.api.gametest.v1.GameTest`（不是 vanilla 的）。

- [ ] **Step 5: 跑 gametest**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
Expected: gametest 随 build 运行且 PASS（Fabric 文档：服务端 gametest 随 `build` 无头执行）。若 build 未触发测试，运行 `JAVA_HOME=... ./gradlew runGameTest`（以 `./gradlew tasks --all | grep -i test` 列出的任务名为准）。
失败排查：`run/logs`、`run/gametest` 输出。

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "feat: loot 掉落打标 + gametest 基建"
```

---

### Task 7: FreshnessScanner（懒扫描）

**Files:**
- Create: `src/main/java/com/eamon/bite/freshness/FreshnessScanner.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（tick 接线）
- Test: `FreshnessGameTests` 追加用例

**Interfaces:**
- Consumes: `FreshnessStamper`、`ServerConfig.scanIntervalTicks()`
- Produces: `FreshnessScanner.scanLevel(ServerLevel level)`（打标 + 兜底一切非 loot 来源）

- [ ] **Step 1: 实现 Scanner**

```java
// src/main/java/com/eamon/bite/freshness/FreshnessScanner.java
package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** 懒扫描：玩家背包 + 已加载区块容器 + 容器实体。未打标的食物首次被观察到时打标。 */
public final class FreshnessScanner {
    private FreshnessScanner() {}

    public static void scanLevel(ServerLevel level, long now) {
        for (var player : level.players()) {
            scanContainer(player.getInventory(), now);
        }
        for (LevelChunk chunk : loadedChunks(level)) {
            for (var be : chunk.getBlockEntities().values()) {
                if (be instanceof RandomizableContainerBlockEntity rcbe && rcbe.getLootTable() != null) continue; // 未开箱不观察
                if (be instanceof Container container) scanContainer(container, now);
            }
        }
        for (var entity : level.getAllEntities()) {
            if (entity instanceof Container container) scanContainer(container, now);
        }
    }

    private static void scanContainer(Container container, long now) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            FreshnessStamper.stamp(stack, now);
        }
    }

    private static Iterable<LevelChunk> loadedChunks(ServerLevel level) {
        // 首选公开 API；编译期确认（见 Step 2 检查点）
        return level.getChunkSource().chunkMap.getChunks();
    }
}
```

`BiteMod.onInitialize()` 追加：

```java
ServerTickEvents.END_LEVEL_TICK.register(level -> {
    if (level.getGameTime() % ServerConfig.get().scanIntervalTicks() != 0) return;
    if (!ServerConfig.get().enabled()) return;
    FreshnessScanner.scanLevel(level, level.getGameTime());
});
```

- [ ] **Step 2: 编译期检查点（区块枚举路径）**

`loadedChunks` 的实现按以下顺序在 genSources 里确认（`grep -r "getChunks" ~/.gradle/caches/fabric-loom/.../minecraft-source` 或 IDE 搜 `ChunkMap`）：
1. `level.getChunkSource().chunkMap.getChunks()`（若 `chunkMap` 字段与 `getChunks()` 均 public —— 直接用）
2. 若字段 private → 写 accessor mixin（`src/main/java/com/eamon/bite/mixin/ChunkMapAccessor.java`，加进 `bite.mixins.json`）：

```java
package com.eamon.bite.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Iterator;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("chunks")
    Iterator<LevelChunk> bite$getLoadedChunks(); // 字段类型以 genSources 为准（可能是 Iterable/Collection<ChunkHolder>，按实际调整）
}
```

- [ ] **Step 3: gametest（懒扫描打标 + 未开箱跳过）**

`FreshnessGameTests` 追加：

```java
@net.fabricmc.fabric.api.gametest.v1.GameTest
public void scannerStampsUnstampedFood(GameTestHelper helper) {
    // 放一个箱子并塞入无 stamp 的面包；扫描周期后应被打标
    helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
    ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
    ItemStack bread = new ItemStack(Items.BREAD);
    org.junit.jupiter.api.Assertions.assertFalse(bread.has(BiteComponents.FRESHNESS));
    chest.setItem(0, bread);

    int interval = com.eamon.bite.config.ServerConfig.get().scanIntervalTicks();
    helper.startSequence()
        .thenExecuteAfter(interval + 40, () -> {
            ItemStack stacked = chest.getItem(0);
            org.junit.jupiter.api.Assertions.assertTrue(stacked.has(BiteComponents.FRESHNESS),
                "scanner should have stamped chest food");
        })
        .thenSucceed();
}
```

- [ ] **Step 4: 跑 build（含 gametest）+ 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
git add -A && git commit -m "feat: 懒扫描打标（玩家背包/容器/容器实体）"
```

---

### Task 8: StackingRules（堆叠规则纯逻辑）

**Files:**
- Create: `src/main/java/com/eamon/bite/freshness/StackingRules.java`
- Test: `src/test/java/com/eamon/bite/freshness/StackingRulesTest.java`（纯函数部分）

**Interfaces:**
- Consumes: `FreshnessMath`、`BiteComponents`、`FreshnessClock`
- Produces: `StackingRules.canMergeRelaxed(ItemStack a, ItemStack b) -> boolean`（mixin 等价性放宽的判定）；`StackingRules.reconcile(ItemStack dest, FreshnessStamp destStampBefore, int destCountBefore, FreshnessStamp originStamp)`（mixin 合并重算入口：dest 当前数量 > destCountBefore 时，按加权平均重写 dest 的 stamp）

- [ ] **Step 1: 失败测试（可纯测的部分：期望值计算）**

```java
// src/test/java/com/eamon/bite/freshness/StackingRulesTest.java
package com.eamon.bite.freshness;

import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackingRulesTest {
    private static final ShelfLife LIFE = new ShelfLife(1000);

    @Test
    void mergedStampForDeltaMatchesWeightedAverage() {
        long now = 5000;
        FreshnessStamp destBefore = new FreshnessStamp(now - 100);       // 0.9
        FreshnessStamp origin = new FreshnessStamp(now - 900);           // 0.1
        FreshnessStamp merged = StackingRules.mergedStampFor(now, destBefore, 3, origin, 2, LIFE);
        double expected = (0.9 * 3 + 0.1 * 2) / 5.0;
        assertEquals(expected, FreshnessMath.fraction(now, merged, LIFE), 1e-6);
    }
}
```

- [ ] **Step 2: 确认失败**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
```
Expected: 编译失败。

- [ ] **Step 3: 实现**

```java
// src/main/java/com/eamon/bite/freshness/StackingRules.java
package com.eamon.bite.freshness;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import net.minecraft.world.item.ItemStack;

/** 饥荒式堆叠规则（spec §6）。被 mixin 调用；无 Level 上下文，时间取 FreshnessClock。 */
public final class StackingRules {
    private StackingRules() {}

    /** 等价性放宽判定：同 item、双方已打标、保质期有限、双方未腐坏（fraction>0）。 */
    public static boolean canMergeRelaxed(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || !a.is(b.getItem())) return false;
        FreshnessStamp sa = a.get(BiteComponents.FRESHNESS);
        FreshnessStamp sb = b.get(BiteComponents.FRESHNESS);
        if (sa == null || sb == null) return false;
        ShelfLife la = a.get(BiteComponents.SHELF_LIFE);
        ShelfLife lb = b.get(BiteComponents.SHELF_LIFE);
        if (la == null || la.spoilTicks() <= 0) return false;
        if (lb == null || lb.spoilTicks() <= 0) return false;
        long now = FreshnessClock.now();
        return FreshnessMath.fraction(now, sa, la) > 0.0 && FreshnessMath.fraction(now, sb, lb) > 0.0;
    }

    /** 合并重算：dest 数量已增加 delta = dest.getCount() - destCountBefore 时调用。 */
    public static void reconcile(ItemStack dest, FreshnessStamp destStampBefore, int destCountBefore, FreshnessStamp originStamp) {
        int delta = dest.getCount() - destCountBefore;
        if (delta <= 0 || destStampBefore == null || originStamp == null) return;
        ShelfLife life = dest.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        long now = FreshnessClock.now();
        FreshnessStamp merged = FreshnessMath.mergeStamps(now, destStampBefore, destCountBefore, originStamp, delta, life);
        dest.set(BiteComponents.FRESHNESS, merged);
    }

    /** 纯函数：给定两边快照与移动数量，返回合并后的 stamp（测试用）。 */
    static FreshnessStamp mergedStampFor(long now, FreshnessStamp destBefore, int destCount, FreshnessStamp origin, int moved, ShelfLife life) {
        return FreshnessMath.mergeStamps(now, destBefore, destCount, origin, moved, life);
    }
}
```

- [ ] **Step 4: 测试通过 + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew test
git add -A && git commit -m "feat: 饥荒式堆叠规则（放宽判定 + 加权合并重算）"
```

---

### Task 9: 等价性放宽 mixin

**Files:**
- Create: `src/main/java/com/eamon/bite/mixin/ItemStackMixin.java`
- Modify: `src/main/resources/bite.mixins.json`（注册 mixin）

**Interfaces:**
- Consumes: `StackingRules.canMergeRelaxed`
- Produces: 不同新鲜度的同种未腐坏食物在原版一切堆叠检查处视为可合并

- [ ] **Step 1: mixin 实现**

```java
// src/main/java/com/eamon/bite/mixin/ItemStackMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 等价性放宽（spec §6.2-A）：单点覆盖全部 26 个原版调用点。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "isSameItemSameComponents", at = @At("RETURN"), cancellable = true)
    private static void bite$relaxFreshnessEquality(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (StackingRules.canMergeRelaxed(a, b)) cir.setReturnValue(true);
    }
}
```

`bite.mixins.json`（`"mixins"` 数组加入 `"ItemStackMixin"`；保留模板的 package/compatLevel 结构）：

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.eamon.bite.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": ["ItemStackMixin"],
  "client": [],
  "injectors": { "defaultRequire": 1 }
}
```

（compatLevel 若模板/校验器报不支持 JAVA_25，用模板原值。）

- [ ] **Step 2: 构建验证**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
Expected: BUILD SUCCESSFUL（mixin 应用错误会在启动期报，Step 3 覆盖）。

- [ ] **Step 3: 游戏内手动验证（需人观察，runClient + 临时作弊）**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew runClient
```
1. 创建创造模式世界，输入 `/gamerule doDaylightCycle false` 固定时间
2. `/give @s minecraft:beef 1`，用 F3 看到 game time 记为 T
3. 等待约 10 分钟（牛肉 2 游戏天 = 48000 ticks ≈ 真实 40 分钟太久 —— **改用 `/time add 40000` 快进**，此时第一块牛肉约 83% 新鲜度）
4. 再 `/give @s minecraft:beef 1`（新牛肉 100%）
5. 把两块牛肉放进同一个箱子/背包槽位相邻，Shift 点击其中一块到另一块：**两块应合并成 x2**（此刻合并后的 stamp 是 destination-wins，Task 10 才会加权平均 —— 本步只验证「能合并」）
6. 验证反例：`/give @s minecraft:porkchop` 与牛肉不应合并（不同 item）

- [ ] **Step 4: 提交**

```bash
git add -A && git commit -m "feat: ItemStack 等价性放宽 mixin（饥荒式可合并）"
```

---

### Task 10: 合并重算 mixin ×5 + gametest

**Files:**
- Create: `src/main/java/com/eamon/bite/mixin/SlotMixin.java`、`InventoryMixin.java`、`AbstractContainerMenuMixin.java`、`ItemEntityMixin.java`、`SimpleContainerMixin.java`
- Modify: `bite.mixins.json`
- Test: `FreshnessGameTests` 追加合并用例

**Interfaces:**
- Consumes: `StackingRules.reconcile(dest, destStampBefore, destCountBefore, originStamp)`
- Produces: 5 个数量移动点的加权平均重算（spec §6.2-B）

统一模式（「快照-重算」）：HEAD 时快照 dest 的 stamp/count 与 origin 的 stamp；RETURN 时若 dest 数量增加则 `StackingRules.reconcile(...)`。字段用 `@Unique` 前缀 `bite$`。

- [ ] **Step 1: SlotMixin（手动放入槽位）**

```java
// src/main/java/com/eamon/bite/mixin/SlotMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"))
    private void bite$capture(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack dest = ((Slot) (Object) this).getItem();
        bite$destStamp = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        bite$destCount = dest.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void bite$reconcile(ItemStack origin, int count, CallbackInfoReturnable<ItemStack> cir) {
        if (bite$destStamp == null || bite$originStamp == null) { bite$reset(); return; }
        ItemStack dest = ((Slot) (Object) this).getItem();
        StackingRules.reconcile(dest, bite$destStamp, bite$destCount, bite$originStamp);
        bite$reset();
    }

    @Unique
    private void bite$reset() { bite$destStamp = null; bite$originStamp = null; bite$destCount = 0; }
}
```

- [ ] **Step 2: InventoryMixin（背包放入/地面拾取）**

```java
// src/main/java/com/eamon/bite/mixin/InventoryMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"))
    private void bite$capture(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        ItemStack dest = ((Inventory) (Object) this).getItem(slot);
        bite$destStamp = dest.isEmpty() ? null : dest.get(BiteComponents.FRESHNESS);
        bite$destCount = dest.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private void bite$reconcile(int slot, ItemStack origin, CallbackInfoReturnable<Integer> cir) {
        if (bite$destStamp != null && bite$originStamp != null) {
            ItemStack dest = ((Inventory) (Object) this).getItem(slot);
            StackingRules.reconcile(dest, bite$destStamp, bite$destCount, bite$originStamp);
        }
        bite$reset();
    }

    @Unique
    private void bite$reset() { bite$destStamp = null; bite$originStamp = null; bite$destCount = 0; }
}
```

⚠️ 编译期检查点：`addResource` 在 26.2 可能有两个重载 `(I Lnet/minecraft/world/item/ItemStack;)I` 与 `(Lnet/minecraft/world/item/ItemStack;)I`。用 `javap -p`（见 Step 7）确认背包拾取实际走哪个（或都挂 —— 把上面的 method 字符串各写一份，HEAD/RETURN 成对）。gametest（Step 6）会验证链路通不通。

- [ ] **Step 3: ItemEntityMixin（掉落物互相合并）**

```java
// src/main/java/com/eamon/bite/mixin/ItemEntityMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "merge", at = @At("HEAD"))
    private void bite$capture(ItemStack destination, ItemStack origin, int amount, CallbackInfo ci) {
        bite$destStamp = destination.get(BiteComponents.FRESHNESS);
        bite$destCount = destination.getCount();
        bite$originStamp = origin.get(BiteComponents.FRESHNESS);
    }

    @Inject(method = "merge", at = @At("TAIL"))
    private void bite$reconcile(ItemStack destination, ItemStack origin, int amount, CallbackInfo ci) {
        if (bite$destStamp != null && bite$originStamp != null) {
            StackingRules.reconcile(destination, bite$destStamp, bite$destCount, bite$originStamp);
        }
        bite$reset();
    }

    @Unique
    private void bite$reset() { bite$destStamp = null; bite$originStamp = null; bite$destCount = 0; }
}
```

⚠️ `merge` 的完整描述符以 javap 为准（`merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)V`，private）。

- [ ] **Step 4: SimpleContainerMixin（漏斗/容器间转移）**

```java
// src/main/java/com/eamon/bite/mixin/SimpleContainerMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SimpleContainer.class)
public abstract class SimpleContainerMixin {
    @Unique private FreshnessStamp bite$destStamp;
    @Unique private int bite$destCount;
    @Unique private FreshnessStamp bite$originStamp;

    @Inject(method = "moveItemsBetweenStacks", at = @At("HEAD"))
    private static void bite$capture(ItemStack source, ItemStack destination, CallbackInfo ci) {
        MergeState.capture(destination.get(BiteComponents.FRESHNESS), destination.getCount(), source.get(BiteComponents.FRESHNESS));
    }

    @Inject(method = "moveItemsBetweenStacks", at = @At("TAIL"))
    private static void bite$reconcile(ItemStack source, ItemStack destination, CallbackInfo ci) {
        if (MergeState.destStamp != null && MergeState.originStamp != null) {
            StackingRules.reconcile(destination, MergeState.destStamp, MergeState.destCount, MergeState.originStamp);
        }
        MergeState.clear();
    }

    /** static 方法上下文的快照持有者（moveItemsBetweenStacks 是 static）。 */
    static final class MergeState {
        static FreshnessStamp destStamp;
        static FreshnessStamp originStamp;
        static int destCount;
        static void capture(FreshnessStamp d, int c, FreshnessStamp o) { destStamp = d; destCount = c; originStamp = o; }
        static void clear() { destStamp = null; originStamp = null; destCount = 0; }
    }
}
```

⚠️ 若 javap 显示 `moveItemsBetweenStacks` 非 static，去掉两个 handler 的 `static` 与 MergeState 嵌套类，改成实例字段模式（同 SlotMixin）。

- [ ] **Step 5: AbstractContainerMenuMixin（Shift 点击转移）**

```java
// src/main/java/com/eamon/bite/mixin/AbstractContainerMenuMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.freshness.StackingRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Unique private final Map<ItemStack, Snapshot> bite$snapshots = new IdentityHashMap<>();

    @Unique
    private static final class Snapshot {
        final FreshnessStamp stamp;
        final int count;
        Snapshot(FreshnessStamp stamp, int count) { this.stamp = stamp; this.count = count; }
    }

    @WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean bite$snapshotOnEquality(ItemStack a, ItemStack b, Operation<Boolean> original, @Local(ordinal = 1) ItemStack dest) {
        boolean result = original.call(a, b);
        if (result && dest != null && dest.has(BiteComponents.FRESHNESS)) {
            bite$snapshots.put(dest, new Snapshot(dest.get(BiteComponents.FRESHNESS), dest.getCount()));
        }
        return result;
    }

    @Inject(method = "moveItemStackTo", at = @At("TAIL"))
    private void bite$reconcile(ItemStack origin, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        FreshnessStamp originStamp = origin.get(BiteComponents.FRESHNESS);
        if (originStamp != null) {
            for (Map.Entry<ItemStack, Snapshot> e : bite$snapshots.entrySet()) {
                StackingRules.reconcile(e.getKey(), e.getValue().stamp, e.getValue().count, originStamp);
            }
        }
        bite$snapshots.clear();
    }
}
```

⚠️ 检查点：`@Local(ordinal = 1)` 假设 LVT 中第 0 个 ItemStack 是参数 `stack`、第 1 个是循环内 `itemstack`（dest）。若 genSources 显示相反，调整 ordinal。MixinExtras 已内置于 Fabric Loader（无需额外依赖）。`moveItemStackTo` 的 26.2 描述符若变化（如参数类型），以 javap 为准同步修改两个注解的 method。

- [ ] **Step 6: gametest（SimpleContainer 加权合并）**

`FreshnessGameTests` 追加：

```java
@net.fabricmc.fabric.api.gametest.v1.GameTest
public void mergeIsWeightedAverage(GameTestHelper helper) {
    long now = helper.getLevel().getGameTime();
    long life = 24000L * 6; // bread 6 days
    ItemStack stale = new ItemStack(Items.BREAD, 3);
    stale.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.9))); // 10%
    ItemStack fresh = new ItemStack(Items.BREAD, 1);
    fresh.set(BiteComponents.FRESHNESS, new FreshnessStamp(now)); // 100%

    SimpleContainer container = new SimpleContainer(9);
    container.addItem(stale.copy());
    container.addItem(fresh.copy());

    ItemStack merged = container.getItem(0);
    org.junit.jupiter.api.Assertions.assertEquals(4, merged.getCount(), "stacks should merge to x4");
    FreshnessStamp stamp = merged.get(BiteComponents.FRESHNESS);
    org.junit.jupiter.api.Assertions.assertNotNull(stamp);
    double expected = (0.1 * 3 + 1.0 * 1) / 4.0; // 0.325
    double actual = com.eamon.bite.freshness.FreshnessMath.fraction(now, stamp, new ShelfLife(life));
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual, 0.01,
        "merged freshness should be weighted average (Don't Starve semantics)");
    helper.succeed();
}
```

（import `net.minecraft.world.SimpleContainer`、`net.minecraft.world.item.Items`。注意：`canMergeRelaxed` 用 `FreshnessClock.now()` —— gametest 服务器里 clock 由 END_SERVER_TICK 持续更新，`now` 与 clock 差 ≤1 tick，误差可忽略。）

- [ ] **Step 7: javap 签名核对 + 构建 + gametest**

```bash
# 核对 mixin 目标方法签名（genSources 后可直接看源码，javap 作 fallback）
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew genSources
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
Expected: BUILD SUCCESSFUL + `mergeIsWeightedAverage` PASS。

- [ ] **Step 8: 游戏内手动验证（需人观察）**

runClient：创造世界，`/time add` 快进后 `give` 两批不同新鲜度牛肉，背包里 Shift 点击合并，观察合并后 tooltip 百分比 ≈ 加权平均；再验证漏斗：箱子 A（两堆不同新鲜度）→ 漏斗 → 箱子 B，转移后应合并且新鲜度为加权平均。**若漏斗路径未重算（destination-wins）**：这是已知边界（spec §6.3 注），在 HopperBlockEntity 的移动方法上补同款快照-重算 mixin，或接受 v1 边界并记录 README。

- [ ] **Step 9: 提交**

```bash
git add -A && git commit -m "feat: 合并重算 mixin x5（Slot/Inventory/ItemEntity/SimpleContainer/AbstractContainerMenu）"
```

---

### Task 11: 进食系统（惩罚 + 拦截）

**Files:**
- Create: `src/main/java/com/eamon/bite/freshness/SpoiledFoodHandler.java`、`src/main/java/com/eamon/bite/mixin/ItemStackFinishMixin.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（UseItemCallback）、`bite.mixins.json`
- Test: `FreshnessGameTests` 追加用例

**Interfaces:**
- Consumes: `FreshnessMath`、`ServerConfig`、`BiteComponents`
- Produces: 进食营养按新鲜度缩放、低新鲜度概率「饥饿」debuff、变质食物无法开始进食（动作栏提示）
- 设计说明：spec §8 原定 `Consumable.onConsume` mixin；实现改为 **`ItemStack.finishUsingItem` mixin + `UseItemCallback` 事件**（营养结算发生在 onConsume 之后，finishUsingItem 的 TAIL 才能拿到完整营养增量；拦截进食开始用公开事件即可，还省一个 mixin）。语义与 spec §8 表格完全一致。

- [ ] **Step 1: SpoiledFoodHandler（惩罚逻辑）**

```java
// src/main/java/com/eamon/bite/freshness/SpoiledFoodHandler.java
package com.eamon.bite.freshness;

import com.eamon.bite.config.ServerConfig;

/** 进食惩罚（spec §8 表）。 */
public final class SpoiledFoodHandler {
    private SpoiledFoodHandler() {}

    /** 营养缩放：>stale 阈值 1.0；(old, stale] nutritionScaleStale；≤old nutritionScaleOld。 */
    public static double nutritionScale(double fraction) {
        ServerConfig cfg = ServerConfig.get();
        if (fraction > cfg.staleThreshold()) return 1.0;
        if (fraction > cfg.oldThreshold()) return cfg.nutritionScaleStale();
        return cfg.nutritionScaleOld();
    }

    /** 低新鲜度 debuff 判定。 */
    public static boolean shouldApplyHunger(double fraction) {
        return fraction <= ServerConfig.get().oldThreshold()
            && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < ServerConfig.get().hungerEffectChance();
    }
}
```

- [ ] **Step 2: finishUsingItem mixin（营养缩放 + debuff）**

```java
// src/main/java/com/eamon/bite/mixin/ItemStackFinishMixin.java
package com.eamon.bite.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ServerConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import com.eamon.bite.freshness.SpoiledFoodHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackFinishMixin {
    @Unique private int bite$foodBefore;
    @Unique private float bite$saturationBefore;
    @Unique private FreshnessStamp bite$stamp;
    @Unique private ShelfLife bite$life;
    @Unique private boolean bite$tracked;

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void bite$capture(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        bite$tracked = false;
        ItemStack self = (ItemStack) (Object) this;
        bite$stamp = self.get(BiteComponents.FRESHNESS);
        bite$life = self.get(BiteComponents.SHELF_LIFE);
        if (bite$stamp == null || bite$life == null || bite$life.spoilTicks() <= 0) return;
        if (!(entity instanceof Player player)) return;
        FoodData food = player.getFoodData();
        bite$foodBefore = food.getFoodLevel();
        bite$saturationBefore = food.getSaturationLevel();
        bite$tracked = true;
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void bite$applyPenalty(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (!bite$tracked) return;
        bite$tracked = false;
        if (!(entity instanceof Player player)) return;
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), bite$stamp, bite$life);
        double scale = SpoiledFoodHandler.nutritionScale(fraction);
        FoodData food = player.getFoodData();
        int delta = food.getFoodLevel() - bite$foodBefore;
        if (delta > 0 && scale < 1.0) {
            food.setFoodLevel(bite$foodBefore + (int) Math.round(delta * scale));
            float satDelta = food.getSaturationLevel() - bite$saturationBefore;
            food.setSaturation(Math.max(0.0f, bite$saturationBefore + satDelta * (float) scale));
        }
        if (SpoiledFoodHandler.shouldApplyHunger(fraction)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER,
                ServerConfig.get().hungerEffectDurationTicks(), 0));
        }
    }
}
```

- [ ] **Step 3: UseItemCallback 拦截变质食物**

`BiteMod.onInitialize()` 追加：

```java
UseItemCallback.EVENT.register((player, level, hand) -> {
    if (!ServerConfig.get().spoiledInedible()) return InteractionResult.PASS;
    ItemStack stack = player.getItemInHand(hand);
    FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
    if (stamp == null) return InteractionResult.PASS;
    ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
    if (life == null || life.spoilTicks() <= 0) return InteractionResult.PASS;
    if (FreshnessMath.fraction(level.getGameTime(), stamp, life) > 0.0) return InteractionResult.PASS;
    if (!level.isClientSide()) {
        player.displayClientMessage(Component.translatable("bite.msg.spoiled_inedible"), true);
    }
    return InteractionResult.FAIL;
});
```

（import `net.fabricmc.fabric.api.event.player.UseItemCallback`、`net.minecraft.world.InteractionResult`、`net.minecraft.network.chat.Component`、组件类。）

`bite.mixins.json` 的 `"mixins"` 数组加入 `"ItemStackFinishMixin"`。

- [ ] **Step 4: gametest（营养缩放）**

`FreshnessGameTests` 追加：

```java
@net.fabricmc.fabric.api.gametest.v1.GameTest
public void staleFoodGivesReducedNutrition(GameTestHelper helper) {
    var player = helper.makeTallerMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    player.getFoodData().setFoodLevel(10);
    long now = helper.getLevel().getGameTime();
    long life = 24000L * 6;
    ItemStack bread = new ItemStack(Items.BREAD);
    bread.set(BiteComponents.FRESHNESS, new FreshnessStamp(now - (long) (life * 0.6))); // 40% → STALE → 75% 营养
    bread.finishUsingItem(helper.getLevel(), player);
    // 面包营养 5 → 5 * 0.75 = 3.75 → round 4 → 10 + 4 = 14
    org.junit.jupiter.api.Assertions.assertEquals(14, player.getFoodData().getFoodLevel(),
        "stale bread should give 75% nutrition");
    helper.succeed();
}

@net.fabricmc.fabric.api.gametest.v1.GameTest
public void freshFoodGivesFullNutrition(GameTestHelper helper) {
    var player = helper.makeTallerMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    player.getFoodData().setFoodLevel(10);
    ItemStack bread = new ItemStack(Items.BREAD);
    bread.set(BiteComponents.FRESHNESS, new FreshnessStamp(helper.getLevel().getGameTime()));
    bread.finishUsingItem(helper.getLevel(), player);
    org.junit.jupiter.api.Assertions.assertEquals(15, player.getFoodData().getFoodLevel(),
        "fresh bread should give full nutrition");
    helper.succeed();
}
```

- [ ] **Step 5: 构建 + gametest + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
git add -A && git commit -m "feat: 进食惩罚（营养缩放/饥饿 debuff）与变质禁食拦截"
```

---

### Task 12: Tooltip（客户端）

**Files:**
- Modify: `src/main/java/com/eamon/bite/component/FreshnessStamp.java`（实现 TooltipProvider）
- Modify: `src/client/java/com/eamon/bite/client/BiteModClient.java`（组件 tooltip 注册）
- Modify: `src/main/resources/assets/bite/lang/en_us.json`、`zh_cn.json`（先建最小键，Task 14 补全）

**Interfaces:**
- Consumes: `FreshnessMath`、`BiteComponents`、`ClientConfig.tooltipStyle()`
- Produces: 食物 tooltip 显示 `新鲜度：63%（约 3.0 天）` / `已变质`

- [ ] **Step 1: FreshnessStamp 实现 TooltipProvider**

record 追加 implements 与方法（record 主体外补 import）：

```java
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;

public record FreshnessStamp(long creationGameTick) implements TooltipProvider {
    // ...已有 CODEC/STREAM_CODEC...

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter getter) {
        ShelfLife life = getter.get(BiteComponents.SHELF_LIFE);
        if (life == null || life.spoilTicks() <= 0) return;
        Level level = context.level();
        if (level == null) return; // 无 level 的上下文（如某些数据校验）不显示
        double fraction = FreshnessMath.fraction(level.getGameTime(), this, life);
        if (fraction <= 0.0) {
            tooltip.accept(Component.translatable("bite.tooltip.spoiled").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component time = formatRemaining(life.spoilTicks() - (level.getGameTime() - creationGameTick()));
        tooltip.accept(Component.translatable("bite.tooltip.freshness",
            Math.round(fraction * 100), time)
            .withStyle(colorFor(fraction)));
    }

    private static ChatFormatting colorFor(double fraction) {
        if (fraction > 0.75) return ChatFormatting.GREEN;
        if (fraction > 0.5) return ChatFormatting.YELLOW;
        if (fraction > 0.25) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    private static Component formatRemaining(long ticks) {
        double days = ticks / 24000.0;
        if (days >= 1.0) return Component.translatable("bite.time.days", String.format(java.util.Locale.ROOT, "%.1f", days));
        double hours = days * 24.0;
        return Component.translatable("bite.time.hours", String.format(java.util.Locale.ROOT, "%.1f", hours));
    }
}
```

⚠️ 检查点：`TooltipProvider.addToTooltip` 的形参以 26.2 源码为准（研究确认的形参表：`TooltipContext, Consumer<Component>, TooltipFlag, DataComponentGetter`）。`java.util.function.Consumer` 需 import。

- [ ] **Step 2: 客户端注册组件 tooltip**

`BiteModClient.onInitializeClient()` 追加（方法名以 Fabric API 26.2 为准，`./gradlew genSources` 后搜 `ItemComponentTooltipProviderRegistry` 确认 —— 语义：把 FRESHNESS 组件挂在 tooltip 末尾）：

```java
ItemComponentTooltipProviderRegistry.registerLast(BiteComponents.FRESHNESS);
// 若方法名不同，候选：ItemComponentTooltipProviderRegistry.addLast(BiteComponents.FRESHNESS);
```

- [ ] **Step 3: 最小 lang 文件**

`src/main/resources/assets/bite/lang/en_us.json`：

```json
{
  "bite.tooltip.freshness": "Freshness: %1$s%% (%2$s)",
  "bite.tooltip.spoiled": "Spoiled",
  "bite.msg.spoiled_inedible": "This food has spoiled and can't be eaten.",
  "bite.time.days": "~%1$s days",
  "bite.time.hours": "~%1$s hours"
}
```

`zh_cn.json`：

```json
{
  "bite.tooltip.freshness": "新鲜度：%1$s%%（%2$s）",
  "bite.tooltip.spoiled": "已变质",
  "bite.msg.spoiled_inedible": "这食物已经变质了，不能吃。",
  "bite.time.days": "约 %1$s 天",
  "bite.time.hours": "约 %1$s 小时"
}
```

- [ ] **Step 4: 构建手动验证 + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew runClient
```
手动：创造世界 give 牛肉，悬停看 tooltip 新鲜度行；切中文语言再验证 zh_cn；游戏内 F3+T 热重载改 lang 生效。

```bash
git add -A && git commit -m "feat: 新鲜度 tooltip（双语）"
```

---

### Task 13: 新鲜度进度条（客户端 mixin）

**Files:**
- Create: `src/client/java/com/eamon/bite/client/mixin/ItemStackBarMixin.java`
- Modify: `src/client/resources/bite.client.mixins.json`
- Modify: `BiteModClient.java`（ClientConfig.bar 开关读取）

**Interfaces:**
- Consumes: `FreshnessMath`、`FreshnessClock`、`ClientConfig.showBar()`
- Produces: 物品图标下方进度条（13px 宽，5 档颜色），耐久条优先（spec §7.1）

- [ ] **Step 1: genSources 定位渲染路径（本计划唯一的探索型步骤）**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew genSources
```
在反编译源码里搜 `isBarVisible` / `getBarWidth` / `getBarColor`（ItemStack 或 ItemRenderState 上）。**优先方案 A**（这些方法还在 ItemStack 上且被物品渲染调用）：直接 mixin 三个方法。若已迁移到 render-state 提取（方案 B），定位新的提取方法名后同构实现（wrap 相同三个语义：可见性、宽度、颜色）。

- [ ] **Step 2: mixin 实现（方案 A 代码）**

```java
// src/client/java/com/eamon/bite/client/mixin/ItemStackBarMixin.java
package com.eamon.bite.client.mixin;

import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.config.ClientConfig;
import com.eamon.bite.freshness.FreshnessClock;
import com.eamon.bite.freshness.FreshnessMath;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 物品图标下的新鲜度进度条（spec §7.1）。耐久条优先。 */
@Mixin(ItemStack.class)
public abstract class ItemStackBarMixin {

    @Inject(method = "isBarVisible", at = @At("RETURN"), cancellable = true)
    private void bite$barVisible(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return; // 耐久条优先
        if (!ClientConfig.get().showBar()) return;
        ItemStack self = (ItemStack) (Object) this;
        cir.setReturnValue(self.has(BiteComponents.FRESHNESS));
    }

    @Inject(method = "getBarWidth", at = @At("RETURN"), cancellable = true)
    private void bite$barWidth(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.isDamaged()) return;
        FractionPair p = fractionOf(self);
        if (p == null) return;
        cir.setReturnValue((int) Math.round(13 * p.fraction()));
    }

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true)
    private void bite$barColor(CallbackInfoReturnable<Integer> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.isDamaged()) return;
        FractionPair p = fractionOf(self);
        if (p == null) return;
        cir.setReturnValue(p.color());
    }

    @Unique
    private static FractionPair fractionOf(ItemStack stack) {
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        if (stamp == null || life == null || life.spoilTicks() <= 0) return null;
        double fraction = FreshnessMath.fraction(FreshnessClock.now(), stamp, life);
        int color;
        if (fraction <= 0.0) color = 0xAAAAAA;
        else if (fraction <= 0.25) color = 0xFF5555;
        else if (fraction <= 0.5) color = 0xFFAA00;
        else if (fraction <= 0.75) color = 0xFFDD55;
        else color = 0x55FF55;
        return new FractionPair(fraction, color);
    }

    @Unique
    private record FractionPair(double fraction, int color) {}
}
```

（import `org.spongepowered.asm.mixin.Unique`。）`bite.client.mixins.json`：

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.eamon.bite.client.mixin",
  "compatibilityLevel": "JAVA_25",
  "client": ["ItemStackBarMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

⚠️ 若 Step 1 判定是方案 B（render-state 提取），在提取方法上做同构注入（可见/宽/色三语义），逻辑函数 `fractionOf` 原样复用。完成后在任务里注明实际注入点。

- [ ] **Step 3: 构建手动验证 + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew runClient
```
手动：give 牛肉若干，`/time add` 快进不同量，背包里观察进度条颜色绿→黄→橙→红变化、宽度缩短；拿一把损耗工具确认耐久条仍正常（不被覆盖）。

```bash
git add -A && git commit -m "feat: 物品新鲜度进度条（客户端 mixin）"
```

---

### Task 14: i18n 完整化 + ModMenu 键

**Files:**
- Modify: `src/main/resources/assets/bite/lang/en_us.json`、`zh_cn.json`

**Interfaces:**
- Produces: 全量翻译键（游戏内 + ModMenu + 调试命令）

- [ ] **Step 1: 补全键**

`en_us.json`（在 Task 12 基础上追加）：

```json
{
  "bite.tooltip.freshness": "Freshness: %1$s%% (%2$s)",
  "bite.tooltip.spoiled": "Spoiled",
  "bite.msg.spoiled_inedible": "This food has spoiled and can't be eaten.",
  "bite.time.days": "~%1$s days",
  "bite.time.hours": "~%1$s hours",
  "bite.cmd.freshness.header": "Freshness report for %1$s",
  "bite.cmd.freshness.no_food": "Hold a food item to inspect it.",
  "bite.cmd.freshness.stamp": "Created at tick %1$s",
  "bite.cmd.freshness.life": "Shelf life: %1$s days",
  "bite.cmd.freshness.fraction": "Freshness: %1$s%%",
  "bite.cmd.freshness.never": "Never spoils",
  "modmenu.nameTranslation.bite": "Because It's Too Easy",
  "modmenu.summaryTranslation.bite": "Food spoilage survival mechanics",
  "modmenu.descriptionTranslation.bite": "Food spoils over time, merges by weighted-average freshness, and stale meals bite back. Survival was too easy."
}
```

`zh_cn.json`：

```json
{
  "bite.tooltip.freshness": "新鲜度：%1$s%%（%2$s）",
  "bite.tooltip.spoiled": "已变质",
  "bite.msg.spoiled_inedible": "这食物已经变质了，不能吃。",
  "bite.time.days": "约 %1$s 天",
  "bite.time.hours": "约 %1$s 小时",
  "bite.cmd.freshness.header": "%1$s 的新鲜度报告",
  "bite.cmd.freshness.no_food": "手持食物后再查询。",
  "bite.cmd.freshness.stamp": "生产于第 %1$s 刻",
  "bite.cmd.freshness.life": "保质期：%1$s 天",
  "bite.cmd.freshness.fraction": "新鲜度：%1$s%%",
  "bite.cmd.freshness.never": "永不腐坏",
  "modmenu.nameTranslation.bite": "Because It's Too Easy（生存哪有这么容易）",
  "modmenu.summaryTranslation.bite": "食物腐坏生存机制",
  "modmenu.descriptionTranslation.bite": "食物随时间腐坏，堆叠按加权平均新鲜度合并，吃不新鲜的东西会吃苦头。生存模式太容易了。"
}
```

- [ ] **Step 2: 验证 + 提交**

runClient 手动：中英双语各查一遍 tooltip/命令文案；F3+T 验证热重载。

```bash
git add -A && git commit -m "feat: 完整双语 i18n（含 ModMenu 键）"
```

---

### Task 15: /bite freshness 调试命令

**Files:**
- Create: `src/main/java/com/eamon/bite/command/BiteCommands.java`
- Modify: `src/main/java/com/eamon/bite/BiteMod.java`（注册）

**Interfaces:**
- Consumes: `BiteComponents`、`FreshnessMath`、Task 14 的 `bite.cmd.*` 键
- Produces: `/bite freshness`（OP 2 级）：输出手持食物的 stamp/shelfLife/fraction

- [ ] **Step 1: 实现**

```java
// src/main/java/com/eamon/bite/command/BiteCommands.java
package com.eamon.bite.command;

import com.eamon.bite.BiteMod;
import com.eamon.bite.component.BiteComponents;
import com.eamon.bite.component.FreshnessStamp;
import com.eamon.bite.component.ShelfLife;
import com.eamon.bite.freshness.FreshnessMath;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class BiteCommands {
    private BiteCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal(BiteMod.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("freshness").executes(BiteCommands::reportFreshness))));
    }

    private static int reportFreshness(CommandContext<CommandSourceStack> ctx) {
        ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
        FreshnessStamp stamp = stack.get(BiteComponents.FRESHNESS);
        ShelfLife life = stack.get(BiteComponents.SHELF_LIFE);
        var source = ctx.getSource();
        if (stamp == null && (life == null || life.spoilTicks() <= 0)) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.no_food"), false);
            return 0;
        }
        String itemName = stack.getHoverName().getString();
        source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.header", itemName), false);
        if (stamp != null) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.stamp", stamp.creationGameTick()), false);
        }
        if (life == null || life.spoilTicks() <= 0) {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.never"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.life", life.spoilTicks() / 24000.0), false);
            long now = source.getLevel().getGameTime();
            double fraction = stamp == null ? 1.0 : FreshnessMath.fraction(now, stamp, life);
            int pct = (int) Math.round(fraction * 100);
            source.sendSuccess(() -> Component.translatable("bite.cmd.freshness.fraction", pct), false);
        }
        return 1;
    }
}
```

`BiteMod.onInitialize()` 追加 `BiteCommands.register();`。

- [ ] **Step 2: 构建手动验证 + 提交**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew build
```
runClient 手动：give 牛肉 → `/bite freshness` 输出四行报告。

```bash
git add -A && git commit -m "feat: /bite freshness 调试命令"
```

---

### Task 16: 收尾——README/LICENSE/icon、发布配置、全量验证

**Files:**
- Create: `README.md`、`LICENSE`、`src/main/resources/assets/bite/icon.png`
- Modify: `build.gradle`（Minotaur + CurseForgeGradle，env 守卫）
- Modify: `gradle.properties`（发布用属性注释）

**Interfaces:**
- Produces: 可发布产物与发布流水线（debug 模式，正式上传等账号 token）

- [ ] **Step 1: LICENSE（MIT，作者 Eamon）**

```
MIT License

Copyright (c) 2026 Eamon

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 2: README.md（双语，Modrinth 正文可同步）**

```markdown
# Because It's Too Easy

生存模式太容易了。/ Survival was too easy.

食物会随时间腐坏：图标下的进度条告诉你还剩多少时间，tooltip 显示精确数值。

## 特性 / Features
- 🥩 食物保质期：生肉 2 天、熟食 4 天、面包/作物 6 天（可配置）
- 📊 新鲜度进度条（物品图标下方）
- 🧮 饥荒式堆叠：不同新鲜度的食物可堆叠，合并取加权平均（守恒）
- 🤢 吃不新鲜的食物：营养打折，低新鲜度概率饥饿 debuff；完全变质不可食用
- 🌐 中英双语（en_us / zh_cn）
- 🔧 全部数值可在 `config/bite/server.json` 配置；其他 mod 的食物自动按 `c:foods` 分类兼容

## 要求 / Requirements
Minecraft 26.2 · Fabric Loader ≥0.19.3 · Fabric API

## English
Food spoils over time. A bar under each item tracks freshness; tooltips show exact
values. Stacks merge Don't-Strive-style: weighted-average freshness is conserved.
Eating stale food costs nutrition and may apply Hunger; spoiled food is inedible.
```

- [ ] **Step 3: 图标（128×128 纯色渐变 PNG，程序生成，非 AI）**

```bash
python3 - << 'EOF'
import struct, zlib
W = H = 128
rows = []
for y in range(H):
    row = bytearray([0])  # filter type 0
    for x in range(W):
        # 左上亮绿到右下深红的渐变（新鲜→腐坏主题）
        t = (x / W * 0.5) + (y / H * 0.5)
        r = int(0x55 + (0xAA - 0x55) * t)
        g = int(0xFF + (0x11 - 0xFF) * t)
        b = int(0x55 + (0x11 - 0x55) * t)
        row += bytes((r, g, b))
    rows.append(bytes(row))
raw = b"".join(rows)
def chunk(tag, data):
    c = tag + data
    return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw))
       + chunk(b"IEND", b""))
open("/Users/heyangming/Code/bite/src/main/resources/assets/bite/icon.png", "wb").write(png)
print("icon written")
EOF
```
（这是占位图标；发布前建议换手绘/设计图 —— Modrinth 禁止 AI 生成图像，程序生成的纯渐变不属于 AI 生成，合规。）

- [ ] **Step 4: 发布插件（env 守卫，默认不执行）**

`build.gradle` 顶部 plugins 块（与已有 plugins 合并）追加：

```gradle
plugins {
    // ...已有 loom 插件...
    id 'com.modrinth.minotaur' version '2.+' apply false
    id 'net.darkhax.curseforgegradle' version '1.3.33' apply false
}

// 文件末尾：
if (project.hasProperty('modrinth_token')) {
    apply plugin: 'com.modrinth.minotaur'
    modrinth {
        token = project.modrinth_token
        projectId = 'REPLACE_WITH_MODRINTH_ID'   // 在 Modrinth 建项目后填 slug/ID
        versionNumber = project.mod_path_version ?: project.version
        versionType = 'release'
        changelog = file('CHANGELOG.md').exists() ? file('CHANGELOG.md').text : 'Initial release'
        uploadFile = tasks.jar    // 26.x 用 jar（非 remapJar）
        gameVersions = ['26.2']
        loaders = ['fabric']
        dependencies { required.project 'fabric-api' }
    }
}

if (project.hasProperty('curseforge_token')) {
    apply plugin: 'net.darkhax.curseforgegradle'
    tasks.register('curseforge', net.darkhax.curseforgegradle.TaskPublishCurseForge) {
        apiToken = project.curseforge_token
        projectId = '000000'      // 在 CurseForge 建项目后填数字 ID
        releaseType = 'release'
        addGameVersion '26.2'
        addModLoader 'Fabric'
        addEnvironment 'Client', 'Server'   // 2026-07 起 CF 强制环境标签
        relations { required 'fabric-api' }
        mainArtifact(tasks.jar)
    }
}
```

> 用户操作（不在本计划内自动执行）：在 Modrinth/CurseForge 各建项目（Modrinth 人工审核 24-48h 起，宜早提交），把项目 ID 填入；token 放 `~/.gradle/gradle.properties`（勿提交仓库）：`modrinth_token=...`、`curseforge_token=...`。**注意不要把 token 写进项目 gradle.properties。**

- [ ] **Step 5: 全量验证**

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./gradlew clean build
```
Expected: BUILD SUCCESSFUL；`build/libs/bite-1.0.0.jar` 存在；gametest 全 PASS（build 日志可见用例数）。

手动冒烟（runClient，逐项过一遍）：
1. 创造世界 give 牛肉：tooltip 有新鲜度行、图标下有进度条
2. `/time add 40000` 后 tooltip 百分比下降、进度条变色变短
3. 两批不同新鲜度牛肉合并后新鲜度 ≈ 加权平均（用 `/bite freshness` 查合并前后）
4. 快进到腐坏（`/time add 200000`）：进度条灰、tooltip「已变质」、右键无法开始吃（动作栏提示）
5. 中英双语切换显示正常
6. `runServer`（改 `run/eula.txt` 为 true、`server.properties` 的 `online-mode=false`）+ 两个 runClient 联机验证双端一致（或用 e4mc）

- [ ] **Step 6: 提交**

```bash
git add -A && git commit -m "chore: README/LICENSE/icon 与发布流水线（debug 守卫）"
```

---

## 自审记录（写计划时已跑）

- **Spec 覆盖**：§2 工具链→T1；§3-4 数据模型→T2/T3/T5；§5 打标→T6/T7；§6 堆叠→T8/T9/T10；§7 显示→T12/T13；§8 进食→T11；§9 配置→T4；§10 i18n→T12/T14；§11 结构→各任务的文件路径与 T1 脚手架；§12 测试→各任务内嵌 + T16 Step 5；§13 发布→T16；§14 风险→对应任务内检查点（进度条注入点 T13、签名核对 T10 Step 7、loot 链路 T6 Step 5、扫描性能 T7）。
- **Spec 偏差（已论证）**：T11 进食锚点从 `Consumable.onConsume` 改为 `ItemStack.finishUsingItem` mixin + `UseItemCallback`（营养结算时序问题 + 省一个 mixin），语义与 spec §8 表一致。
- **类型一致性**：`FreshnessStamp(long creationGameTick)`、`ShelfLife(long spoilTicks)`、`FreshnessClock.now()`、`StackingRules.canMergeRelaxed/reconcile`、`FreshnessStamper.stamp(stack, now)` 全文一致。
- **已知边界**（实现期验证点，均在任务内标注 ⚠️）：DefaultItemComponentEvents 形参（T5）、addResource 重载（T10）、@Local ordinal（T10）、进度条渲染路径 A/B（T13）、TooltipProvider 形参（T12）、ChunkMap 枚举（T7）、注册表 ID API（T5）。这些是 26.2 新版重构区，genSources 一次即可全部确认。
