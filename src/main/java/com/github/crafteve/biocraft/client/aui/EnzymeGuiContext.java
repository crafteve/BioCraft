package com.github.crafteve.biocraft.client.aui;

import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;

/**
 * 酶工厂 GUI 的客户端运行时数据持有器
 * <p>
 * 由 {@code ClientboundEnzymeGuiPacket} 的客户端处理填充（每 tick 更新），
 * 供全部 AUI 自定义元素在 drawPhase 中逐帧读取。它是"纯 Java push"数据流的
 * 唯一权威出口：动态数值不进 DOM textContent（避免每帧样式重算），
 * 由自定义元素绘制时直接读取
 * <p>
 * 字段语义：
 * <ul>
 *   <li>data：当前酶的静态档案（酶名/缩写/反应式/类别）</li>
 *   <li>definition：由酶数据构建的反应网络（平衡条计算浓度商 Q 用）</li>
 *   <li>accentColor：类别主题色（EC 六类高饱和度纯色，注入 CSS --accent）</li>
 *   <li>tempX100 / fluxX1000：温度（K×100）与净通量（堆叠分数/s×1000）</li>
 *   <li>concentrations：每物种浓度×1000（下标 = 物种索引）</li>
 *   <li>history：v-t 通量历史快照（×1000，最旧→最新）</li>
 * </ul>
 */
public final class EnzymeGuiContext {
    /** 当前酶数据档案（服务端与客户端同源查表，引用比较判断是否换酶） */
    private static EnzymeFactoryData data;

    /** 反应网络档案（仅在换酶时重建，温度/通量更新不触发） */
    private static ReactionDefinition definition;

    /** 类别主题色（ARGB 不透明） */
    private static int accentColor = 0xFFFFA94D;

    private static int tempX100;
    private static int fluxX1000;
    private static int[] concentrations = new int[0];
    private static int[] history = new int[0];

    private EnzymeGuiContext() {
    }

    /**
     * 更新全部运行时数据（由客户端数据包处理调用）
     * <p>
     * 反应网络档案只在酶数据引用变化时重建，避免每 tick 重复构建模拟器；
     * 温度/通量/浓度/历史每 tick 直接覆盖
     *
     * @param data          酶数据档案
     * @param tempX100      温度×100
     * @param fluxX1000     净通量×1000
     * @param concentrations 每物种浓度×1000
     * @param history       v-t 历史快照
     */
    public static void update(EnzymeFactoryData data, int tempX100, int fluxX1000,
                              int[] concentrations, int[] history) {
        if (data != EnzymeGuiContext.data) {
            MachineCategory category = MachineCategory.byId(data.category());
            accentColor = 0xFF000000 | category.getThemeColor();
            definition = data.buildSimulator().getDefinition();
        }
        EnzymeGuiContext.data = data;
        EnzymeGuiContext.tempX100 = tempX100;
        EnzymeGuiContext.fluxX1000 = fluxX1000;
        EnzymeGuiContext.concentrations = concentrations == null ? new int[0] : concentrations;
        EnzymeGuiContext.history = history == null ? new int[0] : history;
    }

    /** 是否已收到首个数据包（可开始构建静态 DOM） */
    public static boolean isReady() {
        return data != null;
    }

    public static EnzymeFactoryData data() {
        return data;
    }

    public static ReactionDefinition definition() {
        return definition;
    }

    public static int accentColor() {
        return accentColor;
    }

    public static int tempX100() {
        return tempX100;
    }

    public static int fluxX1000() {
        return fluxX1000;
    }

    public static int[] history() {
        return history;
    }

    public static double flux() {
        return fluxX1000 / 1000.0;
    }

    public static double temperature() {
        return tempX100 / 100.0;
    }

    /**
     * 读取物种浓度（0~1）
     *
     * @param index 物种下标
     * @return 浓度，越界返回 0
     */
    public static double concentration(int index) {
        int[] c = concentrations;
        if (index < 0 || index >= c.length) {
            return 0.0;
        }
        return c[index] / 1000.0;
    }

    /** 物种总数 */
    public static int speciesCount() {
        return concentrations.length;
    }
}
