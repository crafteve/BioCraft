package com.github.crafteve.biocraft.program;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 酶设计单解析结果（字段表）
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁
 *
 * @param fields      字段表（重复字段后者覆盖；含所有合法字段）
 * @param lineNumbers 字段 → 行号（1 起，错误定位用）
 * @param inputList   input 字段解析出的物种 id 列表（有序）
 * @param outputList  output 字段解析出的物种 id 列表（有序）
 */
public record EnzymeProgram(
        Map<ProgramField, String> fields,
        Map<ProgramField, Integer> lineNumbers,
        List<String> inputList,
        List<String> outputList) {

    /** 空设计单（无任何字段） */
    public static EnzymeProgram empty() {
        return new EnzymeProgram(Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList(), Collections.emptyList());
    }

    /** 读取字段值（无则 null） */
    public String value(ProgramField field) {
        return fields.get(field);
    }

    /** 字段是否存在 */
    public boolean has(ProgramField field) {
        return fields.containsKey(field);
    }
}
