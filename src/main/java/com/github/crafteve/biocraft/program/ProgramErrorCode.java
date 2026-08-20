package com.github.crafteve.biocraft.program;

/**
 * 酶设计单错误码（错误蛋白错误码 + 编辑器提示共用）
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁
 */
public enum ProgramErrorCode {

    /** id 字段缺失（致命：无法确定基酶） */
    MISSING_ID,
    /** id 值不是已知酶 id（基酶解析失败） */
    ID_NOT_FOUND,
    /** 未知/不存在的关键词（含行号与关键词） */
    UNKNOWN_FIELD,
    /** 值格式错误（如 kcat: 20 缺 %、空值） */
    BAD_VALUE,
    /** input/output 新增物种数超上限（各 ≤ 2） */
    TOO_MANY_SPECIES,
    /** 物种 id 未注册（substances.json 不存在） */
    UNKNOWN_SPECIES,
    /** 完整反应式（模板 + 新增项）原子不守恒 */
    CHEM_UNBALANCED
}
