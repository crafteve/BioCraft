package com.github.crafteve.biocraft.item;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IIsotope;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 分子数据计算器（common 端）：从 SMILES 计算分子式与精确摩尔质量
 * <p>
 * 使用 CDK 解析 SMILES 并补全隐氢后计数，分子式按 Hill 排序（C、H 优先，
 * 其余按元素符号字母序），数字渲染为 Unicode 下标/上标，供 tooltip 直接显示
 * <p>
 * 计算结果按 SMILES 缓存，首次访问时计算，之后零开销；
 * 本类为 common 类（不依赖客户端），服务端同样可安全调用
 */
public final class MoleculeDataCalculator {

    /** SMILES -> 计算结果的缓存 */
    private static final Map<String, MoleculeData> CACHE = new HashMap<>();

    private MoleculeDataCalculator() {
    }

    /**
     * 分子计算数据
     *
     * @param formula 格式化后的分子式（含 Unicode 下标/上标）
     * @param mass    精确摩尔质量（g/mol）
     */
    public record MoleculeData(String formula, double mass) {
    }

    /**
     * 计算指定 SMILES 的分子式与摩尔质量（带缓存）
     *
     * @param smiles SMILES 结构式
     * @return 计算结果
     */
    public static synchronized MoleculeData forSmiles(String smiles) {
        return CACHE.computeIfAbsent(smiles, MoleculeDataCalculator::compute);
    }

    /**
     * 解析 SMILES 并计算分子式与精确质量
     *
     * @param smiles SMILES 结构式
     * @return 计算结果
     */
    private static MoleculeData compute(String smiles) {
        try {
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer container = parser.parseSmiles(smiles);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);
            IMolecularFormula formula = MolecularFormulaManipulator.getMolecularFormula(container);
            double mass = MolecularFormulaManipulator.getTotalExactMass(formula);
            return new MoleculeData(formatHillFormula(formula), mass);
        } catch (Exception e) {
            throw new IllegalArgumentException("SMILES 解析失败: " + smiles, e);
        }
    }

    /**
     * 按 Hill 排序规则格式化分子式：C、H 优先，其余按字母序，计数省略 1
     * <p>
     * 离子会在末尾追加带上标的电荷（如 [H+] 显示为 H⁺）
     *
     * @param formula CDK 分子式对象
     * @return 格式化字符串
     */
    private static String formatHillFormula(IMolecularFormula formula) {
        Map<String, Integer> counts = new TreeMap<>();
        for (IIsotope isotope : formula.isotopes()) {
            counts.merge(isotope.getSymbol(), (int) formula.getIsotopeCount(isotope), Integer::sum);
        }

        StringBuilder sb = new StringBuilder();
        if (counts.containsKey("C")) {
            appendElement(sb, "C", counts.remove("C"));
            if (counts.containsKey("H")) {
                appendElement(sb, "H", counts.remove("H"));
            }
        }
        counts.forEach((symbol, count) -> appendElement(sb, symbol, count));

        int charge = formula.getCharge();
        if (charge != 0) {
            if (Math.abs(charge) > 1) {
                sb.append(toSuperscript(Math.abs(charge)));
            }
            sb.append(charge > 0 ? "⁺" : "⁻");
        }
        return sb.toString();
    }

    /**
     * 追加一个元素及其计数（计数为 1 时省略下标）
     *
     * @param sb     目标字符串构建器
     * @param symbol 元素符号
     * @param count  原子数
     */
    private static void appendElement(StringBuilder sb, String symbol, int count) {
        sb.append(symbol);
        if (count > 1) {
            sb.append(toSubscript(count));
        }
    }

    /**
     * 数字转 Unicode 下标（0-9 → ₀-₉）
     *
     * @param number 数字
     * @return 下标字符串
     */
    private static String toSubscript(int number) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) {
            sb.append((char) (c - '0' + '\u2080'));
        }
        return sb.toString();
    }

    /**
     * 数字转 Unicode 上标（0-9 → ⁰¹²³⁴⁵⁶⁷⁸⁹）
     *
     * @param number 数字
     * @return 上标字符串
     */
    private static String toSuperscript(int number) {
        String[] superscripts = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"};
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(number).toCharArray()) {
            sb.append(superscripts[c - '0']);
        }
        return sb.toString();
    }
}
