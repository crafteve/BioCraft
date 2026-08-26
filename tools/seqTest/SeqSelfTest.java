package seqTest;

import com.github.crafteve.biocraft.central.Codec;

import java.util.Random;

/**
 * 序列引擎独立单测（镜像 tools/engineTest 模式，纯 JDK 零依赖）
 * <p>
 * 编译：javac -encoding UTF-8 -cp build/classes/java/main -d tools/seqTest/out tools/seqTest/*.java
 * 运行：java -cp "build/classes/java/main;tools/seqTest/out" seqTest.SeqSelfTest
 * 退出码 0 = 全绿、1 = 有失败
 * <p>
 * 守护契约：编解码双向一致、双射（异文本必异链）、无终止子、容量边界、
 * 损坏可检测、SeqOps 互补正确、CodonTable 完整性与无终止
 */
public class SeqSelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testRoundtrip();
        testCapacity();
        testBijection();
        testNoStop();
        testDecodeFailures();
        testSeqOps();
        testCodonTable();
        testRandomRoundtrip();

        System.out.println("----");
        System.out.println("SeqSelfTest: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void testRoundtrip() {
        roundtrip("ATG");
        roundtrip("import 酶库; HK = 酶.HK; 修饰(kcat=0.9)");
        roundtrip("你好，BioCraft！中心法则信息层。123 abc !@#😀");
        roundtrip("");
        roundtrip("a".repeat(100));
        roundtrip("中".repeat(150)); // 450 字节，在新上限 538 字节内
    }

    private static void testCapacity() {
        String maxText = "a".repeat(Codec.MAX_BYTES);
        String enc = Codec.encodeText(maxText);
        check("上限文本可编码且不超 " + Codec.MAX_DNA_BP + "bp",
                enc.length() <= Codec.MAX_DNA_BP);
        check("上限文本往返一致", maxText.equals(Codec.decodeText(enc).text()));
        checkThrows("超上限编码抛异常", () -> Codec.encodeText("a".repeat(Codec.MAX_BYTES + 1)));
    }

    private static void testBijection() {
        String s1 = Codec.encodeText("A+B=C");
        String s2 = Codec.encodeText("A-B=C");
        check("双射：异文本必异链", !s1.equals(s2));
        check("解码 A+B=C", "A+B=C".equals(Codec.decodeText(s1).text()));
        check("解码 A-B=C", "A-B=C".equals(Codec.decodeText(s2).text()));
    }

    private static void testNoStop() {
        check("编码产物无终止子（A+B=C）", noStop(Codec.encodeText("A+B=C")));
        check("编码产物无终止子（中文+emoji）", noStop(Codec.encodeText("你好😀 中心法则")));
        check("编码产物无终止子（上限文本）", noStop(Codec.encodeText("a".repeat(Codec.MAX_BYTES))));
    }

    private static void testDecodeFailures() {
        check("null 解码失败", !Codec.decodeText(null).ok());
        check("空串解码失败", !Codec.decodeText("").ok());
        check("长度非 3 倍数失败", !Codec.decodeText("ATG").ok());
        check("非程序 DNA 失败", !Codec.decodeText("AAAGGGCCC").ok());

        String enc = Codec.encodeText("hello biocraft 测试 123");

        String badMagic = "GTA" + enc.substring(3);
        check("魔数破坏解码失败", !Codec.decodeText(badMagic).ok());

        check("截断解码失败", !Codec.decodeText(enc.substring(0, enc.length() - 3)).ok());

        StringBuilder headBroken = new StringBuilder(enc);
        char c = headBroken.charAt(3);
        headBroken.setCharAt(3, c == 'A' ? 'C' : 'A');
        check("长度头破坏解码失败", !Codec.decodeText(headBroken.toString()).ok());

        // 内容区单碱基翻转：改变一个数字 → 解码出不同文本（或非法 UTF-8）
        StringBuilder contentFlip = new StringBuilder(enc);
        int idx = 3 + 9; // 长度头之后的内容区
        char d = contentFlip.charAt(idx);
        contentFlip.setCharAt(idx, d == 'A' ? 'C' : 'A');
        Codec.DecodeResult r = Codec.decodeText(contentFlip.toString());
        check("内容突变解码不同或失败", !r.ok() || !"hello biocraft 测试 123".equals(r.text()));
    }

    private static void testSeqOps() {
        check("互补 A→T", Codec.complementDna('A') == 'T');
        check("互补 T→A", Codec.complementDna('T') == 'A');
        check("互补链 TACG", "TACG".equals(Codec.complementDna("ATGC")));
        check("反向互补 GCAT", "GCAT".equals(Codec.reverseComplement("ATGC")));
        check("mRNA 转换", "AUGCAU".equals(Codec.toMrna("ATGCAT")));
        check("DNA 合法", Codec.isValidDna("ATGC"));
        check("DNA 非法（含 U）", !Codec.isValidDna("ATGU"));
        check("RNA 合法", Codec.isValidRna("AUGC"));
        check("RNA 非法（含 T）", !Codec.isValidRna("AUGT"));
        checkThrows("非法碱基互补抛异常", () -> Codec.complementDna('U'));
    }

    private static void testCodonTable() {
        check("标准表恰好 64 条", Codec.STANDARD.size() == 64);
        check("Met = AUG 起始密码子", Codec.codonToAa("AUG") == 'M');
        check("Trp = UGG", Codec.codonToAa("UGG") == 'W');
        check("三个终止子识别", Codec.isStop("UAA") && Codec.isStop("UAG") && Codec.isStop("UGA"));
        check("非终止子不误判", !Codec.isStop("UUU"));
        check("规范 20 条全为有义密码子", canonicalNoStop());
        check("规范密码子全部互异（双射）", canonicalBijective());
        check("反查表 20 条", Codec.CANONICAL_DNA_TO_DIGIT.size() == 20);
        check("反查表一致性", Codec.CANONICAL_DNA_TO_DIGIT.get("GTT") == 19
                && Codec.CANONICAL_DNA_TO_DIGIT.get("GCT") == 0);
    }

    private static void testRandomRoundtrip() {
        Random rnd = new Random(20260818L);
        // 用码点池而非 char[] 池：emoji 是代理对，按码点取可保持配对完整
        int[] pool = "abcXYZ019!@# 中文字符混合测试😀\n\t".codePoints().toArray();
        for (int t = 0; t < 300; t++) {
            int len = rnd.nextInt(150);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.appendCodePoint(pool[rnd.nextInt(pool.length)]);
            }
            String text = sb.toString();
            String enc = Codec.encodeText(text);
            String dec = Codec.decodeText(enc).text();
            if (!text.equals(dec)) {
                fail("随机往返不一致 t=" + t + " len=" + len);
                return;
            }
        }
        passed++;
        System.out.println("  PASS 随机往返 300 例（含中文/emoji/空白）");
    }

    private static boolean canonicalNoStop() {
        for (String rna : Codec.CANONICAL_RNA) {
            if (Codec.isStop(rna)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canonicalBijective() {
        for (int i = 0; i < Codec.CANONICAL_DNA.length; i++) {
            for (int j = i + 1; j < Codec.CANONICAL_DNA.length; j++) {
                if (Codec.CANONICAL_DNA[i].equals(Codec.CANONICAL_DNA[j])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 编码产物的每个密码子（转 RNA 后）都不是终止密码子 */
    private static boolean noStop(String nucleotides) {
        String rna = Codec.toMrna(nucleotides);
        for (int i = 0; i + 3 <= rna.length(); i += 3) {
            if (Codec.isStop(rna.substring(i, i + 3))) {
                return false;
            }
        }
        return true;
    }

    private static void roundtrip(String text) {
        String enc = Codec.encodeText(text);
        String dec = Codec.decodeText(enc).text();
        if (!text.equals(dec)) {
            fail("往返不一致: " + text + " -> " + dec);
        } else {
            passed++;
            System.out.println("  PASS 往返 " + text.length() + " 字符 → " + enc.length() + "bp");
        }
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS " + name);
        } else {
            fail(name);
        }
    }

    private static void checkThrows(String name, Runnable r) {
        try {
            r.run();
            fail(name + "（未抛异常）");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            passed++;
            System.out.println("  PASS " + name);
        }
    }

    private static void fail(String name) {
        failed++;
        System.out.println("  FAIL " + name);
    }
}
