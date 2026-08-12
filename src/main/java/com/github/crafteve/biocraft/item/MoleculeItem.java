package com.github.crafteve.biocraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 通用分子物品基类，所有由物质表注册的分子物品都使用本类
 * <p>
 * 承载物质的化学属性：SMILES 结构式（驱动分子图渲染与分子式计算）、
 * 内容物染色值（双层贴图 layer0 的 ItemColor 着色）、缩写与所属类别
 * <p>
 * tooltip 布局（自下而上为视觉分区）：
 * <ol>
 *   <li>缩写徽章与分子式（黄色，Hill 排序 + Unicode 下标）</li>
 *   <li>类别徽章（主题色圆点 + 类别名）</li>
 *   <li>摩尔质量（紫色）</li>
 *   <li>键线式结构图（图片组件，由 getTooltipImage 注入，渲染在文本之后）</li>
 * </ol>
 *
 * @param properties   物品属性
 * @param smiles       SMILES 结构式
 * @param abbreviation 物质缩写（如 G6P、NAD+）
 * @param tintColor    内容物染色值（ARGB）
 * @param category     分子类别（tooltip 类别徽章）
 */
public class MoleculeItem extends Item {
    private final String smiles;
    private final String abbreviation;
    private final int tintColor;
    private final MoleculeCategory category;

    public MoleculeItem(Properties properties, String smiles, String abbreviation, int tintColor, MoleculeCategory category) {
        super(properties);
        this.smiles = smiles;
        this.abbreviation = abbreviation;
        this.tintColor = tintColor;
        this.category = category;
    }

    /**
     * 组装 tooltip 文本行（图片组件由 getTooltipImage 追加在文本之后）
     * <p>
     * 化学式/SMILES 是本模组区分物质的权威依据（AGENTS.md 1.3），
     * 分子式由 CDK 从 SMILES 计算并格式化（Hill 排序 + Unicode 下标）
     *
     * @param stack       当前物品堆
     * @param context     tooltip 上下文
     * @param tooltip     待填充的 tooltip 行列表
     * @param tooltipFlag tooltip 标志
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        MoleculeDataCalculator.MoleculeData data = MoleculeDataCalculator.forSmiles(smiles);

        // SMILES 解析失败时降级显示，避免工具提示崩溃（个别分子数据异常不影响游戏）
        if (!data.valid()) {
            tooltip.add(Component.translatable("tooltip.biocraft.smiles_error")
                    .withStyle(style -> style.withColor(0x9E9E9E)));
        } else {
            // 缩写徽章 + 分子式：黄色，权威依据
            tooltip.add(Component.literal("[" + abbreviation + "] " + data.formula())
                    .withStyle(style -> style.withColor(0xFFD700)));

            // 类别徽章：纯类别色文本（无符号前缀，避免 Unicode 符号渲染错位）
            tooltip.add(Component.translatable("category.biocraft." + category.getId())
                    .withStyle(style -> style.withColor(category.getColor())));

            // 摩尔质量：精确质量 4 位小数
            tooltip.add(Component.translatable("tooltip.biocraft.molar_mass",
                            String.format(Locale.ROOT, "%.4f", data.mass()))
                    .withStyle(style -> style.withColor(0xB57EDC)));
        }
    }

    /**
     * 注入键线式结构图图片组件
     * <p>
     * vanilla 原生机制（1.20.5+）：TooltipComponent 渲染在全部文本行之后，
     * 本方法在客户端组装 tooltip 时调用，服务端不会执行
     * （返回类型为 common 的 TooltipComponent，组件类本身仅客户端加载）
     *
     * @param stack 当前物品堆
     * @return 结构图组件（客户端针对复杂分子自动降级为提示行）
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new com.github.crafteve.biocraft.client.MoleculeTooltipComponent(smiles));
    }

    /**
     * 获取内容物染色值
     * <p>
     * 由客户端 MoleculeColors 在注册 ItemColor 时读取，仅作用于模型 layer0（内容物层），
     * 瓶子层与标注层不受染色影响
     *
     * @return ARGB 颜色值
     */
    public int getTintColor() {
        return tintColor;
    }

    /**
     * 获取物质缩写
     *
     * @return 缩写字符串
     */
    public String getAbbreviation() {
        return abbreviation;
    }

    /**
     * 获取分子类别
     *
     * @return 类别枚举
     */
    public MoleculeCategory getCategory() {
        return category;
    }
}
