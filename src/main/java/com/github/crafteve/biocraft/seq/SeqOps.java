package com.github.crafteve.biocraft.seq;

/**
 * 序列字符串运算工具箱（纯函数，零依赖）
 * <p>
 * 承载"碱基互补配对"这一教学核心的纯计算：DNA 互补、反向互补、T→U 转录
 */
public final class SeqOps {

    /** DNA 互补碱基（A↔T, C↔G） */
    public static char complementDna(char base) {
        return switch (base) {
            case 'A' -> 'T';
            case 'T' -> 'A';
            case 'C' -> 'G';
            case 'G' -> 'C';
            default -> throw new IllegalArgumentException("非 DNA 碱基: " + base);
        };
    }

    /** DNA 整链互补（5'→3' 同向） */
    public static String complementDna(String seq) {
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = 0; i < seq.length(); i++) {
            sb.append(complementDna(seq.charAt(i)));
        }
        return sb.toString();
    }

    /** 反向互补（双链新链的 5'→3' 方向与模板相反） */
    public static String reverseComplement(String seq) {
        StringBuilder sb = new StringBuilder(seq.length());
        for (int i = seq.length() - 1; i >= 0; i--) {
            sb.append(complementDna(seq.charAt(i)));
        }
        return sb.toString();
    }

    /** DNA → mRNA（T→U） */
    public static String toMrna(String dna) {
        return dna.replace('T', 'U');
    }

    /** 转录启动子（编码链 5'→3'，模板链 3'→5' 为其互补 ATATTA） */
    public static final String PROMOTER_CODING = "TATAAT";
    /** 转录启动子在模板链 3'→5' 上的互补序列 */
    public static final String PROMOTER_TEMPLATE = "ATATTA";
    /** 转录终止子（编码链 5'→3'，模板链 3'→5' 为 AAAAA） */
    public static final String TERMINATOR_CODING = "TTTTT";
    /** 终止子在模板链 3'→5' 上的互补序列 */
    public static final String TERMINATOR_TEMPLATE = "AAAAA";

    /** DNA 序列合法性（非空 + 仅 ACGT） */
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

    /** RNA 序列合法性（非空 + 仅 ACGU） */
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

    private SeqOps() {
    }
}
