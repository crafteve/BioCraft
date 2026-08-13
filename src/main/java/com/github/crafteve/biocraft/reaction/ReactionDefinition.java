package com.github.crafteve.biocraft.reaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 不可变的反应网络档案
 * <p>
 * 一个实例描述一台机器完整的化学反应网络，由 EnzymeFactoryData 经
 * {@link #build(EnzymeFactoryData)} 在注册期构建一次，此后全程只读，
 * 运行期所有 tick 复用同一实例
 * <p>
 * 档案内容：
 * <ul>
 *   <li>物种索引表：物品注册名 → 浓度数组下标（含固定活性物种）</li>
 *   <li>速率项物种：参与速率方程的底物/产物条目（含系数与堆叠分数尺度 Km）</li>
 *   <li>固定活性物种：H₂O/H⁺ 等，不进速率方程但保留化学计量结算</li>
 *   <li>化学计量向量：每物种的净产生系数（产物正、反应物负）</li>
 *   <li>热力学：Keq(T₀)（绝不缩放的硬红线）与 ΔH（可 null）</li>
 * </ul>
 * 温度对网络的唯一影响是逆向 Vmax_b 随 Keq(T) 重算：
 * Vmax_b(T) = Vmax_f·∏KmP/(∏KmS·Keq(T))，即 Haldane 关系在任何温度下成立，
 * 保证模拟器平衡位置永远精确落在热力学判决点（Keq 硬规则的运行时保障）
 */
public final class ReactionDefinition {

    /**
     * 速率项物种条目：物种下标 + 化学计量系数 + 堆叠分数尺度 Km
     * <p>
     * 系数作为乘积项的指数（与质量作用定律一致，糖酵解十步系数全为 1，
     * 通用网络可支持任意正整数系数）
     */
    public record SpeciesEntry(int index, int coeff, double kmFraction) {
    }

    /** 物种注册名数组（下标即浓度数组下标） */
    private final String[] speciesIds;

    /** 固定活性掩码：true 表示该物种不进速率方程（H₂O/H⁺ 热力学约定） */
    private final boolean[] fixedActivity;

    /** 净化学计量向量：产物系数减反应物系数，固定活性物种同样计入（结算用） */
    private final double[] stoich;

    /** 参与速率方程的反应物条目（正向项） */
    private final List<SpeciesEntry> rateReactants;

    /** 参与速率方程的产物条目（逆向项，不可逆反应为空） */
    private final List<SpeciesEntry> rateProducts;

    /** 固定活性反应物下标：浓度耗尽时反应停供（供料门，未来水解反应用） */
    private final int[] supplyReactants;

    /** 反应是否可逆（可逆才有逆向 Vmax 与逆向项） */
    private final boolean reversible;

    /** 正向最大速率（kcat 经时间尺度缩放后的引擎值，单位 堆叠分数/s） */
    private final double vmaxF;

    /** ∏KmP/∏KmS（Km 均为堆叠分数尺度），逆向 Vmax 计算的常数因子 */
    private final double kmRatio;

    /** 平衡常数 Keq(T₀)，由 ΔG°′ 换算后直接写入数据表，引擎绝不修改 */
    private final double keq;

    /** 反应焓（kJ/mol），null 表示未测量（温度修正走 Q10 降级） */
    private final Double deltaHKjPerMol;

    /**
     * 私有构造：只允许经 build 工厂装配，保证断言校验不可能被绕过
     */
    private ReactionDefinition(String[] speciesIds, boolean[] fixedActivity, double[] stoich,
                               List<SpeciesEntry> rateReactants, List<SpeciesEntry> rateProducts,
                               int[] supplyReactants, boolean reversible,
                               double vmaxF, double kmRatio, double keq, Double deltaHKjPerMol) {
        this.speciesIds = speciesIds;
        this.fixedActivity = fixedActivity;
        this.stoich = stoich;
        this.rateReactants = List.copyOf(rateReactants);
        this.rateProducts = List.copyOf(rateProducts);
        this.supplyReactants = supplyReactants;
        this.reversible = reversible;
        this.vmaxF = vmaxF;
        this.kmRatio = kmRatio;
        this.keq = keq;
        this.deltaHKjPerMol = deltaHKjPerMol;
    }

    /**
     * 由酶数据装配反应网络档案（注册期一次）
     * <p>
     * 装配流程：收集物种并建立下标 → 标记固定活性物种（内置名单识别）→
     * 计算化学计量 → 分离速率项条目与固定活性供料门 → 调用 KineticsCalculator
     * 换算 Vmax 与 Km 分数 → 执行全部断言（失败即抛异常快速失败）
     *
     * @param data 酶数据档案（物品 id 直接为物种名，无额外映射层）
     * @return 校验通过的不可变网络档案
     */
    public static ReactionDefinition build(EnzymeFactoryData data) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (EnzymeFactoryData.SpeciesSpec spec : data.reactants()) {
            index.putIfAbsent(spec.item(), index.size());
        }
        for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
            index.putIfAbsent(spec.item(), index.size());
        }
        int n = index.size();
        String[] ids = index.keySet().toArray(new String[0]);
        boolean[] fixed = new boolean[n];
        double[] stoich = new double[n];
        List<SpeciesEntry> rateReactants = new ArrayList<>();
        List<SpeciesEntry> rateProducts = new ArrayList<>();
        List<Integer> supply = new ArrayList<>();

        for (EnzymeFactoryData.SpeciesSpec spec : data.reactants()) {
            int i = index.get(spec.item());
            stoich[i] -= spec.count();
            if (KineticConstants.FIXED_ACTIVITY_SPECIES.contains(spec.item())) {
                fixed[i] = true;
                supply.add(i);
            } else {
                rateReactants.add(new SpeciesEntry(i, spec.count(),
                        KineticsCalculator.toKmFraction(spec.kmMillimolar())));
            }
        }
        for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
            int i = index.get(spec.item());
            stoich[i] += spec.count();
            if (KineticConstants.FIXED_ACTIVITY_SPECIES.contains(spec.item())) {
                fixed[i] = true;
            } else if (data.reversible()) {
                rateProducts.add(new SpeciesEntry(i, spec.count(),
                        KineticsCalculator.toKmFraction(spec.kmMillimolar())));
            }
        }

        double vmaxF = KineticsCalculator.toVmaxF(data.kcat());
        double kmRatio = data.reversible() ? KineticsCalculator.kmRatio(rateReactants, rateProducts) : 1.0;

        ReactionDefinition def = new ReactionDefinition(ids, fixed, stoich, rateReactants,
                rateProducts, supply.stream().mapToInt(Integer::intValue).toArray(),
                data.reversible(), vmaxF, kmRatio, data.keq(), data.deltaHKjPerMol());
        def.assertValid();
        return def;
    }

    /**
     * 构建期断言：数据防火墙，失败即抛异常快速失败
     * <p>
     * 校验内容：物种表非空、速率项反应物存在且 Km 为正有限、
     * 可逆反应必须有速率项产物、Vmax 与 Keq 为正有限、
     * 可逆反应在参考温度下的逆向 Vmax 为正有限
     * <p>
     * 说明：元素守恒级别的配平校验需要分子式（CDK），引擎保持零依赖，
     * 该项校验由数据准备阶段的外部工具执行（M0/M6 的 Hess 校验工具）
     */
    private void assertValid() {
        if (speciesIds.length == 0) {
            throw new IllegalArgumentException("反应网络物种表为空");
        }
        if (rateReactants.isEmpty()) {
            throw new IllegalArgumentException("反应缺少速率项底物（反应物不能全为固定活性物种）");
        }
        for (SpeciesEntry entry : rateReactants) {
            if (!(entry.kmFraction() > 0.0) || !Double.isFinite(entry.kmFraction())) {
                throw new IllegalArgumentException("反应物 Km 必须为正有限值: " + speciesIds[entry.index()]);
            }
        }
        if (reversible && rateProducts.isEmpty()) {
            throw new IllegalArgumentException("可逆反应缺少速率项产物（产物不能全为固定活性物种）");
        }
        for (SpeciesEntry entry : rateProducts) {
            if (!(entry.kmFraction() > 0.0) || !Double.isFinite(entry.kmFraction())) {
                throw new IllegalArgumentException("产物 Km 必须为正有限值: " + speciesIds[entry.index()]);
            }
        }
        if (!(vmaxF > 0.0) || !Double.isFinite(vmaxF)) {
            throw new IllegalArgumentException("Vmax_f 必须为正有限值: " + vmaxF);
        }
        if (!(keq > 0.0) || !Double.isFinite(keq)) {
            throw new IllegalArgumentException("Keq 必须为正有限值: " + keq);
        }
        boolean hasProduct = false;
        for (double s : stoich) {
            if (s > 0.0) {
                hasProduct = true;
                break;
            }
        }
        if (!hasProduct) {
            throw new IllegalArgumentException("反应缺少产物");
        }
        if (reversible) {
            double vmaxB0 = vmaxBForTemperature(KineticConstants.T0);
            if (!(vmaxB0 > 0.0) || !Double.isFinite(vmaxB0)) {
                throw new IllegalArgumentException("参考温度下逆向 Vmax 无效: " + vmaxB0);
            }
        }
    }

    /**
     * 当前温度下的逆向最大速率（Haldane 关系）
     * <p>
     * Vmax_b(T) = Vmax_f·∏KmP/(∏KmS·Keq(T))，Keq(T) 由 ThermoUtil 修正
     * （van't Hoff 精确式或 Q10 降级）。这是温度对动力学的唯一影响通道，
     * 保证任意温度下模拟器平衡位置都精确等于热力学判决点
     *
     * @param temperatureK 当前温度（K）
     * @return 逆向最大速率（堆叠分数/s），不可逆反应返回 0
     */
    public double vmaxBForTemperature(double temperatureK) {
        if (!reversible) {
            return 0.0;
        }
        double keqT = ThermoUtil.keqAtTemperature(keq, deltaHKjPerMol, temperatureK);
        return KineticsCalculator.toVmaxB(vmaxF, kmRatio, keqT);
    }

    /**
     * 供料门检查：固定活性反应物（如水解反应的水）是否有存量
     * <p>
     * 任一固定活性反应物浓度耗尽则反应停供，实现"水解必须供水"的
     * 资源约束；糖酵解十步中 H₂O/H⁺ 均在产物侧，本门首版实际不触发，
     * 规则为未来反应网络预置
     *
     * @param concentrations 全物种浓度数组（可能含 RK4 中间负值，检查时钳制）
     * @return true 表示供料充足，反应可运行
     */
    public boolean hasSupply(double[] concentrations) {
        for (int index : supplyReactants) {
            if (KineticsCalculator.clamp01(concentrations[index]) <= 0.0) {
                return false;
            }
        }
        return true;
    }

    public String[] getSpeciesIds() {
        return speciesIds.clone();
    }

    /**
     * 按物品注册名查找浓度数组下标
     *
     * @param itemId 物品注册名（与 substances.json 的 id 一致）
     * @return 下标，未找到返回 -1
     */
    public int getSpeciesIndex(String itemId) {
        for (int i = 0; i < speciesIds.length; i++) {
            if (speciesIds[i].equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    public int getSpeciesCount() {
        return speciesIds.length;
    }

    public boolean isFixedActivity(int index) {
        return fixedActivity[index];
    }

    public double getStoich(int index) {
        return stoich[index];
    }

    public List<SpeciesEntry> getRateReactants() {
        return rateReactants;
    }

    public List<SpeciesEntry> getRateProducts() {
        return rateProducts;
    }

    public boolean isReversible() {
        return reversible;
    }

    public double getVmaxF() {
        return vmaxF;
    }

    public double getKeq() {
        return keq;
    }

    public Double getDeltaHKjPerMol() {
        return deltaHKjPerMol;
    }
}
