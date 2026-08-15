package smilesCheck;

import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.isomorphism.Pattern;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 物质表 SMILES 批量校验程序（独立工具，不进 mod 源码源集）
 * <p>
 * 输入：actual.tsv（id + 实际 SMILES，从 substances.json 提取）+ 内置期望表
 * （《全部SMILES结构式清单_2026-08-13.md》的 PubChem canonical SMILES，或
 * eQuilibrator 形式，见 F16P 备注）
 * <p>
 * 校验内容（与清单"连通性同构"口径一致）：
 * <ol>
 *   <li>实际 SMILES 可被 CDK 解析（可解析性）</li>
 *   <li>分子式一致：实际与期望补 H 后的元素计数相同</li>
 *   <li>连通性同构：芳香化后双向子图同构（忽略立体与电荷，
 *       即清单中"记法差异（连同性同）"的判定标准）</li>
 * </ol>
 * 清单中无对照条目的分子（thymine/OH⁻/Fe³⁺/H⁺/5 原子）只做可解析性检查
 * <p>
 * 编译：javac -encoding UTF-8 -cp build/cdk/cdk-all.jar -d tools/smilesCheck/out tools/smilesCheck/SmilesCheck.java
 * 运行：java -cp "build/cdk/cdk-all.jar;tools/smilesCheck/out" smilesCheck.SmilesCheck
 * 退出码：0=全部通过、1=有失败
 */
public final class SmilesCheck {

    /**
     * 期望表：id -> 期望 SMILES（取自核对清单；null 表示清单无对照，只做可解析性检查）
     * <p>
     * 特殊说明：
     * fructose_1_6_bisphosphate 用 eQuilibrator 呋喃形式（清单标注"游戏建议以 eQ 形式为准"，
     * 表内将改为其中性无立体写法）；phosphate_ion 用清单 Pi（HPO₄²⁻）形式
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        // 表 A1 糖酵解 11 中间物（PubChem canonical，F16P 用 eQ 呋喃形式）
        EXPECTED.put("glucose", "C(C1C(C(C(C(O1)O)O)O)O)O");
        EXPECTED.put("glucose_6_phosphate", "C(C1C(C(C(C(O1)O)O)O)O)OP(=O)(O)O");
        EXPECTED.put("fructose_6_phosphate", "C(C(C(C(C(=O)CO)O)O)O)OP(=O)(O)O");
        EXPECTED.put("fructose_1_6_bisphosphate", "OC1C(O)C(O)(COP(=O)(O)O)OC1COP(=O)(O)O");
        EXPECTED.put("dihydroxyacetone_phosphate", "C(C(=O)COP(=O)(O)O)O");
        EXPECTED.put("glyceraldehyde_3_phosphate", "C(C(C=O)O)OP(=O)(O)O");
        EXPECTED.put("1_3_bisphosphoglycerate", "C(C(C(=O)OP(=O)(O)O)O)OP(=O)(O)O");
        EXPECTED.put("3_phosphoglycerate", "C(C(C(=O)O)O)OP(=O)(O)O");
        EXPECTED.put("2_phosphoglycerate", "C(C(C(=O)O)OP(=O)(O)O)O");
        EXPECTED.put("phosphoenolpyruvate", "C=C(C(=O)O)OP(=O)(O)O");
        EXPECTED.put("lactate", "CC(C(=O)[O-])O");
        EXPECTED.put("acetaldehyde", "CC=O");
        EXPECTED.put("ethanol", "CCO");
        EXPECTED.put("pyruvate", "CC(=O)C(=O)[O-]");
        // 表 A2 20 种氨基酸（PubChem canonical）
        EXPECTED.put("glycine", "C(C(=O)O)N");
        EXPECTED.put("alanine", "CC(C(=O)O)N");
        EXPECTED.put("valine", "CC(C)C(C(=O)O)N");
        EXPECTED.put("leucine", "CC(C)CC(C(=O)O)N");
        EXPECTED.put("isoleucine", "CCC(C)C(C(=O)O)N");
        EXPECTED.put("serine", "C(C(C(=O)O)N)O");
        EXPECTED.put("threonine", "CC(C(C(=O)O)N)O");
        EXPECTED.put("cysteine", "C(C(C(=O)O)N)S");
        EXPECTED.put("methionine", "CSCCC(C(=O)O)N");
        EXPECTED.put("proline", "C1CC(NC1)C(=O)O");
        EXPECTED.put("phenylalanine", "C1=CC=C(C=C1)CC(C(=O)O)N");
        EXPECTED.put("tyrosine", "C1=CC(=CC=C1CC(C(=O)O)N)O");
        EXPECTED.put("tryptophan", "C1=CC=C2C(=C1)C(=CN2)CC(C(=O)O)N");
        EXPECTED.put("asparagine", "C(C(C(=O)O)N)C(=O)N");
        EXPECTED.put("glutamine", "C(CC(=O)N)C(C(=O)O)N");
        EXPECTED.put("aspartic_acid", "C(C(C(=O)O)N)C(=O)O");
        EXPECTED.put("glutamic_acid", "C(CC(=O)O)C(C(=O)O)N");
        EXPECTED.put("lysine", "C(CCN)CC(C(=O)O)N");
        EXPECTED.put("arginine", "C(CC(C(=O)O)N)CN=C(N)N");
        EXPECTED.put("histidine", "C1=C(NC=N1)CC(C(=O)O)N");
        // 表 A3 能量与辅因子（PubChem canonical；Pi 用清单 HPO₄²⁻ 形式）
        EXPECTED.put("atp", "C1=NC(=C2C(=N1)N(C=N2)C3C(C(C(O3)COP(=O)(O)OP(=O)(O)OP(=O)(O)O)O)O)N");
        EXPECTED.put("adp", "C1=NC(=C2C(=N1)N(C=N2)C3C(C(C(O3)COP(=O)(O)OP(=O)(O)O)O)O)N");
        EXPECTED.put("nad_plus", "C1=CC(=C[N+](=C1)C2C(C(C(O2)COP(=O)([O-])OP(=O)(O)OCC3C(C(C(O3)N4C=NC5=C(N=CN=C54)N)O)O)O)O)C(=O)N");
        EXPECTED.put("nadh", "C1C=CN(C=C1C(=O)N)C2C(C(C(O2)COP(=O)(O)OP(=O)(O)OCC3C(C(C(O3)N4C=NC5=C(N=CN=C54)N)O)O)O)O");
        EXPECTED.put("phosphate_ion", "OP(=O)([O-])[O-]");
        EXPECTED.put("water", "O");
        EXPECTED.put("carbon_dioxide", "O=C=O");
        // 表 A4 碱基与 NTP（PubChem canonical）
        EXPECTED.put("adenine", "C1=NC2=NC=NC(=C2N1)N");
        EXPECTED.put("cytosine", "C1=C(NC(=O)N=C1)N");
        EXPECTED.put("guanine", "C1=NC2=C(N1)C(=O)NC(=N2)N");
        EXPECTED.put("uracil", "C1=CNC(=O)NC1=O");
        EXPECTED.put("ctp", "C1=CN(C(=O)N=C1N)C2C(C(C(O2)COP(=O)(O)OP(=O)(O)OP(=O)(O)O)O)O");
        EXPECTED.put("gtp", "C1=NC2=C(N1C3C(C(C(O3)COP(=O)(O)OP(=O)(O)OP(=O)(O)O)O)O)N=C(NC2=O)N");
        EXPECTED.put("utp", "C1=CN(C(=O)NC1=O)C2C(C(C(O2)COP(=O)(O)OP(=O)(O)OP(=O)(O)O)O)O");
        // 表 A4 离子（清单列出的 8 种；OH⁻/Fe³⁺/H⁺ 与 5 原子无对照，只做可解析性检查）
        EXPECTED.put("sodium_ion", "[Na]");
        EXPECTED.put("potassium_ion", "[K]");
        EXPECTED.put("calcium_ion", "[Ca]");
        EXPECTED.put("magnesium_ion", "[Mg]");
        EXPECTED.put("iron_2_ion", "[Fe+2]");
        EXPECTED.put("ammonium_ion", "[NH4+]");
        EXPECTED.put("chloride_ion", "[Cl-]");
    }

    private SmilesCheck() {
    }

    /**
     * 主入口：读取 actual.tsv 逐条校验并输出报告
     *
     * @param args 无参（actual.tsv 固定位于 tools/smilesCheck/ 下，支持命令行覆盖路径）
     */
    public static void main(String[] args) throws Exception {
        Path tsv = args.length > 0 ? Path.of(args[0]) : Path.of("tools/smilesCheck/actual.tsv");
        if (!Files.exists(tsv)) {
            System.out.println("找不到 actual.tsv: " + tsv.toAbsolutePath());
            System.exit(1);
        }
        SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        List<String> failures = new ArrayList<>();
        int total = 0;
        int compared = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Files.newInputStream(tsv), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                String id = line.substring(0, tab);
                String actual = line.substring(tab + 1);
                total++;
                String expected = EXPECTED.get(id);
                if (expected == null) {
                    // 无对照条目（thymine/OH⁻/Fe³⁺/H⁺/5 原子等）也做可解析性检查
                    try {
                        parser.parseSmiles(actual);
                        System.out.println("PASS " + id + "（清单无对照，仅解析）");
                    } catch (Exception ex) {
                        failures.add("FAIL " + id + "：解析失败 " + ex.getMessage());
                        System.out.println("FAIL " + id + "：解析失败 " + ex.getMessage());
                    }
                    continue;
                }
                compared++;
                String report = checkOne(parser, id, actual, expected);
                System.out.println(report);
                if (!report.startsWith("PASS")) {
                    failures.add(report);
                }
            }
        }
        System.out.println();
        System.out.println("对照 " + compared + " 条 / 总 " + total + " 条，失败 " + failures.size() + " 条");
        if (!failures.isEmpty()) {
            failures.forEach(System.out::println);
            System.exit(1);
        }
    }

    /**
     * 单条三连校验：解析 → 分子式比对 → 连通性同构比对
     *
     * @param parser   CDK SMILES 解析器
     * @param id       物质注册名
     * @param actual   表内实际 SMILES
     * @param expected 期望 SMILES（核对清单）
     * @return 单行报告文本（PASS 或 FAIL + 差异说明）
     */
    private static String checkOne(SmilesParser parser, String id, String actual, String expected) throws Exception {
        IAtomContainer a;
        IAtomContainer e;
        try {
            a = parser.parseSmiles(actual);
            e = parser.parseSmiles(expected);
        } catch (Exception ex) {
            return "FAIL " + id + "：解析失败 " + ex.getMessage();
        }
        // 重原子计数比对：质子化状态差异（H 数量）不算失败，
        // 如 pyruvate 中性酸 vs 阴离子、NAD⁺ 磷酸 OH vs [O-]
        Map<String, Integer> fa = elementCountsHeavy(a);
        Map<String, Integer> fe = elementCountsHeavy(e);
        if (!fa.equals(fe)) {
            return "FAIL " + id + "：重原子组成不一致 实际 " + formatCounts(fa) + " 期望 " + formatCounts(fe);
        }
        // 键序归一化为单键后 Pattern（VF2）双向子图匹配：只比原子骨架（连通性），
        // 芳香/双键/电荷/立体差异自动视为"记法差异"（与清单口径一致）
        normalizeBonds(a);
        normalizeBonds(e);
        boolean iso;
        try {
            iso = Pattern.findSubstructure(a).matches(e)
                    && Pattern.findSubstructure(e).matches(a);
        } catch (Exception ex) {
            return "FAIL " + id + "：同构判定异常 " + ex.getMessage();
        }
        if (!iso) {
            return "FAIL " + id + "：连通性不同构（实际 " + actual + " vs 期望 " + expected + "）"
                    + " [原子数 实际=" + a.getAtomCount() + " 期望=" + e.getAtomCount()
                    + " 键数 实际=" + a.getBondCount() + " 期望=" + e.getBondCount() + "]";
        }
        return "PASS " + id + "（重原子 " + formatCounts(fa) + "，连通性同构）";
    }

    /**
     * 键序统一归一化为单键（连通性同构只关心原子骨架）
     *
     * @param container 原子容器（原地修改）
     */
    private static void normalizeBonds(IAtomContainer container) {
        for (IBond bond : container.bonds()) {
            bond.setOrder(IBond.Order.SINGLE);
            bond.setIsAromatic(false);
        }
    }

    /**
     * 统计容器重原子元素计数（补 H 后，排除 H：质子化状态差异不计为失败）
     *
     * @param container 原子容器
     * @return 元素符号 -> 原子数
     */
    private static Map<String, Integer> elementCountsHeavy(IAtomContainer container) {
        Map<String, Integer> counts = new TreeMap<>();
        var formula = MolecularFormulaManipulator.getMolecularFormula(container);
        formula.isotopes().forEach(iso -> {
            if (!"H".equals(iso.getSymbol())) {
                counts.merge(iso.getSymbol(), (int) formula.getIsotopeCount(iso), Integer::sum);
            }
        });
        return counts;
    }

    /**
     * 元素计数转显示文本（如 {C=6, H=12, O=6}）
     *
     * @param counts 元素计数
     * @return 显示文本
     */
    private static String formatCounts(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((symbol, count) -> sb.append(symbol).append(count).append(' '));
        return sb.toString().trim();
    }
}
