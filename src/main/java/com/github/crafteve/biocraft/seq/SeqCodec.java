package com.github.crafteve.biocraft.seq;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 编解码器：程序文本 ↔ DNA 碱基序列（无损双射，信息层核心算法）
 * <p>
 * 编码：UTF-8 字节 → BigInteger → base-20 数字串 → 头部（魔数 1 密码子 +
 * 长度头 3 位 base-20）+ 内容（前导零补齐到固定宽度 W），每数字映射 1 个
 * 规范密码子（20 个全避终止子 → 序列天然不含终止密码子，翻译必然可读）
 * <p>
 * 性质（单测守护）：双向一致、双射（异文本必异链）、无终止子、容量上限、
 * 损坏可检测（魔数/长度头/非法规范密码子/非 UTF-8 均返回失败）
 */
public final class SeqCodec {

    /** 解码结果（ok=false 时 text 为空、error 为失败原因） */
    public record DecodeResult(boolean ok, String text, String error) {
        public static DecodeResult fail(String error) {
            return new DecodeResult(false, "", error);
        }

        public static DecodeResult ok(String text) {
            return new DecodeResult(true, text, "");
        }
    }

    /**
     * 程序文本 → DNA 碱基序列（5'→3'，T 编码；含魔数头 + 长度头）
     *
     * @param text 任意 UTF-8 文本（无输入限制）
     * @return DNA 序列
     * @throws IllegalArgumentException 文本超出容量上限
     */
    public static String encodeText(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SequenceConstants.MAX_BYTES) {
            throw new IllegalArgumentException("程序文本过长: " + bytes.length
                    + " 字节，上限 " + SequenceConstants.MAX_BYTES + " 字节");
        }
        String contentDigits = contentDigits(bytes);
        String head = toBase20Fixed(bytes.length, SequenceConstants.LENGTH_HEAD_DIGITS);
        String nucleotides = SequenceConstants.PROGRAM_MAGIC + mapDigitsToDna(head + contentDigits);
        if (nucleotides.length() > SequenceConstants.MAX_DNA_BP) {
            throw new IllegalArgumentException("编码后 DNA 超出长度上限");
        }
        return nucleotides;
    }

    /**
     * DNA 碱基序列 → 程序文本
     *
     * @param nucleotides DNA 序列（应含魔数头）
     * @return 解码结果（魔数不符/长度头非法/内容损坏/非 UTF-8 → ok=false）
     */
    public static DecodeResult decodeText(String nucleotides) {
        if (nucleotides == null || nucleotides.isEmpty() || nucleotides.length() % 3 != 0) {
            return DecodeResult.fail("非完整密码子序列");
        }
        if (!nucleotides.startsWith(SequenceConstants.PROGRAM_MAGIC)) {
            return DecodeResult.fail("非程序 DNA（缺少魔数头）");
        }
        String rest = nucleotides.substring(SequenceConstants.PROGRAM_MAGIC.length());
        int headLen = SequenceConstants.LENGTH_HEAD_DIGITS * 3;
        if (rest.length() < headLen) {
            return DecodeResult.fail("长度头不完整");
        }
        String headDigits = digitsFromCodons(rest.substring(0, headLen));
        if (headDigits == null) {
            return DecodeResult.fail("长度头非法");
        }
        long headValue = fromBase20(headDigits).longValue();
        if (headValue < 0 || headValue > SequenceConstants.MAX_BYTES) {
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
        // 右对齐拷贝：BigInteger 的最小补码表示可能带符号位/前导零
        int copy = Math.min(raw.length, n);
        System.arraycopy(raw, raw.length - copy, bytes, n - copy, copy);
        String text = decodeUtf8(bytes);
        if (text == null) {
            return DecodeResult.fail("内容不是合法 UTF-8");
        }
        return DecodeResult.ok(text);
    }

    /** 判断一段 DNA 是否为可解码的程序 DNA */
    public static boolean isProgramDna(String nucleotides) {
        return decodeText(nucleotides).ok();
    }

    /** 内容数字串（字节 → base-20，前导零补齐到固定宽度） */
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

    /** 指定字节数所需 base-20 数字位数 */
    static int widthForBytes(int byteCount) {
        return (int) Math.ceil(byteCount * 8.0 / SequenceConstants.LOG2_20);
    }

    /** BigInteger → base-20 数字串（0~19 → '0'~'9'/'a'~'j'） */
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

    /** 整数 → 固定位宽 base-20 数字串（超位宽抛异常） */
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

    /** base-20 数字串 → BigInteger */
    static BigInteger fromBase20(String digits) {
        BigInteger result = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(20);
        for (int i = 0; i < digits.length(); i++) {
            result = result.multiply(base).add(BigInteger.valueOf(fromDigitChar(digits.charAt(i))));
        }
        return result;
    }

    /** 数字串 → DNA 密码子串 */
    static String mapDigitsToDna(String digits) {
        StringBuilder sb = new StringBuilder(digits.length() * 3);
        for (int i = 0; i < digits.length(); i++) {
            sb.append(CodonTable.CANONICAL_DNA[fromDigitChar(digits.charAt(i))]);
        }
        return sb.toString();
    }

    /** DNA 密码子串 → 数字串（任一密码子非规范则返回 null） */
    static String digitsFromCodons(String codons) {
        StringBuilder sb = new StringBuilder(codons.length() / 3);
        for (int i = 0; i < codons.length(); i += 3) {
            Integer digit = CodonTable.CANONICAL_DNA_TO_DIGIT.get(codons.substring(i, i + 3));
            if (digit == null) {
                return null;
            }
            sb.append(toDigitChar(digit));
        }
        return sb.toString();
    }

    /** 字节 → UTF-8 文本（非法编码返回 null） */
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

    private SeqCodec() {
    }
}
