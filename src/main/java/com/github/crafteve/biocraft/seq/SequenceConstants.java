package com.github.crafteve.biocraft.seq;

/**
 * 序列机体系代码侧常量（纯数学地基，零 MC 依赖，可独立单测）
 * <p>
 * 与 KineticConstants 同定位：本类是信息层唯一的节奏/容量旋钮，
 * 修改游戏节奏只允许动本类
 */
public final class SequenceConstants {

    /** DNA 链长度上限（碱基数） */
    public static final int MAX_DNA_BP = 4096;

    /** 程序 DNA 魔数密码子（base-20 数字 19 = Val 的规范密码子，固定标识"程序 DNA"） */
    public static final String PROGRAM_MAGIC = "GTT";

    /** 长度头位数（base-20 三位，可表示 0~7999 字节） */
    public static final int LENGTH_HEAD_DIGITS = 3;

    /** log₂20：单个 base-20 数字携带的信息比特数 */
    public static final double LOG2_20 = Math.log(20.0) / Math.log(2.0);

    /** 步进频率（每 tick 一步），序列机唯一的"速率"旋钮，Phase 3/4 工程读速从这挂入 */
    public static final int STEP_TICKS = 1;

    /**
     * 4096 bp 下可容纳的最大程序字节数
     * <p>总密码子 = floor(4096/3) = 1365，扣除魔数 1 + 长度头 3，
     * 内容密码子 ≈ 1361，每密码子携带 log₂20 比特 → ≈ 735 字节
     */
    public static final int MAX_BYTES = (int) Math.floor(
            (MAX_DNA_BP / 3.0 - 1 - LENGTH_HEAD_DIGITS) * LOG2_20 / 8.0);

    private SequenceConstants() {
    }
}
