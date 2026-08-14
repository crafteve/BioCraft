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
 * 标出，折线用类别主题色纯色矩形逐采样点绘制（采样点间距约 1.4px，2×2 色块
 * 自然连成实线）
 * <p>
 * 性能要点：全部矩形经 {@link Graph#beginLayeredBatch()} 合批为单次网格提交，
 * 严禁在逐像素循环里走即时模式（每矩形一次 beginMesh+submit 会冻结渲染线程）
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

        int[] history = EnzymeGuiContext.history();

        Graph.beginLayeredBatch();
        try {
            // 背景
            Graph.drawFillRect(poseStack.last().pose(),
                    (float) pos.x, (float) pos.y, (float) pos.x + w, (float) pos.y + h, BG);

            if (history.length >= 2) {
                int maxAbs = 1;
                for (int value : history) {
                    maxAbs = Math.max(maxAbs, Math.abs(value));
                }

                float midY = (float) pos.y + h / 2.0F;
                float amplitude = h / 2.0F - 2.0F;

                // 零轴（负数数轴的零点）
                Graph.drawFillRect(poseStack.last().pose(),
                        (float) pos.x, midY, (float) pos.x + w, midY + 1.0F, ZERO_AXIS);

                // 折线：每个采样点一个 2×2 色块（间距约 1.4px，视觉上连成实线）
                int accent = EnzymeGuiContext.accentColor();
                int count = history.length;
                for (int i = 0; i < count; i++) {
                    float x = (float) pos.x + w * i / (count - 1);
                    float y = midY - history[i] * amplitude / maxAbs;
                    Graph.drawFillRect(poseStack.last().pose(), x - 1.0F, y - 1.0F, x + 1.0F, y + 1.0F, accent);
                }
            }

            Graph.drawComplexRoundedBorder(poseStack.last().pose(),
                    (float) pos.x, (float) pos.y, w, h, new float[]{0, 0, 0, 0},
                    new float[]{1, 1, 1, 1}, new int[]{BORDER, BORDER, BORDER, BORDER});
        } finally {
            Graph.endBatch();
        }
    }
}
