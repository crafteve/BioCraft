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
     * 流水线：温度缓存检查（0.1K 阈值）→ 供料门 → RK4 积分 →
     * 边界缩放与钳制 → 有效通量报告。浓度原地更新
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

        double[] k1 = derivatives(x, vmaxB, activity, dt);
        double[] k2 = derivatives(shift(x, k1, 0.5), vmaxB, activity, dt);
        double[] k3 = derivatives(shift(x, k2, 0.5), vmaxB, activity, dt);
        double[] k4 = derivatives(shift(x, k3, 1.0), vmaxB, activity, dt);

        double[] next = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            next[i] = x[i] + (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]) / 6.0;
        }

        double scale = boundaryScale(x, next);
        for (int i = 0; i < x.length; i++) {
            x[i] = KineticsCalculator.clampConcentration(x[i] + (next[i] - x[i]) * scale);
        }

        double forward = KineticsCalculator.forwardFlux(definition, x, vmaxB) * activity * scale;
        double reverse = KineticsCalculator.reverseFlux(definition, x, vmaxB) * activity * scale;
        return new StepResult(forward, reverse, forward - reverse);
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
