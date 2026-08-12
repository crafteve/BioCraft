package com.github.crafteve.biocraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 通用分子物品基类，所有由物质表注册的分子物品都使用本类
 * <p>
 * 本类承载物质的化学属性：SMILES 结构式（后续轮次用于 tooltip 结构式绘制与化学式推导）、
 * 内容物染色值（配合双层贴图中的 layer0 进行 ItemColor 着色）、缩写（后续轮次用于 overlay 标注层绘制）
 * <p>
 * 本轮为"两层贴图 + 染色"的最小验证版：tooltip 直接显示 SMILES 明文占位，
 * 后续轮次将替换为结构式图形渲染
 *
 * @param properties   物品属性
 * @param smiles       SMILES 结构式
 * @param abbreviation 物质缩写（如 G6P、NAD+），用于后续 overlay 标注层
 * @param tintColor    内容物染色值（ARGB）
 */
public class MoleculeItem extends Item {
    private final String smiles;
    private final String abbreviation;
    private final int tintColor;

    public MoleculeItem(Properties properties, String smiles, String abbreviation, int tintColor) {
        super(properties);
        this.smiles = smiles;
        this.abbreviation = abbreviation;
        this.tintColor = tintColor;
    }

    /**
     * 在 tooltip 中显示 SMILES 结构式（占位实现，后续替换为结构式图形）
     * <p>
     * 化学式/SMILES 是本模组区分物质的权威依据（AGENTS.md 1.3），
     * 因此每个分子物品都必须能在 tooltip 中看到其结构信息
     *
     * @param stack     当前物品堆
     * @param context   tooltip 上下文
     * @param tooltip   待填充的 tooltip 行列表
     * @param tooltipFlag tooltip 标志
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.literal("SMILES: " + smiles).withStyle(style -> style.withColor(0x9E9E9E)));
    }

    /**
     * 获取内容物染色值
     * <p>
     * 由客户端 MoleculeColors 在注册 ItemColor 时读取，仅作用于模型 layer0（内容物层），
     * 瓶子层与后续的标注层不受染色影响
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
}
