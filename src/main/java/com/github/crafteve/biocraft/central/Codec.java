package com.github.crafteve.biocraft.central;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中心法则编解码门面（原 seq/SeqCodec + CodonTable20 + SeqOps + SequenceConstants 合并）
 * <p>
 * 零 MC 依赖（只 import java.*），与 reaction 同门禁；对外 4 方法：
 * encode/decodeFromDna/Mrna/Polypeptide，均 String 进 DecodeResult 出，
 * 不碰 SequenceData NBT（NBT 已揉进 ModDataComponents 内 record）
 * <p>
 * 编码：UTF-8 → BigInteger → base20 → 20 规范密码子（避终止子）+ 魔数 GTT + 3 位长度头
 * 解码：剥 TATAAT/ATG/TTTTT 外包装后走同一 codec，Polypeptide 额外 aa1→规范密码子还原
 */
public final class Codec {

    /** 解码结果（ok=false 时 text 为空、error 为失败原因） */
    public record DecodeResult(boolean ok, String text, String error) {
        public static DecodeResult fail(String error) {
            return new DecodeResult(false, "", error);
        }

        public static DecodeResult ok(String text) {
            return new DecodeResult(true, text, "");
        }
    }

    // ------------------------------------------------------------------
    // 常量（原 SequenceConstants + SeqOps 常量私有化）
    // ------------------------------------------------------------------

    /** DNA 链长度上限（碱基数） */
    public static final int MAX_DNA_BP = 3000;

    /** 程序 DNA 魔数密码子（Val 的规范密码子 GTT，固定标识“程序 DNA”） */
    public static final String PROGRAM_MAGIC = "GTT";

    /** 长度头位数（base20 三位，可表示 0~7999 字节） */
    public static final int LENGTH_HEAD_DIGITS = 3;

    /** log2(20)：单个 base20 数字携带比特数 */
    public static final double LOG2_20 = Math.log(20.0) / Math.log(2.0);

    /** 3000 bp 下可容纳最大程序字节数（996 内容密码子 → 538 字节） */
    public static final int MAX_BYTES = (int) Math.floor(
            (MAX_DNA_BP / 3.0 - 1 - LENGTH_HEAD_DIGITS) * LOG2_20 / 8.0);

    /** 转录启动子（编码链 5'→3'，模板链 3'→5' 为 ATATTA） */
    public static final String PROMOTER_CODING = "TATAAT";
    public static final String PROMOTER_TEMPLATE = "ATATTA";
    /** 转录终止子（编码链 5'→3'，模板链 3'→5' 为 AAAAA） */
    public static final String TERMINATOR_CODING = "TTTTT";
    public static final String TERMINATOR_TEMPLATE = "AAAAA";
    /** 起始密码子（编码链 5'→3'，mRNA AUG）：程序 DNA 在正文前固定携带 */
    public static final String START_CODON_CODING = "ATG";

    // ------------------------------------------------------------------
    // 规范密码子表（20 条，私藏；T 版存盘，U 版现场换）
    // ------------------------------------------------------------------

    private static final String[] CANONICAL_DNA = {
            "GCT", "CGT", "AAT", "GAT", "TGT", "CAA", "GAA", "GGT",
            "CAT", "ATT", "CTT", "AAA", "ATG", "TTT", "CCT", "TCT",
            "ACT", "TGG", "TAT", "GTT"
    };

    private static final String[] CANONICAL_AA3 = {
            "Ala", "Arg", "Asn", "Asp", "Cys", "Gln", "Glu", "Gly",
            "His", "Ile", "Leu", "Lys", "Met", "Phe", "Pro", "Ser",
            "Thr", "Trp", "Tyr", "Val"
    };

    private static final char[] CANONICAL_AA1 = {
            'A', 'R', 'N', 'D', 'C', 'Q', 'E', 'G',
            'H', 'I', 'L', 'K', 'M', 'F', 'P', 'S',
            'T', 'W', 'Y', 'V'
    };

    private static final String[] STOP_CODONS = {"UAA", "UAG", "UGA"};

    private static final Map<String, Integer> CANONICAL_DNA_TO_DIGIT = buildCanonicalDigitMap();

    private static Map<String, Integer> buildCanonicalDigitMap() {
        Map<String, Integer> map = new LinkedHashMap<>(20);
        for (int i = 0; i < CANONICAL_DNA.length; i++) {
            if (map.put(CANONICAL_DNA[i], i) != null) {
                throw new IllegalStateException("规范密码子重复: " + CANONICAL_DNA[i]);
            }
        }
        // 断言 20 条互异且全不为终止子（现场 U 版判）
        for (String rna : canonicalRnaAll()) {
            if (isStop(rna)) {
                throw new IllegalStateException("规范密码子含终止子: " + rna);
            }
        }
        return map;
    }

    private static String[] canonicalRnaAll() {
        String[] rna = new String[CANONICAL_DNA.length];
        for (int i = 0; i < CANONICAL_DNA.length; i++) {
            rna[i] = CANONICAL_DNA[i].replace('T', 'U');
        }
        return rna;
    }

    // ------------------------------------------------------------------
    // 对外 4 门面
    // ------------------------------------------------------------------

    /**
     * 程序文本 → DNA 碱基序列（5'→3'，T 编码；含魔数头 + 长度头）
     *
     * @param text 任意 UTF-8 文本
     * @return DNA 序列
     * @throws IllegalArgumentException 超上限
     */
    public static String encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("程序文本过长: " + bytes.length + " 字节，上限 " + MAX_BYTES + " 字节");
        }
        String contentDigits = contentDigits(bytes);
        String head = toBase20Fixed(bytes.length, LENGTH_HEAD_DIGITS);
        String nucleotides = PROGRAM_MAGIC + mapDigitsToDna(head + contentDigits);
        if (nucleotides.length() > MAX_DNA_BP) {
            throw new IllegalArgumentException("编码后 DNA 超出长度上限");
        }
        return nucleotides;
    }

    /** DNA 链（含 TATAAT/ATG/TTTTT 外包装亦可）→ 程序文本 */
    public static DecodeResult decodeFromDna(String dna) {
        if (dna == null || dna.isEmpty()) {
            return DecodeResult.fail("空 DNA");
        }
        String core = stripWrapper(dna);
        return decodeCore(core);
    }

    /** mRNA 链（U 版）→ 程序文本（U→T 后复用 DNA 路径） */
    public static DecodeResult decodeFromMrna(String mrna) {
        if (mrna == null || mrna.isEmpty()) {
            return DecodeResult.fail("空 mRNA");
        }
        String dnaEquiv = mrna.replace('U', 'T');
        return decodeFromDna(dnaEquiv);
    }

    /** 多肽链（1 字母 aa 串，首位 M 为起始密码子）→ 程序文本（aa→规范密码子还原） */
    public static DecodeResult decodeFromPolypeptide(String aaSeq) {
        if (aaSeq == null || aaSeq.isEmpty()) {
            return DecodeResult.fail("空多肽");
        }
        StringBuilder codons = new StringBuilder(aaSeq.length() * 3);
        for (int i = 0; i < aaSeq.length(); i++) {
            String codon = aa1ToCanonicalDna(aaSeq.charAt(i));
            if (codon == null) {
                return DecodeResult.fail("含未知氨基酸: " + aaSeq.charAt(i));
            }
            codons.append(codon);
        }
        return decodeFromDna(codons.toString());
    }

    /** 剥外包装：去启动子 TATAAT / 起始 ATG / 终止 TTTTT（任一存在即剥，不存在保持原样） */
    public static String stripWrapper(String dna) {
        String core = dna;
        if (core.startsWith(PROMOTER_CODING) && core.endsWith(TERMINATOR_CODING)
                && core.length() > PROMOTER_CODING.length() + TERMINATOR_CODING.length()) {
            core = core.substring(PROMOTER_CODING.length(), core.length() - TERMINATOR_CODING.length());
        }
        // 起始密码子兼容：程序 DNA 在正文前固定带 ATG（多肽首 M），剥掉再解
        if (core.startsWith(START_CODON_CODING)) {
            // 先试不剥，失败再剥——与 SequenceItem.tryDecodeProgram 同策略，保证旧链兼容
            DecodeResult direct = decodeCore(core);
            if (!direct.ok()) {
                DecodeResult stripped = decodeCore(core.substring(3));
                if (stripped.ok()) {
                    return core.substring(3);
                }
            }
        }
        return core;
    }

    /** 判断一段 DNA 是否为可解码的程序 DNA（剥包装后可解） */
    public static boolean isProgramDna(String nucleotides) {
        return decodeFromDna(nucleotides).ok();
    }

    // ------------------------------------------------------------------
    // 内部编解码（原 SeqCodec 私有逻辑下沉）
    // ------------------------------------------------------------------

    private static DecodeResult decodeCore(String nucleotides) {
        if (nucleotides == null || nucleotides.isEmpty() || nucleotides.length() % 3 != 0) {
            return DecodeResult.fail("非完整密码子序列");
        }
        if (!nucleotides.startsWith(PROGRAM_MAGIC)) {
            return DecodeResult.fail("非程序 DNA（缺少魔数头）");
        }
        String rest = nucleotides.substring(PROGRAM_MAGIC.length());
        int headLen = LENGTH_HEAD_DIGITS * 3;
        if (rest.length() < headLen) {
            return DecodeResult.fail("长度头不完整");
        }
        String headDigits = digitsFromCodons(rest.substring(0, headLen));
        if (headDigits == null) {
            return DecodeResult.fail("长度头非法");
        }
        long headValue = fromBase20(headDigits).longValue();
        if (headValue < 0 || headValue > MAX_BYTES) {
            return DecodeResult.fail("长度头非法");
        }
        int n = (int) headValue;
        int width = widthForBytes(n);
        String content = rest.substring(headLen);
        if (content.length() < width * 3) {
            return DecodeResult.fail("内容长度不足");
        }
        String contentDigits = digitsFromCodons(content.substring(0, width * 3));
        if (contentDigits == null) {
            return DecodeResult.fail("内容含非法规范密码子");
        }
        BigInteger big = fromBase20(contentDigits);
        byte[] bytes = new byte[n];
        byte[] raw = big.toByteArray();
        int copy = Math.min(raw.length, n);
        System.arraycopy(raw, raw.length - copy, bytes, n - copy, copy);
        String text = decodeUtf8(bytes);
        if (text == null) {
            return DecodeResult.fail("内容不是合法 UTF-8");
        }
        return DecodeResult.ok(text);
    }

    private static String contentDigits(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        }
        String raw = toBase20(new BigInteger(1, bytes));
        int width = widthForBytes(bytes.length);
        StringBuilder sb = new StringBuilder(width);
        for (int i = raw.length(); i < width; i++) {
            sb.append('0');
        }
        sb.append(raw);
        return sb.toString();
    }

    static int widthForBytes(int byteCount) {
        return (int) Math.ceil(byteCount * 8.0 / LOG2_20);
    }

    static String toBase20(BigInteger value) {
        if (value.signum() == 0) {
            return "0";
        }
        BigInteger base = BigInteger.valueOf(20);
        StringBuilder sb = new StringBuilder();
        BigInteger v = value;
        while (v.signum() > 0) {
            BigInteger[] qr = v.divideAndRemainder(base);
            sb.append(toDigitChar(qr[1].intValue()));
            v = qr[0];
        }
        return sb.reverse().toString();
    }

    static String toBase20Fixed(int value, int digits) {
        String raw = toBase20(BigInteger.valueOf(value));
        if (raw.length() > digits) {
            throw new IllegalArgumentException("数值超出 " + digits + " 位 base-20 表示范围: " + value);
        }
        StringBuilder sb = new StringBuilder(digits);
        for (int i = raw.length(); i < digits; i++) {
            sb.append('0');
        }
        sb.append(raw);
        return sb.toString();
    }

    static BigInteger fromBase20(String digits) {
        BigInteger result = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(20);
        for (int i = 0; i < digits.length(); i++) {
            result = result.multiply(base).add(BigInteger.valueOf(fromDigitChar(digits.charAt(i))));
        }
        return result;
    }

    static String mapDigitsToDna(String digits) {
        StringBuilder sb = new StringBuilder(digits.length() * 3);
        for (int i = 0; i < digits.length(); i++) {
            sb.append(CANONICAL_DNA[fromDigitChar(digits.charAt(i))]);
        }
        return sb.toString();
    }

    static String digitsFromCodons(String codons) {
        StringBuilder sb = new StringBuilder(codons.length() / 3);
        for (int i = 0; i < codons.length(); i += 3) {
            Integer digit = CANONICAL_DNA_TO_DIGIT.get(codons.substring(i, i + 3));
            if (digit == null) {
                return null;
            }
            sb.append(toDigitChar(digit));
        }
        return sb.toString();
    }

    static String decodeUtf8(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static char toDigitChar(int digit) {
        if (digit < 10) {
            return (char) ('0' + digit);
        }
        return (char) ('a' + digit - 10);
    }

    static int fromDigitChar(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'j') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException("非法 base-20 数字: " + c);
    }

    // ------------------------------------------------------------------
    // 密码子/氨基酸辅助（私藏，供 Polypeptide 解码用）
    // ------------------------------------------------------------------

    private static String aa1ToCanonicalDna(char aa1) {
        for (int i = 0; i < CANONICAL_AA1.length; i++) {
            if (CANONICAL_AA1[i] == aa1) {
                return CANONICAL_DNA[i];
            }
        }
        return null;
    }

    private static boolean isStop(String rnaCodon) {
        for (String s : STOP_CODONS) {
            if (s.equals(rnaCodon)) {
                return true;
            }
        }
        return false;
    }

    /** DNA 互补（A↔T,C↔G） */
    public static char complementDna(char base) {
        return switch (base) {
            case 'A' -> 'T';
            case 'T' -> 'A';
            case 'C' -> 'G';
            case 'G' -> 'C';
            default -> throw new IllegalArgumentException("非 DNA 碱基: " + base);
        };
    }

    public static String complementDna(String seq) {
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = 0; i < seq.length(); i++) {
            sb.append(complementDna(seq.charAt(i)));
        }
        return sb.toString();
    }

    public static String reverseComplement(String seq) {
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = seq.length() - 1; i >= 0; i--) {
            sb.append(complementDna(seq.charAt(i)));
        }
        return sb.toString();
    }

    public static String toMrna(String dna) {
        return dna.replace('T', 'U');
    }

    public static boolean isValidDna(String seq) {
        if (seq == null || seq.isEmpty()) {
            return false;
        }
        for (int i = 0; i < seq.length(); i++) {
            char c = seq.charAt(i);
            if (c != 'A' && c != 'C' && c != 'G' && c != 'T') {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidRna(String seq) {
        if (seq == null || seq.isEmpty()) {
            return false;
        }
        for (int i = 0; i < seq.length(); i++) {
            char c = seq.charAt(i);
            if (c != 'A' && c != 'C' && c != 'G' && c != 'U') {
                return false;
            }
        }
        return true;
    }

    private Codec() {
    }
}
