package com.github.crafteve.biocraft.client.aui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import org.joml.Matrix4f;

/**
 * v-t 通量折线图自定义元素（含负数数轴）
 * <p>
 * 纵轴以元素垂直中线为零轴：正通量（正向反应）在上，负通量（逆向反应）在下，
 * 按历史快照的绝对值最大值归一化。折线用三角形带绘制为连续粗线（非逐像素色块，
 * 无锯齿），全部顶点经 {@link Graph#beginLayeredBatch()} 合批单次提交
 */
public final class BiocraftVtChartElement extends Element {
    public static final String TAG_NAME = "BIOCRAFT-VTCHART";

    private static final int BG = 0xFFFBFCFE;
    private static final int ZERO_AXIS = 0xFFC9D2DE;
    private static final int BORDER = 0xFFD8DEE6;

    /** 折线粗度（逻辑像素） */
    private static final float LINE_WIDTH = 1.5F;

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

        int[] history = EnzymeGuiContext.history();
        Matrix4f mat = poseStack.last().pose();

        Graph.beginLayeredBatch();
        try {
            // 背景
            Graph.drawFillRect(mat, (float) pos.x, (float) pos.y, (float) pos.x + w, (float) pos.y + h, BG);

            if (history.length >= 2) {
                int maxAbs = 1;
                for (int value : history) {
                    maxAbs = Math.max(maxAbs, Math.abs(value));
                }
                float midY = (float) pos.y + h / 2.0F;
                float amplitude = h / 2.0F - 2.0F;

                // 零轴（负数数轴的零点）
                Graph.drawFillRect(mat, (float) pos.x, midY, (float) pos.x + w, midY + 1.0F, ZERO_AXIS);

                // 折线：连续粗线段（三角形带），平滑无锯齿
                int accent = EnzymeGuiContext.accentColor();
                int count = history.length;
                float prevX = (float) pos.x;
                float prevY = midY - history[0] * amplitude / maxAbs;
                for (int i = 1; i < count; i++) {
                    float x = (float) pos.x + w * i / (count - 1);
                    float y = midY - history[i] * amplitude / maxAbs;
                    drawThickLine(mat, prevX, prevY, x, y, LINE_WIDTH, accent);
                    prevX = x;
                    prevY = y;
                }
            }

            Graph.drawComplexRoundedBorder(mat,
                    (float) pos.x, (float) pos.y, w, h, new float[]{0, 0, 0, 0},
                    new float[]{1, 1, 1, 1}, new int[]{BORDER, BORDER, BORDER, BORDER});
        } finally {
            Graph.endBatch();
        }
    }

    /**
     * 画一段粗线（两个三角形拼成的四边形）
     * <p>
     * 沿线段方向取单位法向量偏移半粗度得到四个角点，按三角形拆分成 6 个顶点
     * 发射进当前批次（TRIANGLES 网格），配合相邻线段形成连续平滑折线
     *
     * @param mat       投影矩阵
     * @param x1        起点 x
     * @param y1        起点 y
     * @param x2        终点 x
     * @param y2        终点 y
     * @param thickness 线粗
     * @param color     颜色
     */
    private void drawThickLine(Matrix4f mat, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001F) {
            return;
        }
        float nx = -dy / length * thickness / 2.0F;
        float ny = dx / length * thickness / 2.0F;

        float ax = x1 + nx;
        float ay = y1 + ny;
        float bx = x1 - nx;
        float by = y1 - ny;
        float cx = x2 - nx;
        float cy = y2 - ny;
        float dx2 = x2 + nx;
        float dy2 = y2 + ny;

        Graph.vtx(null, mat, ax, ay, color);
        Graph.vtx(null, mat, bx, by, color);
        Graph.vtx(null, mat, cx, cy, color);
        Graph.vtx(null, mat, ax, ay, color);
        Graph.vtx(null, mat, cx, cy, color);
        Graph.vtx(null, mat, dx2, dy2, color);
    }
}
