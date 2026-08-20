package com.github.crafteve.biocraft.program;

/**
 * 酶设计单解析/校验错误（错误蛋白错误码 + 编辑器提示共用）
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁
 *
 * @param code   错误码
 * @param line   行号（1 起；0 = 不指向具体行，如 MISSING_ID）
 * @param detail 详情（如未知关键词名 / 字段名 / 未注册物种 id）
 */
public record ProgramError(ProgramErrorCode code, int line, String detail) {

    /** 人类可读描述（编辑器/错误蛋白共用） */
    public String describe() {
        String prefix = line > 0 ? "第 " + line + " 行" : "程序";
        return switch (code) {
            case MISSING_ID -> prefix + "：缺少 id 字段（无法确定基酶）";
            case ID_NOT_FOUND -> prefix + "：id \"" + detail + "\" 不是已知酶（基酶解析失败）";
            case UNKNOWN_FIELD -> prefix + "：未知字段 \"" + detail + "\"";
            case BAD_VALUE -> prefix + "：字段 \"" + detail + "\" 值格式错误";
            case TOO_MANY_SPECIES -> prefix + "：" + detail;
            case UNKNOWN_SPECIES -> prefix + "：未知物种 \"" + detail + "\"";
            case CHEM_UNBALANCED -> prefix + "：完整反应式原子不守恒";
        };
    }
}
