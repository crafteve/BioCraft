package engineTest;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.EnzymeSimulator;
import com.github.crafteve.biocraft.reaction.EnergyKinetics;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.KineticsCalculator;
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
        run("16 可达通量契约：引擎给出满堆=槽位组数的可达上限（手算对照）", EngineSelfTest::test16ReachableFlux);
        run("17 浓度钳制上限：余量+满槽共存不被吞（64.77/64 保留）", EngineSelfTest::test17ConcentrationClamp);
        run("18 ALDO 容量翻倍：平衡产物 ≥1 个可抽出（旧容量 0.77 卡死回归）", EngineSelfTest::test18AlodoProductExtractable);
        run("19 能量纯函数：容量/镜像/FE 结算公式手算对照", EngineSelfTest::test19EnergyKinetics);
        run("20 ATPase 含 fe 物种构建通过 + H₂O 耗尽停供", EngineSelfTest::test20AtpaseSupply);
        run("21 ATPase 满能量镜像停转（边界缩放回压）", EngineSelfTest::test21AtpaseFullEnergyStall);
        run("22 LDH 平衡收敛至 Keq 判决点（乳酸线可逆酶）", EngineSelfTest::test22LdhConvergence);
        run("23 RK4 刚性自适应：TPI kcat=9000 数据浓度正常推进", EngineSelfTest::test23RigidAdaptive);
        run("24 ALDO 64 活性强刚性：Vmax_b≈19200 正常推进并收敛 Keq", EngineSelfTest::test24AlodoHighActivity);
        run("25 ALDO 64 活性长周期：平衡区驻留 10000 tick Q 锁定 Keq 零漂移", EngineSelfTest::test25AlodoEquilibriumDrift);
        run("26 TPI [E]=3 满堆反应物冻结回归：方向矛盾细分 + 近平衡无极限环", EngineSelfTest::test26TpiFullReactantFreeze);

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

    /** 六套数据全部能通过构建断言 */
    private static void test01Build() {
        TestEnzymes.pgi().buildSimulator();
        TestEnzymes.hk().buildSimulator();
        TestEnzymes.gapdh().buildSimulator();
        TestEnzymes.tpi().buildSimulator();
        TestEnzymes.eno().buildSimulator();
        TestEnzymes.aldo().buildSimulator();
        TestEnzymes.ldh().buildSimulator();
        TestEnzymes.pdc().buildSimulator();
        TestEnzymes.adh().buildSimulator();
        TestEnzymes.atpase().buildSimulator();
    }

    /** kcat 非正、速率项 Km 非正等坏数据必须在构建期快速失败 */
    private static void test02BadDataRejected() {
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", 0xFFB57EDC,
                        TestEnzymes.pgi().reactants(), TestEnzymes.pgi().products(),
                        true, 0.3104, null, 0.0, 298.15, 1, 1)
                .buildSimulator(), "kcat=0 应被拒绝");
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", 0xFFB57EDC,
                        List.of(new EnzymeFactoryData.SpeciesSpec("glucose_6_phosphate", 1, 0.0)),
                        TestEnzymes.pgi().products(),
                        true, 0.3104, null, 79.0, 298.15, 1, 1)
                .buildSimulator(), "反应物 Km=0 应被拒绝");
        checkThrows(() -> new EnzymeFactoryData("bad", "坏酶", "Bad Enzyme", "BAD", "EC5", 0xFFB57EDC,
                        TestEnzymes.pgi().reactants(),
                        List.of(new EnzymeFactoryData.SpeciesSpec("fructose_6_phosphate", 1, 0.0)),
                        true, 0.3104, null, 79.0, 298.15, 1, 1)
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
                        check(!Double.isNaN(x[i]) && x[i] >= 0.0 && x[i] <= KineticConstants.MAX_CONCENTRATION,
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
     * 可达通量契约：引擎给出"满堆"的可达上限（手算对照）
     * <p>
     * 满堆浓度 = 槽位组数（SLOT_GROUPS=2，即 128 个物品），
     * 显示层（GUI 刻度/JEI 信息卡）只做单位换算，不得在显示层重写速率公式。
     * 手算对照（旧显示层公式的同数值回归，浓度 x=SLOT_GROUPS）：
     * <ul>
     *   <li>PGI 可逆单底物：fwd = Vmax_f·(x/Km₆ₚ)/(1+x/Km₆ₚ)，rev = Vmax_b·(x/Km_ᶠ⁶ᵖ)/(1+x/Km_ᶠ⁶ᵖ)</li>
     *   <li>HK 不可逆：rev 恒 0，fwd = Vmax_f·∏(x/(Km+x))</li>
     * </ul>
     * 同时断言饱和有界：可达通量必须小于 Vmax（Vmax 是浓度趋无穷的数学极限）
     */
    private static void test16ReachableFlux() {
        double x = KineticConstants.SLOT_GROUPS;
        EnzymeSimulator pgi = TestEnzymes.pgi().buildSimulator();
        ReactionDefinition pgiDef = pgi.getDefinition();
        double f = x / 0.43;
        double pgiFwd = pgiDef.getVmaxF() * f / (1.0 + f);
        checkNear(pgiDef.forwardReachableFlux(), pgiFwd, 1e-9, "PGI 正向可达通量与手算不符");
        double r = x / 0.046;
        double pgiRev = pgiDef.vmaxBForTemperature(KineticConstants.T0) * r / (1.0 + r);
        checkNear(pgiDef.reverseReachableFlux(), pgiRev, 1e-9, "PGI 逆向可达通量与手算不符");
        check(pgiDef.forwardReachableFlux() < pgiDef.getVmaxF(),
                "PGI 可达通量应小于 Vmax_f（饱和有界）");

        EnzymeSimulator hk = TestEnzymes.hk().buildSimulator();
        ReactionDefinition hkDef = hk.getDefinition();
        checkNear(hkDef.reverseReachableFlux(), 0.0, 0.0, "HK 不可逆逆向可达通量应为 0");
        double hkFactor = (x / (x + 0.049)) * (x / (x + 1.12));
        checkNear(hkDef.forwardReachableFlux(), hkDef.getVmaxF() * hkFactor, 1e-9,
                "HK 正向可达通量与手算不符");
        check(hkDef.forwardReachableFlux() < hkDef.getVmaxF(),
                "HK 可达通量应小于 Vmax_f（饱和有界）");
    }

    /**
     * 浓度钳制上限契约：输入浓度可超过 1.0（槽位 n 组 + 余量），
     * 引擎钳制在 MAX_CONCENTRATION 而非旧的 1.0
     * <p>
     * 用户实测 bug：平衡 63.23 个物品（浓度 0.9879）+ 投入 1 个 → 浓度 1.0036
     * 被旧 clamp01 钳回 1.0，0.23 个物品被吞。新上限 = n + 1/64 允许
     * "槽满仍攒余量"的状态存在
     */
    private static void test17ConcentrationClamp() {
        checkNear(KineticsCalculator.clampConcentration(64.77 / 64.0), 64.77 / 64.0, 1e-12,
                "浓度 64.77/64 不应被钳制（余量 + 满槽应共存）");
        checkNear(KineticsCalculator.clampConcentration(KineticConstants.MAX_CONCENTRATION),
                KineticConstants.MAX_CONCENTRATION, 1e-12, "MAX_CONCENTRATION 边界应原样保留");
        check(KineticsCalculator.clampConcentration(5.0) <= KineticConstants.MAX_CONCENTRATION,
                "超上限浓度应被钳制到 MAX_CONCENTRATION");
        check(KineticsCalculator.clampConcentration(-0.1) == 0.0, "负浓度应钳制到 0");
        check(Double.isNaN(KineticsCalculator.clampConcentration(Double.NaN)) == false,
                "NaN 应钳制到 0");

        // 引擎端：超上限输入 step 后浓度仍被钳在 MAX 内（不爆 NaN 不越界）
        EnzymeSimulator pgi = TestEnzymes.pgi().buildSimulator();
        double[] x = pgi.getState().getConcentrations();
        x[idx(pgi, "glucose_6_phosphate")] = 2.5;
        x[idx(pgi, "fructose_6_phosphate")] = 2.5;
        runTicks(pgi, 10);
        for (double v : x) {
            check(!Double.isNaN(v) && v >= 0.0 && v <= KineticConstants.MAX_CONCENTRATION,
                    "step 后浓度越界: " + v);
        }
    }

    /**
     * 满堆容量下强偏向反应物酶的产物可抽出契约
     * <p>
     * ALDO（F16P⇌DHAP+G3P，Keq=1.456e-4 极小）：旧容量（1 组 = 64 个，
     * 浓度钳 1.0）下平衡产物浓度 sqrt(Keq×0.988)×64 ≈ 0.77 个 < 1 个，
     * 槽位投影为 0，玩家永远抽不出产物 → 反应卡死（用户实测 bug）。
     * 容量翻倍（n=2，满堆 128 个 = 浓度 2.0）后平衡产物
     * sqrt(Keq×2.0)×64 ≈ 1.09 个 > 1 个，可抽出
     */
    private static void test18AlodoProductExtractable() {
        double keq = 1.456e-4;
        double eqProductConc = Math.sqrt(keq * KineticConstants.SLOT_GROUPS);
        check(eqProductConc * 64.0 >= 1.0,
                String.format("满堆 %.1f 组时 ALDO 平衡产物 %.3f 个应 ≥1 个（可抽出）",
                        (double) KineticConstants.SLOT_GROUPS, eqProductConc * 64.0));

        // 引擎端：F16P 满堆 2.0 起步跑向平衡，产物浓度应达到可抽出的 1/64 以上
        EnzymeSimulator ald = TestEnzymes.aldo().buildSimulator();
        double[] x = ald.getState().getConcentrations();
        x[idx(ald, "fructose_1_6_bisphosphate")] = KineticConstants.SLOT_GROUPS;
        runTicks(ald, 20_000);
        double dhap = x[idx(ald, "dihydroxyacetone_phosphate")];
        check(dhap >= 1.0 / 64.0,
                String.format("ALDO 满堆平衡后 DHAP 浓度 %.5f 应 ≥ 1/64（可抽出）", dhap));
        check(dhap < 0.05, String.format("ALDO 平衡 DHAP 浓度 %.5f 应远小于 1（Keq 极小）", dhap));
    }

    /**
     * 能量（FE）纯函数契约：容量/镜像/FE 结算三公式手算对照
     * <p>
     * ATPase（count=100）：容量 = 100×1000×64×MAX_CONCENTRATION ≈ 1290 万 FE；
     * 镜像：满存量 = MAX_CONCENTRATION、空 = 0、半 = 一半；
     * 每 tick 结算：fluxNet×stoich×64×0.05×1000，正负方向与产物/反应物侧对应
     */
    private static void test19EnergyKinetics() {
        double max = KineticConstants.MAX_CONCENTRATION;
        int capacity = EnergyKinetics.capacity(100);
        checkNear(capacity, 100 * EnergyKinetics.KFE_SCALE * 64.0 * max, 1e-9, "容量公式与手算不符");

        checkNear(EnergyKinetics.mirrorConcentration(0, capacity), 0.0, 1e-12, "空存量镜像应为 0");
        checkNear(EnergyKinetics.mirrorConcentration(capacity, capacity), max, 1e-12,
                "满存量镜像应为 MAX_CONCENTRATION");
        checkNear(EnergyKinetics.mirrorConcentration(capacity / 2, capacity), max / 2.0, 1e-12,
                "半存量镜像应为 MAX/2");

        // 产物侧（stoich=+100）：正向通量充能
        double charge = EnergyKinetics.fePerTick(0.1, 100.0);
        checkNear(charge, 0.1 * 100.0 * 64.0 * KineticConstants.TICK_SECONDS * EnergyKinetics.KFE_SCALE,
                1e-9, "产物侧 FE 结算与手算不符");
        check(charge > 0.0, "产物侧正向通量应充能（正值）");
        // 反应物侧（stoich=-50）：正向通量消耗
        double drain = EnergyKinetics.fePerTick(0.1, -50.0);
        check(drain < 0.0, "反应物侧正向通量应消耗（负值）");
        checkNear(drain, -0.5 * charge, 1e-9, "反应物侧结算应为产物侧一半（系数比 100:50）");

        check(EnergyKinetics.isEnergySpecies("fe"), "fe 应被识别为能量物种");
        check(!EnergyKinetics.isEnergySpecies("atp"), "atp 不应被识别为能量物种");
    }

    /**
     * ATPase（含 fe 物种）构建通过；H₂O 耗尽（固定活性反应物）→ 停供门
     * <p>
     * fe 在产物侧：不触发 hasSupply；water 在反应物侧：耗尽即停供。
     * 镜像语义由 BE 维护，引擎层只需保证 fe 物种浓度合法存在且结算不越界
     */
    private static void test20AtpaseSupply() {
        EnzymeSimulator sim = TestEnzymes.atpase().buildSimulator();
        ReactionDefinition def = sim.getDefinition();
        check(def.getSpeciesIndex("fe") >= 0, "fe 应进入物种表");
        check(def.isFixedActivity(def.getSpeciesIndex("fe")), "fe 应为固定活性");
        check(def.getStoich(def.getSpeciesIndex("fe")) == 100.0, "fe 净化学计量应为 +100");

        // 无水：停供门立即冻结
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "atp")] = 1.0;
        StepResult r0 = sim.step(KineticConstants.TICK_SECONDS);
        checkNear(r0.fluxNet(), 0.0, 1e-12, "H₂O 耗尽时 ATPase 应停供");

        // 供水后恢复
        x[idx(sim, "water")] = 1.0;
        check(sim.step(KineticConstants.TICK_SECONDS).fluxNet() > 0.0, "供水后 ATPase 应恢复运行");
    }

    /**
     * 满能量镜像停转（边界缩放回压）：fe 浓度被 BE 写为 MAX 后，
     * RK4 终值越界触发全局缩放 → 净通量归零（满能量停转、抽走恢复）
     */
    private static void test21AtpaseFullEnergyStall() {
        EnzymeSimulator sim = TestEnzymes.atpase().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "atp")] = 1.0;
        x[idx(sim, "water")] = 1.0;
        // 满能量镜像（BE 每 tick 覆写 fe 浓度；此处模拟 BE 行为直接写上限）
        x[idx(sim, "fe")] = KineticConstants.MAX_CONCENTRATION;
        StepResult r = sim.step(KineticConstants.TICK_SECONDS);
        checkNear(r.fluxNet(), 0.0, 1e-9, "满能量镜像下 ATPase 应停转（边界缩放回压）");
        check(x[idx(sim, "fe")] <= KineticConstants.MAX_CONCENTRATION,
                "满能量镜像 step 后 fe 浓度不得越界");
    }

    /**
     * 乳酸线可逆酶平衡契约：LDH 从反应物侧收敛到 Keq 判决点
     * <p>
     * Keq=22000 强偏乳酸：平衡时 [LAC][NAD⁺]/([PYR][NADH]) = 22000。
     * 从 PYR/NADH 满堆起步，LAC/NAD⁺ 应收敛到接近满堆（大部分转化）；
     * H⁺ 固定活性（km 0）不影响平衡式，但它在反应物侧参与计量结算——
     * 必须给足初值（平衡净耗 1 质子/分子），否则耗尽触发停供门冻结
     */
    private static void test22LdhConvergence() {
        EnzymeSimulator sim = TestEnzymes.ldh().buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "pyruvate")] = 1.0;
        x[idx(sim, "nadh")] = 1.0;
        x[idx(sim, "hydrogen_ion")] = 1.0;
        runTicks(sim, 20000);
        double q = x[idx(sim, "lactate")] * x[idx(sim, "nad_plus")]
                / (x[idx(sim, "pyruvate")] * x[idx(sim, "nadh")]);
        checkNear(q, 22000.0, 0.02 * 22000.0, "LDH 平衡商 Q 未收敛到 Keq（2% 容差）");
        check(x[idx(sim, "lactate")] > 0.9, "LDH 强偏产物，乳酸应接近满堆");
        check(x[idx(sim, "hydrogen_ion")] > 0.0, "H⁺ 不应耗尽（停供门不得误触发）");
    }

    /**
     * RK4 刚性自适应守护：高 kcat（TPI 9000）数据必须正常推进到平衡
     * <p>
     * 回归根因（AGENTS.md 2.6 欠账 28）：旧引擎 RK4 在此数据下四阶项
     * 剧烈震荡自抵消——通量报告 3168/tick 但 Δ浓度 ≈ 1.6e-5（卡死）。
     * 修复后引擎自动细分步长，200 tick 内应收敛到 Keq 判决点
     * （G3P = Keq/(1+Keq)，与 PGI/ENO 收敛用例同口径）
     */
    private static void test23RigidAdaptive() {
        EnzymeFactoryData rigidTpi = new EnzymeFactoryData(
                "tpi_rigid", "刚性TPI", "Rigid TPI", "TPIR", "EC5", 0xFFD966,
                List.of(new EnzymeFactoryData.SpeciesSpec("dihydroxyacetone_phosphate", 1, 0.88)),
                List.of(new EnzymeFactoryData.SpeciesSpec("glyceraldehyde_3_phosphate", 1, 0.79)),
                true, 0.10874, null, 9000.0, 298.15,
                1, 1);
        EnzymeSimulator sim = rigidTpi.buildSimulator();
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "dihydroxyacetone_phosphate")] = 1.0;
        double expected = 0.10874 / 1.10874;
        // 收敛过程快速检查：前 5 tick 内 G3P 必须显著推进（证明未卡死）
        runTicks(sim, 5);
        double g3pEarly = x[idx(sim, "glyceraldehyde_3_phosphate")];
        check(g3pEarly > 0.02, String.format("刚性 TPI 前 5 tick G3P 应显著推进（实测 %.6f，疑似卡死）", g3pEarly));
        // 200 tick 应收敛到平衡（2% 容差，与 PGI/ENO 同口径）
        runTicks(sim, 200);
        double g3p = x[idx(sim, "glyceraldehyde_3_phosphate")];
        checkNear(g3p, expected, 0.02 * expected,
                "刚性 TPI 200 tick 未收敛到 Keq 判决点（自适应细分失效）");
        // 通量报告与浓度一致：平衡时净通量趋近 0（不再"v 大但不动"）
        StepResult r = sim.step(KineticConstants.TICK_SECONDS);
        check(Math.abs(r.fluxNet()) < 0.01,
                String.format("平衡后净通量应趋近 0（实测 %.6f）", r.fluxNet()));
    }

    /**
     * ALDO 64 活性强刚性回归：64 个酶（[E]=64）放大特征速率
     * <p>
     * 场景（用户实测）：64 个 ALDO + 满堆 F16P（浓度 2.0 = 128 个物品）+ 0 产物。
     * ALDO Keq=1.456e-4 → Vmax_b = Vmax_f·∏KmP/(∏KmS·Keq) ≈ 300，×64 = 19200；
     * RK4 稳定条件 h·λ < 2.8 需要子步 > 343——旧 64 子步上限下数值震荡
     * （浓度几乎不推进），MAX_SUBSTEPS 提到 512 后应正常推进并收敛
     * （引擎测试用例 23 的强刚性延伸）
     */
    private static void test24AlodoHighActivity() {
        EnzymeSimulator sim = TestEnzymes.aldo().buildSimulator();
        sim.getState().setActivity(64.0);
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "fructose_1_6_bisphosphate")] = 2.0;
        // 收敛过程快速检查：前 10 tick 产物必须显著推进（证明未卡死）
        runTicks(sim, 10);
        double products = x[idx(sim, "dihydroxyacetone_phosphate")]
                + x[idx(sim, "glyceraldehyde_3_phosphate")];
        check(products > 1e-4,
                String.format("ALDO 64 活性前 10 tick 产物应推进（实测 %.8f，疑似数值卡死）", products));
        // 平衡收敛：Q = [DHAP][G3P]/[F16P] → Keq（高刚性收敛慢，2000 tick + 10% 容差）
        runTicks(sim, 2000);
        double dhap = x[idx(sim, "dihydroxyacetone_phosphate")];
        double g3p = x[idx(sim, "glyceraldehyde_3_phosphate")];
        double f16p = x[idx(sim, "fructose_1_6_bisphosphate")];
        double q = dhap * g3p / f16p;
        checkNear(q, 1.456e-4, 0.10 * 1.456e-4, "ALDO 高活性平衡商 Q 未收敛到 Keq（10% 容差）");
    }

    /**
     * ALDO 64 活性长周期漂移回归（判据 3：平衡区驻留细分）
     * <p>
     * 场景（用户实测）：64 个 ALDO 满堆 F16P 长时间运转，槽位数量在
     * "产物 0↔1 个、反应物 128↔127 个"之间来回跳。
     * 根因（已定位）：判据 1/2 的盲区——平衡点附近净速率≈0，单步变化量
     * 极小不触发细分，大步长 RK4 放大因子 |R(h·λ)|（h·λ≈1475）远超 1，
     * 数值误差缓慢把系统推离平衡形成长周期极限环（实测 Q 从 1.44e-4
     * 漂移到 6.4e-5 再回弹，周期 &gt;4000 tick——旧 2000 tick 测试"恰好落在
     * 高位"是收敛假象）；判据 3 按"高 Vmax 背景 + 净通量≈0"直接细到
     * 最大子步数（64 子步 h·λ≈0.68 落稳定域），平衡区 Q 应锁定 Keq 零漂移
     */
    private static void test25AlodoEquilibriumDrift() {
        EnzymeSimulator sim = TestEnzymes.aldo().buildSimulator();
        sim.getState().setActivity(64.0);
        double[] x = sim.getState().getConcentrations();
        x[idx(sim, "fructose_1_6_bisphosphate")] = 2.0;
        // 长周期窗口内 Q 与产物浓度必须完全稳定（零漂移）
        runTicks(sim, 1000);
        double dhap = x[idx(sim, "dihydroxyacetone_phosphate")];
        double q = dhap * x[idx(sim, "glyceraldehyde_3_phosphate")]
                / x[idx(sim, "fructose_1_6_bisphosphate")];
        double minQ = q, maxQ = q, minDhap = dhap, maxDhap = dhap;
        for (int t = 0; t < 9000; t++) {
            sim.step(KineticConstants.TICK_SECONDS);
            dhap = x[idx(sim, "dihydroxyacetone_phosphate")];
            double qi = dhap * x[idx(sim, "glyceraldehyde_3_phosphate")]
                    / x[idx(sim, "fructose_1_6_bisphosphate")];
            minQ = Math.min(minQ, qi);
            maxQ = Math.max(maxQ, qi);
            minDhap = Math.min(minDhap, dhap);
            maxDhap = Math.max(maxDhap, dhap);
        }
        // Q 全程锁定 Keq：漂移幅度 &lt;1e-6 相对容差（修复前实测漂移到 6.4e-5 ≈ 56% 偏差）
        checkNear(maxQ - minQ, 0.0, 1e-6,
                String.format("ALDO 平衡区 Q 漂移（窗口 [%.3e, %.3e]，应锁定 Keq=1.456e-4）", minQ, maxQ));
        // 产物浓度稳定在平衡点：槽位投影（floor(浓度×64)）不来回跳
        check(maxDhap - minDhap < 1e-6,
                String.format("ALDO 平衡区产物浓度漂移（窗口 [%.7f, %.7f]）", minDhap, maxDhap));
        // 平衡点正确：DHAP ≈ sqrt(Keq×F16P) ≈ 0.0170（产物恒投影 1 个物品）
        checkNear(dhap, 0.01699, 0.001, "ALDO 平衡区 DHAP 应稳定在平衡点（约 0.0170）");
    }

    /**
     * TPI [E]=3 满堆反应物冻结回归（判据 4：方向矛盾/过冲细分）
     * <p>
     * 场景（用户实测 + 游戏日志实证）：TPI×3（[E]=3，kcat=9000 真实数据）
     * 反应物 DHAP 满堆（浓度 2.015625 = 槽位 128 + 余量 1.0，卡片读数 129.0）
     * 且产物 G3P 为 0 时，旧引擎单步 RK4 的 k2~k4 采样点把 G3P 抬到高位
     * 使逆向通量主导，加权平均预测"逆向净流"（与欧拉的正向完全相反），
     * 反应物恰在浓度上限 → 边界缩放 scale=0 → 引擎永久冻结
     * （v=0.00、产物恒 0.00——日志实测 DHAP=128 G3P=0 fluxX1000=0）。
     * 判据 1（自抵消）只覆盖 |rk4|&lt;&lt;|euler|，漏检"方向相反/幅度过冲"，
     * 判据 4 补上后细分至采样点不出界，方向恢复正确
     * <p>
     * 另覆盖同根因的近平衡极限环：修复前 [E]=3 真实数据在平衡点附近
     * 以 3 tick 周期振荡（G3P 0.199↔0.303 不收敛，Q 偏离 Keq 达 60%），
     * 修复后应快速收敛且 Q 锁定 Keq
     */
    private static void test26TpiFullReactantFreeze() {
        // 与 enzymes.json 的 triose_phosphate_isomerase 完全一致的真实数据
        EnzymeFactoryData realTpi = new EnzymeFactoryData(
                "triose_phosphate_isomerase", "磷酸丙糖异构酶", "Triosephosphate Isomerase", "TPI", "EC5", 0xFFFFD966,
                List.of(new EnzymeFactoryData.SpeciesSpec("dihydroxyacetone_phosphate", 1, 0.88)),
                List.of(new EnzymeFactoryData.SpeciesSpec("glyceraldehyde_3_phosphate", 1, 0.79)),
                true, 0.10874, null, 9000.0, 298.15,
                1, 1);
        double keq = 0.10874;
        // 阶段 1：满堆反应物 + 空产物（用户冻结状态）必须产出并收敛
        EnzymeSimulator sim = realTpi.buildSimulator();
        sim.getState().setActivity(3.0);
        double[] x = sim.getState().getConcentrations();
        int dhap = idx(sim, "dihydroxyacetone_phosphate");
        int g3p = idx(sim, "glyceraldehyde_3_phosphate");
        x[dhap] = KineticConstants.MAX_CONCENTRATION;
        x[g3p] = 0.0;
        // 前 3 tick 产物必须显著推进（修复前恒 0.0000，永久冻结）
        runTicks(sim, 3);
        check(x[g3p] > 0.05,
                String.format("满堆反应物 + 空产物前 3 tick G3P 应产出（实测 %.6f，疑似冻结）", x[g3p]));
        // 200 tick 收敛到 Keq 判决点（G3P = total×Keq/(1+Keq)，2% 容差）
        runTicks(sim, 200);
        double total = KineticConstants.MAX_CONCENTRATION;
        double expectedG3p = total * keq / (1.0 + keq);
        checkNear(x[g3p], expectedG3p, 0.02 * expectedG3p,
                "满堆反应物场景 200 tick 未收敛到 Keq 判决点");
        // 阶段 2：近平衡极限环回归——修复前以 3 tick 周期振荡不收敛
        sim = realTpi.buildSimulator();
        sim.getState().setActivity(3.0);
        x = sim.getState().getConcentrations();
        x[dhap] = 1.8004;
        x[g3p] = 0.2152;
        // 30 tick 内 Q 必须锁定 Keq（修复前振荡窗口 [0.109, 0.174] 偏差 60%）
        runTicks(sim, 30);
        double q = x[g3p] / x[dhap];
        checkNear(q, keq, 0.02 * keq, "近平衡极限环场景 Q 未锁定 Keq");
        // 再跑 200 tick 验证零漂移（修复前周期振荡持续存在）
        runTicks(sim, 200);
        double q2 = x[g3p] / x[dhap];
        checkNear(q2, keq, 0.001 * keq, "近平衡场景 200 tick 后 Q 漂移");
    }

    private EngineSelfTest() {
    }
}
