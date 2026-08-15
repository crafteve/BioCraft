package com.github.crafteve.biocraft.reaction;

import java.util.Set;

/**
 * 化学引擎的全部代码侧常量
 * <p>
 * 修改游戏节奏只允许动本类：数值缩放遵循"保相对、变绝对"原则，
 * 酶与酶之间的速率比值在任何缩放下保持不变（统一乘除同一因子）
 * <p>
 * 本类与 ThermoUtil 是引擎的纯数学地基，无任何 Minecraft 依赖，
 * 可在脱离游戏的环境下独立单测
 */
public final class KineticConstants {
    /** 摩尔气体常数，单位 J/(mol·K)，物理常数永不修改 */
    public static final double R = 8.314;

    /** 热力学参考温度 298.15K（25°C），Keq 的注册基准温度，永不修改 */
    public static final double T0 = 298.15;

    /**
     * 浓度尺度：满堆叠（64 个物品 = 浓度 1.0）对应的真实毫摩尔浓度
     * <p>
     * 决定 Km 在堆叠分数尺度上的位置，即"饱和行为是否可见"：
     * 值越小 Km 相对越大、反应越难饱和。初值 1.0，待 M6 端到端调参
     */
    public static final double CONCENTRATION_SCALE = 1.0;

    /**
     * 槽位组数（n）：每槽可容纳 n 组物品（1 组 = 64 个 = 浓度 1.0）
     * <p>
     * 容量参数化的核心旋钮：n=2 时槽位可放 128 个物品、浓度上限 2.0，
     * 让 ALDO 类强偏向反应物（Keq 极小）的酶在满堆下平衡产物突破
     * 1 个物品粒度，玩家能抽出产物推动反应（否则平衡产物 <1 个
     * 永远抽不出，反应卡死——用户实测的 ALDO 卡死问题根因）
     */
    public static final int SLOT_GROUPS = 2;

    /**
     * 浓度上限：槽位满 n 组 + 进度条余量 <1 个物品
     * <p>
     * 总量 = count + 余量，最大 = 64n + 0.99 个物品 → 浓度上限 = n + 1/64。
     * 此前钳制在 1.0（64 个）导致余量 + 投入物品被吞（用户实测
     * "63.23 投入后变 64 被钳回 63"的吞物品 bug）；钳制必须允许
     * "槽位已满 n 组但余量仍在积累"的状态存在
     */
    public static final double MAX_CONCENTRATION = SLOT_GROUPS + 1.0 / 64.0;

    /**
     * 时间尺度：游戏秒 / 真实秒，即真实 kcat 除以本值进入引擎
     * <p>
     * 唯一可随意调节的节奏旋钮：值越大反应越慢。
     * 初值 1000（PGI kcat 79 → 引擎内 0.079 s⁻¹，一满堆 G6P 半程约 15 秒），
     * 待 M6 进游戏实测后调参
     */
    public static final double TIME_SCALE = 1000.0;

    /** 温度对 Keq 的 Q10 经验因子：ΔH 数据缺失时的降级方案（标注 simplified） */
    public static final double Q10 = 2.0;

    /**
     * 温度缓存重算阈值（K）
     * <p>
     * 温度变化超过本值才重算温度相关速率常数：Keq 对温度的对数导数约 0.4%/K，
     * 0.1K 引起的速率变化小于 0.05% 不可感知，同时温度源是离散事件（方块变化触发），
     * 缓存命中率极高，几乎零重算开销
     */
    public static final double TEMP_RECOMPUTE_EPS = 0.1;

    /** 单个游戏 tick 的模拟时长（秒），策划 1.5 单位体系定死 */
    public static final double TICK_SECONDS = 0.05;

    /**
     * 固定活性物种名单
     * <p>
     * 依据策划 2.5 热力学约定：eQuilibrator 变换值已隐含"H₂O 活度 1、pH 7"，
     * 这些物种不乘进速率方程（活性恒 1），但照常参与化学计量结算（如 ENO 产出水物品），
     * 若出现在反应物侧则浓度耗尽时反应停供（未来水解类反应需要玩家供水）
     */
    public static final Set<String> FIXED_ACTIVITY_SPECIES = Set.of("water", "hydrogen_ion");

    /**
     * 各类别酶的默认活化能（kJ/mol），用于温度活性因子（Arrhenius）
     * <p>
     * 实际酶 Ea 常见 30~60 kJ/mol；限速酶（磷酸化类）取 50、
     * 异构酶（分子重排）取 40、氧化裂解（脱氢）取 45，
     * 酶数据表（enzymes.json）可逐酶覆盖，M5 温度机制接入时使用
     */
    public static final double DEFAULT_EA_LIMITING = 50.0;
    public static final double DEFAULT_EA_ISOMERASE = 40.0;
    public static final double DEFAULT_EA_OXIDO_LYASE = 45.0;

    private KineticConstants() {
    }
}
