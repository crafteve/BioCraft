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

纪元一的开发分批顺序：物品地基（原子/分子注册 + 视觉）→ TNT 爆炸转化 + 熔炉产 ATP → 反应引擎（ReactionParser）→ 三台原始机器

### 1.6 开发流程

迭代循环：编写代码 → `gradlew build` 验证编译 → `gradlew runClient` 进游戏实测 → `gradlew runData` 生成资源 → 提交 commit。具体命令见第二章，任务执行规范见第四章

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

### 2.2 构建与运行目录

- `gradle/wrapper/` — wrapper 配置。`distributionUrl` 指向**腾讯云镜像**的 Gradle 9.2.1（原官方 URL 已替换，勿改回）
- `build/` — 构建产物（libs 下产出 `biocraft-1.0.0.jar`），gitignore
- `run/` — 游戏运行目录（存档、日志），gitignore
- `src/generated/` — datagen 输出目录，随 `runData` 生成

### 2.3 资源配置

- `src/main/templates/META-INF/neoforge.mods.toml` — 含 `${mod_id}` 等占位符，由 `generateModMetadata` 展开到 `build/generated/sources/modMetadata`，**不要直接改生成产物**
- `src/main/resources/assets/biocraft/lang/` — 目前只有模板 `en_us.json`，缺 `zh_cn.json`

### 2.4 Java 源码结构

现状（模板遗留，待清理）：`com.github.crafteve.biocraft` 下只有 `BioCraft.java`（主类，塞了 3 个 DeferredRegister 和 example_block/example_item/example_tab 示例）、`BioCraftClient.java`（客户端入口）、`Config.java`（示例配置项）。新功能不要挂在这些示例上，按规划包结构开发

规划中的包结构（与 1.4 技术架构一一对应）：

```
com.github.crafteve.biocraft
├── BioCraft.java                 # 瘦身为纯装配：注册各 init 类 + 事件总线
├── BioCraftClient.java
├── Config.java
├── init/                          # 注册中心（从主类拆出）
│   ├── ModItems.java             # 原子/分子物品 DeferredRegister
│   ├── ModBlocks.java            # 机器方块注册（方块本体只有通用 MachineBlock）
│   ├── ModBlockEntities.java     # MachineType → BlockEntityType 映射
│   └── ModCreativeTabs.java
├── item/
│   ├── MoleculeItem.java         # 通用分子基类：化学式、元素组成、堆叠=分子数
│   ├── MoleculeColors.java       # ItemColor 实现（Dist.CLIENT）
│   └── DnaTemplateItem.java      # DNA 模板（NBT 存碱基序列）
├── block/MachineBlock.java       # 唯一机器方块类
├── blockentity/
│   ├── MachineType.java          # 枚举：DNA_ENCODER / TRANSCRIBER / TRANSLATOR / 糖酵解各步...
│   └── MachineBlockEntity.java   # 事件驱动+睡眠、startTick/requiredTicks、仅变更时发包
├── reaction/                      # 反应引擎（配置驱动，不硬编码）
│   ├── MoleculeRegistry.java     # 分子定义表（化学式↔物品）
│   ├── ReactionParser.java       # "GLC + 2ATP -> F6P + 2ADP" 字符串解析
│   ├── Reaction.java             # 底物/产物/系数/机器类型
│   └── ReactionLoader.java       # 从配置文件加载配方
├── machine/
│   ├── MachineBehavior.java      # 策略抽象：速度公式/进度/停摆判定
│   ├── RateLimitingBehavior.java # 限速酶：AMP 激活、指数进度
│   ├── IsomeraseBehavior.java    # 异构酶：恒定 1 秒、堆叠≥8
│   └── RedoxBehavior.java        # 氧化酶：NAD⁺ 双阶段、卡死 50%
├── gui/                          # MachineMenu + MachineScreen（3 种变体渲染 + 停摆提示）
├── network/                      # ModMessages + MachineSyncPacket
├── event/                        # TNT 爆炸转化、熔炉燃烧产 ATP（纪元一）
└── organelle/                    # 纪元三：相邻检测 + 控制核心（线粒体/内质网）
```

### 2.5 其他环境

- `.github/workflows/build.yml` — CI：push/PR 触发，JDK 21 + `gradlew build`。CI 环境无国内镜像（走官方源），构建慢是预期的，**不要试图为 CI 配镜像**
- 用户全局 `C:\Users\17094\.gradle\init.d\mirror.gradle` — 阿里云 Maven 镜像（前置 + 官方 fallback），**不在仓库内**。JDK 21 位于 `C:\Program Files\Java\jdk-21`（JAVA_HOME 已配好）
- **GitHub 推送网络配置**（国内网络硬性要求，实测经验）：
  - GitHub 直连 HTTPS 会被重置（Connection reset），git 默认也不读系统代理，且 Windows 自带 schannel TLS 后端与代理握手失败
  - 本机代理：`127.0.0.1:7892`（系统代理已开启）。push/fetch 必须显式携带 OpenSSL 后端 + 代理参数：
    - `git -c http.sslBackend=openssl -c http.proxy=http://127.0.0.1:7892 -c https.proxy=http://127.0.0.1:7892 push`
  - 本地分支名为 `main`，与远端默认分支一致；远端仓库初始含 GitHub 自动生成的 `LICENSE`，已合并保留，勿删除

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
- GameTest 用 `.\gradlew.bat gameTestServer`；进游戏实测用 `.\gradlew.bat runClient`
- 构建中途失败可直接重跑，Gradle 缓存可断点续传

### 4.3 变更纪律

- 每轮对话结束时有文件修改必须 commit 并 push（见 1.7），push 必须携带 2.5 所述网络参数
- commit 前自查：无遗留调试代码、无未注释的关键逻辑、git status 无意外文件
