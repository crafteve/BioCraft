package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeEquation;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * 酶蛋白物品（催化剂载荷，新架构下的酶形态）
 * <p>
 * 酶由数据表 enzymes.json 驱动注册（ModItems 循环注册，注册名 = enzyme_&lt;酶id&gt;），
 * 堆叠数 = 酶浓度 [E]（stacksTo 64，1 个 = 1 倍速、64 个 = 64 倍速，
 * 速率线性倍率由反应腔引擎的活动通道实现，本类只承载物品形态）；
 * 过渡期与酶工厂方块物品并存，注册名前缀 enzyme_ 避免 id 冲突，
 * 未来方块删除后注册名保持稳定（存档物品 id 不随方块移除而变）
 * <p>
 * 视觉与分子物品一致：双层贴图（layer0 内容物按数据表 color 字段染色 +
 * layer1 容器贴图）+ 图标左上角缩写标注（AbbreviationProvider 接口
 * 复用 MoleculeItemDecorator）
 * <p>
 * tooltip 沿用酶工厂方块物品的展示内容（缩写/EC 类别/可逆性/反应方程式/
 * 平衡常数/正逆向饱和可达速率/能量行/最适温度），数据源为
 * EnzymeRecipeDisplay（JEI/EMI/GUI 共享只读 DTO）与 EnzymeEquation，
 * 不重写任何速率公式（AGENTS.md 2.6 欠账 23）
 */
public class EnzymeItem extends Item implements AbbreviationProvider {
    /** 深色 tooltip 底上的主信息文字色（白） */
    private static final int COLOR_WHITE = 0xFFFFFF;
    /** 深色 tooltip 底上的次要信息文字色（灰：Keq/速率/温度统一灰色） */
    private static final int COLOR_DIM = 0x9E9E9E;
    /** 深色 tooltip 底上的能量行颜色（深绿，深底可读） */
    private static final int COLOR_ENERGY = 0x4CAF50;

    /** 酶数据档案（tooltip 数据源，注册期绑定） */
    private final EnzymeFactoryData enzymeData;

    /** 内容物染色值（数据表 color 字段，ARGB；双层贴图 layer0 用） */
    private final int tintColor;

    /**
     * @param properties 物品属性（注册期传入，堆叠 64 = [E] 上限）
     * @param enzymeData 酶数据档案（tooltip 与染色数据源）
     */
    public EnzymeItem(Item.Properties properties, EnzymeFactoryData enzymeData) {
        super(properties);
        this.enzymeData = enzymeData;
        this.tintColor = enzymeData.color();
    }

    /**
     * 组装酶数据摘要 tooltip（沿用酶工厂方块物品的展示口径）
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
        int theme = enzymeData.color();

        // 行 1：缩写（主题色）+ 可逆性（灰）
        tooltip.add(Component.literal("[" + enzymeData.abbreviation() + "] ")
                .withStyle(style -> style.withColor(theme))
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

        // 能量行（绿色，仅含 fe 物种的酶显示：产出/消耗 + 容量）
        if (display.energyStoich() != 0) {
            int kfePerMolecule = Math.abs(display.energyStoich());
            String direction = display.energyStoich() > 0 ? "+" : "-";
            tooltip.add(Component.translatable("tooltip.biocraft.enzyme.energy",
                            direction + kfePerMolecule,
                            String.format(Locale.ROOT, "%,d", display.energyCapacityFE() / 1000))
                    .withStyle(style -> style.withColor(COLOR_ENERGY)));
        }

        // 行 6：最适温度（灰）
        tooltip.add(Component.translatable("tooltip.biocraft.enzyme.temp",
                        String.format(Locale.ROOT, "%.2f", enzymeData.tempOptimum()))
                .withStyle(style -> style.withColor(COLOR_DIM)));
    }

    /**
     * 获取酶缩写（图标缩写标注数据源）
     *
     * @return 缩写字符串（如 HK/PGI/GAPDH）
     */
    @Override
    public String getAbbreviation() {
        return enzymeData.abbreviation();
    }

    /**
     * 获取内容物染色值（双层贴图 layer0 用，数据表 color 字段直取）
     *
     * @return ARGB 颜色值
     */
    public int getTintColor() {
        return tintColor;
    }

    /**
     * 获取酶数据档案（tooltip 与反应腔查询共用）
     *
     * @return 数据档案
     */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }
}
