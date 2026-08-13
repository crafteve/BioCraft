package com.github.crafteve.biocraft.blockentity;

/**
 * 机器类别枚举：大类按 EC 酶学分类，形色分离
 * <p>
 * 设计约束（策划 3.2）：
 * <ul>
 *   <li>大类（EC 六类）决定方块形状 / 正面符号徽章 / GUI 结构差异，不用颜色区分</li>
 *   <li>同类别实例的区分用实例色（方块 tint + GUI 图例点），不在本枚举承载</li>
 *   <li>SPECIAL 保留给中心法则链原始机器（DNA 编码器），手动注册不走酶数据表</li>
 * </ul>
 * 本枚举是第一版形状映射的占位：类别色已可用（方块 tint），
 * 六类形状拼装模型（双罐式/塔式/V 形/环台/双口汇聚）在里程碑 E 美化阶段落地
 *
 * @param id             类别注册标识（EC1~EC6，与 enzymes.json 的 category 字段一致）
 * @param displayName    类别显示名（中文，GUI 高级面板/图例用）
 * @param themeColor     类别主题色（方块 tint 与 GUI 强调色，同类别同色相）
 */
public enum MachineCategory {
    /** EC1 氧化还原酶：双罐式形状，代表：GAPDH */
    EC1("EC1", "氧化还原酶", 0x6FC3DF),
    /** EC2 转移酶：塔式形状，代表：HK / PFK / PGK / PK */
    EC2("EC2", "转移酶", 0xFFA94D),
    /** EC3 水解酶：带水槽形状（未使用，占位） */
    EC3("EC3", "水解酶", 0x7BD88F),
    /** EC4 裂合酶：V 形形状，代表：ALDO / ENO */
    EC4("EC4", "裂合酶", 0xB57EDC),
    /** EC5 异构酶：环台形状，代表：PGI / TPI / PGM */
    EC5("EC5", "异构酶", 0xFFD966),
    /** EC6 连接酶：双口汇聚形状（未使用，占位） */
    EC6("EC6", "连接酶", 0x9BD1A8),
    /** SPECIAL 中心法则链原始机器（DNA 编码器等），手动注册不走酶数据表 */
    SPECIAL("SPECIAL", "中心法则机器", 0x4A90D9);

    private final String id;
    private final String displayName;
    private final int themeColor;

    MachineCategory(String id, String displayName, int themeColor) {
        this.id = id;
        this.displayName = displayName;
        this.themeColor = themeColor;
    }

    /**
     * 按 JSON 字符串查找类别（不存在时快速失败）
     *
     * @param id JSON 中 category 字段值（EC1~EC6）
     * @return 对应类别
     */
    public static MachineCategory byId(String id) {
        for (MachineCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        throw new IllegalArgumentException("未知酶类别: " + id);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getThemeColor() {
        return themeColor;
    }
}
