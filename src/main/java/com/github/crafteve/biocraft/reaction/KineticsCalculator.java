package com.github.crafteve.biocraft.reaction;

import java.util.List;

/**
 * 动力学计算器：数值换算与速率方程（引擎最核心的数学部分）
 * <p>
 * 换算职责（注册期一次）：
 * <ul>
 *   <li>Vmax_f = kcat / TIME_SCALE（时间尺度缩放，酶间相对快慢保持）</li>
 *   <li>Km 分数 = Km_mM / CONCENTRATION_SCALE（浓度尺度换算，决定饱和行为）</li>
 *   <li>Vmax_b = Vmax_f·∏KmP/(∏KmS·Keq)（Haldane 关系，平衡位置 = 热力学判决点）</li>
 * </ul>
 * 速率职责（每 tick 多次，RK4 四采样）：
 * <ul>
 *   <li>可逆多底物共享分母乘积形式（本征动力学的米氏推广）：
 *       v = (Vmax_f·∏(Sᵢ/KmSᵢ) − Vmax_b·∏(Pⱼ/KmPⱼ)) / (1 + ∏(Sᵢ/KmSᵢ) + ∏(Pⱼ/KmPⱼ))</li>
 *   <li>不可逆多底物乘积饱和形式：v = Vmax_f·∏(Sᵢ/(KmSᵢ+Sᵢ))</li>
 * </ul>
 * 数学性质（设计依据，均有测试守护）：
 * <ul>
 *   <li>平衡精确：v=0 时 ∏(Pⱼ)/∏(Sᵢ) = Keq（Haldane 保证，Keq 绝不缩放红线）</li>
 *   <li>饱和有界：底物趋无穷时 v 趋近 Vmax_f，永不爆表（高浓度回归测试）</li>
 *   <li>产物回压：产物堆积增大逆向项，副产物不回收则产线自然堵塞</li>
 *   <li>低浓度线性：浓度趋零时退化为质量作用定律（v ∝ ∏浓度）</li>
 * </ul>
 */
public final class KineticsCalculator {

    /**
     * 换算正向最大速率：真实 kcat（s⁻¹）经时间尺度缩放进入引擎
     * <p>
     * 所有酶统一除以同一 TIME_SCALE，酶间相对快慢比值保持不变
     * （如 TPI 比 PGI 快 37 倍，缩放后仍快 37 倍），这是"保相对、变绝对"
     * 缩放原则的实现点
     *
     * @param kcatPerSecond 真实周转数（s⁻¹，BRENDA 几何中位数）
     * @return 引擎内正向最大速率（堆叠分数/s）
     */
    public static double toVmaxF(double kcatPerSecond) {
        return kcatPerSecond / KineticConstants.TIME_SCALE;
    }

    /**
     * 换算 Km 到堆叠分数尺度
     * <p>
     * 浓度 1.0 = 满堆叠（64 个物品）= CONCENTRATION_SCALE 毫摩尔，
     * Km 分数决定"多少个物品堆出半饱和"，是饱和行为可见性的调节点
     *
     * @param kmMillimolar 米氏常数（mM，BRENDA 人源几何中位数）
     * @return 堆叠分数尺度的 Km
     */
    public static double toKmFraction(double kmMillimolar) {
        return kmMillimolar / KineticConstants.CONCENTRATION_SCALE;
    }

    /**
     * 计算 ∏KmP/∏KmS（逆向 Vmax 的 Km 常数因子）
     * <p>
     * 仅统计参与速率方程的物种（固定活性物种不参与），
     * 与平衡式 ∏(P)/∏(S) = Keq 的物种集合一致
     *
     * @param rateReactants 速率项反应物条目
     * @param rateProducts  速率项产物条目
     * @return 产物 Km 乘积除以底物 Km 乘积
     */
    public static double kmRatio(List<ReactionDefinition.SpeciesEntry> rateReactants,
                                 List<ReactionDefinition.SpeciesEntry> rateProducts) {
        double product = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : rateProducts) {
            product *= Math.pow(entry.kmFraction(), entry.coeff());
        }
        for (ReactionDefinition.SpeciesEntry entry : rateReactants) {
            product /= Math.pow(entry.kmFraction(), entry.coeff());
        }
        return product;
    }

    /**
     * 逆向最大速率（Haldane 关系）
     * <p>
     * Vmax_b = Vmax_f·∏KmP/(∏KmS·Keq)。Keq 由热力学数据决定且绝不缩放，
     * 逆向数据（BRENDA 逆向 Km/kcat）仅作数据校验参考不参与计算，
     * 保证模拟器平衡位置精确等于热力学判决点
     *
     * @param vmaxF   正向最大速率（缩放后）
     * @param kmRatio ∏KmP/∏KmS
     * @param keqT    当前温度下的平衡常数
     * @return 逆向最大速率（堆叠分数/s）
     */
    public static double toVmaxB(double vmaxF, double kmRatio, double keqT) {
        return vmaxF * kmRatio / keqT;
    }

    /**
     * 可逆多底物共享分母乘积速率（正向分量）
     * <p>
     * fwd = Vmax_f·∏(Sᵢ/KmSᵢ)^cᵢ / (1 + ∏(Sᵢ/KmSᵢ)^cᵢ + ∏(Pⱼ/KmPⱼ)^cⱼ)，
     * 分母共享保证了整体饱和与产物对正向的竞争回压
     *
     * @param definition    反应网络档案（提供速率项条目与可逆标志）
     * @param concentrations 全物种浓度数组（可能为 RK4 中间负值，内部钳制）
     * @param vmaxB         当前温度下的逆向最大速率
     * @return 正向通量（堆叠分数/s，不含活性缩放）
     */
    public static double forwardFlux(ReactionDefinition definition, double[] concentrations, double vmaxB) {
        if (!definition.isReversible()) {
            return definition.getVmaxF()
                    * productTerm(definition.getRateReactants(), concentrations, true);
        }
        double f = productTerm(definition.getRateReactants(), concentrations, false);
        double r = productTerm(definition.getRateProducts(), concentrations, false);
        return definition.getVmaxF() * f / (1.0 + f + r);
    }

    /**
     * 可逆多底物共享分母乘积速率（逆向分量）
     * <p>
     * rev = Vmax_b·∏(Pⱼ/KmPⱼ)^cⱼ / (1 + ∏(Sᵢ/KmSᵢ)^cᵢ + ∏(Pⱼ/KmPⱼ)^cⱼ)，
     * 产物堆积时该项增大，将反应推回平衡——"副产物必须回收否则产线
     * 堵塞"（AGENTS.md 1.2）的动力学实现
     *
     * @param definition    反应网络档案
     * @param concentrations 全物种浓度数组
     * @param vmaxB         当前温度下的逆向最大速率
     * @return 逆向通量（堆叠分数/s），不可逆反应恒为 0
     */
    public static double reverseFlux(ReactionDefinition definition, double[] concentrations, double vmaxB) {
        if (!definition.isReversible()) {
            return 0.0;
        }
        double f = productTerm(definition.getRateReactants(), concentrations, false);
        double r = productTerm(definition.getRateProducts(), concentrations, false);
        return vmaxB * r / (1.0 + f + r);
    }

    /**
     * 净速率 = 正向通量 − 逆向通量（引擎积分用的标量）
     * <p>
     * 附带供料门检查：固定活性反应物耗尽（浓度钳制后为 0）时返回 0，
     * 实现"水解必须供水"的资源约束；糖酵解首版 H₂O/H⁺ 均在产物侧，
     * 此门不触发但为未来网络预置
     *
     * @param definition    反应网络档案
     * @param concentrations 全物种浓度数组
     * @param vmaxB         当前温度下的逆向最大速率
     * @return 净速率（堆叠分数/s，可为负表示逆向净流）
     */
    public static double netRate(ReactionDefinition definition, double[] concentrations, double vmaxB) {
        if (!definition.hasSupply(concentrations)) {
            return 0.0;
        }
        return forwardFlux(definition, concentrations, vmaxB)
                - reverseFlux(definition, concentrations, vmaxB);
    }

    /**
     * 计算条目乘积项 ∏(xᵢ/Kmᵢ)^cᵢ 或 ∏(xᵢ/(Kmᵢ+xᵢ))^cᵢ
     * <p>
     * 可逆形式（线性项）：低浓度时与质量作用定律一致（v ∝ ∏浓度），
     * 分母共享项提供整体饱和；不可逆形式（饱和项）：各底物独立饱和
     * <p>
     * 浓度先钳制到 [0, MAX_CONCENTRATION]：RK4 中间采样点允许出现临时负值，
     * 负浓度的整数次幂虽然数学可算但会造成符号混乱，钳制后速率函数
     * 对任意中间值都稳定有定义；上限放宽到槽位容量（n 组 + 余量），
     * 满堆（浓度 2.0）时饱和行为仍然正确
     *
     * @param entries       速率项物种条目
     * @param concentrations 浓度数组
     * @param saturating    true 用饱和项 x/(Km+x)，false 用线性项 x/Km
     * @return 乘积项（非负）
     */
    private static double productTerm(List<ReactionDefinition.SpeciesEntry> entries,
                                      double[] concentrations, boolean saturating) {
        double product = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : entries) {
            double c = clampConcentration(concentrations[entry.index()]);
            if (saturating) {
                product *= Math.pow(c / (entry.kmFraction() + c), entry.coeff());
            } else {
                product *= Math.pow(c / entry.kmFraction(), entry.coeff());
            }
        }
        return product;
    }

    /**
     * 浓度钳制到 [0, MAX_CONCENTRATION]，NaN 归零（NaN 防护统一出口）
     * <p>
     * 引擎内所有进入速率方程的浓度都必须经过本函数，
     * 防止任何数值异常（RK4 中间值、存档脏数据）把 NaN 传播进模拟；
     * 上限为槽位容量（n 组 + 余量 <1 个物品），允许"槽满仍攒余量"
     * 的物理状态存在（此前钳制 1.0 会吞掉投入物品）
     *
     * @param value 原始浓度值
     * @return 钳制后的浓度（恒在 [0, MAX_CONCENTRATION]）
     */
    public static double clampConcentration(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(value, KineticConstants.MAX_CONCENTRATION);
    }

    /**
     * 边界缩放因子：保证增量后的终值不越出 [0, MAX_CONCENTRATION] 且守恒不被钳制破坏
     * <p>
     * 若积分终值使某物种越界（如产物满堆仍继续产出），对全部物种的增量
     * 按同一比例缩减，使最紧迫的物种恰好停在边界——所有物种同步缩放，
     * 化学计量守恒精确保持。产物满堆（上限 = 槽位容量 n 组 + 余量）时
     * 反应自动减速至停，玩家取走产物即恢复，这就是"槽满停转"的动力学
     * 实现；上限放宽后满堆浓度可达 2.0（128 个物品），"槽满仍攒余量"
     * 的中间状态不会被冻结。本方法是积分器（RK4/Rosenbrock 共用）与
     * 物理语义（物流回压）的桥接点，任何积分器都必须套用它
     *
     * @param oldX 更新前浓度
     * @param newX 积分原始终值（未钳制）
     * @return 0~1 的全局缩放因子（1 表示无需缩放）
     */
    public static double boundaryScale(double[] oldX, double[] newX) {
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

    private KineticsCalculator() {
    }
}
