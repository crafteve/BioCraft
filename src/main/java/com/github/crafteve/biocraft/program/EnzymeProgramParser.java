package com.github.crafteve.biocraft.program;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 酶设计单解析器：文本 → 字段表 + 错误列表
 * <p>
 * 语法：每行一个字段 `keyword: value`；关键词大小写不敏感；空行与
 * `#` 注释行跳过；值按第一个冒号后取（值可含冒号）；重复字段宽容
 * （后者覆盖前者——DNA 冗余设定）。仅做语法/格式/数量校验——
 * 物种存在性与化学守恒由外部校验器（EnzymeProgramChecker）消费
 * 本解析结果完成。
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁；TNT 诱变、
 * 折叠机、编辑器预览共用本解析器
 */
public final class EnzymeProgramParser {

    /** 每个 input/output 字段允许的物种数上限 */
    public static final int MAX_SPECIES_PER_FIELD = 2;

    /** kcat 修饰格式：[+-] 后跟 1~3 位数字 + % */
    private static final Pattern KCAT_PATTERN = Pattern.compile("[+-]\\d{1,3}%");

    /** 解析结果：字段表 + 错误列表（错误可多个，全部收集） */
    public record ParseResult(EnzymeProgram program, List<ProgramError> errors) {
    }

    private EnzymeProgramParser() {
    }

    /**
     * 解析程序文本
     *
     * @param text 程序全文（可为 null）
     * @return 解析结果（字段表 + 全部错误；id 缺失在 errors 中标记，不设致命中断）
     */
    public static ParseResult parse(String text) {
        Map<ProgramField, String> fields = new LinkedHashMap<>();
        Map<ProgramField, Integer> lineNumbers = new HashMap<>();
        List<ProgramError> errors = new ArrayList<>();
        List<String> inputList = new ArrayList<>();
        List<String> outputList = new ArrayList<>();
        if (text != null) {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    errors.add(new ProgramError(ProgramErrorCode.UNKNOWN_FIELD, i + 1, line));
                    continue;
                }
                String keyword = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                ProgramField field = ProgramField.byKeyword(keyword);
                if (field == null) {
                    errors.add(new ProgramError(ProgramErrorCode.UNKNOWN_FIELD, i + 1, keyword));
                    continue;
                }
                String badValue = validateValue(field, value);
                if (badValue != null) {
                    errors.add(new ProgramError(ProgramErrorCode.BAD_VALUE, i + 1,
                            field.keyword() + "：" + badValue));
                    continue;
                }
                // 物种列表额外校验：数量上限（独立错误码，教学价值）
                if (field.valueType() == ProgramField.ValueType.SPECIES_LIST) {
                    List<String> species = splitSpecies(value);
                    if (species.size() > MAX_SPECIES_PER_FIELD) {
                        errors.add(new ProgramError(ProgramErrorCode.TOO_MANY_SPECIES, i + 1,
                                field.keyword() + " 新增物种最多 " + MAX_SPECIES_PER_FIELD + " 个"));
                        continue;
                    }
                    if (field == ProgramField.INPUT) {
                        inputList.addAll(species);
                    } else {
                        outputList.addAll(species);
                    }
                }
                // 重复字段宽容：后者覆盖前者（DNA 冗余设定），行号取最新
                fields.put(field, value);
                lineNumbers.put(field, i + 1);
            }
        }
        if (!fields.containsKey(ProgramField.ID)) {
            errors.add(new ProgramError(ProgramErrorCode.MISSING_ID, 0, "id"));
        }
        return new ParseResult(new EnzymeProgram(fields, lineNumbers, inputList, outputList), errors);
    }

    /**
     * 字段值格式校验（不校验物种存在性/化学守恒/数量上限）
     *
     * @param field 字段
     * @param value 值（已 trim）
     * @return 错误描述，null = 格式合法
     */
    private static String validateValue(ProgramField field, String value) {
        switch (field.valueType()) {
            case TEXT -> {
                return value.isEmpty() ? "值不能为空" : null;
            }
            case PERCENT -> {
                return KCAT_PATTERN.matcher(value).matches()
                        ? null : "格式应为 +N% 或 -N%（如 +20%、-50%）";
            }
            case SPECIES_LIST -> {
                return value.isEmpty() ? "物种列表不能为空" : null;
            }
        }
        return null;
    }

    /**
     * 逗号分隔物种列表解析（trim 去空，保持顺序）
     *
     * @param value 字段值
     * @return 物种 id 列表
     */
    public static List<String> splitSpecies(String value) {
        List<String> species = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                species.add(trimmed);
            }
        }
        return species;
    }
}
