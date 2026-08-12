package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.item.MoleculeItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * 分子物品图标缩写装饰器（Dist.CLIENT）
 * <p>
 * 在物品图标左上角绘制物质缩写（如 ATP、GLUC、NAD+、H2O），
 * 便于在快捷栏/物品栏/创意标签页中快速分辨不同分子。
 * 绘制方式：白色文字 + 黑色阴影双写（先画阴影再画主文字，
 * 右偏下偏 1px），并以 0.55 倍缩放适配 16px 图标
 * （MC 字体原字号 9px 会溢出图标，最长 4 字符缩写缩放后约 13px）
 */
public final class MoleculeItemDecorator implements IItemDecorator {

    /** 单例（装饰器无状态，所有分子物品共用） */
    public static final MoleculeItemDecorator INSTANCE = new MoleculeItemDecorator();

    /** 文字缩放倍数（MC 字体 9px 高 × 0.55 ≈ 5px，4 字符缩写约 13px 宽，可放入 16px 图标） */
    private static final float TEXT_SCALE = 0.55f;
    /** 图标左上角偏移（px，未缩放坐标系） */
    private static final int OFFSET_X = 1;
    private static final int OFFSET_Y = 1;

    private MoleculeItemDecorator() {
    }

    /**
     * 在物品图标左上角绘制缩写
     * <p>
     * 双写阴影保证在各种染色内容物上均可读：
     * 先画黑色阴影（右偏下偏 1px），再画白色主文字
     *
     * @param guiGraphics 渲染上下文
     * @param font        MC 字体
     * @param stack       物品堆
     * @param x           图标左上角 x
     * @param y           图标左上角 y
     * @return false（不阻断其他装饰器，如破损条/堆叠数）
     */
    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int x, int y) {
        if (!(stack.getItem() instanceof MoleculeItem molecule)) {
            return false;
        }
        String abbreviation = molecule.getAbbreviation();
        if (abbreviation.isEmpty()) {
            return false;
        }

        guiGraphics.pose().pushPose();
        // z 提升到 200 层（与 vanilla 堆叠数 ITEM_COUNT_BLIT_OFFSET 同级）：
        // 物品模型贴图渲染在 z=0 且可能覆盖后绘制的文字，必须提升 z 才能显示在贴图之上
        guiGraphics.pose().translate(x + OFFSET_X, y + OFFSET_Y, 200.0);
        guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        // 阴影（黑色，右偏下偏 1px，未缩放坐标）
        guiGraphics.drawString(font, abbreviation, 1, 1, 0xFF000000, false);
        // 主文字（白色）
        guiGraphics.drawString(font, abbreviation, 0, 0, 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();
        return false;
    }
}
