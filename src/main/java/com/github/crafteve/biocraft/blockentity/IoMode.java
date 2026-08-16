package com.github.crafteve.biocraft.blockentity;

/**
 * 侧向 IO 模式：INPUT/OUTPUT 区域各自的物品进出许可
 * <p>
 * GUI 的 INPUT/OUTPUT 滚动槽底部按钮管理本模式，三态循环切换：
 * 仅输入 → 仅输出 → 输入输出 → 仅输入
 * <p>
 * 门控作用范围（"管理是否允许 IO"的完整语义）：
 * <ul>
 *   <li>玩家 GUI：Slot.mayPlace（插入）/ mayPickup（抽出）</li>
 *   <li>工业管道：EnzymeFactoryItemHandler 的 insertItem/extractItem</li>
 *   <li>原版漏斗：容器 canPlaceItem（塞入）/ removeItem（抽出）</li>
 * </ul>
 * 默认 INPUT 区域仅输入、OUTPUT 区域仅输出（防玩家/管道把物品误塞进
 * 产物槽导致容量冻结——TPI 满堆卡 0.00 事故的预防性设计）
 */
public enum IoMode {
    /** 仅允许物品输入（禁止抽出） */
    INPUT_ONLY(0),
    /** 仅允许物品输出（禁止插入） */
    OUTPUT_ONLY(1),
    /** 允许输入与输出（旧的全通行为） */
    BOTH(2);

    private final int id;

    /**
     * @param id 容器数据/网络包中的整数编码
     */
    IoMode(int id) {
        this.id = id;
    }

    /**
     * 容器数据整数编码（ContainerData 同步与 NBT 存档用）
     *
     * @return 0/1/2
     */
    public int id() {
        return id;
    }

    /**
     * 按整数编码查枚举（非法值防御性归为双向）
     *
     * @param id 编码
     * @return 对应模式
     */
    public static IoMode byId(int id) {
        return switch (id) {
            case 0 -> INPUT_ONLY;
            case 1 -> OUTPUT_ONLY;
            default -> BOTH;
        };
    }

    /**
     * 循环到下一状态（点击按钮的切换顺序）
     *
     * @return 下一模式
     */
    public IoMode next() {
        return switch (this) {
            case INPUT_ONLY -> OUTPUT_ONLY;
            case OUTPUT_ONLY -> BOTH;
            case BOTH -> INPUT_ONLY;
        };
    }

    /**
     * 本模式是否允许物品插入
     *
     * @return true 表示允许
     */
    public boolean allowsInsert() {
        return this != OUTPUT_ONLY;
    }

    /**
     * 本模式是否允许物品抽出
     *
     * @return true 表示允许
     */
    public boolean allowsExtract() {
        return this != INPUT_ONLY;
    }
}
