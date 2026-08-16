package com.github.crafteve.biocraft.reaction;

/**
 * 酶促反应模拟器：每台机器一个实例，引擎的唯一对外入口
 * <p>
 * 职责划分（策划 1.1 契约）：
 * <ul>
 *   <li>构造期：由酶数据装配不可变反应网络档案（ReactionDefinition），
 *       执行全部断言，失败即抛异常快速失败</li>
 *   <li>运行期：step(dt) 每 tick 调用一次——温度缓存检查 →
 *       米氏速率（活性缩放）→ 化学计量 → RK4 积分 → 边界缩放与钳制 →
 *       通量报告；本机 ReactionState 原地更新</li>
 * </ul>
 * 引擎不碰槽位、不碰 NBT、不碰网络：浓度的来源与去向（物品桥接）
 * 全部是方块实体的事，本类只做纯数学
 * <p>
 * 积分方法说明：直接采用 RK4（四阶龙格-库塔）。策划 1.6 原方案为
 * 显式欧拉起步、振荡时切换 RK4——但数据驱动下必然振荡（TPI 缩放后
 * 速率常数与步长的乘积远超欧拉稳定界），接口同构的前提下一步到位
 * 避免返工。RK4 中间采样点允许临时越界（速率函数内部钳制），
 * 终值做边界缩放保证守恒与 [0,1] 钳制
 */
public final class EnzymeSimulator {
    /**
     * 刚性自适应最大子步数（dt 细分上限，防极端情况死循环）
     * <p>
     * 64 曾不够：64 个 ALDO（[E]=64）时 activity 放大特征速率，
     * Vmax_b ≈ Vmax_f·∏KmP/(∏KmS·Keq) ≈ 300 × 64 = 19200，RK4 稳定条件
     * h·λ < 2.8：平衡点附近 λ ≈ activity×Vmax_b×∂f(P)/∂x ≈ 29500，
     * 需要 h < 9.5e-5 → 子步 > 526——1024 留足裕量（实测 512 时 Q 在
     * 平衡点两侧极限环振荡，不收敛到 Keq；1024 后收敛正常）。
     * 正常酶（非刚性）子步数恒为 1，本上限只在极端数据下生效；
     * 每 tick 开销 = 子步×4 次速率计算（纯浮点，极端场景仍远小于
     * 1ms 预算），未来更高活性（一厂多酶）若再现振荡需再评估上限
     * 或调整 TIME_SCALE 节奏旋钮
     */
    private static final int MAX_SUBSTEPS = 1024;

    /**
     * 刚性自抵消判据阈值：RK4 增量小于欧拉预测的此比例即视为步长过大
     * <p>
     * 正常系统 RK4 增量 ≈ 欧拉预测（比值 0.4~1.0）；刚性系统四阶项
     * 剧烈震荡互相抵消，比值可小到 1e-4（TPI kcat=9000 实测）——
     * 比值低于 0.25 即触发步长细分
     */
    private static final double RIGID_SELF_CANCEL_RATIO = 0.25;

    /**
     * 单调大消耗判据阈值：欧拉单步吃掉物种过半存量/余量即视为步长过大
     * <p>
     * 覆盖"Vmax_b &gt;&gt; Vmax_f"型刚性（ALDO 等 Keq 极小酶 + 高活性），
     * 该场景不自抵消、原判据漏检导致"产-吃光"极限环（实测 Q 周期振荡）
     */
    private static final double RIGID_CONSUME_RATIO = 0.5;

    /** 不可变反应网络档案（构造期装配，全程只读） */
    private final ReactionDefinition definition;

    /** 本机反应状态（浓度/温度/活性，与方块实体共享） */
    private final ReactionState state;

    /** 温度缓存：上次重算逆向 Vmax 时的温度，NaN 表示尚未初始化 */
    private double cachedTemperature = Double.NaN;

    /** 温度缓存对应的逆向 Vmax（可逆反应有效值） */
    private double cachedVmaxB;

    /** 温度重算计数器：供测试与调试观测缓存行为（生产代码不使用） */
    private int temperatureRecalculations;

    /**
     * 由酶数据装配模拟器（注册期一次）
     *
     * @param data 酶数据档案（反应式 + 热力学 + 动力学，全部带出处）
     */
    public EnzymeSimulator(EnzymeFactoryData data) {
        this.definition = ReactionDefinition.build(data);
        this.state = new ReactionState(definition.getSpeciesCount());
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
     * 流水线：温度缓存检查（0.1K 阈值）→ 供料门 → 刚性自适应细分探测 →
     * 逐子步 RK4 积分（每子步边界缩放与钳制）→ 有效通量报告。浓度原地更新
     * <p>
     * 刚性自适应：RK4 是显式方法，稳定性受"步长 × 系统特征速率"限制。
     * 高 kcat（如 TPI 9000）使逆向 Vmax 巨大（Haldane：Vmax_b 可达 74），
     * 单步内通量在正逆向间剧烈震荡，四阶项互相抵消——通量报告正常但
     * 浓度几乎不推进（AGENTS.md 2.6 欠账 28 的"v 大但卡死"根因）。
     * 探测到自抵消（RK4 增量 << 欧拉预测）时步长二分重试，直到子步稳定
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
            return new StepResult(0.0, 0.0, 0.0);
        }

        // 刚性探测：单步是否出现高阶项自抵消（不修改浓度，只算采样）
        int substeps = 1;
        double h = dt;
        if (isRigid(x, vmaxB, activity, h)) {
            substeps = 2;
            for (; substeps <= MAX_SUBSTEPS; substeps *= 2) {
                h = dt / substeps;
                if (!isRigid(x, vmaxB, activity, h)) {
                    break;
                }
            }
            if (substeps > MAX_SUBSTEPS) {
                substeps = MAX_SUBSTEPS;
                h = dt / substeps;
            }
        }
        // 逐子步积分；追踪全 tick 最小边界缩放（满堆截断时通量报告归零）
        double minScale = 1.0;
        for (int s = 0; s < substeps; s++) {
            minScale = Math.min(minScale, rk4Step(x, h, vmaxB, activity));
        }

        double forward = KineticsCalculator.forwardFlux(definition, x, vmaxB) * activity * minScale;
        double reverse = KineticsCalculator.reverseFlux(definition, x, vmaxB) * activity * minScale;
        return new StepResult(forward, reverse, forward - reverse);
    }

    /**
     * 刚性探测：从当前浓度执行一次完整 RK4 采样（不修改浓度），
     * 检查两类步长过大信号：
     * <ol>
     *   <li>高阶项自抵消（振荡型刚性）：RK4 增量远小于欧拉预测——
     *       步长内通量剧烈往返（采样点震荡），显式积分不可信</li>
     *   <li>单调大消耗（Vmax_b &gt;&gt; Vmax_f 型刚性）：欧拉单步吃掉
     *       物种过半存量/余量——ALDO 类 Keq 极小酶的逆向 Vmax 巨大
     *       （Vmax_b ≈ 300，64 活性时 ≈ 19200），单步把产物直接
     *       吃光并钳 0，逆向项消失后下 tick 重新积累——形成
     *       "产-吃光"极限环（实测 Q 在平衡点两侧周期振荡不收敛）；
     *       此场景不自抵消，原判据漏检，必须按"单步变化量占比"细分</li>
     * </ol>
     *
     * @param x        当前浓度（只读）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @param h        待探测的步长
     * @return true 表示该步长过大，应细分
     */
    private boolean isRigid(double[] x, double vmaxB, double activity, double h) {
        double[] k1 = derivatives(x, vmaxB, activity, h);
        double[] k2 = derivatives(shift(x, k1, 0.5), vmaxB, activity, h);
        double[] k3 = derivatives(shift(x, k2, 0.5), vmaxB, activity, h);
        double[] k4 = derivatives(shift(x, k3, 1.0), vmaxB, activity, h);
        for (int i = 0; i < x.length; i++) {
            double euler = k1[i];
            double rk4 = (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]) / 6.0;
            // 判据 1：高阶项自抵消（振荡型刚性）
            if (Math.abs(euler) > 1e-12 && Math.abs(rk4) < RIGID_SELF_CANCEL_RATIO * Math.abs(euler)) {
                return true;
            }
            // 判据 2：单调大消耗——欧拉单步变化量过大
            // 无论方向：吃掉物种过半存量（步长内采样点严重失真，正常系统
            // 单步变化远小于存量不会误触发）；正方向同时查过半余量（撞上限）
            if (Math.abs(euler) > 1e-12) {
                if (x[i] > 1e-9 && Math.abs(euler) > x[i] * RIGID_CONSUME_RATIO) {
                    return true;
                }
                if (euler > 0 && euler > (KineticConstants.MAX_CONCENTRATION - x[i]) * RIGID_CONSUME_RATIO) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 单步 RK4 积分（子步内部）：四阶龙格-库塔 + 边界缩放 + 钳制，
     * 浓度原地更新；返回本子步的边界缩放因子（供全 tick 最小缩放追踪）
     *
     * @param x        浓度数组（原地更新）
     * @param h        本子步步长（秒）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return 本子步边界缩放因子（0~1，1 表示未截断）
     */
    private double rk4Step(double[] x, double h, double vmaxB, double activity) {
        double[] k1 = derivatives(x, vmaxB, activity, h);
        double[] k2 = derivatives(shift(x, k1, 0.5), vmaxB, activity, h);
        double[] k3 = derivatives(shift(x, k2, 0.5), vmaxB, activity, h);
        double[] k4 = derivatives(shift(x, k3, 1.0), vmaxB, activity, h);

        double[] next = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            next[i] = x[i] + (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]) / 6.0;
        }

        double scale = boundaryScale(x, next);
        for (int i = 0; i < x.length; i++) {
            x[i] = KineticsCalculator.clampConcentration(x[i] + (next[i] - x[i]) * scale);
        }
        return scale;
    }

    /**
     * RK4 采样导数：返回每种物种在一个步长 dt 内的浓度增量
     * <p>
     * 增量 = 化学计量系数 × 净速率 × 活性 × dt，全部物种共享同一
     * 净速率标量（单反应网络），比例严格守恒
     *
     * @param y        采样点浓度（允许越界，速率函数内部钳制）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @param dt       步长
     * @return 每物种的浓度增量数组
     */
    private double[] derivatives(double[] y, double vmaxB, double activity, double dt) {
        double[] delta = new double[y.length];
        double v = KineticsCalculator.netRate(definition, y, vmaxB) * activity * dt;
        for (int i = 0; i < y.length; i++) {
            delta[i] = definition.getStoich(i) * v;
        }
        return delta;
    }

    /**
     * RK4 中间采样点平移（不做钳制，保持积分线性）
     *
     * @param base   基准浓度
     * @param delta  增量
     * @param factor 平移比例
     * @return 中间采样点浓度
     */
    private static double[] shift(double[] base, double[] delta, double factor) {
        double[] result = new double[base.length];
        for (int i = 0; i < base.length; i++) {
            result[i] = base[i] + delta[i] * factor;
        }
        return result;
    }

    /**
     * 边界缩放因子：保证终值不越出 [0, MAX_CONCENTRATION] 且守恒不被钳制破坏
     * <p>
     * 若 RK4 终值使某物种越界（如产物满堆仍继续产出），本方法对全部
     * 物种的增量按同一比例缩减，使最紧迫的物种恰好停在边界——
     * 所有物种同步缩放，化学计量守恒精确保持。产物满堆（上限 = 槽位
     * 容量 n 组 + 余量）时反应自动减速至停，玩家取走产物即恢复，
     * 这就是"槽满停转"的动力学实现；上限放宽后满堆浓度可达 2.0
     * （128 个物品），"槽满仍攒余量"的中间状态不会被冻结
     *
     * @param oldX 更新前浓度
     * @param newX RK4 原始终值
     * @return 0~1 的全局缩放因子（1 表示无需缩放）
     */
    private static double boundaryScale(double[] oldX, double[] newX) {
        double scale = 1.0;
        for (int i = 0; i < oldX.length; i++) {
            double delta = newX[i] - oldX[i];
            if (delta > 1e-12) {
                scale = Math.min(scale, (KineticConstants.MAX_CONCENTRATION - oldX[i]) / delta);
            } else if (delta < -1e-12) {
                scale = Math.min(scale, oldX[i] / -delta);
            }
        }
        return Math.max(scale, 0.0);
    }
}
