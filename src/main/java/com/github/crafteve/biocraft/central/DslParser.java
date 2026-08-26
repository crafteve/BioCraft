package com.github.crafteve.biocraft.central;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DSL 填表解析器（原 program/EnzymeProgramParser + EnzymeProgram 合并，一文件管填表）
 * <p>
 * 语法：每行 keyword: value，大小写不敏感，空行/# 注释跳过，值按首冒号后取（可含冒号），
 * 重复字段后者覆盖。仅语法/格式/数量校验，物种存在性与守恒由 data/EnzymeProgramChecker 外层补。
 * 解析结果回键值表（Map&lt;DslField,String&gt;），调用方（折叠机）按表填产物 DataComponent，不回 ItemStack
 * <p>
 * 零 MC 依赖
 */
public final class DslParser {

    /** 每个 input/output 字段允许物种数上限 */
    public static final int MAX_SPECIES_PER_FIELD = 2;

    /** kcat 格式：[+-]1~3位数字+% */
    private static final Pattern KCAT_PATTERN = Pattern.compile("[+-]\\d{1,3}%");

    /** 解析结果：字段表 + 错误列表（键值表供调用方填 NBT） */
    public record ParseResult(Program program, List<ProgramError> errors) {
    }

    /** 字段表（原 EnzymeProgram），回表不回 Item */
    public record Program(
            Map<DslField, String> fields,
            Map<DslField, Integer> lineNumbers,
            List<String> inputList,
            List<String> outputList) {

        public static Program empty() {
            return new Program(Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyList(), Collections.emptyList());
        }

        /** 读取字段值（无则 null） */
        public String value(DslField field) {
            return fields.get(field);
        }

        public boolean has(DslField field) {
            return fields.containsKey(field);
        }
    }

    /** 错误（原 ProgramError 内移，与 DslField 同包收口） */
    public record ProgramError(ProgramErrorCode code, int line, String detail) {
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

    public enum ProgramErrorCode {
        MISSING_ID, ID_NOT_FOUND, UNKNOWN_FIELD, BAD_VALUE, TOO_MANY_SPECIES, UNKNOWN_SPECIES, CHEM_UNBALANCED
    }

    private DslParser() {
    }

    /**
     * 解析程序文本
     *
     * @param text 程序全文（可 null）
     * @return 字段键值表 + 全部错误
     */
    public static ParseResult parse(String text) {
        Map<DslField, String> fields = new LinkedHashMap<>();
        Map<DslField, Integer> lineNumbers = new HashMap<>();
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
                DslField field = DslField.byKeyword(keyword);
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
                if (field.valueType() == DslField.ValueType.SPECIES_LIST) {
                    List<String> species = splitSpecies(value);
                    if (species.size() > MAX_SPECIES_PER_FIELD) {
                        errors.add(new ProgramError(ProgramErrorCode.TOO_MANY_SPECIES, i + 1,
                                field.keyword() + " 新增物种最多 " + MAX_SPECIES_PER_FIELD + " 个"));
                        continue;
                    }
                    if (field == DslField.INPUT) {
                        inputList.addAll(species);
                    } else {
                        outputList.addAll(species);
                    }
                }
                fields.put(field, value);
                lineNumbers.put(field, i + 1);
            }
        }
        if (!fields.containsKey(DslField.ID)) {
            errors.add(new ProgramError(ProgramErrorCode.MISSING_ID, 0, "id"));
        }
        return new ParseResult(new Program(fields, lineNumbers, inputList, outputList), errors);
    }

    private static String validateValue(DslField field, String value) {
        return switch (field.valueType()) {
            case TEXT -> value.isEmpty() ? "值不能为空" : null;
            case PERCENT -> KCAT_PATTERN.matcher(value).matches() ? null : "格式应为 +N% 或 -N%（如 +20%、-50%）";
            case SPECIES_LIST -> value.isEmpty() ? "物种列表不能为空" : null;
        };
    }

    /** 逗号分隔物种列表（trim 去空，保持顺序） */
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
