package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeEquation;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/**
 * 酶工厂方块物品：手持/悬停时展示酶数据摘要 tooltip
 * <p>
 * 展示内容（自上而下）：缩写 + EC 类别 + 可逆性、反应方程式
 * （与 GUI 同一份 EnzymeEquation 分段逻辑，样式一致）、平衡常数、
 * 正逆向饱和可达最大速率（引擎通量 ×64×0.05，与 JEI/GUI 同口径）、
 * 最适温度
 * <p>
 * 速率数据复用 {@link EnzymeRecipeDisplay}（JEI/EMI/GUI 共享只读 DTO），
 * 不在此处重写任何速率公式（AGENTS.md 2.6 欠账 23）
 *
 * @param block 酶工厂方块
 * @param properties 物品属性
 * @param enzymeData 酶数据档案（tooltip 数据源，注册期绑定）
 */
public class EnzymeBlockItem extends BlockItem {
    /** 深色 tooltip 底上的主信息文字色（白） */
    private static final int COLOR_WHITE = 0xFFFFFF;
    /** 深色 tooltip 底上的次要信息文字色（灰：Keq/速率/温度统一灰色） */
    private static final int COLOR_DIM = 0x9E9E9E;

    private final EnzymeFactoryData enzymeData;

    /**
     * @param block      酶工厂方块
     * @param properties 物品属性
     * @param enzymeData 酶数据档案（tooltip 数据源，注册期绑定）
     */
    public EnzymeBlockItem(Block block, Item.Properties properties, EnzymeFactoryData enzymeData) {
        super(block, properties);
        this.enzymeData = enzymeData;
    }

    /**
     * 组装酶数据摘要 tooltip
     * <p>
     * 布局（深色 tooltip 底，与浅色 GUI 底是两种配色口径，见 EnzymeEquation）：
     * <ol>
     *   <li>缩写（主题色）+ EC 类别名（主题色）+ 可逆性（灰）</li>
     *   <li>反应方程式（分段彩色，与 GUI 同构；符号浅灰，深底可读）</li>
     *   <li>平衡常数、正逆向饱和可达最大速率、最适温度（全部灰）</li>
     * </ol>
     *
     * @param stack       当前物品堆
     * @param context     tooltip 上下文
     * @param tooltip     待填充的 tooltip 行列表
     * @param tooltipFlag tooltip 标志
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        MachineCategory category = MachineCategory.byId(enzymeData.category());
        int theme = category.getThemeColor() | 0xFF000000;

        // 行 1：缩写 + EC 类别名（主题色）+ 可逆性（灰）
        tooltip.add(Component.literal("[" + enzymeData.abbreviation() + "] ")
                .withStyle(style -> style.withColor(theme))
                .append(Component.translatable("machine.category." + category.getId())
                        .withStyle(style -> style.withColor(theme)))
                .append(Component.literal(" · ")
                        .withStyle(style -> style.withColor(COLOR_DIM)))
                .append(Component.translatable(enzymeData.reversible()
                                ? "tooltip.biocraft.enzyme.reversible"
                                : "tooltip.biocraft.enzyme.irreversible")
                        .withStyle(style -> style.withColor(COLOR_DIM))));

        // 行 2：反应方程式（分段彩色，与 GUI 同一份构建逻辑，深底配色）
        net.minecraft.network.chat.MutableComponent equation = Component.empty();
        for (EnzymeEquation.Segment segment : EnzymeEquation.tooltipSegments(enzymeData)) {
            equation.append(Component.literal(segment.text()).withStyle(style -> style.withColor(segment.color())));
        }
        tooltip.add(equation);

        // 行 3：平衡常数（灰，formatKeq 同款）
        tooltip.add(Component.translatable("tooltip.biocraft.enzyme.keq",
                        CompatRenderUtil.formatKeq(enzymeData.keq()))
                .withStyle(style -> style.withColor(COLOR_DIM)));

        // 行 4/5：正逆向饱和可达最大速率（引擎通量 ×64×0.05，与 JEI/GUI 同口径）
        EnzymeRecipeDisplay display = EnzymeRecipeDisplay.from(enzymeData);
        tooltip.add(Component.translatable("tooltip.biocraft.enzyme.vmax_f",
                        CompatRenderUtil.formatRate(display.vmaxFPerTick()))
                .withStyle(style -> style.withColor(COLOR_DIM)));
        tooltip.add(Component.translatable("tooltip.biocraft.enzyme.vmax_b",
                        CompatRenderUtil.formatRate(display.vmaxBPerTick()))
                .withStyle(style -> style.withColor(COLOR_DIM)));

        // 行 6：最适温度（灰）
        tooltip.add(Component.translatable("tooltip.biocraft.enzyme.temp",
                        String.format(Locale.ROOT, "%.2f", enzymeData.tempOptimum()))
                .withStyle(style -> style.withColor(COLOR_DIM)));
    }
}
