package engineTest;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.EnzymeSimulator;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import com.github.crafteve.biocraft.reaction.StepResult;
import com.github.crafteve.biocraft.reaction.ThermoUtil;

import java.util.List;
import java.util.Random;

/**
 * 化学引擎独立单测主程序（脱离游戏环境，纯 JDK 运行）
 * <p>
 * 扮演"伪方块实体"的角色：手工构造酶数据、初始化浓度、每 tick 调用
 * 引擎 step 并断言结果——将来方块实体做的槽位桥接在这里被"直接读浓度"
 * 替代，引擎纯函数契约在无游戏环境下得到完整验证
 * <p>
 * 编译（需先 gradlew build 生成主代码类文件）：
 * javac -encoding UTF-8 -cp build/classes/java/main -d tools/engineTest/out tools/engineTest/*.java
 * 运行：
 * java -cp "build/classes/java/main;tools/engineTest/out" engineTest.EngineSelfTest
 * <p>
 * 退出码：全绿 0，任一用例失败 1（可接入脚本）
 */
public final class EngineSelfTest {

    private static int failures;
    private static int total;

    public static void main(String[] args) {
        run("01 五套酶数据构建通过", EngineSelfTest::test01Build);
        run("02 坏数据被构建断言拒绝", EngineSelfTest::test02BadDataRejected);
        run("03 PGI 平衡收敛至 Keq 判决点", EngineSelfTest::test03KeqConvergence);
        run("04 PGI 双向收敛对称", EngineSelfTest::test04BothSidesConverge);
        run("05 饱和回归：高浓度速率不超 Vmax", EngineSelfTest::test05SaturationBounded);
        run("06 HK 速率随 ATP 浓度变化且耗尽即停", EngineSelfTest::test06AtpSensitivity);
        run("07 HK 化学计量 1:1 守恒", EngineSelfTest::test07Stoichiometry);
        run("08 NADH 堆积产生回压（产线堵塞）", EngineSelfTest::test08NadhBackPressure);
        run("09 固定活性：水不影响 ENO 平衡、H⁺ 正常结算", EngineSelfTest::test09FixedActivity);
        run("10 温度修正 Keq(T) 与 0.1K 缓存", EngineSelfTest::test10Temperature);
        run("11 Arrhenius 活性因子单调性", EngineSelfTest::test11Arrhenius);
        run("12 随机稳定性：无 NaN 无越界", EngineSelfTest::test12Stability);
        run("13 相对快慢：TPI/PGI 初速比 ≈ 科学 kcat 比", EngineSelfTest::test13RelativeSpeed);
        run("14 PGI 黄金值快照", EngineSelfTest::test14Snapshot);
        run("15 产物堆积时底物稀少仍逆向反应（无停机判定）", EngineSelfTest::test15ReverseWithLowSubstrate);
        run("16 可达通量契约：引擎给出浓度=1 的可达上限（手算对照）", EngineSelfTest::test16ReachableFlux);

        if (failures > 0) {
            System.err.println("引擎单测失败: " + failures + "/" + total);
            System.exit(1);
        }
        System.out.println("引擎单测全部通过: " + total + " 用例");
    }

    private static void run(String name, Runnable test) {
        total++;
        try {
            test.run();
            System.out.println("[通过] " + name);
        } catch (Throwable e) {
            failures++;
            System.out.println("[失败] " + name + " -> " + e);
            e.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkNear(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(String.format("%s: 实际 %.9f 期望 %.9f±%.9f",
                    message, actual, expected, tolerance));
        }
    }

    private static void checkThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + "（未抛出异常）");
        } catch (AssertionError e) {
            throw e;
        } catch (IllegalArgumentException expected) {
            // 预期内的构建断言失败
        } catch (Exception e) {
            throw new AssertionError(message + "（抛出异常类型不符: " + e + "）", e);
        }
    }

    /** 便捷：跑指定 tick 数，返回模拟器 */
    private static EnzymeSimulator runTicks(EnzymeSimulator sim, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sim.step(KineticConstants.TICK_SECONDS);
        }
        return sim;
    }

    private static int idx(EnzymeSimulator sim, String itemId) {
        int i = sim.getDefinition().getSpeciesIndex(itemId);
        check(i >= 0, "物种未找到: " + itemId);
        return i;
    }

    /** 五套数据全部能通过构建断言 */
    private static void test01Build() {
        TestEnzymes.pgi().buildSimulator();
        TestEnzymes.hk().buildSimulator();
        TestEnzymes.gapdh().buildSimulator();
        TestEnzymes.tpi().buildSimulator();
        TestEnzymes.eno().buildSimulator();
    }

    /** kcat 非正、速率项 Km 非正等坏数据必须在构建期快速失败 */
    private static void test02BadDataRejected() {
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", "ISOMERASE",
                        TestEnzymes.pgi().reactants(), TestEnzymes.pgi().products(),
                        true, 0.3104, null, 0.0, 298.15, 1, 1, "x", List.of())
                .buildSimulator(), "kcat=0 应被拒绝");
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", "ISOMERASE",
                        List.of(new EnzymeFactoryData.SpeciesSpec("glucose_6_phosphate", 1, 0.0)),
                        TestEnzymes.pgi().products(),
                        true, 0.3104, null, 79.0, 298.15, 1, 1, "x", List.of())
                .buildSimulator(), "反应物 Km=0 应被拒绝");
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", "ISOMERASE",
                        TestEnzymes.pgi().reactants(),
                        List.of(new EnzymeFactoryData.SpeciesSpec("fructose_6_phosphate", 1, 0.0)),
                        true, 0.3104, null, 79.0, 298.15, 1, 1, "x", List.of())
                .buildSimulator(), "可逆产物 Km=0 应被拒绝");
        checkThrows(() -> new EnzymeFactoryData.SpeciesSpec("x", 0, 1.0),
                "系数 0 应被拒绝");
    }

    /** 核心硬指标：模拟器平衡位置与热力学判决点误差 <1% */
    private static void test03KeqConvergence() {
        EnzymeSimulator sim = TestEnzymes.pgi().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glucose_6_phosphate")] = 1.0;
        runTicks(sim, 10000);
        double f6p = x[idx(sim, "fructose_6_phosphate")];
        checkNear(f6p, TestEnzymes.PGI_EQ_F6P, 0.01 * TestEnzymes.PGI_EQ_F6P,
                "PGI 平衡 F6P 未收敛到 Keq 判决点");
    }

    /** 从产物侧起始应收敛到同一平衡点（可逆性对称） */
    private static void test04BothSidesConverge() {
        EnzymeSimulator sim = TestEnzymes.pgi().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "fructose_6_phosphate")] = 1.0;
        runTicks(sim, 10000);
        double f6p = x[idx(sim, "fructose_6_phosphate")];
        checkNear(f6p, TestEnzymes.PGI_EQ_F6P, 0.01 * TestEnzymes.PGI_EQ_F6P,
                "PGI 逆向起始未收敛到同一平衡点");
    }

    /** 高浓度下速率永不超过 Vmax（本征动力学会爆表，米氏项守护） */
    private static void test05SaturationBounded() {
        EnzymeSimulator pgi = TestEnzymes.pgi().buildSimulator();
        pgi.getState().getConcentrations()[idx(pgi, "glucose_6_phosphate")] = 1.0;
        StepResult r1 = pgi.step(KineticConstants.TICK_SECONDS);
        check(r1.fluxNet() <= pgi.getDefinition().getVmaxF() * 1.000001,
                "PGI 满堆叠初速超 Vmax_f（爆表）");

        EnzymeSimulator hk = TestEnzymes.hk().buildSimulator();
        double[] x = hk.getState().getConcentrations();
        x[idx(hk, "glucose")] = 1.0;
        x[idx(hk, "atp")] = 1.0;
        StepResult r2 = hk.step(KineticConstants.TICK_SECONDS);
        check(r2.fluxNet() <= hk.getDefinition().getVmaxF() * 1.000001,
                "HK 双满堆叠初速超 Vmax_f（爆表）");
    }

    /** 全底物平等：ATP 浓度参与速率，耗尽即停 */
    private static void test06AtpSensitivity() {
        EnzymeSimulator noAtp = TestEnzymes.hk().buildSimulator();
        double[] x0 = noAtp.getState().getConcentrations();
        x0[idx(noAtp, "glucose")] = 1.0;
        StepResult r0 = noAtp.step(KineticConstants.TICK_SECONDS);
        checkNear(r0.fluxNet(), 0.0, 1e-12, "ATP 耗尽时 HK 应停转");

        EnzymeSimulator low = TestEnzymes.hk().buildSimulator();
        double[] x1 = low.getState().getConcentrations();
        x1[idx(low, "glucose")] = 1.0;
        x1[idx(low, "atp")] = 0.2;

        EnzymeSimulator high = TestEnzymes.hk().buildSimulator();
        double[] x2 = high.getState().getConcentrations();
        x2[idx(high, "glucose")] = 1.0;
        x2[idx(high, "atp")] = 0.5;

        double v1 = low.step(KineticConstants.TICK_SECONDS).fluxNet();
        double v2 = high.step(KineticConstants.TICK_SECONDS).fluxNet();
        check(v2 > v1, "ATP 浓度上升速率应上升: " + v1 + " -> " + v2);
        check(v1 > 0.0, "低浓度 ATP 下速率应为正");
    }

    /** 化学计量守恒：同一净速率标量驱动，各物种变化严格 1:1 */
    private static void test07Stoichiometry() {
        EnzymeSimulator sim = TestEnzymes.hk().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glucose")] = 0.4;
        x[idx(sim, "atp")] = 0.6;
        runTicks(sim, 200);
        double dGlc = 0.4 - x[idx(sim, "glucose")];
        double dAtp = 0.6 - x[idx(sim, "atp")];
        double dG6p = x[idx(sim, "glucose_6_phosphate")];
        double dAdp = x[idx(sim, "adp")];
        checkNear(dGlc, dG6p, 1e-9, "GLC 消耗与 G6P 产出不守恒");
        checkNear(dGlc, dAtp, 1e-9, "GLC 消耗与 ATP 消耗不守恒");
        checkNear(dGlc, dAdp, 1e-9, "GLC 消耗与 ADP 产出不守恒");
    }

    /**
     * 产物回压：NADH 堆积把净通量推成逆向，副产物不回收产线自然堵塞
     * <p>
     * 场景设计：G3P/NAD⁺/Pi 初始 0.5 给逆向流出空间（逆向产它们，
     * 满堆会触发边界截断冻结——那本身也是正确物流行为）；
     * H⁺ 初始 0.5：逆向流消耗 H⁺（质子是逆向资源，耗尽即冻结）
     */
    private static void test08NadhBackPressure() {
        EnzymeSimulator full = TestEnzymes.gapdh().buildSimulator();
        double[] x1 = full.getState().getConcentrations();
        x1[idx(full, "glyceraldehyde_3_phosphate")] = 0.5;
        x1[idx(full, "nad_plus")] = 0.5;
        x1[idx(full, "phosphate_ion")] = 0.5;
        x1[idx(full, "1_3_bisphosphoglycerate")] = 0.5;
        x1[idx(full, "nadh")] = 1.0;
        x1[idx(full, "hydrogen_ion")] = 0.5;
        check(full.step(KineticConstants.TICK_SECONDS).fluxNet() < 0.0,
                "NADH 满堆应把 GAPDH 推成逆向净流");

        EnzymeSimulator low = TestEnzymes.gapdh().buildSimulator();
        double[] x2 = low.getState().getConcentrations();
        x2[idx(low, "glyceraldehyde_3_phosphate")] = 0.5;
        x2[idx(low, "nad_plus")] = 0.5;
        x2[idx(low, "phosphate_ion")] = 0.5;
        x2[idx(low, "1_3_bisphosphoglycerate")] = 0.5;
        x2[idx(low, "nadh")] = 0.01;
        x2[idx(low, "hydrogen_ion")] = 0.5;
        check(low.step(KineticConstants.TICK_SECONDS).fluxNet() > 0.0,
                "NADH 少量时 GAPDH 应为正向净流");
    }

    /** 固定活性物种：水不进速率方程（平衡不受水量影响），H⁺ 照常 1:1 结算 */
    private static void test09FixedActivity() {
        double pepNoWater = enoEquilibrium(0.0);
        double pepSomeWater = enoEquilibrium(0.1);
        checkNear(pepNoWater, TestEnzymes.ENO_EQ_PEP, 0.01 * TestEnzymes.ENO_EQ_PEP,
                "ENO 平衡 PEP 未收敛");
        checkNear(pepNoWater, pepSomeWater, 1e-9,
                "水初值不同导致 ENO 平衡移动（固定活性失效）");

        EnzymeSimulator sim = TestEnzymes.gapdh().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glyceraldehyde_3_phosphate")] = 0.5;
        x[idx(sim, "nad_plus")] = 0.5;
        x[idx(sim, "phosphate_ion")] = 0.5;
        runTicks(sim, 100);
        double dH = x[idx(sim, "hydrogen_ion")];
        double dBpg = x[idx(sim, "1_3_bisphosphoglycerate")];
        checkNear(dH, dBpg, 1e-9, "H⁺ 结算与 1,3BPG 产出不守恒（应为 1:1）");
    }

    private static double enoEquilibrium(double initialWater) {
        EnzymeSimulator sim = TestEnzymes.eno().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "2_phosphoglycerate")] = 1.0;
        x[idx(sim, "water")] = initialWater;
        runTicks(sim, 10000);
        return x[idx(sim, "phosphoenolpyruvate")];
    }

    /** 温度修正：Q10 与 van't Hoff 两路径 + 0.1K 缓存命中 */
    private static void test10Temperature() {
        // Q10 降级路径（ΔH 缺失）：T₀+10K → Keq 翻倍
        double keqQ10 = ThermoUtil.keqAtTemperature(0.3104, null, 308.15);
        checkNear(keqQ10, 0.6208, 1e-9, "Q10 路径 Keq(308.15K) 应为 0.6208");
        // van't Hoff 精确路径（有 ΔH）：与独立公式对照
        double deltaH = -20.5;
        double expected = 0.3104 * Math.exp(-deltaH * 1000.0 / 8.314
                * (1.0 / 308.15 - 1.0 / 298.15));
        checkNear(ThermoUtil.keqAtTemperature(0.3104, deltaH, 308.15), expected, 1e-9,
                "van't Hoff 路径与解析值不符");

        // 升温后平衡点按 Keq(T) 移动：308.15K 下 Keq=0.6208 → [F6P]=0.6208/1.6208
        EnzymeSimulator sim = TestEnzymes.pgi().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glucose_6_phosphate")] = 1.0;
        sim.getState().setTemperature(308.15);
        runTicks(sim, 10000);
        double expectedF6p = 0.6208 / 1.6208;
        checkNear(x[idx(sim, "fructose_6_phosphate")], expectedF6p, 0.01 * expectedF6p,
                "升温后平衡点未按 Keq(T) 移动");

        // 缓存断言：同温连续 step 只重算一次；升温 1K 后触发第二次
        EnzymeSimulator cached = TestEnzymes.pgi().buildSimulator();
        cached.getState().getConcentrations()[idx(cached, "glucose_6_phosphate")] = 0.5;
        runTicks(cached, 100);
        check(cached.getTemperatureRecalculations() == 1,
                "同温 100 tick 应只重算一次（缓存失效）");
        cached.getState().setTemperature(299.0);
        cached.step(KineticConstants.TICK_SECONDS);
        check(cached.getTemperatureRecalculations() == 2,
                "升温 0.85K 超阈值应触发重算");
    }

    /** Arrhenius：最适温度因子恒 1，升温加速降温减速 */
    private static void test11Arrhenius() {
        checkNear(ThermoUtil.arrheniusFactor(50.0, 298.15, 298.15), 1.0, 1e-12,
                "最适温度活性因子应为 1");
        double hot = ThermoUtil.arrheniusFactor(50.0, 308.15, 298.15);
        double cold = ThermoUtil.arrheniusFactor(50.0, 288.15, 298.15);
        check(hot > 1.0, "升温应加速（因子大于 1）");
        check(cold < 1.0, "降温应减速（因子小于 1）");
        check(hot > 1.0 && cold > 0.0 && hot > cold, "Arrhenius 单调性错误");
    }

    /** 随机初始浓度长时间运行：恒无 NaN、恒在 [0,1]（刚性场景 TPI 覆盖） */
    private static void test12Stability() {
        Random random = new Random(42);
        List<EnzymeFactoryData> enzymes = List.of(
                TestEnzymes.pgi(), TestEnzymes.hk(), TestEnzymes.gapdh(), TestEnzymes.tpi(), TestEnzymes.eno());
        for (EnzymeFactoryData data : enzymes) {
            for (int trial = 0; trial < 5; trial++) {
                EnzymeSimulator sim = data.buildSimulator();
                double[] x = sim.getState().getConcentrations();
                for (int i = 0; i < x.length; i++) {
                    x[i] = random.nextDouble();
                }
                for (int tick = 0; tick < 500; tick++) {
                    sim.step(KineticConstants.TICK_SECONDS);
                    for (int i = 0; i < x.length; i++) {
                        check(!Double.isNaN(x[i]) && x[i] >= 0.0 && x[i] <= 1.0,
                                data.id() + " 稳定性破坏: " + x[i]);
                    }
                }
            }
        }
    }

    /**
     * 缩放保持相对快慢：TPI/PGI 初速比 ≈ 科学 kcat 比（37.3，容差 20% 吸收 Km 差异）
     * <p>
     * 用零时长步进 step(0.0) 取 t=0 的瞬时通量：TPI 逆向极强（Keq 很小），
     * 常规步进末端通量已被一步内的产物回压显著削减，不能代表初速
     */
    private static void test13RelativeSpeed() {
        EnzymeSimulator pgi = TestEnzymes.pgi().buildSimulator();
        pgi.getState().getConcentrations()[idx(pgi, "glucose_6_phosphate")] = 1.0;
        double vPgi = pgi.step(0.0).fluxNet();

        EnzymeSimulator tpi = TestEnzymes.tpi().buildSimulator();
        tpi.getState().getConcentrations()[idx(tpi, "dihydroxyacetone_phosphate")] = 1.0;
        double vTpi = tpi.step(0.0).fluxNet();

        double ratio = vTpi / vPgi;
        double expected = TestEnzymes.TPI_PGI_KCAT_RATIO;
        check(ratio > expected * 0.8 && ratio < expected * 1.2,
                String.format("TPI/PGI 初速比 %.3f 偏离科学 kcat 比 %.3f 超 20%%", ratio, expected));
    }

    /**
     * PGI 黄金值快照：固定初始跑 1000 tick，断言最终浓度与累计通量
     * <p>
     * 黄金值在引擎定稿后固化防回归（数值首次运行人工核验：
     * F6P 终值 0.2359 与 Keq 判决点 0.23687 一致，通量为正向单峰递减序列的积分）
     */
    private static void test14Snapshot() {
        EnzymeSimulator sim = TestEnzymes.pgi().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glucose_6_phosphate")] = 1.0;
        double totalFlux = 0.0;
        for (int i = 0; i < 1000; i++) {
            totalFlux += sim.step(KineticConstants.TICK_SECONDS).fluxNet();
        }
        checkNear(x[idx(sim, "glucose_6_phosphate")], 0.764121484, 1e-6,
                "快照 G6P 终值偏离黄金值");
        checkNear(x[idx(sim, "fructose_6_phosphate")], 0.235878516, 1e-6,
                "快照 F6P 终值偏离黄金值");
        checkNear(totalFlux, 4.690124653, 1e-6, "快照累计净通量偏离黄金值");
    }

    /**
     * 回归守护：F6P 大量堆积而 G6P 稀少（仅 1 个）时，净通量必须为逆向（负值）
     * <p>
     * 机器是否运转完全由引擎计算决定，不得有任何"底物过少直接停机"的
     * 外部判定——产物回压应把反应推向逆向（策划：副产物不回收则产线堵塞）
     */
    private static void test15ReverseWithLowSubstrate() {
        EnzymeSimulator sim = TestEnzymes.pgi().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "glucose_6_phosphate")] = 1.0 / 64.0;
        x[idx(sim, "fructose_6_phosphate")] = 48.0 / 64.0;
        check(sim.step(KineticConstants.TICK_SECONDS).fluxNet() < 0.0,
                "产物大量堆积且底物稀少时应逆向净流（不得因底物少而停机）");
    }

    /**
     * 可达通量契约：引擎给出"游戏内可达上限"——速率方程代入浓度=1 的最大通量
     * <p>
     * 显示层（GUI 刻度/JEI 信息卡）只做单位换算，不得在显示层重写速率公式。
     * 手算对照（旧显示层公式的同数值回归）：
     * <ul>
     *   <li>PGI 可逆单底物：fwd = Vmax_f·(1/Km₆ₚ)/(1+1/Km₆ₚ)，rev = Vmax_b·(1/Km_ᶠ⁶ᵖ)/(1+1/Km_ᶠ⁶ᵖ)</li>
     *   <li>HK 不可逆：rev 恒 0，fwd = Vmax_f·∏(1/(1+Km))</li>
     * </ul>
     * 同时断言饱和有界：可达通量必须小于 Vmax（Vmax 是浓度趋无穷的数学极限）
     */
    private static void test16ReachableFlux() {
        EnzymeSimulator pgi = TestEnzymes.pgi().buildSimulator();
        ReactionDefinition pgiDef = pgi.getDefinition();
        double f = 1.0 / 0.43;
        double pgiFwd = pgiDef.getVmaxF() * f / (1.0 + f);
        checkNear(pgiDef.forwardReachableFlux(), pgiFwd, 1e-9, "PGI 正向可达通量与手算不符");
        double r = 1.0 / 0.046;
        double pgiRev = pgiDef.vmaxBForTemperature(KineticConstants.T0) * r / (1.0 + r);
        checkNear(pgiDef.reverseReachableFlux(), pgiRev, 1e-9, "PGI 逆向可达通量与手算不符");
        check(pgiDef.forwardReachableFlux() < pgiDef.getVmaxF(),
                "PGI 可达通量应小于 Vmax_f（饱和有界）");

        EnzymeSimulator hk = TestEnzymes.hk().buildSimulator();
        ReactionDefinition hkDef = hk.getDefinition();
        checkNear(hkDef.reverseReachableFlux(), 0.0, 0.0, "HK 不可逆逆向可达通量应为 0");
        double hkFactor = (1.0 / (1.0 + 0.049)) * (1.0 / (1.0 + 1.12));
        checkNear(hkDef.forwardReachableFlux(), hkDef.getVmaxF() * hkFactor, 1e-9,
                "HK 正向可达通量与手算不符");
        check(hkDef.forwardReachableFlux() < hkDef.getVmaxF(),
                "HK 可达通量应小于 Vmax_f（饱和有界）");
    }

    private EngineSelfTest() {
    }
}
