package com.github.crafteve.biocraft.item;

/**
 * 序列物品载荷（纯数据 record，DataComponent 的 seq 侧映射对象）
 * <p>
 * 字段：
 * <ul>
 *   <li>type —— 聚合物类型（DNA/mRNA/多肽）</li>
 *   <li>strand —— 单/双链（仅 DNA 有意义；ds/ss 用物品 id 区分，本字段为内容标注）</li>
 *   <li>kind —— 程序 DNA 或基因 DNA（决定翻译/折叠的扫描与解码策略）</li>
 *   <li>seq —— 碱基串（DNA 用 T、mRNA 用 U、多肽用 1 字母氨基酸码）</li>
 *   <li>complete —— 多肽是否完整（半成品 = false，折叠机拒绝）</li>
 * </ul>
 */
public record SequenceData(SeqType type, Strand strand, Kind kind, String seq, boolean complete) {

    public enum SeqType { DNA, MRNA, POLYPEPTIDE }

    public enum Strand { SS, DS }

    public enum Kind { PROGRAM, GENE }

    public SequenceData {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind 不能为 null");
        }
        if (seq == null) {
            throw new IllegalArgumentException("seq 不能为 null");
        }
        if (type == SeqType.DNA && strand == null) {
            throw new IllegalArgumentException("DNA 必须有 strand");
        }
    }
}
