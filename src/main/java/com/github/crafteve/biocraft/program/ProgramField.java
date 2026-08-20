package com.github.crafteve.biocraft.program;

/**
 * 酶设计单字段表（硬编码枚举，非 JSON 查表）
 * <p>
 * DSL 是字段型声明式语言：程序 = 一张"酶设计单"，每个关键词 = 酶的
 * 一个字段，无执行顺序。关键词集合 = 酶的字段集合（enzymes.json 数据表
 * 结构 + 物品展示属性的镜像），不需要发明任何执行语义。
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁；TNT 诱变/折叠机
 * 等后续系统直接消费本表
 */
public enum ProgramField {

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

    private final String keyword;
    private final ValueType valueType;
    private final boolean unlocked;

    ProgramField(String keyword, ValueType valueType, boolean unlocked) {
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
    public static ProgramField byKeyword(String keyword) {
        for (ProgramField field : values()) {
            if (field.keyword.equalsIgnoreCase(keyword)) {
                return field;
            }
        }
        return null;
    }
}
