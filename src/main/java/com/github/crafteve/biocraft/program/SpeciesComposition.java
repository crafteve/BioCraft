package com.github.crafteve.biocraft.program;

import java.util.Collections;
import java.util.Map;

/**
 * 物种原子组成（元素 → 计数，如葡萄糖 C₆H₁₂O₆ → {C:6, H:12, O:6}）
 * <p>
 * 化学守恒校验的输入数据（由 MC 侧装配层从 substances.json SMILES
 * 经 CDK 计算构建，本核心只做纯逻辑判定，不碰 CDK）。
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁
 *
 * @param atoms 元素符号 → 原子数（不可变视图）
 */
public record SpeciesComposition(Map<String, Integer> atoms) {

    /** 空组成（未知物种/解析失败兜底） */
    public static SpeciesComposition empty() {
        return new SpeciesComposition(Collections.emptyMap());
    }

    /** 是否无任何原子（解析失败标记） */
    public boolean isEmpty() {
        return atoms.isEmpty();
    }
}
