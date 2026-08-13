package com.github.crafteve.biocraft.reaction;

/**
 * 热力学公式工具箱（全部静态纯函数，零依赖）
 * <p>
 * 承担三个职责：
 * <ul>
 *   <li>Keq 计算与温度修正（van't Hoff 精确式与 Q10 降级式）</li>
 *   <li>Arrhenius 温度活性因子</li>
 *   <li>数据准备期换算（M0 把表 B 的 ΔG°′ 换算为 Keq 填入 enzymes.json）</li>
 * </ul>
 * 温度对引擎的唯一影响通道是 Keq(T)：可逆反应的正向 Vmax 与 Km 不随温度变，
 * 只有逆向 Vmax_b 随 Keq(T) 重算（详见 ReactionDefinition.vmaxBForTemperature）
 */
public final class ThermoUtil {

    /**
     * 由生化标准生成吉布斯能变化计算平衡常数
     * <p>
     * Keq = exp(−ΔG°′/RT)，ΔG°′ 单位为 kJ/mol 故乘 1000 转 J/mol。
     * 该换算在数据准备阶段执行一次（enzymes.json 直接存 Keq），
     * 引擎运行期不再持有 ΔG，保证"Keq 绝不缩放"的红线只有一处数据源头
     *
     * @param deltaGKjPerMol 生化标准反应吉布斯能（kJ/mol，eQuilibrator I=0.25 值）
     * @param temperatureK   温度（K），参考温度取 KineticConstants.T0
     * @return 平衡常数（无量纲）
     */
    public static double keqFromDeltaG(double deltaGKjPerMol, double temperatureK) {
        return Math.exp(-deltaGKjPerMol * 1000.0 / (KineticConstants.R * temperatureK));
    }

    /**
     * 温度修正后的平衡常数 Keq(T)
     * <p>
     * 有反应焓数据时走 van't Hoff 精确式：
     * Keq(T) = Keq(T₀)·exp(−ΔH/R·(1/T − 1/T₀))
     * <p>
     * ΔH 缺失时（当前 10 步全部未测量，NIST SRD 74 在线不可达）降级为
     * Q10 经验因子：Keq(T) = Keq(T₀)·Q10^((T−T₀)/10)，标注 simplified，
     * 这是策划 4.9 风险清单既定的降级方案，数据补全后自动切换精确式无需改调用方
     *
     * @param keq0             参考温度 T₀ 下的平衡常数（enzymes.json 直接填写的值）
     * @param deltaHKjPerMol  反应焓（kJ/mol），null 表示未测量
     * @param temperatureK    当前温度（K）
     * @return 当前温度下的平衡常数
     */
    public static double keqAtTemperature(double keq0, Double deltaHKjPerMol, double temperatureK) {
        if (deltaHKjPerMol == null) {
            return keq0 * Math.pow(KineticConstants.Q10, (temperatureK - KineticConstants.T0) / 10.0);
        }
        double invDelta = 1.0 / temperatureK - 1.0 / KineticConstants.T0;
        return keq0 * Math.exp(-deltaHKjPerMol * 1000.0 / KineticConstants.R * invDelta);
    }

    /**
     * Arrhenius 温度活性因子
     * <p>
     * activity = exp(−Ea/R·(1/T − 1/T_opt))，以最适温度 T_opt 为参考点：
     * T = T_opt 时因子恒为 1；升温（如岩浆旁）因子大于 1 产速提升，
     * 降温（如雪地）因子小于 1 产速下降，符合策划 M5 验收预期
     * <p>
     * 超温失活（denatureTemp）不在此函数内：那是策略层/KineticBehavior 的
     * 阶跃判定（超温 activity 直接归零并报警），引擎只接收最终 activity 值
     *
     * @param eaKjPerMol    活化能（kJ/mol，缺省用 KineticConstants 类别默认值）
     * @param temperatureK  当前温度（K）
     * @param tempOptimumK  最适温度（K，酶数据表逐酶填写，人源 25°C/37°C 测定值）
     * @return 活性因子（大于 0，T=T_opt 时为 1）
     */
    public static double arrheniusFactor(double eaKjPerMol, double temperatureK, double tempOptimumK) {
        double invDelta = 1.0 / temperatureK - 1.0 / tempOptimumK;
        return Math.exp(-eaKjPerMol * 1000.0 / KineticConstants.R * invDelta);
    }

    private ThermoUtil() {
    }
}
