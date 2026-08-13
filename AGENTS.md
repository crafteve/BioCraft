# AGENTS.md

## 第一章 项目概述

### 1.1 项目简介

- **BioCraft**（生物工艺），Mod ID `biocraft`
- 技术栈：**Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 / Gradle 9.2.1 / ModDevGradle 2.0.143 / Parchment 2024.11.17**
- 硬核生物化学/工业模组：将真实的代谢通路、中心法则、酶动力学机制完整搬进 Minecraft，构建以分子/原子为基本操作单元、以化学势能与物质循环为驱动力的工业体系
- 目标人群：格雷科技类玩家、化学/生物爱好者、Factorio/Shapez 类自动化玩家、追求创新科技树而非套皮模组的硬核玩家

### 1.2 核心设计约束（"反默认"规则）

来自"标准 Minecraft 模组开发"的代理很容易在这些地方出错。它们是硬性规则，而非建议：

- **机器不以 FE / Forge Energy 作为输入**。机器以 **ATP 分子**驱动（每 tick 消耗）。FE 仅作为输出端，"ATP合酶发电机"（约 1 ATP ≈ 100 FE）为其他模组（AE2、Mekanism）供电。唯一的 FE 输入端机器是电化学合成器——刻意做成高耗低效，让"用电合成"显得代价高昂
- **所有功能机器本质上都是蛋白质**，只能通过中心法则链获得：DNA编码器 → 转录仪 → 翻译仪 → 内质网折叠器 → 高尔基体修饰仪。手持 `[成熟酶蛋白]` 右键地面将其变为功能机器方块。**没有任何原版风格的机器合成配方**
- **反应是多底物 / 多辅因子 / 多产物网络**，不是熔炉式"输入A→输出B"。机器通常需要 ATP + 氧化还原辅因子（NAD⁺/NADP⁺），并产出 ADP/AMP + NADH 等副产物，必须回收利用，否则产线堵塞
- **能量 = 物品物流，而非电线**。ATP/ADP循环 与 NAD⁺/NADH循环 是需要玩家设计并维护的闭环
- **没有升级阶级（MK2/MK3），也没有物理多方块结构**。升级靠"酶插件"（酶插件物品，NBT 驱动）插入机器；大型细胞器靠相邻方块检测 + 控制核心实现，绝不构造物理多方块结构

### 1.3 系统机制

- **物质层级**：物品即原子/分子/离子（碳/氢/氧/氮/磷、H₂O、葡萄糖 C₆H₁₂O₆、ATP/ADP、20 种氨基酸、核苷酸 A/C/G/T/U、NAD⁺/NADH、NTP、DNA模板/mRNA/新生肽链/成熟酶蛋白）。堆叠数 = 分子个数，严格化学计量比（如 1 葡萄糖 = 6C + 12H + 6O）
- **物品区分**：Tooltip 中的化学式为权威依据；不同分子类型使用 ItemColor 动态着色；在基础纹理上叠加原子符号图标
- **三台原始机器**（手动操作、不可自动化、不可升级）：DNA编码器（即时，NBT 存储序列）、转录仪（30 秒）、翻译仪（60 秒）
- **酶动力学 → 3 种机器 GUI 变体**：限速酶（红/橙，指数进度条，无 AMP 激活剂则仅 10% 效率）、异构酶（中性灰，约 1 秒，要求底物堆叠 ≥8）、氧化/裂解酶（紫/蓝双阶段，NAD⁺ 耗尽时卡死在 50% 并报警）。GUI 需显示"停摆原因"提示

### 1.4 技术架构

- 只写一个通用 `MachineBlock` 类，用 BlockEntity 中的 `MachineType` 枚举区分功能，**不要为每种机器单独建方块类**
- 配方通过**解析反应方程式字符串**实现（如 `GLC + 2ATP -> F6P + 2ADP`），由配置文件驱动，**绝不硬编码**
- 性能："事件驱动 + 睡眠"机制（输入槽变动时唤醒计算），进度用 `startTick` / `requiredTicks` 差值计算，仅在状态变更时发送同步数据包
- 细胞器：相邻机器检测 + 控制核心方块（线粒体 = 基质控制器 + 十字排列的 4 个 ETC 模块；内质网 = 腔体机器紧邻堆叠实现速度线性叠加；膜 = 装饰性透明无碰撞方块，提供区室化增益）

### 1.5 项目规划

按 4 个纪元顺序推进，不得引入任何违反上述"反默认"规则的特性：

1. **化学起源**：TNT 爆炸 → 基础原子/分子；有机物熔炉燃烧产出少量 ATP。用原版材料合成 3 台原始机器
2. **糖酵解**：10 步糖酵解流水线，每步一台独立机器；受氨基酸供给、模板获取、蛋白质折叠、辅因子供给四重关卡约束
3. **真核纪元**：TCA 循环 + ETC 机器群 → 工业级 ATP/FE 输出
4. **合成生物纪元**：自定义酶"编程" + 合成细胞核，实现近乎创造模式的合成，且完全依赖生化产线供能

纪元一的开发分批顺序：物品地基（原子/分子注册 + 视觉）→ 反应引擎（已完成：多底物可逆米氏乘积速率 + RK4，JSON 结构化反应无字符串解析）→ TNT 爆炸转化 + 熔炉产 ATP → 三台原始机器

**当前进度**（2026-08-12）：
- ✅ 物品地基第一阶段完成：62 个分子物品（20 氨基酸/13 离子/5 原子/2 无机物/4 碱基/4 NTP/3 辅酶/11 糖酵解）由 `substances.json` 数据表驱动注册，datagen 自动生成模型/语言/贴图
- ✅ Tooltip 分子图渲染完成（自绘管线）：CDK 负责解析/2D 坐标/Kekulize（`Kekulization`），渲染层自绘——4x 超采样抗锯齿细键线（0.8px）、Kekulé 单双交替、环内双键朝环心偏移、杂原子符号绘制进纹理（深色底块截断键线、随分子等比缩放、显式 H 如 OH/NH₂）、竖长分子自动旋转 90° 横放、标签碰撞推开
- ✅ Tooltip 信息行：黄色分子式（Hill 排序 Unicode 下标）+ 类别徽章 + 摩尔质量；结构式改为**按住 Shift 展示**，未按时显示提示行；离子/原子/无机物不展示结构式
- ✅ 图标缩写标注：`IItemDecorator` 在物品图标左上角绘制缩写（白字黑阴影双写、缩放 0.55、z 提升 200 层）；缩写数据使用 Unicode 上下标（H⁺/Ca²⁺/NH₄⁺/H₂O/NAD⁺，糖酵解编号如 G6P 保持原样）
- ✅ tooltip 布局：手持物品时创意标签页标题（蓝色）自动移至 tooltip 末尾（`MoleculeTooltipLayout`）
- ✅ 视觉校验闭环：`.opencode/agents/vision.md` 视觉审查子代理（opencode-go/qwen3.7-plus 多模态）+ `tools/texturegen/` 程序化贴图工具链（PixelCanvas DSL，生成 PNG 后派 vision 子代理读图审查）
- ✅ 化学引擎内核完成（纯 Java 零 MC 依赖，`tools/engineTest/` 独立单测 14 用例全绿）：多底物可逆米氏乘积速率方程（共享分母，平衡精确 = Keq 绝不缩放 + 饱和有界 + 产物回压 + 全底物平等，ATP/NAD⁺ 参与速率）、RK4 积分（Δt=0.05）、温度修正（van't Hoff/Q10 + 0.1K 缓存）、固定活性物种（H₂O/H⁺ 只结算不进速率）、三断言数据防火墙（配平/数值健康/Keq 红线）；PGI 平衡收敛误差 <1%、黄金值快照防回归
- ⏳ 待开发：糖酵解热力学数据 JSON 化（enzymes.json）、酶工厂注册体系、BE 桥接、TNT 爆炸转化、熔炉产 ATP、三台原始机器

### 1.6 开发流程

迭代循环：编写代码 → `gradlew build` 验证编译 → `gradlew runClient` 进游戏实测 → `gradlew runData` 生成资源 → 提交 commit。具体命令见第二章，任务执行规范见第四章

贴图迭代循环：`TextureScript` 生成贴图 → Task 派 vision 子代理读图审查 → 改脚本重新生成，满意后拷入 `src/main/resources` 正式使用

### 1.7 Git 管理规范

- 本项目全程使用 git 进行版本管理
- **每次对话结束时，若有任何文件修改，必须提交 commit**
- **每次 commit 后必须 push 到 GitHub 远程仓库**（`https://github.com/crafteve/BioCraft`，国内网络下 push 必须携带网络参数，见 2.5）
- Commit Message 格式遵循 Conventional Commits 规范：
  - `feat: xxx` — 新功能
  - `fix: xxx` — 修复 bug
  - `refactor: xxx` — 重构代码
  - `docs: xxx` — 文档变更
  - `chore: xxx` — 杂项（依赖、配置等）
  - `style: xxx` — 代码风格调整（不影响逻辑）
  - `perf: xxx` — 性能优化
  - `test: xxx` — 测试相关
- Message 使用中文描述，简明扼要

## 第二章 项目架构与目录

### 2.1 根目录文件

- `build.gradle` — ModDevGradle 2.0.143；Java 21 toolchain；Parchment 2024.11.17；runs 四配置（client / server / gameTestServer / data）；`generateModMetadata` 任务展开 mods.toml 占位符；datagen 输出 `src/generated/resources` 已加入资源源集
- `gradle.properties` — mod 元数据（mod_id=biocraft、mod_group_id=com.github.crafteve.biocraft）+ 构建参数（Xmx1G、daemon、parallel、caching、configuration-cache）
- `settings.gradle` — pluginManagement + foojay 工具链插件（本地已有 JDK 21，不会触发下载）
- `gradlew` / `gradlew.bat` — Gradle wrapper 启动脚本（Windows 上 gradlew.bat 依赖 JAVA_HOME 定位 JDK）
- `.gitignore` — 忽略 `build/`、`run/`、`.gradle/`、**`.vscode/`**、`src/generated/.cache/` 等。注意 `.vscode/` 被忽略，工作区配置不提交
- `.gitattributes` — 行尾/文本属性
- `README.md` — 仍是模板默认内容，待改写
- `TEMPLATE_LICENSE.txt` — 模板许可
- `tools/texturegen/` — 程序化贴图工具链（`PixelCanvas.java` 像素画 DSL + `TextureScript.java` 示例脚本），纯 JDK 21 AWT 零依赖，与 Gradle 构建完全隔离。编译 `javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java`，运行 `java -cp tools/texturegen/out TextureScript [输出目录]`；输出目录 `tools/texturegen/output/` 已 gitignore，正式贴图确定后拷入 `src/main/resources`
- `tools/engineTest/` — 化学引擎独立单测（14 用例），纯 JDK 零依赖扮演"伪方块实体"验证引擎纯函数契约。运行前需先 `gradlew build`（生成主代码 class），再 `javac -encoding UTF-8 -cp build/classes/java/main -d tools/engineTest/out tools/engineTest/*.java` + `java -cp "build/classes/java/main;tools/engineTest/out" engineTest.EngineSelfTest`；退出码 0=全绿、1=有失败。输出目录 `tools/engineTest/out/` 已 gitignore

### 2.2 构建与运行目录

- `gradle/wrapper/` — wrapper 配置。`distributionUrl` 指向**腾讯云镜像**的 Gradle 9.2.1（原官方 URL 已替换，勿改回）
- `build/` — 构建产物（libs 下产出 `biocraft-1.0.0.jar`），gitignore
- `run/` — 游戏运行目录（存档、日志），gitignore
- `src/generated/` — datagen 输出目录，随 `runData` 生成

### 2.3 资源配置

- `src/main/templates/META-INF/neoforge.mods.toml` — 含 `${mod_id}` 等占位符，由 `generateModMetadata` 展开到 `build/generated/sources/modMetadata`，**不要直接改生成产物**
- `src/main/resources/assets/biocraft/lang/` — 语言文件由 datagen 生成（src/generated），源目录无手写 lang；物品名/类别/标签页/提示文案均来自 `SubstanceLanguageProvider`

### 2.4 Java 源码结构

现状：模板示例已清理，`BioCraft.java` 已瘦身为纯装配；分子物品体系（注册/染色/tooltip 分子图）已落地，具体见下方包结构

当前包结构（与 1.4 技术架构一一对应）：

```
com.github.crafteve.biocraft
├── BioCraft.java                 # 瘦身为纯装配：注册各 init 类 + 事件总线
├── BioCraftClient.java
├── Config.java
├── init/
│   ├── ModItems.java             # 读 substances.json → 动态注册 62 个 MoleculeItem
│   └── ModCreativeTabs.java      # 多标签页架构，现有"生物工艺 · 分子"页
├── item/
│   ├── MoleculeItem.java         # 分子基类：SMILES/缩写/染色/类别 + tooltip 布局（Shift 展示结构式）
│   ├── MoleculeCategory.java     # 8 类分子类别枚举（主题色）
│   ├── MoleculeDataCalculator.java # CDK 计算分子式（Hill 排序）与摩尔质量，缓存+防御降级
│   └── MoleculeColors.java       # ItemColor 染色 + TooltipComponent 工厂 + 装饰器注册（Dist.CLIENT）
├── client/
│   ├── MoleculeTextureCache.java # CDK 解析+2D 坐标+Kekulize → 自绘键线骨架（4x 超采样/环内双键/旋转/碰撞）→ DynamicTexture 缓存
│   ├── MoleculeTooltipComponent.java # TooltipComponent+ClientTooltipComponent：blit 结构图
│   ├── MoleculeTooltipLayout.java # 标签页标题移置 tooltip 末尾（GatherComponents 事件）
│   └── MoleculeItemDecorator.java # 图标左上角缩写标注（IItemDecorator，白字黑阴影、z=200）
├── datagen/
│   ├── ModDataGen.java           # GatherDataEvent 装配
│   ├── SubstanceData.java        # 物质表读取工具（classpath）
│   ├── SubstanceModelProvider.java # 每物质两层模型 JSON
│   └── SubstanceLanguageProvider.java # en_us/zh_cn 语言生成（含类别/摩尔质量 key）
├── block/MachineBlock.java       # 唯一机器方块类（DNA编码器在用，酶工厂改造待 M2）
├── blockentity/                  # MachineType + MachineBlockEntity（DNA编码器在用，酶工厂 BE 待 M3）
├── reaction/                     # 化学引擎内核（纯 Java 零 MC 依赖，已完成 + 14 用例单测）
│   ├── EnzymeFactoryData.java    # 酶数据档案 record（物品 id 直填/每物种自带 Km/直存 Keq）
│   ├── EnzymeSimulator.java      # 每机一实例：RK4 积分 + 温度缓存 + 边界缩放
│   ├── ReactionDefinition.java   # 不可变网络档案（物种表/化学计量/Haldane Vmax_b(T)）
│   ├── KineticsCalculator.java   # 共享分母乘积速率方程 + 缩放换算
│   ├── ReactionState.java        # 浓度/温度/活性容器（BE 与引擎共享）
│   ├── StepResult.java           # 通量报告（fwd/rev/net）
│   ├── ThermoUtil.java           # Keq 换算/van't Hoff+Q10/Arrhenius
│   └── KineticConstants.java     # 缩放常量（TIME_SCALE=1000 唯一节奏旋钮，待 M6 调参）
├── machine/                      # 3 种酶动力学行为策略（未开发）
├── gui/                          # MachineMenu + MachineScreen（未开发）
├── network/                      # 同步数据包（DNA编码器序列包在用）
├── event/                        # TNT 爆炸转化、熔炉燃烧产 ATP（未开发）
└── organelle/                    # 纪元三：相邻检测 + 控制核心（未开发）
```

### 2.5 其他环境

- `.github/workflows/build.yml` — CI：push/PR 触发，JDK 21 + `gradlew build`。CI 环境无国内镜像（走官方源），构建慢是预期的，**不要试图为 CI 配镜像**
- 用户全局 `C:\Users\17094\.gradle\init.d\mirror.gradle` — 阿里云 Maven 镜像（前置 + 官方 fallback），**不在仓库内**。JDK 21 位于 `C:\Program Files\Java\jdk-21`（JAVA_HOME 已配好）
- **GitHub 推送网络配置**（国内网络硬性要求，实测经验）：
  - GitHub 直连 HTTPS 会被重置（Connection reset），git 默认也不读系统代理，且 Windows 自带 schannel TLS 后端与代理握手失败
  - 本机代理：`127.0.0.1:7892`（系统代理已开启）。push/fetch 必须显式携带 OpenSSL 后端 + 代理参数：
    - `git -c http.sslBackend=openssl -c http.proxy=http://127.0.0.1:7892 -c https.proxy=http://127.0.0.1:7892 push`
  - 本地分支名为 `main`，与远端默认分支一致；远端仓库初始含 GitHub 自动生成的 `LICENSE`，已合并保留，勿删除
- `.opencode/agents/vision.md` — 视觉审查子代理（mode: subagent，`edit: deny` 只读），模型 `opencode-go/qwen3.7-plus`（多模态 text+image+video），备选 `opencode-go/qwen3.6-plus` / `qwen3.8-max` / `gpt-5.6-luna` / `mimo-v2.5`（均多模态；mimo-v2.5 描述精度不足已弃用）。用法：Task 工具派发，子代理用 Read 读 PNG 后返回中文结构化审查；opencode 配置非热加载，新增/修改 agent 后必须重启 opencode 才生效

### 2.6 CDK 依赖架构与已知注意事项（欠账）

**依赖架构**（build.gradle）：CDK 化学库（`org.openscience.cdk:2.9`，9 个分拆模块：silent/smiles/sdg/interfaces/data/atomtype/standard/formula/depict）通过 `cdkDeps` 配置解析，由 `mergeCdkJar` 任务合并为单个 `build/cdk/cdk-all.jar`，随后三处引用同一产物：
- `implementation` — 编译期
- `additionalRuntimeClasspath` — dev 运行期（ModDevGradle 的 dev run 不包含 implementation 依赖）
- `jarJar` — 发布打包（嵌入 mod jar 的 `META-INF/jarjar/`，玩家单 jar 可运行）

**为什么必须合并成单 jar**：NeoForge 1.21.1 会把 classpath 上的库 jar 自动模块化（JPMS 自动模块），而 CDK 各模块存在分包（如 `org.openscience.cdk.tools.manipulator` 同时存在于 cdk-standard 与 cdk-formula），模块间分包非法，导致部分类运行期 CNFE。合并为单 jar（单一模块 `cdk.all`）后包内分包不受限。这是排查最久的问题，**不要改回分模块引用方式**。

**已知注意事项（欠账清单）**：
1. **CDK 版本锁定 2.9**：2.12 全家桶（cdk-bundle）带 JPMS module-info 与 JDK 冲突；分拆模块 + 2.9 验证通过。升级 CDK 必须重跑全量 SMILES 校验（62 个全部能解析 + SDG 布局），校验方法：临时独立 Java 程序 + `build/cdk/cdk-all.jar`（详见 git 历史中 SmokeSmiles 类）
2. **依赖排除规则**：CDK 的依赖声明会传播版本约束，与 NeoForge 严格锁定冲突，必须排除 log4j/commons-io/commons-lang3/guava（MC 环境自带），且 `resolutionStrategy.force commons-lang3:3.14.0` 不能删
3. **SMILES 数据坑**：芳香环写法必须 CDK 兼容（小写芳香 + 显式 `[nH]`），曾修 4 个（adenine/cytosine/uracil/gtp）。新增分子后建议跑一遍批量解析校验
4. **防御性降级**：`MoleculeDataCalculator` 解析失败返回 valid=false（tooltip 显示灰色提示），不抛异常——新增分子若写错 SMILES 不会崩游戏，但会显示"结构数据解析失败"
5. **tooltip 组件注册**：自定义 TooltipComponent 必须经 `RegisterClientTooltipComponentFactoriesEvent` 注册（NeoForge 查表转换，非 instanceof 机制），遗漏会抛 Unknown TooltipComponent
6. **进程残留**：runData/runClient 报错后可能残留 java 进程导致终端"卡住"，用 `--no-daemon` 运行可避免；残留进程任务管理器杀 java.exe
7. **Kekulé 交替用 CDK `Kekulization`**：自研 BFS 交替对奇数环/融合环/酮基环有化学错误，已弃用（git 历史有）；环键判定用 `RingSearch`，勿改回自研
8. **图标装饰器 z 层级**：IItemDecorator 绘制必须 `pose().translate(0,0,200)` 提升 z，否则被物品贴图覆盖（vanilla 堆叠数同款处理）
9. **结构式按 Shift 展示**：`getTooltipImage` 用 `Screen.hasShiftDown()` 控制；离子/原子/无机物类别恒不展示
10. **标签页标题移置**：vanilla 将标签页标题插入 tooltip index 1（物品名后），`MoleculeTooltipLayout` 遍历全列表匹配移动，勿只查首行
11. **缩写上下标**：`substances.json` 的 abbreviation 使用 Unicode 上下标（H⁺/Ca²⁺/NH₄⁺/H₂O/NAD⁺），糖酵解编号（G6P/3PG）保持原样——新增离子/无机物缩写需按此惯例书写
12. **MC 源码查找方法（重要排查手段）**：ModDevGradle 在本地缓存了已映射（mojmap）的反编译 MC 源码，路径：`%USERPROFILE%\.gradle\caches\neoformruntime\intermediate_results\decompile_*.jar`（按 jar 内 `net/minecraft/.../*.java` 路径直接 `jar xf` 提取即可，比 javap 字节码逆向高效得多）。另有 `sourcesAndCompiledWithNeoForge_*_output.jar` 可 javap 查 NeoForge patch 后的类（混淆 jar `minecraft_1.21.1_client.jar` 无映射名不可用）。排查"vanilla 机制行为"类问题时优先查源码而非猜
13. **1.21.1 容器 GUI 的 tooltip 渲染机制**：`AbstractContainerScreen.render` 本身**不**渲染 hoveredSlot 物品 tooltip（1.21 重构移除），`renderTooltip(GuiGraphics,int,int)` 改由**各子类 Screen 在 render 中显式调用**（源码实证：`InventoryScreen`、`ContainerScreen` 覆写 render 后调用 `this.renderTooltip(...)`）。自定义容器 Screen 覆写 render 时必须在 super 之后补调 `this.renderTooltip(graphics, mouseX, mouseY)`，否则悬停槽位无物品 tooltip（含自研 tooltip 组件）。另：`renderWithTooltip`（final）是 Minecraft 渲染入口，延迟 tooltip 走 `setTooltipForNextRenderPass`
14. **BlockEntity.setRemoved 双触发陷阱**：`setRemoved()` 不只方块破坏时调用，**世界卸载/区块卸载同样触发**（Level 卸载 chunk 时清理 BE）。掉落实体/玩家反馈类逻辑**禁止放 setRemoved**，否则进出存档会误触发（实测：DNA 编码器缓冲池碱基每次进出存档爆一地）。正确位置是方块类的 `Block.onRemove`（仅方块被破坏/替换时触发），通过 `BlockEntity` 的 `dropExtraContents(Level, BlockPos)` 类钩子统一调用
15. **视觉审查子代理**：vision agent 定义在 `.opencode/agents/`，启动时加载，修改后必须重启 opencode 才生效；Task 派发时给出图片绝对路径与审查要点，子代理无 edit 权限只能读图返回文字；mimo-v2.5 描述精度不足已弃用，主用 qwen3.7-plus，若在 OpenCode Go 订阅出现额度/速率限制或精度下降，换备选视觉模型（qwen3.6-plus / qwen3.8-max / gpt-5.6-luna）
16. **贴图工具链编码**：`tools/texturegen` 的 javac 必须带 `-encoding UTF-8`（Windows 默认 GBK 会编译失败），输出目录 gitignore，正式贴图需手动拷入 `src/main/resources`，工具脚本不进 mod 源码源集
17. **引擎零依赖隔离门禁**：`reaction/` 包只能 import `java.*`（当前仅 java.util.*）；`tools/engineTest` 的 javac classpath 只含 `build/classes/java/main`（无 MC 类），引擎若意外引入 MC 依赖编译直接失败——这是天然门禁，新增引擎代码时保持此约束
18. **引擎速率公式三大数学性质（勿改坏）**：①平衡精确——可逆多底物共享分母乘积形式下 v=0 时 ∏产物/∏底物 = Keq（Haldane 保证），Keq 绝不缩放红线由构建断言+收敛测试双重守护；②逆向 Vmax 由 `Vmax_f·∏KmP/(∏KmS·Keq)` 决定而非独立逆向数据（Keq 小时逆向极强是正确行为）；③饱和有界——高浓度速率 ≤ Vmax_f 不爆表
19. **边界截断是正确物流行为不是 bug**：RK4 终值越界时全局同比缩放（scale=0 反应冻结）——产物满堆、逆向底物满堆、固定活性资源耗尽（水解缺水/H⁺ 耗尽无法逆向）都会表现为"反应停摆"，物理语义正确，测试场景设计时必须给预期方向的产物留出容量空间
20. **固定活性物种约定**：`{water, hydrogen_ion}` 不进速率方程（eQuilibrator 变换值已隐含 H₂O 活度 1/pH7）但参与化学计量结算（ENO 产水物品），反应物侧耗尽停供（水解必须供水）

## 第三章 编码与开发规范

### 3.1 命名规范

- Java 惯例：类/接口 PascalCase、方法/字段 camelCase、常量 UPPER_SNAKE_CASE、包名全小写、`final` 修饰不可变字段
- 注册名（DeferredRegister 的 path）：小写蛇形 `lower_snake_case`，如 `glucose_molecule`
- `MachineType` 枚举名用大写下划线，与方块注册名语义对应
- 主类、注册类、工具类命名直白，避免缩写（`ReactionParser` 而非 `ReacParser`）

### 3.2 注释规则

- **所有注释使用中文**
- **禁止使用分割线类型注释**：如 `# ----`、`# ====`、`# ****` 等
- **禁止使用 emoji**
- **注释句尾不加句号**
- **注释必须完善且详细**：既要写明这段代码在做什么，也要写明为什么要这么做；关键逻辑、复杂算法、非显而易见的设计决策必须有充分注释说明
- 每个函数和类必须有 docstring 注释，说明：
  - 函数/类的用途
  - 做了什么，为什么这样做
  - 传入参数（名称、含义）
  - 返回值（含义）

### 3.3 文件编码约定

- 所有文本文件（.java / .json / .md / .toml / .gradle）统一为**无 BOM 的 UTF-8**
- PowerShell 5.1 的 `Get-Content` 默认按系统代码页（GBK）解析 UTF-8 文件，导致中文乱码，读取文本文件必须显式指定编码：`Get-Content -Encoding UTF8`
- 即使正确读取，PowerShell 5.1 控制台代码页（chcp 936）输出中文仍会乱码，命令开头先设置控制台输出编码：`[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`
- Java 编译编码已由 build.gradle 的 `options.encoding = 'UTF-8'` 保证，无需额外处理
- 若发现日志/输出中文乱码，优先检查上述读取与显示两层的编码设置，而非怀疑文件本身（write 工具写出的文件均为 UTF-8）

## 第四章 工作流程和工程标准

### 4.1 任务执行流程

每次接到任务时，必须按以下流程执行，不得跳步：

#### 1. 明确任务目标
- 确认任务类型：新增功能（feat）、修复 bug（fix）、重构（refactor）、还是其他
- 如果任务描述不清晰，**必须先向用户确认**，禁止自行假设

#### 2. 调查现状
- 阅读相关实验文件夹的代码，理解当前实现
- 查看目录结构、依赖关系、调用链路
- 确认修改范围和影响面

#### 3. 制定计划
- 列出具体的修改步骤（改哪些文件、加什么逻辑、删什么代码）
- 对于复杂任务，使用 todo list 跟踪进度
- 使用数据流图梳理逻辑，要求：分阶段组织、按模块展开、说明每步的**输入/操作/产出**，关键设计决策必须写明原因
- 计划中必须包含验证方式（如何确认改动正确）

#### 4. 确认计划
- 将计划呈现给用户，等待用户确认后再执行
- 如果用户提出调整，修改计划后重新确认

#### 5. 执行实施
- 严格按照确认后的计划逐步执行
- 遵守本文档中所有编码规范
- 执行过程中发现计划遗漏，及时告知用户并补充

#### 6. 对照检查
- 执行完成后，回顾计划中的每一条，逐项确认是否已完成
- 如发现遗漏或错误，立即修正

#### 7. 提交成果
- 确认所有修改无误后，提交 git commit
- Commit Message 遵循 Conventional Commits 规范
- 向用户简要汇报本次完成的内容

### 4.2 验证标准

- 任何代码改动后必须运行 `.\gradlew.bat build` 确认编译与打包通过
- **推送时机：`gradlew build` 编译打包通过即可 commit 并 push，禁止等待 runClient 实测**。实测属于可选的后置验证，实测发现的问题在后续对话中以新的 fix 提交处理，绝不阻塞当前推送
- GameTest 用 `.\gradlew.bat gameTestServer`；进游戏实测用 `.\gradlew.bat runClient`
- 构建中途失败可直接重跑，Gradle 缓存可断点续传

### 4.3 变更纪律

- 每轮对话结束时有文件修改必须 commit 并 push（见 1.7），push 必须携带 2.5 所述网络参数
- commit 前自查：无遗留调试代码、无未注释的关键逻辑、git status 无意外文件
