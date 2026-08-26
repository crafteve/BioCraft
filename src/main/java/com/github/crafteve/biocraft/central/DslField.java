package com.github.crafteve.biocraft.central;

/**
 * DSL 字段表（原 ProgramField，改名 DslField）+ 高亮规则收口（CentralProgramHighlight）
 * <p>
 * 零 MC 依赖（只 import java.*），与 Codec 同门禁；颜色枚举收归本文件，
 * ProgramHighlight 与 CodeEditorWidget 同引，不再双份
 */
public enum DslField {

    /** 基酶锚点：以该酶为底做修改；缺失/解析失败 → 错误蛋白 */
    ID("id", ValueType.TEXT, true),
    /** 生成物品的显示名 */
    NAME("name", ValueType.TEXT, true),
    /** kcat 修饰百分比（初始未解锁：TNT 诱变/翻译成功后成就解锁，编辑器灰显） */
    KCAT("kcat", ValueType.PERCENT, false),
    /** 新增底物列表（追加；物种校验 + 化学守恒 + 数量上限） */
    INPUT("input", ValueType.SPECIES_LIST, true),
    /** 新增产物列表（追加；与 input 配对，方案甲：声明完整扩展反应式） */
    OUTPUT("output", ValueType.SPECIES_LIST, true);

    /** 字段值类型 */
    public enum ValueType { TEXT, PERCENT, SPECIES_LIST }

    /**
     * 高亮规则（CentralProgramHighlight）：关键字/颜色/Token 类型收口
     * <p>
     * 与 ProgramHighlight/CodeEditorWidget 同色，改色只改此处
     */
    public enum Highlight {
        KEYWORD(0xFF569CD6),   // 关键字蓝（id/name/kcat/input/output/import/as/修饰）
        FUNCTION(0xFFC586C0),  // 函数紫（word 后紧跟 '('）
        NUMBER(0xFFB5CEA8),    // 数字绿
        STRING(0xFFCE9178),    // 字符串橙（"..."）
        COMMENT(0xFF6A9955),   // 注释灰绿（#...）
        SYMBOL(0xFFD7BA7D),    // 符号金（=,():;）
        PLAIN(0xFFD4D4D4);     // 普通白

        private final int color;

        Highlight(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    private final String keyword;
    private final ValueType valueType;
    private final boolean unlocked;

    DslField(String keyword, ValueType valueType, boolean unlocked) {
        this.keyword = keyword;
        this.valueType = valueType;
        this.unlocked = unlocked;
    }

    /** 关键词（小写规范形式） */
    public String keyword() {
        return keyword;
    }

    /** 值类型 */
    public ValueType valueType() {
        return valueType;
    }

    /** 是否默认解锁（false = 需 TNT 诱变/翻译解锁，编辑器灰显提示但不拦截） */
    public boolean unlocked() {
        return unlocked;
    }

    /**
     * 关键词 → 字段（大小写不敏感），未知关键词返回 null
     *
     * @param keyword 原始关键词文本
     * @return 字段枚举，未知为 null
     */
    public static DslField byKeyword(String keyword) {
        for (DslField field : values()) {
            if (field.keyword.equalsIgnoreCase(keyword)) {
                return field;
            }
        }
        return null;
    }

    /** 是否 DSL 字段关键词（供高亮判定，大小写不敏感） */
    public static boolean isFieldKeyword(String word) {
        return byKeyword(word) != null;
    }
}
