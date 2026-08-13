package com.github.crafteve.biocraft.reaction;

import java.util.List;

/**
 * 酶数据档案（不可变数据容器，纯字段零逻辑）
 * <p>
 * 数据来源与引擎完全解耦：本步（M1）由测试手工构造三套数据验证引擎，
 * 步骤 2 起由 EnzymeFactoryRegistry 解析 enzymes.json 后构造本记录，
 * 引擎代码无需任何改动
 * <p>
 * 设计要点（已与策划确认）：
 * <ul>
 *   <li>反应物/产物直接写物品注册名（substances.json 的 id），无额外物种映射层</li>
 *   <li>每物种自带 Km（mM 科学值）；不可逆反应的产物与固定活性物种的 km 填 0</li>
 *   <li>直接存 Keq（由 ΔG°′ 在数据准备阶段换算），引擎绝不修改；deltaH 可 null</li>
 *   <li>无 source/measured 字段：溯源在数据文档（仓库 md），null 语义即"未测量"</li>
 * </ul>
 *
 * @param id            酶注册名（lower_snake_case，与方块注册名一致）
 * @param name          显示名（中文，GUI 标题用）
 * @param category      EC 类别（EC1~EC6 字符串，决定方块形状与 GUI 结构，不决定颜色）
 * @param kinetic       动力学变体（LIMITING/ISOMERASE/OXIDO_LYASE，策略层分派用）
 * @param reactants     反应物条目列表（系数 + Km）
 * @param products      产物条目列表（系数 + Km，不可逆反应 km 填 0）
 * @param reversible    反应是否可逆
 * @param keq           平衡常数 Keq(T₀)（由 ΔG°′ 换算直填，无量纲）
 * @param deltaHKjPerMol 反应焓（kJ/mol），null 表示未测量（温度走 Q10 降级）
 * @param kcat          正向周转数（s⁻¹，BRENDA 人源几何中位数）
 * @param tempOptimum   最适温度（K，M5 温度机制使用）
 * @param inputSlots    输入槽位数（M3 方块实体布局用）
 * @param outputSlots   输出槽位数（M3 方块实体布局用）
 * @param stallMessage  停摆原因文案（策略层 stallReason 的翻译键）
 * @param activators    激活剂物品注册名列表（策略层存在性判定，数值动力学二期）
 */
public record EnzymeFactoryData(
        String id,
        String name,
        String category,
        String kinetic,
        List<SpeciesSpec> reactants,
        List<SpeciesSpec> products,
        boolean reversible,
        double keq,
        Double deltaHKjPerMol,
        double kcat,
        double tempOptimum,
        int inputSlots,
        int outputSlots,
        String stallMessage,
        List<String> activators) {

    /**
     * 单个物种在反应式中的条目：物品注册名 + 化学计量系数 + 米氏常数
     * <p>
     * km 约定：该物种作为底物（在其所处方向）的 Km（mM 科学值）；
     * 不可逆反应的产物与固定活性物种（H₂O/H⁺）的 km 填 0（构建期忽略）
     *
     * @param item          物品注册名（substances.json 的 id）
     * @param count         化学计量系数（正整数）
     * @param kmMillimolar  米氏常数（mM），0 表示不参与速率方程
     */
    public record SpeciesSpec(String item, int count, double kmMillimolar) {
        public SpeciesSpec {
            if (count <= 0) {
                throw new IllegalArgumentException("化学计量系数必须为正整数: " + item + "=" + count);
            }
        }
    }

    /**
     * 构建本酶的引擎模拟器实例
     * <p>
     * 每台机器方块实体持有一个模拟器；模拟器构造时执行完整构建链
     * （反应网络装配 + 数值换算 + 断言），失败即抛异常快速失败
     *
     * @return 校验通过的引擎模拟器
     */
    public EnzymeSimulator buildSimulator() {
        return new EnzymeSimulator(this);
    }
}
