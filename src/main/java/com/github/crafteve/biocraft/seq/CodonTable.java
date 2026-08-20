package com.github.crafteve.biocraft.seq;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 密码子表（代码常量而非数据表——遗传密码是冻结的科学事实
 * （Crick"冻结的意外"），20 个规范密码子为固定设计规格，见设计稿 4.3）
 * <p>
 * 三张表：
 * <ul>
 *   <li>STANDARD —— 标准遗传密码（RNA 密码子 → 1 字母氨基酸，'*' = 终止）</li>
 *   <li>CANONICAL_DNA / CANONICAL_RNA —— base-20 数字 0~19 → 规范密码子
 *       （DNA 存储用 T、RNA 翻译用 U；20 个全部避开终止密码子）</li>
 *   <li>CANONICAL_AA3 / CANONICAL_AA1 —— base-20 数字 → 氨基酸（3/1 字母）</li>
 * </ul>
 * 类初始化即断言校验（标准表完整、规范密码子无终止、双射），失败快速失败
 */
public final class CodonTable {

    /** 标准遗传密码（RNA 密码子 → 1 字母氨基酸；'*' 表示终止） */
    public static final Map<String, Character> STANDARD = buildStandard();

    /** base-20 数字 → 规范 DNA 密码子（物品存储用，T） */
    public static final String[] CANONICAL_DNA = {
            "GCT", "CGT", "AAT", "GAT", "TGT", "CAA", "GAA", "GGT",
            "CAT", "ATT", "CTT", "AAA", "ATG", "TTT", "CCT", "TCT",
            "ACT", "TGG", "TAT", "GTT"
    };

    /** base-20 数字 → 规范 RNA 密码子（翻译用，U） */
    public static final String[] CANONICAL_RNA = {
            "GCU", "CGU", "AAU", "GAU", "UGU", "CAA", "GAA", "GGU",
            "CAU", "AUU", "CUU", "AAA", "AUG", "UUU", "CCU", "UCU",
            "ACU", "UGG", "UAU", "GUU"
    };

    /** base-20 数字 → 3 字母氨基酸 */
    public static final String[] CANONICAL_AA3 = {
            "Ala", "Arg", "Asn", "Asp", "Cys", "Gln", "Glu", "Gly",
            "His", "Ile", "Leu", "Lys", "Met", "Phe", "Pro", "Ser",
            "Thr", "Trp", "Tyr", "Val"
    };

    /** base-20 数字 → 1 字母氨基酸 */
    public static final char[] CANONICAL_AA1 = {
            'A', 'R', 'N', 'D', 'C', 'Q', 'E', 'G',
            'H', 'I', 'L', 'K', 'M', 'F', 'P', 'S',
            'T', 'W', 'Y', 'V'
    };

    /** 规范 DNA 密码子 → base-20 数字（编解码反查表） */
    public static final Map<String, Integer> CANONICAL_DNA_TO_DIGIT = buildCanonicalDigitMap();

    /** 终止密码子集合（RNA） */
    public static final String[] STOP_CODONS = {"UAA", "UAG", "UGA"};

    /** 密码子 → 1 字母氨基酸（'*' = 终止；未知密码子抛异常） */
    public static char codonToAa(String rnaCodon) {
        Character aa = STANDARD.get(rnaCodon);
        if (aa == null) {
            throw new IllegalArgumentException("未知密码子: " + rnaCodon);
        }
        return aa;
    }

    /** 是否终止密码子 */
    public static boolean isStop(String rnaCodon) {
        return codonToAa(rnaCodon) == '*';
    }

    private static Map<String, Character> buildStandard() {
        Map<String, Character> map = new LinkedHashMap<>(64);
        map.put("UUU", 'F'); map.put("UUC", 'F'); map.put("UUA", 'L'); map.put("UUG", 'L');
        map.put("UCU", 'S'); map.put("UCC", 'S'); map.put("UCA", 'S'); map.put("UCG", 'S');
        map.put("UAU", 'Y'); map.put("UAC", 'Y'); map.put("UAA", '*'); map.put("UAG", '*');
        map.put("UGU", 'C'); map.put("UGC", 'C'); map.put("UGA", '*'); map.put("UGG", 'W');
        map.put("CUU", 'L'); map.put("CUC", 'L'); map.put("CUA", 'L'); map.put("CUG", 'L');
        map.put("CCU", 'P'); map.put("CCC", 'P'); map.put("CCA", 'P'); map.put("CCG", 'P');
        map.put("CAU", 'H'); map.put("CAC", 'H'); map.put("CAA", 'Q'); map.put("CAG", 'Q');
        map.put("CGU", 'R'); map.put("CGC", 'R'); map.put("CGA", 'R'); map.put("CGG", 'R');
        map.put("AUU", 'I'); map.put("AUC", 'I'); map.put("AUA", 'I'); map.put("AUG", 'M');
        map.put("ACU", 'T'); map.put("ACC", 'T'); map.put("ACA", 'T'); map.put("ACG", 'T');
        map.put("AAU", 'N'); map.put("AAC", 'N'); map.put("AAA", 'K'); map.put("AAG", 'K');
        map.put("AGU", 'S'); map.put("AGC", 'S'); map.put("AGA", 'R'); map.put("AGG", 'R');
        map.put("GUU", 'V'); map.put("GUC", 'V'); map.put("GUA", 'V'); map.put("GUG", 'V');
        map.put("GCU", 'A'); map.put("GCC", 'A'); map.put("GCA", 'A'); map.put("GCG", 'A');
        map.put("GAU", 'D'); map.put("GAC", 'D'); map.put("GAA", 'E'); map.put("GAG", 'E');
        map.put("GGU", 'G'); map.put("GGC", 'G'); map.put("GGA", 'G'); map.put("GGG", 'G');
        if (map.size() != 64) {
            throw new IllegalStateException("标准遗传密码表必须恰好 64 条: " + map.size());
        }
        return map;
    }

    private static Map<String, Integer> buildCanonicalDigitMap() {
        Map<String, Integer> map = new LinkedHashMap<>(20);
        for (int i = 0; i < CANONICAL_DNA.length; i++) {
            if (!STANDARD.containsKey(CANONICAL_RNA[i]) || isStop(CANONICAL_RNA[i])) {
                throw new IllegalStateException("规范密码子非法或为终止密码子: " + CANONICAL_RNA[i]);
            }
            if (map.put(CANONICAL_DNA[i], i) != null) {
                throw new IllegalStateException("规范密码子重复: " + CANONICAL_DNA[i]);
            }
        }
        return map;
    }

    private CodonTable() {
    }
}
