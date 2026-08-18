package com.github.crafteve.biocraft.reaction;

/**
 * 酶促反应模拟器：每台机器一个实例，引擎的唯一对外入口
 * <p>
 * 职责划分（策划 1.1 契约）：
 * <ul>
 *   <li>构造期：由酶数据装配不可变反应网络档案（ReactionDefinition），
 *       执行全部断言，失败即抛异常快速失败</li>
 *   <li>运行期：step(dt) 每 tick 调用一次——温度缓存检查 →
 *       Rosenbrock 积分（半隐式 L-stable，大步长直解刚性）→
 *       边界缩放与钳制 → 通量报告；本机 ReactionState 原地更新</li>
 * </ul>
 * 引擎不碰槽位、不碰 NBT、不碰网络：浓度的来源与去向（物品桥接）
 * 全部是方块实体的事，本类只做纯数学
 * <p>
 * 积分方法说明（2026-08-16 由显式 RK4 更换为 Rosenbrock）：旧引擎
 * 用显式 RK4 + 四判据自适应细分（自抵消/单调大消耗/平衡区驻留/方向
 * 矛盾），高 kcat 刚性系统（TPI 9000、ALDO [E]=64 的 Vmax_b≈19200）
 * 每 tick 最坏细分 64 子步 × 4 次求值 = 256 次速率计算，且平衡区
 * 长周期漂移/满堆方向失真等数值陷阱需要专门判据逐一修补。Rosenbrock
 * 是半隐式 L-stable 方法：稳定域覆盖整个左半平面，单 tick 大步长
 * 直接稳定积分，四判据体系整体删除（净删约 200 行），收敛精度与
 * RK4 同阶（两者均 4 阶），刚性行为由方法本身的稳定性保证而非
 * 启发式细分——详见 {@link RosenbrockIntegrator} 的实现说明
 */
public final class EnzymeSimulator {
    /** 不可变反应网络档案（构造期装配，全程只读） */
    private final ReactionDefinition definition;

    /** 本机反应状态（浓度/温度/活性，与方块实体共享） */
    private final ReactionState state;

    /**
     * Rosenbrock 积分器（半隐式 L-stable 4 阶，每机一个实例）
     * <p>
     * 积分器只持不可变网络档案与系数表，无每 tick 可变状态，
     * step 调用是纯函数式更新（浓度数组原地修改）
     */
    private final RosenbrockIntegrator integrator;

    /** 温度缓存：上次重算逆向 Vmax 时的温度，NaN 表示尚未初始化 */
    private double cachedTemperature = Double.NaN;

    /** 温度缓存对应的逆向 Vmax（可逆反应有效值） */
    private double cachedVmaxB;

    /** 温度重算计数器：供测试与调试观测缓存行为（生产代码不使用） */
    private int temperatureRecalculations;

    /**
     * 积分结果与显式欧拉预测的幅度比上限（超过即视为单步过冲）
     * <p>
     * Rosenbrock 是 L-stable 线性方法，但"单步绝对值过大"在强非线性
     * 区（远离平衡 + 高倍率）仍会过冲——步内穿越平衡点造成下一 tick
     * 反向振荡（实测 GAPDH NADH 少时 2 tick 周期来回跳）。与旧
     * RK4 判据 4（方向矛盾/过冲）同一数学动机，但现在是"Rosenbrock
     * 对显式欧拉"的单判据：方向相反（修正方向失真）或幅度超过
     * 欧拉预测 2 倍（过冲越界）即细分步长
     */
    private static final double GUARD_OVERSHOOT_RATIO = 2.0;

    /**
     * 细分守卫最大子步数（与旧 MAX_SUBSTEPS 同上限，防极端情况死循环）
     */
    private static final int MAX_GUARD_SUBSTEPS = 64;

    /**
     * 上次 step 的最小边界缩放（0..1）：满堆截断（产物/逆向底物满堆）或
     * 固定活性资源耗尽时 < 1，接近 0 即"物理性停摆冻结"——方块实体的
     * 停摆红灯状态判定数据源（AGENTS.md 2.6 欠账 19：边界截断是正确
     * 物流行为，停摆灯只是向玩家提示"机器在等什么"）
     */
    private double lastBoundaryScale = 1.0;

    /**
     * 由酶数据装配模拟器（注册期一次）
     *
     * @param data 酶数据档案（反应式 + 热力学 + 动力学，全部带出处）
     */
    public EnzymeSimulator(EnzymeFactoryData data) {
        this.definition = ReactionDefinition.build(data);
        this.state = new ReactionState(definition.getSpeciesCount());
        this.integrator = new RosenbrockIntegrator(this.definition);
    }

    /**
     * 获取反应网络档案（方块实体桥接物种下标、GUI 读取常数用）
     *
     * @return 不可变网络档案
     */
    public ReactionDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取本机反应状态（方块实体桥接槽位、策略层写活性用）
     *
     * @return 反应状态容器
     */
    public ReactionState getState() {
        return state;
    }

    /**
     * 温度重算计数（测试观测缓存行为，生产代码不用）
     *
     * @return 自构造以来重算逆向 Vmax 的次数
     */
    public int getTemperatureRecalculations() {
        return temperatureRecalculations;
    }

    /**
     * 执行一次引擎步进（每游戏 tick 调用一次）
     * <p>
     * 流水线：温度缓存检查（0.1K 阈值）→ 供料门 → Rosenbrock 单步
     * 积分（内部含数值梯度 + 4 阶段 + Sherman-Morrison 线性求解）→
     * 边界缩放与钳制 → 有效通量报告。浓度原地更新
     * <p>
     * 刚性说明：Rosenbrock 是半隐式 L-stable 方法，无需旧 RK4 的
     * 子步细分体系——一个 tick 就是一步，步长恒定 TICK_SECONDS，
     * 刚性系统的稳定性由方法本身保证（AGENTS.md 2.6 欠账 28 的
     * "v 大但卡死"与平衡区漂移等数值陷阱不再需要启发式修补）
     *
     * @param dt 步长（秒），游戏 tick 为 KineticConstants.TICK_SECONDS
     * @return 有效通量报告（已含活性与边界缩放）
     */
    public StepResult step(double dt) {
        double[] x = state.getConcentrations();
        double temperature = state.getTemperature();

        if (Double.isNaN(cachedTemperature)
                || Math.abs(cachedTemperature - temperature) > KineticConstants.TEMP_RECOMPUTE_EPS) {
            cachedVmaxB = definition.vmaxBForTemperature(temperature);
            cachedTemperature = temperature;
            temperatureRecalculations++;
        }
        double vmaxB = definition.isReversible() ? cachedVmaxB : 0.0;
        double activity = state.getActivity();

        if (!definition.hasSupply(x)) {
            // 固定活性资源耗尽（缺水/H⁺/fe 停供）：反应无法进行 = 停摆冻结
            this.lastBoundaryScale = 0.0;
            return new StepResult(0.0, 0.0, 0.0);
        }

        // Rosenbrock 积分 + 单一矛盾守卫（方向/幅度，必要时二分细分）
        double minScale = rosenbrockStepWithGuard(x, dt, vmaxB, activity);

        double forward = KineticsCalculator.forwardFlux(definition, x, vmaxB) * activity * minScale;
        double reverse = KineticsCalculator.reverseFlux(definition, x, vmaxB) * activity * minScale;
        this.lastBoundaryScale = minScale;
        return new StepResult(forward, reverse, forward - reverse);
    }

    /**
     * Rosenbrock 单步 + 显式欧拉矛盾守卫（细分保护）
     * <p>
     * 流程：用步起点算显式欧拉预测向量（1 次速率求值）→
     * integrator.step 走一步 → 与实际推进对比：任一物种"方向相反"
     * 或"幅度超过欧拉 2 倍"即视为单步失真 → 子步 2/4/8…/64 二分重跑
     * （每子步重新计算梯度），子步粒度足够小时非线性失真消失
     * <p>
     * 与旧 RK4 四判据的职责对比：Rosenbrock 的 L-stable 特性已消灭
     * 振荡型/平衡区驻留型刚性（TPI 9000 直解、ALDO 平衡区零漂移），
     * 本守卫只保留对"单步绝对值过大"（过冲/方向失真）的兜底——
     * 一个判据，正常工况子步恒为 1（零开销），刚性极端工况最多
     * 二分到 64（与旧体系上限一致但触发面窄得多）
     *
     * @param x        浓度数组（原地更新）
     * @param dt       步长（秒）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return 全 tick 最小边界缩放（子步内 scale 的最小值）
     */
    private double rosenbrockStepWithGuard(double[] x, double dt, double vmaxB, double activity) {
        // 显式欧拉预测向量（步起点一阶预测，方向/幅度校验的参考系）
        double v0 = KineticsCalculator.netRate(definition, x, vmaxB) * activity;
        int n = definition.getSpeciesCount();
        double[] euler = new double[n];
        for (int i = 0; i < n; i++) {
            euler[i] = definition.getStoich(i) * v0 * dt;
        }
        double[] xBackup = x.clone();
        int substeps = 1;
        while (true) {
            System.arraycopy(xBackup, 0, x, 0, n);
            double h = dt / substeps;
            double subScale = 1.0;
            for (int s = 0; s < substeps; s++) {
                subScale = Math.min(subScale, integrator.step(x, h, vmaxB, activity));
            }
            if (!guardViolated(x, xBackup, euler) || substeps >= MAX_GUARD_SUBSTEPS) {
                return subScale;
            }
            substeps *= 2;
        }
    }

    /**
     * 矛盾校验：Rosenbrock 实际推进与显式欧拉预测对比
     * <p>
     * 触发细分的两类失真：
     * <ol>
     *   <li>方向矛盾：预测正向实际负向（或相反）——修正项方向失真
     *       （旧 RK4 判据 4 的满堆方向失真同族，Rosenbrock 在非死区
     *       通常不发生，仅在极端非线性组合下出现）</li>
     *   <li>幅度过冲：实际推进超过欧拉预测 2 倍——单步穿越平衡点，
     *       下一 tick 的反向振荡根源（实测 GAPDH 数据）</li>
     * </ol>
     * 变化量 < 1e-9（边界冻结/平衡驻留）不算矛盾；死区起步由积分器
     * 内部的显式欧拉分支处理，不经过本判断
     *
     * @param x     积分后的浓度
     * @param x0    步起点浓度
     * @param euler 显式欧拉预测向量（全步长）
     * @return true = 存在方向矛盾或幅度过冲，应细分
     */
    private boolean guardViolated(double[] x, double[] x0, double[] euler) {
        for (int i = 0; i < x.length; i++) {
            double e = euler[i];
            if (Math.abs(e) < 1e-12) {
                continue;
            }
            double delta = x[i] - x0[i];
            if (Math.abs(delta) < 1e-9) {
                continue;
            }
            if (delta * e < 0.0) {
                return true;
            }
            if (Math.abs(delta) > GUARD_OVERSHOOT_RATIO * Math.abs(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 上次 step 是否物理性停摆（边界冻结）
     * <p>
     * 判定口径：全 tick 最小边界缩放 ≈ 0——产物满堆、逆向底物满堆、
     * 固定活性资源耗尽（缺水/H⁺/fe 停供）都会触发；部分截断
     * （"槽满仍攒余量"scale 介于 0~1）不算停摆；空闲（浓度全 0）与
     * 平衡态不算（引擎未被卡住，只是没事干/自然终态）
     *
     * @return true = 上次 step 被边界完全冻结
     */
    public boolean wasStalled() {
        return this.lastBoundaryScale < 1e-9;
    }
}