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
 * v-t 通量折线图自定义元素（含负数数轴）
 * <p>
 * 纵轴以元素垂直中线为零轴：正通量（正向反应）在上，负通量（逆向反应）在下，
 * 按历史快照的绝对值最大值归一化；横轴为 100 tick（5 秒）窗口。零轴用浅灰细线
 * 标出，折线用类别主题色纯色矩形逐段绘制，无像素贴图
 */
public final class BiocraftVtChartElement extends Element {
    public static final String TAG_NAME = "BIOCRAFT-VTCHART";

    /** 背景与零轴 */
    private static final int BG = 0xFFFAFCFE;
    private static final int ZERO_AXIS = 0xFFC9D2DE;
    private static final int BORDER = 0xFFD8DDE6;

    public BiocraftVtChartElement(Document document) {
        super(document, TAG_NAME);
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
        if (w <= 2 || h <= 2) {
            return;
        }

        // 背景
        Graph.drawFillRect(poseStack.last().pose(),
                (float) pos.x, (float) pos.y, (float) pos.x + w, (float) pos.y + h, BG);

        int[] history = EnzymeGuiContext.history();
        if (history.length < 2) {
            return;
        }

        // 绝对值最大值归一化（含负数）
        int maxAbs = 1;
        for (int value : history) {
            maxAbs = Math.max(maxAbs, Math.abs(value));
        }

        float midY = (float) pos.y + h / 2.0F;
        float amplitude = h / 2.0F - 2.0F;

        // 零轴（负数数轴的零点）
        Graph.drawFillRect(poseStack.last().pose(),
                (float) pos.x, midY, (float) pos.x + w, midY + 1.0F, ZERO_AXIS);

        // 折线（逐段画 1.5px 粗竖条/斜段，简化用竖线连接相邻采样点）
        int count = history.length;
        int accent = EnzymeGuiContext.accentColor();
        float prevX = (float) pos.x;
        float prevY = midY - history[0] * amplitude / maxAbs;
        for (int i = 1; i < count; i++) {
            float x = (float) pos.x + w * i / (count - 1);
            float y = midY - history[i] * amplitude / maxAbs;
            drawSegment(poseStack, prevX, prevY, x, y, accent);
            prevX = x;
            prevY = y;
        }

        Graph.drawComplexRoundedBorder(poseStack.last().pose(),
                (float) pos.x, (float) pos.y, w, h, new float[]{0, 0, 0, 0},
                new float[]{1, 1, 1, 1}, new int[]{BORDER, BORDER, BORDER, BORDER});
    }

    /**
     * 用逐像素整型插值画一段粗线（1.5px 宽，简化 Bresenham 采样）
     * <p>
     * 纯色矩形像素段，避免引入纹理，任意缩放清晰
     */
    private void drawSegment(PoseStack poseStack, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            Graph.drawFillRect(poseStack.last().pose(), x1 - 0.5F, y1 - 0.5F, x1 + 1.0F, y1 + 1.0F, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            float x = x1 + dx * i / steps;
            float y = y1 + dy * i / steps;
            Graph.drawFillRect(poseStack.last().pose(), x - 0.5F, y - 0.5F, x + 1.0F, y + 1.0F, color);
        }
    }
}
