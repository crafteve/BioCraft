package com.github.crafteve.biocraft.compat;

import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * 酶反应方程式的共享分段构建器（GUI 与物品 tooltip 共用同一份逻辑）
 * <p>
 * 分段规则与颜色口径和 GUI 绘制完全一致：系数（&gt;1 时前缀）与物质缩写
 * 各自成段、物质段按物品色着色、符号（+ 与 =/→）按背景色着色；
 * 可逆统一用 "="（MC 无 ⇌ 字形），不可逆用 "→"
 * <p>
 * 本类只输出"文本段 + 颜色"序列，不做换行——换行是 GUI 宽度测量的事
 * （MachineScreen.wrapEquation 保留），tooltip 无宽度约束直接拼接
 */
public final class EnzymeEquation {

    /**
     * 方程式段：文本 + 颜色（ARGB）
     *
     * @param text  段文本（系数、缩写或符号）
     * @param color 段颜色（ARGB）
     */
    public record Segment(String text, int color) {
    }

    private EnzymeEquation() {
    }

    /**
     * 构建浅底（GUI 浅色主题底）配色方程式段
     * <p>
     * 物质段用物品色加深 1/5（与 GUI 卡片缩写同步），符号纯黑
     *
     * @param data 酶数据档案
     * @return 方程式段序列（反应物 + 箭头 + 产物）
     */
    public static List<Segment> guiSegments(EnzymeFactoryData data) {
        return build(data, CompatRenderUtil::darkenOneFifth, 0xFF000000);
    }

    /**
     * 构建深底（tooltip 深色底）配色方程式段
     * <p>
     * 物质段用物品原色（深底上比加深色更醒目），符号浅灰
     *
     * @param data 酶数据档案
     * @return 方程式段序列（反应物 + 箭头 + 产物）
     */
    public static List<Segment> tooltipSegments(EnzymeFactoryData data) {
        return build(data, color -> color, 0xD0D0D0);
    }

    /**
     * 分段构建核心：系数（&gt;1 时前缀）+ 缩写，段间 "+" 分隔，中间放箭头
     * <p>
     * 物质段颜色由背景决定（speciesColor 工厂），符号段颜色统一（symbolColor）
     *
     * @param data         酶数据档案
     * @param speciesColor 物质段颜色工厂（入参为物品原色 ARGB）
     * @param symbolColor  符号段颜色（+ 与箭头统一）
     * @return 方程式段序列（反应物 + 箭头 + 产物）
     */
    private static List<Segment> build(EnzymeFactoryData data,
                                       IntUnaryOperator speciesColor, int symbolColor) {
        List<Segment> segments = new ArrayList<>();
        appendSide(segments, data.reactants(), speciesColor, symbolColor);
        segments.add(new Segment(data.reversible() ? "=" : "→", symbolColor));
        appendSide(segments, data.products(), speciesColor, symbolColor);
        return segments;
    }

    /**
     * 追加一侧物种段：系数（&gt;1 时前缀）+ 缩写，段间以 "+" 分隔
     *
     * @param segments     段列表（追加目标）
     * @param specs        反应物或产物条目列表（JSON 解析顺序）
     * @param speciesColor 物质段颜色工厂（入参为物品原色 ARGB）
     * @param symbolColor  符号段颜色
     */
    private static void appendSide(List<Segment> segments,
                                   List<EnzymeFactoryData.SpeciesSpec> specs,
                                   IntUnaryOperator speciesColor, int symbolColor) {
        boolean first = true;
        for (EnzymeFactoryData.SpeciesSpec spec : specs) {
            if (!first) {
                segments.add(new Segment("+", symbolColor));
            }
            first = false;
            MoleculeItem item = ModItems.byId(spec.item()).get();
            int color = speciesColor.applyAsInt(item.getTintColor());
            if (spec.count() > 1) {
                segments.add(new Segment(String.valueOf(spec.count()), color));
            }
            segments.add(new Segment(item.getAbbreviation(), color));
        }
    }
}
