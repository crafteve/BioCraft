package engineTest;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;

import java.util.List;

/**
 * 引擎单测的三套酶数据与黄金值常量
 * <p>
 * 数据来源：仓库根目录《糖酵解热力学数据库_2026-08-13.md》表 B
 * （eQuilibrator I=0.25 的 ΔG°′ 换算 Keq；BRENDA 人源优先、范围值取几何中位数；
 * 人源缺失的 TPI kcat 用酵母值）
 * <p>
 * 黄金值推导（全部可在 md 文档复现）：
 * <ul>
 *   <li>PGI：ΔG°′=+2.9 → Keq=exp(−2900/2478.8)=0.3104；平衡 [F6P]=Keq/(1+Keq)=0.23687</li>
 *   <li>TPI：ΔG°′=+5.5 → Keq=0.1087；平衡 [G3P]=0.09805</li>
 *   <li>ENO：ΔG°′=−3.7 → Keq=4.449；平衡 [PEP]=4.449/5.449=0.81648</li>
 *   <li>GAPDH：ΔG°′=+5.8 → Keq=0.0963；全动态平衡断言 ∏P/∏S=Keq</li>
 *   <li>HK：ΔG°′=−21.0 → Keq≈4800（不可逆，仅参考）</li>
 * </ul>
 */
public final class TestEnzymes {

    /** PGI 平衡点黄金值：Keq/(1+Keq)，两侧起始均应收敛至此 */
    public static final double PGI_EQ_F6P = 0.23687;

    /** ENO 平衡点黄金值：Keq/(1+Keq)（H₂O 固定活性，不参与平衡式） */
    public static final double ENO_EQ_PEP = 0.81648;

    /** TPI 与 PGI 的 kcat 比值（BRENDA：2946/79 ≈ 37.3），缩放不破坏相对快慢 */
    public static final double TPI_PGI_KCAT_RATIO = 2946.0 / 79.0;

    /**
     * 磷酸葡萄糖异构酶（PGI，EC 5.3.1.9）：G6P ⇌ F6P，可逆无辅因子
     * <p>
     * kcat 15~420 人源 → 几何中位 79；Km G6P 0.18~1.04 → 0.43，
     * Km F6P 0.031~0.068 → 0.046（双向 Km 数据完整）
     */
    public static EnzymeFactoryData pgi() {
        return new EnzymeFactoryData(
                "phosphoglucose_isomerase", "磷酸葡萄糖异构酶", "Phosphoglucose Isomerase", "PGI", "EC5", 0xFFFFD966,
                List.of(s("glucose_6_phosphate", 1, 0.43)),
                List.of(s("fructose_6_phosphate", 1, 0.046)),
                true, 0.3104, null, 79.0, 298.15,
                1, 1);
    }

    /**
     * 己糖激酶（HK，EC 2.7.1.1）：GLC + ATP → G6P + ADP，不可逆
     * <p>
     * kcat 29~101 人源 → 54.1；Km GLC 0.03~0.08 → 0.049，
     * Km ATP 0.1~12.6 → 1.12（ATP 是速度瓶颈：满堆叠也只 47% 饱和因子）
     */
    public static EnzymeFactoryData hk() {
        return new EnzymeFactoryData(
                "hexokinase", "己糖激酶", "Hexokinase", "HK", "EC2", 0xFFFFA94D,
                List.of(s("glucose", 1, 0.049), s("atp", 1, 1.12)),
                List.of(s("glucose_6_phosphate", 1, 0.0), s("adp", 1, 0.0)),
                false, 4800.0, null, 54.1, 298.15,
                2, 2);
    }

    /**
     * 甘油醛-3-磷酸脱氢酶（GAPDH，EC 1.2.1.12）：G3P + NAD⁺ + Pi ⇌ 1,3BPG + NADH + H⁺
     * <p>
     * kcat 199 人源；Km G3P 0.07~0.27 → 0.137、NAD⁺ 0.02~0.1 → 0.045、
     * Pi 4.0（极钝，满堆叠 Pi 因子仅 25%，持续供给压力）；
     * 逆向 Km 缺数据 → 对称近似（1,3BPG←0.137、NADH←0.045），标注 simplified；
     * H⁺ 为固定活性物种（km 0），只结算不进速率方程
     */
    public static EnzymeFactoryData gapdh() {
        return new EnzymeFactoryData(
                "glyceraldehyde_3_phosphate_dehydrogenase", "甘油醛-3-磷酸脱氢酶",
                "Glyceraldehyde-3-phosphate Dehydrogenase", "GAPDH", "EC1", 0xFF6FC3DF,
                List.of(s("glyceraldehyde_3_phosphate", 1, 0.137),
                        s("nad_plus", 1, 0.045),
                        s("phosphate_ion", 1, 4.0)),
                List.of(s("1_3_bisphosphoglycerate", 1, 0.137),
                        s("nadh", 1, 0.045),
                        s("hydrogen_ion", 1, 0.0)),
                true, 0.0963, null, 199.0, 298.15,
                3, 2);
    }

    /**
     * 磷酸丙糖异构酶（TPI，EC 5.3.1.1）：DHAP ⇌ G3P，可逆
     * <p>
     * kcat 人源 BRENDA 未列 → 酵母 520~16700 → 2946（近扩散极限的超级酶，
     * 用于验证缩放不破坏相对快慢与 RK4 刚性稳定性）；
     * Km DHAP 0.26~1.5 → 0.62、G3P 0.2~1.373 → 0.52
     */
    public static EnzymeFactoryData tpi() {
        return new EnzymeFactoryData(
                "triosephosphate_isomerase", "磷酸丙糖异构酶", "Triosephosphate Isomerase", "TPI", "EC5", 0xFFFFD966,
                List.of(s("dihydroxyacetone_phosphate", 1, 0.62)),
                List.of(s("glyceraldehyde_3_phosphate", 1, 0.52)),
                true, 0.1087, null, 2946.0, 298.15,
                1, 1);
    }

    /**
     * 烯醇化酶（ENO，EC 4.2.1.11）：2PG ⇌ PEP + H₂O，可逆且产物含固定活性水
     * <p>
     * kcat 81.7 人源；Km 2PG 0.199~0.3 → 0.244、PEP 0.58~0.7 → 0.637；
     * H₂O 固定活性（km 0）：不进速率方程（热力学约定活性 1）但结算产出水物品，
     * 平衡位置由 PEP/2PG 决定（Keq=4.449 已隐含 H₂O 活度 1 的变换约定）
     */
    public static EnzymeFactoryData eno() {
        return new EnzymeFactoryData(
                "enolase", "烯醇化酶", "Enolase", "ENO", "EC4", 0xFFB57EDC,
                List.of(s("2_phosphoglycerate", 1, 0.244)),
                List.of(s("phosphoenolpyruvate", 1, 0.637), s("water", 1, 0.0)),
                true, 4.449, null, 81.7, 298.15,
                1, 2);
    }

    /**
     * 果糖二磷酸醛缩酶（ALDO，EC 4.1.2.13）：F16P ⇌ DHAP + G3P，可逆
     * <p>
     * kcat 醛缩酶 A 4.7~16.7 → 10.7；Km F16P 0.0016~0.33 → 0.17、
     * 产物 Km 借 TPI 同分子数据（DHAP 0.88 / G3P 0.79，逆向缺测对称近似）；
     * Keq=1.456e-4（ΔG°′=+21.9，强偏向反应物）——旧槽位容量（64 个）
     * 下平衡产物仅 0.77 个 < 1 个无法抽出（卡死），容量翻倍后满堆
     * 平衡产物 sqrt(Keq×2.0)×64 ≈ 1.09 个可抽出（test18 守护）
     */
    public static EnzymeFactoryData aldo() {
        return new EnzymeFactoryData(
                "aldolase", "果糖二磷酸醛缩酶", "Fructose-bisphosphate Aldolase", "ALDO", "EC4", 0xFFB57EDC,
                List.of(s("fructose_1_6_bisphosphate", 1, 0.17)),
                List.of(s("dihydroxyacetone_phosphate", 1, 0.88), s("glyceraldehyde_3_phosphate", 1, 0.79)),
                true, 1.456e-4, null, 10.7, 298.15,
                1, 2);
    }

    /**
     * 便捷构造单个物种条目
     */
    private static EnzymeFactoryData.SpeciesSpec s(String item, int count, double km) {
        return new EnzymeFactoryData.SpeciesSpec(item, count, km);
    }

    /**
     * 乳酸脱氢酶（LDH，EC 1.1.1.27）：PYR + NADH + H⁺ ⇌ LAC + NAD⁺，可逆
     * <p>
     * 数据：Km PYR 0.14 / NADH 0.03（BRENDA 人源范围内）、LAC 10 / NAD⁺ 0.3；
     * kcat 150（BRENDA 人源无实测，待补，用户建议值）；
     * H⁺ 固定活性（km 0）且在反应物侧 → 消耗质子，耗尽停供
     */
    public static EnzymeFactoryData ldh() {
        return new EnzymeFactoryData(
                "lactate_dehydrogenase", "乳酸脱氢酶", "Lactate Dehydrogenase", "LDH", "EC1", 0xFF6FC3DF,
                List.of(s("pyruvate", 1, 0.14), s("nadh", 1, 0.03), s("hydrogen_ion", 1, 0.0)),
                List.of(s("lactate", 1, 10.0), s("nad_plus", 1, 0.3)),
                true, 22000.0, null, 150.0, 298.15,
                3, 2);
    }

    /**
     * 丙酮酸脱羧酶（PDC，EC 4.1.1.1，酵母）：PYR + H⁺ → AcH + CO₂，不可逆
     * <p>
     * Km PYR 1.0（WT 0.5~2.3 取中）；kcat 60（WT 60~73 下界）；
     * Keq 取 CO₂ 气体约定 3.2e3（CO₂ 逸出游戏语义）；
     * H⁺ 固定活性且在反应物侧 → 消耗质子，耗尽停供
     */
    public static EnzymeFactoryData pdc() {
        return new EnzymeFactoryData(
                "pyruvate_decarboxylase", "丙酮酸脱羧酶", "Pyruvate Decarboxylase", "PDC", "EC4", 0xFFB57EDC,
                List.of(s("pyruvate", 1, 1.0), s("hydrogen_ion", 1, 0.0)),
                List.of(s("acetaldehyde", 1, 0.0), s("carbon_dioxide", 1, 0.0)),
                false, 3200.0, null, 60.0, 298.15,
                2, 2);
    }

    /**
     * 乙醇脱氢酶（ADH，EC 1.1.1.1）：AcH + NADH + H⁺ ⇌ EtOH + NAD⁺，可逆
     * <p>
     * Km AcH 0.05 / NADH 0.02 / EtOH 10（酵母范围）/ NAD⁺ 0.3（实测 0.06 量级，
     * 建议值偏高可用）；kcat 200（酵母 ADH1 范围内）；
     * H⁺ 固定活性且在反应物侧 → 消耗质子，耗尽停供
     */
    public static EnzymeFactoryData adh() {
        return new EnzymeFactoryData(
                "alcohol_dehydrogenase", "乙醇脱氢酶", "Alcohol Dehydrogenase", "ADH", "EC1", 0xFF6FC3DF,
                List.of(s("acetaldehyde", 1, 0.05), s("nadh", 1, 0.02), s("hydrogen_ion", 1, 0.0)),
                List.of(s("ethanol", 1, 10.0), s("nad_plus", 1, 0.3)),
                true, 11000.0, null, 200.0, 298.15,
                3, 2);
    }

    /**
     * ATP 水解发电机（ATPase，EC 3.6.1.15）：ATP + H₂O → ADP + Pi + 100FE，不可逆
     * <p>
     * Km ATP 0.5（跨物种范围 0.005~2.5 取中）；kcat 100（真核实测 0.01~10 量级，
     * 100 为游戏平衡值）；fe 固定活性产物（km 0），count=100 即每分子 100 kFE；
     * H₂O 固定活性且在反应物侧 → 必须供水，耗尽停供
     */
    public static EnzymeFactoryData atpase() {
        return new EnzymeFactoryData(
                "atp_hydrolase", "ATP 水解酶", "ATP Hydrolase", "ATPase", "EC3", 0xFF7BD88F,
                List.of(s("atp", 1, 0.5), s("water", 1, 0.0)),
                List.of(s("adp", 1, 0.0), s("phosphate_ion", 1, 0.0), s("fe", 100, 0.0)),
                false, 190000.0, null, 100.0, 298.15,
                2, 3);
    }

    private TestEnzymes() {
    }
}
