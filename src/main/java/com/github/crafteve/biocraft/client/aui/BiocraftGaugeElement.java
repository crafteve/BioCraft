package com.github.crafteve.biocraft.client.aui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;

/**
 * 物种浓度进度条自定义元素（每物种一张卡片内一个）
 * <p>
 * 轨道与填充均在本元素 BODY 绘制阶段用 Graph 纯色矩形绘制，不依赖像素贴图，
 * 保证任意 GUI 缩放与窗口尺寸下清晰锐利（无像素模糊）。填充宽度 = 物种浓度×元素宽，
 * 填充色由 {@code data-color} 属性指定（物品染色，高饱和度纯色）
 * <p>
 * 数据来源：{@link EnzymeGuiContext#concentration(int)}，逐帧读取，零 DOM 重建
 */
public final class BiocraftGaugeElement extends Element {
    public static final String TAG_NAME = "BIOCRAFT-GAUGE";

    /** 轨道浅灰色 */
    private static final int TRACK = 0xFFE9EDF3;

    /** 本元素对应的物种下标（槽位下标 = 物种下标） */
    private int slotIndex = -1;

    /** 填充色（ARGB，由 data-color 十六进制解析） */
    private int fillColor = 0xFFFFA94D;

    public BiocraftGaugeElement(Document document) {
        super(document, TAG_NAME);
    }

    /**
     * DOM 初始化钩子：从属性缓存槽位下标与填充色，避免每帧解析字符串
     */
    @Override
    protected void onInitFromDom(Element origin) {
        String slot = getAttribute("data-slot");
        if (slot != null && !slot.isBlank()) {
            try {
                slotIndex = Integer.parseInt(slot.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String color = getAttribute("data-color");
        if (color != null && !color.isBlank()) {
            try {
                fillColor = (int) Long.parseLong(color.trim().replaceFirst("^#", ""), 16);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY) {
            return;
        }
        Position pos = Position.of(this);
        Size size = Box.of(this).elementSize();
        float w = (float) size.width();
        float h = (float) size.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = h / 2.0F;
        float[] radii = {radius, radius, radius, radius};

        Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                (float) pos.x, (float) pos.y, w, h, radii, TRACK);

        double concentration = EnzymeGuiContext.concentration(slotIndex);
        float fillW = (float) (w * Math.max(0.0, Math.min(1.0, concentration)));
        if (fillW > 0.5F) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                    (float) pos.x, (float) pos.y, fillW, h, radii, fillColor);
        }
    }
}
