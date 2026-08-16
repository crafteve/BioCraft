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
- **酶工厂由 `enzymes.json` 结构化数据表解析创建**——每个工厂方块是一个**化学热力学 + 动力学反应模拟器**：遵循米氏方程（Km/kcat/多底物共享分母）、化学热力学（Keq 绝不缩放/Haldane 逆向 Vmax/温度修正），反应式即配方。**没有任何原版风格的机器合成配方**。中心法则链（蛋白质来源：DNA编码器 → 转录仪 → 翻译仪 → 内质网折叠器 → 高尔基体修饰仪）为未来设计，尚未实现
- **反应是多底物 / 多辅因子 / 多产物网络**，不是熔炉式"输入A→输出B"。机器通常需要 ATP + 氧化还原辅因子（NAD⁺/NADP⁺），并产出 ADP/AMP + NADH 等副产物，必须回收利用，否则产线堵塞
- **能量 = 物品物流，而非电线**。ATP/ADP循环 与 NAD⁺/NADH循环 是需要玩家设计并维护的闭环
- **没有升级阶级（MK2/MK3），也没有物理多方块结构**。升级靠"酶插件"（酶插件物品，NBT 驱动）插入机器；大型细胞器靠相邻方块检测 + 控制核心实现，绝不构造物理多方块结构

### 1.3 系统机制

- **物质层级**：物品即原子/分子/离子（碳/氢/氧/氮/磷、H₂O、葡萄糖 C₆H₁₂O₆、ATP/ADP、20 种氨基酸、核苷酸 A/C/G/T/U、NAD⁺/NADH、NTP、DNA模板/mRNA/新生肽链/成熟酶蛋白）。堆叠数 = 分子个数，严格化学计量比（如 1 葡萄糖 = 6C + 12H + 6O）
- **物品区分**：Tooltip 中的化学式为权威依据；不同分子类型使用 ItemColor 动态着色；在基础纹理上叠加原子符号图标
- **中心法则链（尚未开始）**：转录仪 → 翻译仪 → 内质网折叠器 → 高尔基体修饰仪，作为未来蛋白质（酶工厂）获取途径的设计方向；DNA 编码器已移除（重构为统一酶反应腔，见 1.5 进度）
- **酶动力学 = 纯米氏方程 + 化学热力学模拟**：机器的行为完全由引擎计算（平衡位置 = Keq 判决点、饱和有界、产物回压、ATP/NAD⁺ 参与速率），GUI 统一展示 v-t 通量/平衡区/速率读数，无特殊 GUI 变体；**温度影响待实现**（引擎温度修正 van't Hoff/Q10 已就绪，外部温度源未接入）

### 1.4 技术架构

- 只写一个通用 `MachineBlock` 类，**不要为每种机器单独建方块类**；酶工厂方块时代结束后机器收敛为唯一的"酶反应腔"（enzyme_chamber）——一个方块 + 一个 BE 类型 + 一个 MenuType，酶由 0 槽（酶蛋白物品）动态解析，`EnzymeFactoryBlockEntity` 随插入的酶种构建对应引擎模拟器
- 配方由 **enzymes.json 结构化数据表**驱动（反应物/产物直接写物品注册名 + 化学计量系数 + Km，Keq/ΔH/kcat 等热力学与动力学参数随表直填），**绝不硬编码**；引擎在注册期对每条数据执行断言校验，失败即快速失败
- 性能设计宗旨："事件驱动 + 睡眠"机制（睡眠已实现：无酶或全部物种浓度≈0 时跳过引擎步进；事件驱动 = 输入槽变动时唤醒计算为设计方向，当前为每 tick 步进）
- 细胞器（**尚未实现**）：相邻机器检测 + 控制核心方块（线粒体 = 基质控制器 + 十字排列的 4 个 ETC 模块；内质网 = 腔体机器紧邻堆叠实现速度线性叠加；膜 = 装饰性透明无碰撞方块，提供区室化增益）；ATP合酶的 H⁺ 浓度机制同样未实现

### 1.5 项目规划

模组核心架构分为两大部分：

1. **酶工厂**：负责生物化学分子的反应（合成代谢与分解代谢），是模组的"工厂"。每个工厂方块由 `enzymes.json` 数据表解析创建，是一个化学热力学 + 动力学反应模拟器；工厂接受分子输入、按米氏方程与化学热力学计算反应、输出分子产物与副产物
2. **分子**：工厂处理的物质。物品即分子/原子/离子（分子间按严格化学计量比换算），堆叠数 = 分子个数

**模组核心 = 建立不同逻辑的工厂**：通过数据表定义反应网络（糖酵解、发酵、TCA、ETC……），玩家搭建工厂流水线，实现完整的生物代谢循环（ATP/ADP、NAD⁺/NADH、H⁺、能量 FE 的回收与闭环）。

**最终目标 = 细胞工厂**：在工厂体系之上搭建合成生物学终极形态——以酶工厂网络模拟完整细胞代谢（合成代谢 + 分解代谢），实现近乎创造模式的全物质合成，且完全依赖生化产线供能。

**当前进度**（2026-08-16）：
- 已完成 物品地基：63 个分子物品（20 氨基酸/13 离子/5 原子/2 无机物/5 碱基含 U/4 NTP/3 辅酶/11 糖酵解）由 `substances.json` 数据表驱动注册，datagen 自动生成模型/语言/贴图
- 已完成 Tooltip 分子图渲染（自绘管线）：CDK 负责解析/2D 坐标/Kekulize（`Kekulization`），渲染层自绘——4x 超采样抗锯齿细键线（0.8px）、Kekulé 单双交替、环内双键朝环心偏移、杂原子符号绘制进纹理（深色底块截断键线、随分子等比缩放、显式 H 如 OH/NH₂）、竖长分子自动旋转 90° 横放、标签碰撞推开
- 已完成 Tooltip 信息行：黄色分子式（Hill 排序 Unicode 下标）+ 类别徽章 + 摩尔质量；结构式改为**按住 Shift 展示**，未按时显示提示行；离子/原子/无机物不展示结构式
- 已完成 图标缩写标注：`IItemDecorator` 在物品图标左上角绘制缩写（白字黑阴影双写、缩放 0.55、z 提升 200 层）；缩写数据使用 Unicode 上下标（H⁺/Ca²⁺/NH₄⁺/H₂O/NAD⁺，糖酵解编号如 G6P 保持原样）
- 已完成 tooltip 布局：手持物品时创意标签页标题（蓝色）自动移至 tooltip 末尾（`MoleculeTooltipLayout`）
- 已完成 视觉校验闭环：`.opencode/agents/vision.md` 视觉审查子代理（opencode-go/qwen3.7-plus 多模态）+ `tools/texturegen/` 程序化贴图工具链（PixelCanvas DSL，生成 PNG 后派 vision 子代理读图审查）
- 已完成 化学引擎内核（纯 Java 零 MC 依赖，`tools/engineTest/` 独立单测 22 用例全绿）：多底物可逆米氏乘积速率方程（共享分母，平衡精确 = Keq 绝不缩放 + 饱和有界 + 产物回压 + 全底物平等，ATP/NAD⁺ 参与速率）、RK4 积分（Δt=0.05）、温度修正（van't Hoff/Q10 + 0.1K 缓存）、固定活性物种（H₂O/H⁺/fe 只结算不进速率）、三断言数据防火墙（配平/数值健康/Keq 红线）；PGI 平衡收敛误差 <1%、黄金值快照防回归、可达通量收敛进引擎（显示层禁复制速率公式，见 2.6 欠账 23）
- 已完成 酶工厂数据驱动注册体系：`enzymes.json` 已有糖酵解 10 步 + 乳酸发酵线 4 酶共 14 条酶数据（HK/PGI/PFK/ALDO/TPI/GAPDH/PGK/PGM/ENO/PK + LDH/PDC/ADH/ATPase，Km/kcat/Keq/ΔH 数值溯源见根目录《糖酵解热力学数据库》md 文档与《新增分子与酶数据汇总（乳酸发酵线）》；无激活剂/抑制剂/stallMessage/kinetic 字段——该项目无此设计），`EnzymeFactoryRegistry` 注册期解析 + 引擎断言防火墙校验；数据表新增酶即自动注册酶蛋白物品/引擎数据/JEI 配方，代码零改动
- 已完成 BE 桥接：`EnzymeFactoryBlockEntity` 浓度-槽位双向投影（引擎连续浓度是权威，槽位 = floor(浓度×64)，余量驱动 GUI 进度条）、每 tick RK4 步进 + 睡眠机制、NBT 定点存档浓度、漏斗防呆弹出非法物品
- 已完成 槽位容量参数化（n 组）：`KineticConstants.SLOT_GROUPS=2` 每槽可容纳 2 组（128 个物品），浓度钳制上限放宽为 `MAX_CONCENTRATION = n + 1/64`（"槽满仍攒余量"合法，修复投入物品被吞 bug），可达通量/边界缩放满堆浓度随容量放大（修复 ALDO 类强偏向反应物酶平衡产物 <1 个抽不出的卡死）
- 已完成 酶工厂 GUI：256×256 手绘基底 `gui_v1.png`、滚动卡片物种槽（`isActive=false` 全接管绘制与命中）、反应方程式彩色分段渲染、v-t 通量折线图（4x 超采样、1s 一点 10 点）、平衡区（log(Q/Keq) 缩放滑块 + Keq/Q 读数）、速率实时读数；ContainerData 每 tick 同步，打开数据包一次性下发 v-t 历史
- 已完成 JEI 酶工厂配方显示：每酶一个专属配方类别（查看用途互不混淆），`EnzymeRecipeDisplay` 为 JEI/EMI 共享只读 DTO（零 JEI 依赖，新增酶自动生效）
- 已完成 酶方块物品 tooltip（过渡期已随方块移除，功能并入 EnzymeItem）：`EnzymeBlockItem` 展示缩写 + EC 类别名 + 可逆性 + 反应方程式（与 GUI 共用 `EnzymeEquation` 分段构建，浅底/深底两套配色）+ Keq + 正逆向饱和可达速率（引擎通量 ×64×0.05）+ 最适温度
- 已完成 酶工厂工业 IO：`ModCapabilities` 注册 ItemHandler.BLOCK capability，`EnzymeFactoryItemHandler` 物种过滤/全槽位可进可出/O(1) 索引，复用 setChanged 浓度回写链，懒加载单例；原版漏斗继续走 Container 接口不受影响；运行时 Pipez（run/mods，gitignore 不入库）实测管道物流
- 已完成 乳酸发酵线数据 + 能量（FE）物种体系：substances.json +3 分子（乳酸 LAC/乙醛 AcH/乙醇 EtOH），enzymes.json +4 酶（LDH 可逆 Keq 22000 / PDC 不可逆 Keq 3200 / ADH 可逆 Keq 11000 / ATP 水解发电机不可逆 Keq 190000，fe 产物 count=100）；`EnergyKinetics` 纯函数（容量 = count×1000×64×MAX_CONCENTRATION，满存量=满浓度镜像，FE/tick 结算，引擎测试 22 用例含 FE 契约）；`FIXED_ACTIVITY_SPECIES` 加 "fe"（与 H₂O/H⁺ 同构：不进速率方程、计量结算、反应物侧耗尽停供）
- 已完成 FE 能量卡片 GUI：滚动卡片区泛化（`CardSpec` 物种卡/能量卡，能量卡按 JSON 原顺序与 input/output 卡片同滚动区），绿色进度条（存量/容量 kFE）+ 产率读数；BE 槽位↔物种映射（fe 无槽位，`slotToSpeciesIndex`），`MachineEnergyStorage` capability（产物侧只可抽/反应物侧只可充），能量存量 NBT 存档，满能量引擎边界缩放停转回压；JEI/tooltip 绿色能量行（每分子 kFE + 容量）
- 已完成 酶蛋白物品（新架构"酶 = 物品"重构第一步）：14 种酶蛋白物品由 enzymes.json 数据驱动注册（注册名 `enzyme_<酶id>` 与过渡期方块物品 id 区分），堆叠 64 = [E]（速率线性倍率待反应腔接入）；视觉与分子同款——双层烧杯贴图（layer0 按数据表 color 字段染色）+ 图标左上角缩写标注（`MoleculeItemDecorator` 泛化为 `AbbreviationProvider` 接口，MoleculeItem/EnzymeItem 共用）；tooltip 沿用酶方块摘要（EnzymeRecipeDisplay + EnzymeEquation）；新增"生物工艺 · 酶"创意标签页；**重构剩余步骤（统一酶反应腔、酶槽、换酶清空、方块废弃）在 feature/enzyme-chamber 分支进行中**
- 已完成 酶主题色数据表化：`MachineCategory` 枚举整体删除，`enzymes.json` 每条酶新增 `color` 字段（`#RRGGBB` 色号，同类别酶手动配同色系；`SubstanceData.parseColor` 解析补 alpha），物品染色/GUI/JEI/tooltip 统一直取数据表色；tooltip 移除 EC 类别名段（缩写 + 可逆性）
- 已完成 分子类别数据表化：`MoleculeCategory` 枚举整体删除，`substances.json` categories 段补 `color`（#色号）+ `structure`（结构式可用性）字段，新建 `MoleculeCategoryData` record 注册期解析——类别 id/主题色/结构语义单一事实源，新增类别改 JSON 即可（与酶体系同构对称）
- 已完成 统一酶反应腔（酶 = 物品重构核心落地）：14 个酶工厂方块全部移除，机器收敛为唯一 `enzyme_chamber`（方块/BE/Menu 各一，纯色占位贴图）；0 槽放酶蛋白物品（堆叠 = [E]，引擎 activity 通道线性倍速，1 个 = 1 倍速、64 个 = 64 倍速），BE 动态解析酶种构建模拟器；容器固定容量 = 1 + 最大非 fe 物种数，槽位映射动态；酶种变更（含取空）→ 物种槽全清空弹出 + 引擎归零（GUI 自动刷新），同种数量增减只改活性；无酶 → 停摆态（GUI 只显示 0 槽 + 背包 + 黑色告示"放入酶蛋白以启动反应腔"）；能量（fe）适配器随酶动态挂载/卸载（invalidateCapabilities 通知）；JEI 统一配方类型（每酶一条配方），酶物品作为配方酶槽 ingredient 精确追溯（U 键唯一配方），enzyme_chamber 催化剂挂统一类型（U 键全部配方）
- 已完成 DNA 编码器移除（破坏性）：方块/GUI/BE/网络包（ModNetwork/ServerboundDnaSequencePacket）/序列物品（SequenceItem/dna_template）/数据组件（ModDataComponents）/配方/贴图全部删除，`MachineType` 枚举与 `MachineSpec.Primitive` 一并移除，MachineBlock 只剩酶工厂形态；旧存档中 DNA 编码器方块与 DNA 模板物品丢失（既定破坏性更新）
- 已完成 侧向 IO 模式按钮（防误塞/防误取）：GUI INPUT/OUTPUT 滚动槽下边界上抬 9px，底部各新增一个 56×11 IO 模式按钮（主题色边框 + 浅色填充 + 模式文字，下边界与中央反应速率区背景底齐平，悬停原版槽位高亮同款浅色遮罩脉动动画 + 说明 tooltip）；三态循环（仅输入 → 仅输出 → 输入输出），INPUT 默认仅输入、OUTPUT 默认仅输出，点击经网络包（ServerboundSetIoModePacket）写服务端并 ContainerData 回刷；门控覆盖玩家 GUI（Slot.mayPlace/mayPickup）、工业管道（ItemHandler insertItem/extractItem）、原版漏斗（容器 canPlaceItem/removeItem）三路——防玩家/管道把物品塞进产物槽导致容量冻结（TPI 满堆卡 0.00 的预防性设计），IO 模式随 NBT 存档（换酶不重置）
- 已完成 酶容器方块视觉概念设计（Phase 1，贴图与渲染机制前置）：六视图 16×16 程序化概念稿（`tools/texturegen/ChamberAssets.java`，PixelCanvas 逐像素绘制：正面大观察窗 + 上排双灯、侧面竖直主题色管道 + 观察孔、背面大法兰 + 维护面板、顶面中央观察孔、底面金属底座）+ 展开图 + 8 倍预览；设计文档《酶容器方块概念设计_2026-08-16.md》定稿设计语言（五档金属色阶/玻璃色阶/统一左上光照/1px 像素语法/六面四角螺栓定位基准）、主题色槽位表（液体 0 号 tint + 灯 1 号 tint）、状态语义（无酶暗灰 / 有酶酶色，靠 BlockColor 动态实现无需 blockstate）；渲染机制方案：vanilla 多元素模型 + 贴片元素 tintindex 分区 + 灰度反照率贴图 + BlockColor 从 BE 缓存取酶色 + MachineBlock 新增水平 FACING；物品形态走默认 3D 方块模型（用户指定），ItemColor 返回无酶暗灰
- 已完成 酶容器方块正式贴图与渲染机制（Phase 2）：12 张反照率贴图（base 5 张 + 西面 side 镜像 1 张 + theme 灰度 6 张 + lamp 1 张，`ChamberAssets textures` 模式生成后拷入 resources）；`MachineModelProvider` 改多元素模型（1 主元素六面 base 无 tint + 12 贴片元素凸出 0.001，BlockElement 校验 [-16,32] 已核对反编译源码，tintindex 0=液体/1=灯，UV 按各面 vanilla 约定（north u=x / south u=16−x / east u=z / west u=16−z / up-down u=x）裁剪 theme 内容区，东西两侧贴片块坐标一致 = 物理镜像对称）；BlockColor 从 BE 缓存取酶色（换酶更新 O(1)，无酶 = 空机暗灰）→ 同一模型零 blockstate 表达空机/有酶；ItemColor 物品固定空机暗灰（用户指定默认 3D 方块模型渲染）；`MachineBlock` 新增水平 FACING（放置正面朝玩家，旧存档回退朝北）；**运行期实测修复**：①外观不随酶变（读档才刷新）= 两层根因：客户端 BE 主题色缓存永不更新（handleEnzymeSlotChanged 客户端守卫 + 无 tick）→ BE 覆写 getUpdateTag/getUpdatePacket 携带主题色 + 服务端换酶时 sendBlockUpdated(pos,state,state,3)（SignBlockEntity 同款标准姿势）；且**服务端发的 BlockUpdatePacket 的 state 与当前相同，客户端 Level.setBlock 因状态不变直接 no-op 不重烘焙**（源码实证）→ 客户端 handleUpdateTag 收到数据后必须主动 `level.sendBlockUpdated` 请求重渲染；另加客户端 setChanged 分支（GUI 操作经菜单槽位同步触发）即时解析 0 槽刷新主题色——GUI 路径即时变色、管道/漏斗路径走 data packet，双通道互备；②物品非默认 3D = 裸 elements 模型缺 display 段（vanilla 方块物品的显示变换来自 block/block 父模型）→ 模型补 vanilla block/block 同款 display + gui_light=side + particle
- 已完成 酶容器贴图 Phase 3 ①②（状态灯与自发光）：**停摆红灯**——引擎暴露 `wasStalled()`（上次 step 最小边界缩放 ≈0 = 物理冻结：产物/逆向底物满堆、固定活性资源耗尽；空闲与平衡不算），BE tickServer 每 tick 判定、状态翻转时把灯色缓存切红灯并复用主题色更新包通道通知客户端（客户端零改动，engineTest 27 守护信号契约）；指示灯三态：无酶灭灯 / 运行提亮酶色 / 停摆红。**自发光**——`ModelEvent.ModifyBakingResult` 把 enzyme_chamber 全部 facing variant 包装为 `EmissiveLampBakedModel`（client 包），getQuads 对 tintindex==1 灯 quad 应用 `QuadTransformers.settingMaxEmissivity()`（光照全亮，夜晚真实发光；物品模型 key 不命中不受影响）
- 待开发 酶容器贴图 Phase 3 ③④（可选，用户暂未选）：气泡粒子（运行中观察窗冒泡）、GUI 小模型图标
- 待开发 TNT 爆炸转化 + 熔炉产 ATP（事件层）
- 待开发 中心法则链（转录仪/翻译仪/内质网折叠器/高尔基体修饰仪，未实现；酶蛋白未来经中心法则产出后插入反应腔）
- 待开发 糖酵解流水线搭建（14 步酶数据已齐含乳酸发酵线，机器布局与产线衔接待做；LDH/PDC/ADH 需供 H⁺、ATPase 需供水，产 H⁺ 机制待电解水补）
- 待开发 温度机制（引擎温度修正已就绪，外部温度源未接入）
- 待开发 酶插件升级、细胞器与细胞工厂（ETC 模块、膜区室化、ATP合酶 H⁺ 浓度机制）

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
- **每次 feat/fix（对玩家可见的功能或修复）提交，必须同步更新 README 底部《更新日志》区域**：
  - 按日期分组（当日已有分组则追加条目），新日期置顶
  - 条目格式：`**feat** 简述` 或 `**fix** 简述`，与 commit message 同内容但面向玩家描述（不含内部类名/文件路径）
  - 纯内部改动（refactor/chore/docs/test 与用户不可感知的修复）可跳过，但**涉及玩法、GUI、物品、数据表、物流的行为变化一律要写**
  - 文档同步纪律见 4.3
- **搁置问题（Issue 区）规则**：已定位根因但暂不修复/无法由本 mod 修复的已知问题，登记在 README 底部《搁置问题（Issue 区）》：
  - 每条含：编号 + 登记日期、现象、根因分析（含反编译/日志等验证证据）、排除项（已排查证明与本 mod 无关的项）、搁置原因、规避方案
  - 新问题先登记再搁置；修复后移出该区并同步写《更新日志》

## 第二章 项目架构与目录

### 2.1 根目录文件

- `build.gradle` — ModDevGradle 2.0.143；Java 21 toolchain；Parchment 2024.11.17；runs 四配置（client / server / gameTestServer / data）；`generateModMetadata` 任务展开 mods.toml 占位符；datagen 输出 `src/generated/resources` 已加入资源源集
- `gradle.properties` — mod 元数据（mod_id=biocraft、mod_group_id=com.github.crafteve.biocraft）+ 构建参数（Xmx1G、daemon、parallel、caching、configuration-cache）
- `settings.gradle` — pluginManagement + foojay 工具链插件（本地已有 JDK 21，不会触发下载）
- `gradlew` / `gradlew.bat` — Gradle wrapper 启动脚本（Windows 上 gradlew.bat 依赖 JAVA_HOME 定位 JDK）
- `.gitignore` — 忽略 `build/`、`.gradle/`、**`.vscode/`**、`src/generated/.cache/` 等；**`run/` 白名单入库**（容灾备份：`run/config/`（含 emi.css 手改 dev-mode）、`run/mods/`（运行时依赖 mod）、`run/saves/`（测试存档）、`options.txt`、`emi.json` 提交，`logs/crash-reports/screenshots` 等易变垃圾仍忽略——回退分支即可恢复测试环境）。注意 `.vscode/` 被忽略，工作区配置不提交
- `.gitattributes` — 行尾/文本属性
- `README.md` — 项目介绍（中文，含 B 站视频嵌入与当前进度章节，与本文档 1.5 进度同步维护；无图片/emoji）
- `糖酵解热力学数据库_2026-08-13.md` — 酶数据数值溯源文档（eQuilibrator ΔG°′/BRENDA Km/kcat 出处与换算过程，enzymes.json 的权威数据源）
- `全部SMILES结构式清单_2026-08-13.md` — 全部化合物 SMILES 核对清单（PubChem canonical 与 eQuilibrator 双源交叉，InChIKey 前 25 位连通性校验），substances.json 的 SMILES 权威对照（tools/smilesCheck 的期望表来源）
- `酶容器方块概念设计_2026-08-16.md` — 酶容器方块视觉概念设计文档（定位/设计语言调色板/主题色槽位表/六面结构图/状态语义/渲染机制方案），配图由 `tools/texturegen/ChamberAssets.java` 程序化生成
- `TEMPLATE_LICENSE.txt` — 模板许可
- `tools/texturegen/` — 程序化贴图工具链（`PixelCanvas.java` 像素画 DSL + `TextureScript.java` 示例脚本 + `GuiAssets.java` GUI 资产生成器 + `ChamberAssets.java` 酶容器方块概念稿与正式贴图生成器），纯 JDK 21 AWT 零依赖，与 Gradle 构建完全隔离。编译 `javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java`，运行 `java -cp tools/texturegen/out TextureScript [输出目录]`；输出目录 `tools/texturegen/output/` 已 gitignore，正式贴图确定后拷入 `src/main/resources`
- `tools/engineTest/` — 化学引擎独立单测（16 用例），纯 JDK 零依赖扮演"伪方块实体"验证引擎纯函数契约。运行前需先 `gradlew build`（生成主代码 class），再 `javac -encoding UTF-8 -cp build/classes/java/main -d tools/engineTest/out tools/engineTest/*.java` + `java -cp "build/classes/java/main;tools/engineTest/out" engineTest.EngineSelfTest`；退出码 0=全绿、1=有失败。输出目录 `tools/engineTest/out/` 已 gitignore
- `tools/smilesCheck/` — 物质表 SMILES 批量校验程序（`SmilesCheck.java`，独立工具不进 mod 源码源集）。先 `javac -encoding UTF-8 -cp build/cdk/cdk-all.jar -d tools/smilesCheck/out tools/smilesCheck/SmilesCheck.java`，再从 substances.json 提取 id+SMILES 生成 actual.tsv，运行 `java -cp "build/cdk/cdk-all.jar;tools/smilesCheck/out" smilesCheck.SmilesCheck`（0=全过、1=有失败）。三连校验：CDK 可解析性、重原子组成一致、键序归一化后 Pattern 双向子图同构（连通性口径，芳香/电荷差异算记法差异，与《全部SMILES结构式清单_2026-08-13.md》对照）；清单无对照条目（thymine/OH⁻/Fe³⁺/H⁺/5 原子）也做可解析性检查。期望表注意事项：立体标记（[C@H]）会让 VF2 双向判定不对称，F16P 期望须用中性无立体写法。升级 CDK 或新增分子后重跑；out/ 与 actual.tsv 已 gitignore

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
├── BioCraft.java                 # 瘦身为纯装配：注册各 init 注册中心（无功能实现）
├── BioCraftClient.java           # 客户端装配：菜单屏幕绑定 + 方块/物品染色（BlockColor 按 BE 缓存酶主题色给贴片元素 tint，ItemColor 物品固定空机暗灰）+ 烘焙模型包装（ModifyBakingResult 给酶反应腔挂自发光灯）
├── init/
│   ├── ModItems.java             # 读 substances.json → 动态注册 66 个 MoleculeItem；读 enzymes.json → 动态注册 14 个酶蛋白物品（enzyme_<酶id>，EnzymeItem）
│   ├── ModBlocks.java            # 方块/BE 类型/MenuType/方块物品四件套：唯一酶反应腔 enzyme_chamber（方块/BE/菜单各一，酶由 0 槽动态解析）
│   ├── ModCreativeTabs.java      # 多标签页架构：现有"生物工艺 · 分子"页 + "生物工艺 · 酶"页 + "生物工艺 · 机器"页
│   ├── EnzymeFactoryRegistry.java # 读 enzymes.json → 构建酶数据档案（含 color 主题色字段；构建期跑引擎断言防火墙，失败快速失败）
│   └── ModCapabilities.java      # 机器 capability 注册（酶工厂 ItemHandler.BLOCK → BE 懒加载 IO 适配器单例）
├── data/
│   └── SubstanceData.java        # 物质表读取工具（classpath，GsonHelper 解析）
├── item/
│   ├── AbbreviationProvider.java # 图标缩写标注数据源接口（MoleculeItem 与 EnzymeItem 共用装饰器的契约）
│   ├── MoleculeItem.java         # 分子基类：SMILES/缩写/染色/类别 + tooltip 布局（Shift 展示结构式）
│   ├── MoleculeCategoryData.java # 分子类别数据档案（substances.json categories 段驱动：id/#色号/结构式可用性）
│   ├── MoleculeDataCalculator.java # CDK 计算分子式（Hill 排序）与摩尔质量，缓存+防御降级
│   ├── MoleculeColors.java       # ItemColor 染色 + TooltipComponent 工厂 + 装饰器注册（Dist.CLIENT，含酶物品染色/装饰器）

│   └── EnzymeItem.java           # 酶蛋白物品（新架构酶形态）：数据驱动注册、堆叠 64 = [E]、双层贴图数据表色染色、tooltip 沿用酶方块摘要
├── client/                       # 客户端渲染辅助（分子结构图自绘管线 + 方块模型包装）
│   ├── EmissiveLampBakedModel.java # 自发光指示灯模型包装：tintindex==1 灯 quad 光照全亮（ModifyBakingResult 挂载）
│   ├── MoleculeTextureCache.java # CDK 解析+2D 坐标+Kekulize → 自绘键线骨架 → DynamicTexture 缓存
│   ├── MoleculeBondRenderer.java # 键线绘制（0.8px 细线、Kekulé 单双交替、环内双键朝环心偏移）
│   ├── MoleculeGeometry.java     # 2D 几何：竖长分子旋转横放、标签碰撞推开
│   ├── MoleculeRingSearch.java   # 环键判定（CDK RingSearch 封装，勿改回自研 BFS）
│   ├── MoleculeRenderConstants.java # 渲染常量（线宽/间距/缩放，统一调参点）
│   ├── MoleculeSymbolRenderer.java # 杂原子符号绘制进纹理（深色底块截断键线、等比缩放、显式 H）
│   ├── MoleculeTooltipComponent.java # TooltipComponent+ClientTooltipComponent：blit 结构图
│   ├── MoleculeTooltipLayout.java # 标签页标题移置 tooltip 末尾（GatherComponents 事件）
│   └── MoleculeItemDecorator.java # 图标左上角缩写标注（IItemDecorator，白字黑阴影、z=200；超长缩写按可用宽度自适应缩小）
├── block/MachineBlock.java       # 唯一机器方块类：统一酶反应腔形态（无酶数据字段——酶由 BE 从 0 槽物品动态解析）+ 水平 FACING（放置正面朝玩家，4 向旋转/镜像支持）
├── blockentity/
│   ├── MachineBlockEntity.java   # 机器 BE 基类：SimpleContainer（setChanged 转发 + getMaxStackSize 委托 slotStackLimit 钩子 + canPlaceItem/removeItem 门控钩子）+ NBT 存档 + MenuProvider + dropExtraContents 钩子
│   ├── EnzymeFactoryBlockEntity.java # 酶反应腔：0 槽酶槽动态解析（换酶清空/同种增减只改活性）+ 浓度-槽位双向投影 + 每 tick 引擎步进 + 睡眠机制 + v-t 历史环形缓冲 + 定点存档 + 懒加载 IO 适配器单例 + fe 槽位映射/能量镜像结算 + 侧向 IO 模式（canInsertIntoSlot/canExtractFromSlot 三路门控统一入口）+ 主题色缓存（液体/灯 ARGB，换酶更新，客户端 BlockColor 数据源）
│   ├── EnzymeFactoryItemHandler.java # 工业 IO 适配器（IItemHandlerModifiable）：0 槽酶槽过滤/物种过滤/全槽位可进可出（受 IO 模式门控）/O(1) 索引，复用 setChanged 浓度回写链
│   ├── IoMode.java               # 侧向 IO 模式枚举（仅输入/仅输出/双向，GUI 按钮/管道/漏斗三路门控共用）
│   └── MachineEnergyStorage.java  # 能量存储适配器（IEnergyStorage）：产物侧 fe 只可抽/反应物侧只可充，懒加载单例
├── reaction/                     # 化学引擎内核（纯 Java 零 MC 依赖，已完成 + 26 用例单测）
│   ├── EnzymeFactoryData.java    # 酶数据档案 record（物品 id 直填/每物种自带 Km/直存 Keq/color 主题色直填）
│   ├── EnzymeSimulator.java      # 每机一实例：RK4 积分 + 温度缓存 + 边界缩放
│   ├── ReactionDefinition.java   # 不可变网络档案（物种表/化学计量/Haldane Vmax_b(T)/可达通量）
│   ├── KineticsCalculator.java   # 共享分母乘积速率方程 + 缩放换算
│   ├── EnergyKinetics.java       # 能量（FE）物种纯函数：容量/存量镜像/FE 结算/isEnergySpecies 拦截（显示层禁复制）
│   ├── ReactionState.java        # 浓度/温度/活性容器（BE 与引擎共享）
│   ├── StepResult.java           # 通量报告（fwd/rev/net）
│   ├── ThermoUtil.java           # Keq 换算/van't Hoff+Q10/Arrhenius
│   └── KineticConstants.java     # 缩放常量（TIME_SCALE=1000 唯一节奏旋钮，待 M6 调参；SLOT_GROUPS=2 槽位容量组数 + MAX_CONCENTRATION 浓度上限；FIXED_ACTIVITY_SPECIES 含 fe）
├── gui/
│   ├── MachineMenu.java          # 酶反应腔菜单：0 槽酶槽（isActive=true vanilla 全权）+ 滚动卡片物种槽（isActive=false 全接管）+ DATA_ENZYME/IO 模式同步 + 打开数据包解析 + RestrictedSlot（mayPlace/mayPickup 双门控 IO 模式）
│   └── MachineScreen.java        # 酶反应腔屏幕：gui_v1.png 手绘基底 + 滚动卡片 + v-t 折线图 + 平衡区 + 速率区 + IO 模式按钮（三态循环/悬停遮罩动画/tooltip）+ 无酶告示态（黑色居中提示）
├── network/                      # 网络载荷（NeoForge payload 机制）
│   ├── ModNetwork.java           # 载荷注册中心（RegisterPayloadHandlersEvent 装配 playToServer 通道）
│   └── ServerboundSetIoModePacket.java # IO 模式切换包（GUI 按钮 → 服务端：方块坐标 + 区域 + 模式，服务端校验后写 BE）
├── compat/                       # 配方显示 mod 兼容层（JEI/EMI 均为可选依赖，compileOnly + run/mods）
│   ├── EnzymeRecipeDisplay.java  # 配方展示只读 DTO（零 JEI/EMI 依赖，两套显示层共享，新增酶自动生效）
│   ├── EnzymeEquation.java       # 反应方程式共享分段构建（GUI 与物品 tooltip 同一份逻辑，浅底/深底两套配色）
│   ├── CompatRenderUtil.java     # 兼容层渲染工具（信息卡文字/槽位纹理绘制/darkenOneFifth）
│   └── jei/
│       ├── BioCraftJeiPlugin.java # JEI 插件入口（@JeiPlugin 自动发现，统一配方类型：酶物品 ingredient 精确追溯 + chamber 催化剂全量）
│       └── EnzymeFactoryRecipeCategory.java # 酶工厂配方类别（统一实例，酶槽显示酶物品 INPUT 角色）
└── datagen/
    ├── ModDataGen.java           # GatherDataEvent 装配
    ├── SubstanceModelProvider.java # 每物质两层模型 JSON（容器层 + 内容物层）
    ├── SubstanceLanguageProvider.java # en_us/zh_cn 语言生成（含类别/摩尔质量 key）
    └── MachineModelProvider.java # 机器模型生成：酶反应腔多元素模型（主元素六面 base + 12 贴片元素 tintindex 分区 0=液体/1=灯 + blockstate FACING 四向）+ 酶蛋白物品双层 generated（DNA 编码器与序列物品模型已随其移除）
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
1. **CDK 版本锁定 2.9**：2.12 全家桶（cdk-bundle）带 JPMS module-info 与 JDK 冲突；分拆模块 + 2.9 验证通过。升级 CDK 必须重跑全量 SMILES 校验（63 个全部能解析 + SDG 布局），校验方法：临时独立 Java 程序 + `build/cdk/cdk-all.jar`（详见 git 历史中 SmokeSmiles 类）
2. **依赖排除规则**：CDK 的依赖声明会传播版本约束，与 NeoForge 严格锁定冲突，必须排除 log4j/commons-io/commons-lang3/guava（MC 环境自带），且 `resolutionStrategy.force commons-lang3:3.14.0` 不能删
3. **SMILES 数据坑**：芳香环写法必须 CDK 兼容（小写芳香 + 显式 `[nH]`）。2026-08-15 批量校验（tools/smilesCheck 对照《全部SMILES结构式清单》连通性同构）修正 4 处：histidine（`c1cnc[nH]1` 写法 CDK Kekulé 解析失败，改用 PubChem canonical `C1=C(NC=N1)`）、cytosine（`NC1=CC(=O)NC=N1` 与 canonical 不同构）、atp/adp（`c1nc2c(nc1N)` 芳香式与 canonical 不同构，嘌呤环连接有误）。此前曾修 adenine/uracil/gtp 等。新增分子后必须跑一遍 `tools/smilesCheck` 批量校验（含无对照条目的可解析性检查）
4. **防御性降级**：`MoleculeDataCalculator` 解析失败返回 valid=false（tooltip 显示灰色提示），不抛异常——新增分子若写错 SMILES 不会崩游戏，但会显示"结构数据解析失败"
5. **tooltip 组件注册**：自定义 TooltipComponent 必须经 `RegisterClientTooltipComponentFactoriesEvent` 注册（NeoForge 查表转换，非 instanceof 机制），遗漏会抛 Unknown TooltipComponent
6. **进程残留**：runData/runClient 报错后可能残留 java 进程导致终端"卡住"，用 `--no-daemon` 运行可避免；残留进程任务管理器杀 java.exe
7. **Kekulé 交替用 CDK `Kekulization`**：自研 BFS 交替对奇数环/融合环/酮基环有化学错误，已弃用（git 历史有）；环键判定用 `RingSearch`，勿改回自研
8. **图标装饰器 z 层级**：IItemDecorator 绘制必须 `pose().translate(0,0,200)` 提升 z，否则被物品贴图覆盖（vanilla 堆叠数同款处理）
9. **结构式按 Shift 展示**：`getTooltipImage` 用 `Screen.hasShiftDown()` 控制；离子/原子/无机物类别恒不展示
10. **标签页标题移置**：vanilla 将标签页标题插入 tooltip index 1（物品名后），`MoleculeTooltipLayout` 遍历全列表匹配移动，勿只查首行
11. **缩写上下标**：`substances.json` 的 abbreviation 使用 Unicode 上下标（H⁺/Ca²⁺/NH₄⁺/H₂O/NAD⁺），糖酵解编号（G6P/3PG）保持原样——新增离子/无机物缩写需按此惯例书写
12. **MC 源码查找方法（重要排查手段）**：ModDevGradle 在本地缓存了已映射（mojmap）的反编译 MC 源码，路径：`%USERPROFILE%\.gradle\caches\neoformruntime\intermediate_results\decompile_*.jar`（按 jar 内 `net/minecraft/.../*.java` 路径直接 `jar xf` 提取即可，比 javap 字节码逆向高效得多）。另有 `sourcesAndCompiledWithNeoForge_*_output.jar` 可 javap 查 NeoForge patch 后的类（混淆 jar `minecraft_1.21.1_client.jar` 无映射名不可用）。排查"vanilla 机制行为"类问题时优先查源码而非猜。**1.21.1 特例（曾踩坑）**：BE 的每 tick 调度不在 `BlockEntityType` 侧——该类没有 `getTicker`（Builder 也无 ticker 参数），ticker 由 `EntityBlock.getTicker(Level, BlockState, BlockEntityType<T>)` 提供（vanilla 1.21 把 ticker 移到方块接口上），为机器加 tick 必须覆写方块类的 getTicker，匿名覆写 BlockEntityType 会编译失败
13. **1.21.1 容器 GUI 的 tooltip 渲染机制**：`AbstractContainerScreen.render` 本身**不**渲染 hoveredSlot 物品 tooltip（1.21 重构移除），`renderTooltip(GuiGraphics,int,int)` 改由**各子类 Screen 在 render 中显式调用**（源码实证：`InventoryScreen`、`ContainerScreen` 覆写 render 后调用 `this.renderTooltip(...)`）。自定义容器 Screen 覆写 render 时必须在 super 之后补调 `this.renderTooltip(graphics, mouseX, mouseY)`，否则悬停槽位无物品 tooltip（含自研 tooltip 组件）。另：`renderWithTooltip`（final）是 Minecraft 渲染入口，延迟 tooltip 走 `setTooltipForNextRenderPass`
14. **BlockEntity.setRemoved 双触发陷阱**：`setRemoved()` 不只方块破坏时调用，**世界卸载/区块卸载同样触发**（Level 卸载 chunk 时清理 BE）。掉落实体/玩家反馈类逻辑**禁止放 setRemoved**，否则进出存档会误触发（实测：DNA 编码器缓冲池碱基每次进出存档爆一地，该机器已移除但规则仍适用）。正确位置是方块类的 `Block.onRemove`（仅方块被破坏/替换时触发），通过 `BlockEntity` 的 `dropExtraContents(Level, BlockPos)` 类钩子统一调用
15. **视觉审查子代理**：vision agent 定义在 `.opencode/agents/`，启动时加载，修改后必须重启 opencode 才生效；Task 派发时给出图片绝对路径与审查要点，子代理无 edit 权限只能读图返回文字；mimo-v2.5 描述精度不足已弃用，主用 qwen3.7-plus，若在 OpenCode Go 订阅出现额度/速率限制或精度下降，换备选视觉模型（qwen3.6-plus / qwen3.8-max / gpt-5.6-luna）
16. **贴图工具链编码**：`tools/texturegen` 的 javac 必须带 `-encoding UTF-8`（Windows 默认 GBK 会编译失败），输出目录 gitignore，正式贴图需手动拷入 `src/main/resources`，工具脚本不进 mod 源码源集
17. **引擎零依赖隔离门禁**：`reaction/` 包只能 import `java.*`（当前仅 java.util.*）；`tools/engineTest` 的 javac classpath 只含 `build/classes/java/main`（无 MC 类），引擎若意外引入 MC 依赖编译直接失败——这是天然门禁，新增引擎代码时保持此约束
18. **引擎速率公式三大数学性质（勿改坏）**：①平衡精确——可逆多底物共享分母乘积形式下 v=0 时 ∏产物/∏底物 = Keq（Haldane 保证），Keq 绝不缩放红线由构建断言+收敛测试双重守护；②逆向 Vmax 由 `Vmax_f·∏KmP/(∏KmS·Keq)` 决定而非独立逆向数据（Keq 小时逆向极强是正确行为）；③饱和有界——高浓度速率 ≤ Vmax_f 不爆表
19. **边界截断是正确物流行为不是 bug**：RK4 终值越界时全局同比缩放（scale=0 反应冻结）——产物满堆（上限 = 槽位容量 n 组 + 余量，见 KineticConstants.MAX_CONCENTRATION）、逆向底物满堆、固定活性资源耗尽（水解缺水/H⁺ 耗尽无法逆向）都会表现为"反应停摆"，物理语义正确，测试场景设计时必须给预期方向的产物留出容量空间
20. **固定活性物种约定**：`{water, hydrogen_ion}` 不进速率方程（eQuilibrator 变换值已隐含 H₂O 活度 1/pH7）但参与化学计量结算（ENO 产水物品），反应物侧耗尽停供（水解必须供水）
21. **JEI/EMI 双装的 EMI 配方 id 重复噪音（已定位根因 + 已解）**：EMI 的 JemiPlugin 兼容桥（`dev/emi/emi/jemi/JemiPlugin`，同时实现 JEI 的 IModPlugin 与 EMI 的 EmiPlugin）会把 JEI 内置的 tag 分组配方（`jei:/minecraft/planks`、`jei:/c/dyed/*` 等共 344 条）导入 EMI，与自身机制产生同 id 重复；EMI 的 `EmiRecipes$Manager` 在 **devMode** 下检测重复并输出 `[EMI] 2 recipes loaded with the same id: jei:/...` ERROR + warning 计数。dev 环境（runClient）被 EMI 自动识别为开发环境，`run/config/emi.css` 生成 `dev-mode: true`（默认值 = `isDevelopmentEnvironment()`）。**解法：手动把 `run/config/emi.css` 的 `dev-mode` 改为 `false`**（EMI 官方注释"Not recommended for general play"，实测改后 ERROR 归零且配置不被回写；run/ 目录 gitignore，新环境需手动改）。玩家正式环境 dev-mode 天然为 false，从来看不到这些噪音，与 BioCraft 代码无关
22. **dev 环境依赖 mod 放置约定**：runClient 需要的可选依赖 mod（JEI/EMI）只放 `run/mods/` 目录（FML 直接扫描加载，日志可证实），**不要再加 localRuntime 冗余**——双份 jar 会被 mod 发现扫描两次（UniqueModListBuilder 虽会按版本去重，但 classpath 冗余属配置错误）
23. **可达通量收敛进引擎（显示层禁复制速率公式）**：引擎 Vmax_f/Vmax_b 是速率方程的数学参数（浓度趋无穷的极限），而"游戏内可达上限"是方程在**满堆浓度**（= 槽位组数 SLOT_GROUPS，n=2 时浓度 2.0 = 128 个物品）处的函数值——**两者都只能由引擎给出**：`ReactionDefinition.forwardReachableFlux()/reverseReachableFlux()`（构造满堆浓度向量直接调 forwardFlux/reverseFlux）。GUI 速率条刻度与 JEI/EMI 信息卡一律调引擎方法，只做 ×64×0.05 单位换算（/tick）；曾在显示层复制 saturationReachable 公式（已删）导致职责错位，违反本约定会造成公式漂移。engineTest 第 16 用例手算对照守护此契约
24. **JEI 兼容层设计**：每酶一个专属配方类型与类别（配方 id 形如 `biocraft:enzyme_factory/<酶id>`，注册顺序 = 酶数据表顺序），查看某酶方块用途时只显示该酶配方而非全部混类；`EnzymeRecipeDisplay` 是 JEI/EMI 共享的只读展示 DTO，**零 JEI/EMI 依赖**（只 import Minecraft 与 reaction 包，两套显示层插件在各自框架存在时才加载）——新增酶（改 enzymes.json）自动生效，勿在 DTO 中引入框架类，否则另一侧框架缺失时类加载崩溃
25. **机器工业 IO 设计（IItemHandler capability）**：酶工厂已注册 `Capabilities.ItemHandler.BLOCK`（`ModCapabilities`），适配器 `EnzymeFactoryItemHandler` 直接操作 BE 容器——**不要改用 IItemHandler 当内部存储**：vanilla GUI Slot/漏斗/掉落/NBT 全部硬绑定 `Container` 接口（`InvWrapper.setStackInSlot` 不过滤、`IItemHandler` 无 setChanged 通知、无序列化），替代即断原版兼容；正确模式是"SimpleContainer 内部权威 + capability 暴露"（Mekanism/AE2 同款）。适配器约定：物种过滤（`isItemValid` 与 GUI `RestrictedSlot.mayPlace` 同规则）、全槽位可进可出（不做方向区分，但受侧向 IO 模式门控——GUI INPUT/OUTPUT 滚动槽底部按钮三态：仅输入/仅输出/双向，GUI mayPlace/mayPickup、管道 insertItem/extractItem、漏斗 canPlaceItem/removeItem 三路同规则，见 IoMode 与 BE 的 canInsertIntoSlot/canExtractFromSlot 统一入口）、O(1) 索引、每 BE 懒加载单例（`getItemHandler`）；浓度回写零额外代码（容器 setChanged 链自动触发 `syncFromSlots`）。运行时管道测试用 Pipez（`run/mods/`，gitignore 不入库，dev 无依赖）
26. **槽位容量参数化的隐藏钳制点（已踩坑 + 已修正方案）**：让槽位容纳 n 组物品（128 个），只改容器 `getMaxStackSize` **不够**——vanilla 多条物流路径以"物品自身堆叠上限"参与 `min(物品, 槽位)` 运算：`Container.getMaxStackSize(ItemStack)` 默认 min、`Slot.safeInsert`/`getMaxStackSize(ItemStack)`、`ItemHandler.insertItem` 的 limit。而 1.21 的物品堆叠上限是**数据组件 `MAX_STACK_SIZE`**（构造时 `Properties.component(...)` 或 `stacksTo(n)` 写入），不是覆写方法。**正确修复（勿改物品全局堆叠，精妙存储同款思路）**：①容器覆写 `getMaxStackSize(ItemStack)` 返回 slotStackLimit（绕过 min 物品）；②GUI `RestrictedSlot` 覆写 `getMaxStackSize(ItemStack)` 返回槽位容量（safeInsert 拖拽与 moveItemStackTo shift 合并都经 Slot 取上限，实测 GUI 两组可放满 128）；③`EnzymeFactoryItemHandler.insertItem` 的 limit 直接用 `getSlotLimit(slot)`，**不要抄 InvWrapper 的 `min(stack.getMaxStackSize(), slotLimit)`**（物品 64 会把槽位 128 钳回 64）；④`getSlotLimit` 返回 64×n。曾误改 `MoleculeItem` 全局 `MAX_STACK_SIZE=128`（已回退）——分子物品保持默认 64，玩家背包/箱子不放大。**已知边界**：原版漏斗 `isFullContainer`/`tryMoveInItem` 硬编码 `itemstack.getMaxStackSize()`，最多塞 64（与 RS/Mekanism 等所有 mod 一致，管道不受限）
27. **vanilla ItemStack 存档 count 上限 [1,99]（崩溃根因，已解）**：1.21 的 `ItemStack.CODEC` 对 count 字段硬编码 `ExtraCodecs.intRange(1, 99)`（ItemStack.java:107），NBT 存档（BE saveAdditional 的 `createTag`、ItemEntity 存档）时槽位/掉落堆 count >99 直接抛 `IllegalStateException: Value must be within range [1;99]`——实测"破坏正在工作的酶工厂崩溃"即此根因（槽位 128 个物品进出存档即崩）。网络同步（STREAM_CODEC，VAR_INT）无此限制。**解法（勿改 CODEC，勿改 vanilla）**：酶工厂覆写 `MachineBlockEntity` 新增的容器序列化钩子 `saveContainerData/loadContainerData`（基类默认 createTag/fromTag，DNA 编码器不受影响），自定义 NBT 把 `{slot, id, count}` 分开存（count 原生 int 绕过 CODEC）；方块破坏掉落同样按 64 拆堆（`MachineBlock.dropContainerContents`，不用 `Containers.dropContents` 直掉 128 堆）。新增超 64 堆叠的序列化改动必须同时考虑这两处（BE 存档 + 掉落）
28. **RK4 刚性失效坑（已踩坑 + 已解：引擎自适应细分 + 平衡区判据）**：RK4 在高刚性系统下数值失效——通量报告正常（甚至巨大）但浓度几乎不推进，症状是"v 显示很大但机器卡死"。根因：kcat 极大（TPI kcat=9000，Vmax_b = Vmax_f·∏KmP/(∏KmS·Keq) ≈ 74）时，RK4 中间采样点在巨大正逆向通量间震荡（G3P 中间值变负），四阶项大幅自抵消，终值增量 ≈ 0（实测 ΔG3P ≈ 1.6e-5），而 StepResult 用终值浓度算出的净通量仍显示 3168/tick——引擎"看似在跑实则卡死"。**引擎修复（EnzymeSimulator）四判据**：①自抵消判据（RK4 增量 < 0.25×欧拉预测 → 步长过大，覆盖振荡型刚性）；②单调大消耗判据（欧拉单步吃掉过半存量/余量 → 细分，覆盖"Vmax_b >> Vmax_f"型——ALDO 类 Keq 极小酶单步把产物吃光钳 0 的"产-吃光"极限环）；③平衡区驻留判据（高 Vmax 背景 >50/s 且净通量 < 背景×1e-4 → 直接取最大子步数 64——平衡点附近净速率≈0 时判据 1/2 都不触发，大步长 RK4 放大因子 |R(h·λ)| 在 h·λ≈1475 时远超 1，数值过冲表现为**两种形态：细分上限低（64）时每 1~4 tick 的快速振荡（128:0:0 起步"产物 0↔1、反应物 126↔127"来回跳），上限高（512+）时 4000+ tick 的缓慢漂移（Q 从 1.44e-4 漂到 6.4e-5 再回弹，旧 2000 tick 测试"恰好落在高位"是收敛假象）**——同根因两形态，判据 3 统一修复：64 子步 h=7.8e-4 → h·λ≈0.68 稳落稳定域）；④方向矛盾/过冲判据（RK4 与欧拉符号相反或 |rk4| > 2×|euler| → 细分——判据 1 只覆盖"RK4 大幅抵消"，漏检"采样点越界钳制后方向失真"：TPI [E]=3 满堆反应物 DHAP（浓度上限 2.015625）+ 产物 G3P=0 时，单步 RK4 的 k2~k4 采样把 G3P 抬到高位使逆向通量主导，加权平均预测"逆向净流 +1.30"与欧拉的正向 −0.94 完全相反，反应物恰在上限 → 边界缩放 scale=0 → 引擎永久冻结（v=0.00、产物恒 0.00，实测日志 DHAP=128 G3P=0 fluxX1000=0——用户"输入满堆 output 卡 0.00"根因）；细分后采样点不出界方向恢复正确（2 子步即解冻，逐子步 ΔDHAP≈0.106 → 单 tick 产出 G3P≈13 个物品，与用户"取下 64 input 恢复、output 变 13"实测一致）。判据 4 同时消除判据 3 盲区外的高活性近平衡极限环（[E]=3 真实数据修复前 G3P 0.199↔0.303 以 3 tick 周期振荡不收敛、Q 偏离 Keq 达 60%，修复后 2 tick 收敛且 Q 锁定）。非刚性系统子步数=1 与原逻辑完全一致（PGI 黄金值回归守护）。**测试守护**：engineTest 用例 23（TPI kcat=9000 推进收敛）+ 24（ALDO 64 活性收敛）+ 25（ALDO 长周期零漂移）+ 26（TPI [E]=3 满堆反应物冻结回归：产物产出 + 收敛 Keq + 近平衡 Q 锁定无极限环）。**数据侧约定**：TPI kcat 用大肠实测 9000 保持数据真实性，引擎已能承载；超扩散极限的极值数据仍需在数据准备阶段核对并标注。**调试陷阱**：PowerShell 5.1 的 `Set-Content -Encoding UTF8` 会写 BOM，直接改源码后用 javac 编译即报"非法字符 \ufeff"（build 崩溃根因）——改源码必须用 edit 工具或显式无 BOM 写回，改动后立即 build 验证
29. **1.21.1 槽位原版化（已解：Access Transformer 拆 final）**：`Slot.x/y` 在 1.21.1 是 `public final int`（构造后不可改）——"滚动时改坐标让 vanilla 命中"的方案需要先拆 final。**精妙存储（Sophisticated Core 1.21.x）已验证的最优路线（MachineScreen 已采用）**：mod 自带 AT（`src/main/resources/META-INF/accesstransformer.cfg`）：
    ```
    public-f net.minecraft.world.inventory.Slot x
    public-f net.minecraft.world.inventory.Slot y
    ```
    `public-f` = 公开 + 移除 final（NeoForge AT 语法，FML 加载时全局应用，dev 环境 ModDevGradle 自动应用——AT 不生效则 `slot.x =` 编译直接失败，天然验证）。随后滚动面板每 tick 把槽位坐标按滚动偏移写入 Slot.x/y（视口外槽位移 (-100,-100) 等效禁用），vanilla 的 findSlot/hoveredSlot/点击/双击收集/拖拽分裂/数字键/JEI-EMI U-R/tooltip 全部原生生效，零手动复刻。**注意组合**：①物品渲染仍自绘（renderSlot 对滚动槽空实现防双重渲染，CardScrollArea.draw 的 scissor 裁剪滚动视口）；②`Slot.isHighlightable()` 覆写返回 false 关闭 vanilla 高亮（renderSlotHighlight 是 static 且渲染循环内联、无法注入裁剪，滚动边缘高亮会溢出到图表区）——自绘高亮替代；③精妙存储还 AT 了 `AbstractContainerScreen` 的 draggingItem/isSplittingStack/quickCraftingRemainder/clickedSlot/findSlot 等私有字段以覆写 render() 复制循环——**本项目不需要**（保留自绘渲染路径，不复制循环）。**走过的弯路（勿回退）**：先做了"屏外坐标 + render 尾部覆写 hoveredSlot + 手动 mouseClicked"方案（可编译可运行但双击收集/触屏拖拽分裂有缺口且代码绕），AT 方案净删 ~70 行且全兼容
30. **BE 客户端数据同步 + 裸模型物品 display（已踩坑 + 已解）**：①客户端 BE 无引擎/无 tick 时，"只存在于服务端的状态"（如酶主题色）必须走 BE 数据同步通道，否则客户端渲染恒读构造默认值（实测"方块外观不随换酶变化"静默 bug）：覆写 `getUpdateTag`（区块加载时下发；注意 vanilla 默认返回**空 tag**）+ `handleUpdateTag`（读回；字段缺失保留构造默认值防误读 0）+ `getUpdatePacket` 返回 `ClientboundBlockEntityDataPacket.create(this)`，服务端状态变化时调 `level.sendBlockUpdated(pos, state, state, 3)`（SignBlockEntity 同款标准姿势，源码实证：ChunkHolder.broadcastChanges → broadcastBlockEntityIfNeeded 发 data packet）。**关键第二层坑**：随 BlockUpdatePacket 下发的 state 与当前相同 → 客户端 `Level.setBlock` 因状态不变直接 no-op、不重烘焙该方块（源码实证，这导致"读档才刷新、热插拔不变色"）——BE 数据到达客户端（handleUpdateTag）后必须**主动调 `level.sendBlockUpdated` 请求重渲染**（客户端调用只走 LevelRenderer.blockChanged 标记 dirty，无递归）；更优：客户端 `setChanged` 分支（GUI 操作经菜单槽位同步触发）直接解析 0 槽刷新颜色 + 重渲染，与服务端 data packet 双通道互备（GUI 即时、管道/漏斗走包）。②裸 elements 方块模型作为物品渲染时缺 display 段：vanilla 方块物品的显示变换（gui 0.625 缩放+旋转/ground 0.25/fixed 0.5/第三人称 0.375/第一人称 0.4）来自 block/block 父模型（vanilla 资源实证），裸模型无 parent 会以原始 16×16 尺寸渲染（物品栏异常形态）——自定义元素模型必须显式补 vanilla block/block 同款 display + `gui_light=side`（物品栏侧面光照）+ particle 纹理键（破坏粒子用）
31. **停摆信号 + 方块自发光（已踩坑 + 已解）**：①引擎"物理性停摆"信号要由引擎给出而非显示层推断：`EnzymeSimulator` 新增 `lastBoundaryScale`（step 内 minScale 落盘，含 hasSupply=false 提前返回分支置 0）+ `wasStalled()`（<1e-9 = 边界完全冻结：产物/逆向底物满堆、固定活性资源耗尽；部分截断"攒余量"、空闲、平衡不算）——方块实体每 tick 判定、状态翻转时把灯色缓存切红并复用主题色更新包通道（客户端零改动）；engineTest 27 守护信号契约（产物满堆逆向越界 → true，腾容量 → false）。②vanilla 方块模型无自发光属性，标准做法是**烘焙后包装 BakedModel**：`ModelEvent.ModifyBakingResult` 遍历 `Map<ModelResourceLocation, BakedModel>` 替换目标模型（注意 1.21.1 的 `ModelResourceLocation` 是 **record（id + variant）**，要用 `key.id().getNamespace()/getPath()`，没有 getNamespace 方法）；包装类 getQuads 对目标 tintindex 的 quad 应用 `QuadTransformers.settingMaxEmissivity()`（光照全亮；物品模型 key 路径为 item/ 前缀不命中，不影响物品渲染）——发光色仍由 BlockColor 染色决定，包装只改光照不改颜色

## 第三章 编码与开发规范

### 3.1 命名规范

- Java 惯例：类/接口 PascalCase、方法/字段 camelCase、常量 UPPER_SNAKE_CASE、包名全小写、`final` 修饰不可变字段
- 注册名（DeferredRegister 的 path）：小写蛇形 `lower_snake_case`，如 `enzyme_hexokinase`（酶蛋白物品带 enzyme_ 前缀，方块注册名直接为酶 id）
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
- **文档同步纪律**：涉及包结构/进度变更的提交（新增/删除/移动类、完成或调整里程碑）必须同步更新本文档 1.5 进度节与 2.4 包结构，并核对 README 的进度章节；**对玩家可见的 feat/fix 必须同时写 README 底部《更新日志》**（规则见 1.7）——文档滞后于代码即视为变更未完成
