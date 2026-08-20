package programTest;

import com.github.crafteve.biocraft.program.ChemBalanceChecker;
import com.github.crafteve.biocraft.program.EnzymeProgram;
import com.github.crafteve.biocraft.program.EnzymeProgramParser;
import com.github.crafteve.biocraft.program.EnzymeProgramParser.ParseResult;
import com.github.crafteve.biocraft.program.ProgramError;
import com.github.crafteve.biocraft.program.ProgramErrorCode;
import com.github.crafteve.biocraft.program.ProgramField;
import com.github.crafteve.biocraft.program.SpeciesComposition;

import java.util.List;
import java.util.Map;

/**
 * 酶设计单解析/校验核心独立单测（镜像 engineTest/seqTest 模式）
 * <p>
 * 运行前先 gradlew build；再 javac -encoding UTF-8 -cp build/classes/java/main
 * -d tools/programTest/out tools/programTest/*.java；
 * java -cp "build/classes/java/main;tools/programTest/out" programTest.ProgramSelfTest
 * 退出码 0 = 全绿
 */
public class ProgramSelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testParseBasic();
        testDuplicateField();
        testUnknownField();
        testKcatFormat();
        testMissingId();
        testSpeciesLimit();
        testCommentsAndBlank();
        testCaseInsensitive();
        testValueWithColon();
        testChemBalance();
        System.out.println("ProgramSelfTest: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // 基础解析
    // ------------------------------------------------------------------

    private static void testParseBasic() {
        ParseResult r = EnzymeProgramParser.parse("id: HK\nname: 己糖激酶\n");
        check("合法程序无错误", r.errors().isEmpty());
        check("id 字段解析", "HK".equals(r.program().value(ProgramField.ID)));
        check("name 字段解析", "己糖激酶".equals(r.program().value(ProgramField.NAME)));
        check("id 行号 = 1", r.program().lineNumbers().get(ProgramField.ID) == 1);
        check("name 行号 = 2", r.program().lineNumbers().get(ProgramField.NAME) == 2);
    }

    private static void testDuplicateField() {
        ParseResult r = EnzymeProgramParser.parse("id: HK\nid: PK\nname: X\n");
        check("重复字段无错误（宽容）", r.errors().isEmpty());
        check("重复字段后者覆盖", "PK".equals(r.program().value(ProgramField.ID)));
        check("覆盖后行号取最新", r.program().lineNumbers().get(ProgramField.ID) == 2);
    }

    private static void testUnknownField() {
        ParseResult r = EnzymeProgramParser.parse("id: HK\nfoo: bar\n");
        check("未知字段报错", hasError(r, ProgramErrorCode.UNKNOWN_FIELD));
        ProgramError e = firstError(r, ProgramErrorCode.UNKNOWN_FIELD);
        check("未知字段行号 = 2", e.line() == 2);
        check("未知字段详情 = foo", "foo".equals(e.detail()));
    }

    private static void testKcatFormat() {
        check("kcat 合法 +20%", EnzymeProgramParser.parse("id: HK\nkcat: +20%").errors().isEmpty());
        check("kcat 合法 -50%", EnzymeProgramParser.parse("id: HK\nkcat: -50%").errors().isEmpty());
        ParseResult bad = EnzymeProgramParser.parse("id: HK\nkcat: 20\n");
        check("kcat 缺 % 报错", hasError(bad, ProgramErrorCode.BAD_VALUE));
        ParseResult bad2 = EnzymeProgramParser.parse("id: HK\nkcat: 20%\n");
        check("kcat 缺符号报错", hasError(bad2, ProgramErrorCode.BAD_VALUE));
    }

    private static void testMissingId() {
        ParseResult r = EnzymeProgramParser.parse("name: 己糖激酶\n");
        check("缺 id 报 MISSING_ID", hasError(r, ProgramErrorCode.MISSING_ID));
        check("缺 id 错误行号 = 0", firstError(r, ProgramErrorCode.MISSING_ID).line() == 0);
    }

    private static void testSpeciesLimit() {
        ParseResult ok = EnzymeProgramParser.parse("id: HK\ninput: mannose, galactose\n");
        check("input 2 个物种合法", ok.errors().isEmpty());
        check("input 列表解析", ok.program().inputList().size() == 2);
        ParseResult bad = EnzymeProgramParser.parse("id: HK\ninput: a, b, c\n");
        check("input 3 个物种报 TOO_MANY_SPECIES", hasError(bad, ProgramErrorCode.TOO_MANY_SPECIES));
        check("超限行号 = 2", firstError(bad, ProgramErrorCode.TOO_MANY_SPECIES).line() == 2);
    }

    private static void testCommentsAndBlank() {
        ParseResult r = EnzymeProgramParser.parse("# 注释\n\nid: HK\n   \nname: X\n");
        check("注释/空行跳过", r.errors().isEmpty());
        check("注释后字段正确", "HK".equals(r.program().value(ProgramField.ID)));
    }

    private static void testCaseInsensitive() {
        ParseResult r = EnzymeProgramParser.parse("ID: HK\nName: X\nKCAT: +10%\n");
        check("关键词大小写不敏感", r.errors().isEmpty());
        check("大写 ID 解析", "HK".equals(r.program().value(ProgramField.ID)));
        check("大写 KCAT 解析", "+10%".equals(r.program().value(ProgramField.KCAT)));
    }

    private static void testValueWithColon() {
        ParseResult r = EnzymeProgramParser.parse("id: HK\nname: 己糖:激酶\n");
        check("值含冒号合法", r.errors().isEmpty());
        check("值按第一个冒号分割", "己糖:激酶".equals(r.program().value(ProgramField.NAME)));
    }

    // ------------------------------------------------------------------
    // 化学守恒（假原子组成映射）
    // ------------------------------------------------------------------

    private static void testChemBalance() {
        // 葡萄糖 C6H12O6 + ATP C10H16N5O13P3 → G6P C6H13O9P + ADP C10H15N5O10P2
        SpeciesComposition glucose = comp("C", 6, "H", 12, "O", 6);
        SpeciesComposition atp = comp("C", 10, "H", 16, "N", 5, "O", 13, "P", 3);
        SpeciesComposition g6p = comp("C", 6, "H", 13, "O", 9, "P", 1);
        SpeciesComposition adp = comp("C", 10, "H", 15, "N", 5, "O", 10, "P", 2);
        boolean balanced = ChemBalanceChecker.isBalanced(
                List.of(new ChemBalanceChecker.ReactionTerm(glucose, 1),
                        new ChemBalanceChecker.ReactionTerm(atp, 1)),
                List.of(new ChemBalanceChecker.ReactionTerm(g6p, 1),
                        new ChemBalanceChecker.ReactionTerm(adp, 1)));
        check("HK 反应式守恒（假数据）", balanced);

        // 不平衡：产物少一个 O
        SpeciesComposition shortP = comp("C", 10, "H", 15, "N", 5, "O", 9, "P", 2);
        boolean unbalanced = ChemBalanceChecker.isBalanced(
                List.of(new ChemBalanceChecker.ReactionTerm(glucose, 1),
                        new ChemBalanceChecker.ReactionTerm(atp, 1)),
                List.of(new ChemBalanceChecker.ReactionTerm(g6p, 1),
                        new ChemBalanceChecker.ReactionTerm(shortP, 1)));
        check("缺 O 产物判定不平衡", !unbalanced);

        // 系数参与：2A + 1B → 1C（A={X:1}, B={X:1}, C={X:3}）
        boolean coeff = ChemBalanceChecker.isBalanced(
                List.of(new ChemBalanceChecker.ReactionTerm(comp("X", 1), 2),
                        new ChemBalanceChecker.ReactionTerm(comp("X", 1), 1)),
                List.of(new ChemBalanceChecker.ReactionTerm(comp("X", 3), 1)));
        check("系数参与守恒", coeff);

        // 元素种类不一致
        boolean elemDiff = ChemBalanceChecker.isBalanced(
                List.of(new ChemBalanceChecker.ReactionTerm(comp("C", 1), 1)),
                List.of(new ChemBalanceChecker.ReactionTerm(comp("O", 1), 1)));
        check("元素种类不一致判定不平衡", !elemDiff);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static SpeciesComposition comp(Object... kv) {
        java.util.Map<String, Integer> atoms = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            atoms.put((String) kv[i], Integer.parseInt(kv[i + 1].toString()));
        }
        return new SpeciesComposition(atoms);
    }

    private static boolean hasError(ParseResult r, ProgramErrorCode code) {
        return firstError(r, code) != null;
    }

    private static ProgramError firstError(ParseResult r, ProgramErrorCode code) {
        for (ProgramError e : r.errors()) {
            if (e.code() == code) {
                return e;
            }
        }
        return null;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }
}
