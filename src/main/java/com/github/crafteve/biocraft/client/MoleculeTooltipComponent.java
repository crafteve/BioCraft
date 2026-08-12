package com.github.crafteve.biocraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 键线式结构图 tooltip 组件（仅客户端加载）
 * <p>
 * 同时实现 TooltipComponent 与 ClientTooltipComponent：
 * vanilla 的 ClientTooltipComponent.create() 通过 instanceof 检查直接使用本组件，
 * 无需额外的 common/client 双层包装
 * <p>
 * 渲染流程：从 MoleculeTextureCache 获取缓存的分子图，
 * 先 blit 键线骨架纹理，再在杂原子位置用 MC 像素字体叠加元素符号，
 * 图片顶部绘制半透明分隔线，与上方文本行形成视觉分区；
 * 复杂分子（重原子 &gt; 150）或解析失败时降级为灰色提示行
 *
 * @param smiles SMILES 结构式
 */
public record MoleculeTooltipComponent(String smiles) implements TooltipComponent, ClientTooltipComponent {

    /** 复杂分子提示行颜色（灰色） */
    private static final int HINT_COLOR = 0xFF9E9E9E;
    /** 分隔线颜色（半透明灰） */
    private static final int DIVIDER_COLOR = 0x33444444;

    /**
     * 计算组件高度：分子图高度（复杂分子为提示行高度）
     *
     * @return 高度（px）
     */
    @Override
    public int getHeight() {
        MoleculeTextureCache.MoleculeImage image = MoleculeTextureCache.get(smiles);
        return image == null ? 10 : image.height();
    }

    /**
     * 计算组件宽度：分子图宽度与提示文本宽度取较大者
     *
     * @param font MC 字体（未使用，宽度只取决于分子图）
     * @return 宽度（px）
     */
    @Override
    public int getWidth(Font font) {
        MoleculeTextureCache.MoleculeImage image = MoleculeTextureCache.get(smiles);
        return image == null ? 100 : image.width();
    }

    /**
     * 绘制分隔线与分子图（或复杂分子提示行）
     *
     * @param font        MC 字体
     * @param x           组件左边缘
     * @param y           组件顶边缘
     * @param guiGraphics 渲染上下文
     */
    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        MoleculeTextureCache.MoleculeImage image = MoleculeTextureCache.get(smiles);
        int width = getWidth(font);

        // 顶部分隔线：与文本行视觉分区
        guiGraphics.fill(x, y, x + width, y + 1, DIVIDER_COLOR);

        if (image == null) {
            guiGraphics.drawString(font, "Structure too complex", x, y + 3, HINT_COLOR, false);
            return;
        }

        // 键线骨架：完整 4x 超采样纹理线性缩放到逻辑尺寸显示
        // 必须用 9 参数 blit 重载：采样区域（uWidth/vHeight）取整个纹理，
        // 目标尺寸（width/height）为逻辑尺寸；
        // 若用 6 参数重载则 w/h 同时决定采样区域，只会显示纹理左上角局部导致线缺失/错位
        int pixelWidth = image.width() * MoleculeTextureCache.SUPERSAMPLE;
        int pixelHeight = image.height() * MoleculeTextureCache.SUPERSAMPLE;
        guiGraphics.blit(image.texture(), x, y + 2, image.width(), image.height(),
                0, 0, pixelWidth, pixelHeight, pixelWidth, pixelHeight);
        // 杂原子符号：MC 像素字体叠加，以原子像素位置为中心动态居中
        // （按字符串实际宽度居中，避免单字符与双字符符号的错位）
        for (MoleculeTextureCache.AtomLabel label : image.labels()) {
            int symbolX = x + label.x() - font.width(label.symbol()) / 2;
            int symbolY = y + 2 + label.y() - 4;
            guiGraphics.drawString(font, label.symbol(), symbolX, symbolY, label.color(), false);
        }
    }
}
