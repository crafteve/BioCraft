package com.github.crafteve.biocraft.item;

import java.util.HashMap;
import java.util.Map;

/**
 * 分子类别枚举，对应 substances.json 中的 categories 段
 * <p>
 * 类别用于 tooltip 中的类别徽章展示（彩色圆点 + 类别名），
 * 类别显示名走语言文件 key category.biocraft.&lt;id&gt;（由 datagen 生成）
 *
 * @param id    类别注册 id，与 substances.json 的 category 字段一致
 * @param color 类别主题色（ARGB），用于徽章圆点与类别名
 */
public enum MoleculeCategory {
    AMINO_ACID("amino_acid", 0xFF7CFC00),
    ION("ion", 0xFFB0C4DE),
    ATOM("atom", 0xFFC0C0C0),
    INORGANIC("inorganic", 0xFF87CEEB),
    BASE("base", 0xFF4FC3F7),
    NUCLEOTIDE("nucleotide", 0xFFE74C3C),
    COENZYME("coenzyme", 0xFFFF8C00),
    GLYCOLYSIS("glycolysis", 0xFFFFB300);

    private final String id;
    private final int color;

    /** id -> 枚举的索引表，供 JSON 解析快速查找 */
    private static final Map<String, MoleculeCategory> BY_ID = new HashMap<>();

    static {
        for (MoleculeCategory category : values()) {
            BY_ID.put(category.id, category);
        }
    }

    MoleculeCategory(String id, int color) {
        this.id = id;
        this.color = color;
    }

    /**
     * 按 id 查找类别
     *
     * @param id 类别 id（substances.json 中的 category 字段）
     * @return 对应枚举；未知 id 抛异常快速失败，防止数据错误被静默吞掉
     */
    public static MoleculeCategory byId(String id) {
        MoleculeCategory category = BY_ID.get(id);
        if (category == null) {
            throw new IllegalArgumentException("未定义的分子类别: " + id);
        }
        return category;
    }

    /**
     * 获取类别 id
     *
     * @return 类别 id 字符串
     */
    public String getId() {
        return id;
    }

    /**
     * 获取类别主题色
     *
     * @return ARGB 颜色值
     */
    public int getColor() {
        return color;
    }
}
