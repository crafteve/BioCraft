package com.github.crafteve.biocraft.client.aui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;

/**
 * 化学平衡双指针条自定义元素
 * <p>
 * 轨道浅灰，左端底物=0、右端=1，按 x = w·k/(1+k) 的单调映射把 Keq（菱形指针）
 * 与 Q（圆点指针）投影到条上；Keq 与 Q 越近说明越接近平衡。纯色矩形绘制
 * <p>
 * Q 由浓度商 ∏产物^系数/∏底物^系数 客户端派生（复用引擎的速率项物种集合，
 * 固定活性物种 H₂O/H⁺ 不参与），零额外网络流量
 */
public final class BiocraftBalanceElement extends Element {
    public static final String TAG_NAME = "BIOCRAFT-BALANCE";

    /** 轨道浅灰色 */
    private static final int TRACK = 0xFFE9EDF3;

    /** Keq 菱形指针色（深灰，静态判决点） */
    private static final int KEQ_POINTER = 0xFF374151;

    /** Q 圆点指针色（类别主题色，动态反应商） */
    private static final int TRACK_BORDER = 0xFFD8DDE6;

    public BiocraftBalanceElement(Document document) {
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
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = h / 2.0F;
        float[] radii = {radius, radius, radius, radius};

        Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                (float) pos.x, (float) pos.y, w, h, radii, TRACK);

        double keq = EnzymeGuiContext.data().keq();
        double q = computeQ();

        int keqX = (int) Math.round(w * keq / (1.0 + keq));
        int qX = (int) Math.round(w * q / (1.0 + q));

        // Keq 菱形指针：一条 2px 竖线 + 上下两个三角近似（简化为加粗竖条）
        drawPointer(poseStack, (float) pos.x + keqX - 1, (float) pos.y + 1, 2.0F, h - 2.0F, KEQ_POINTER);
        // Q 圆点指针：5px 实心圆点
        float qr = Math.min(5.0F, h / 2.0F);
        drawPointer(poseStack, (float) pos.x + qX - qr / 2.0F, (float) pos.y + (h - qr) / 2.0F, qr, qr, EnzymeGuiContext.accentColor());

        Graph.drawComplexRoundedBorder(poseStack.last().pose(),
                (float) pos.x, (float) pos.y, w, h, radii,
                new float[]{1, 1, 1, 1}, new int[]{TRACK_BORDER, TRACK_BORDER, TRACK_BORDER, TRACK_BORDER});
    }

    /**
     * 计算当前浓度商 Q = ∏产物浓度^系数 / ∏底物浓度^系数
     * <p>
     * 浓度钳制到最小 1e-9 避免除零；速率项物种（排除固定活性物种）由反应网络档案提供
     *
     * @return 浓度商（无单位）
     */
    private double computeQ() {
        ReactionDefinition def = EnzymeGuiContext.definition();
        if (def == null) {
            return 1.0;
        }
        double numerator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : def.getRateProducts()) {
            double c = EnzymeGuiContext.concentration(entry.index());
            numerator *= Math.pow(Math.max(c, 1e-9), entry.coeff());
        }
        double denominator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : def.getRateReactants()) {
            double c = EnzymeGuiContext.concentration(entry.index());
            denominator *= Math.pow(Math.max(c, 1e-9), entry.coeff());
        }
        return numerator / denominator;
    }

    private void drawPointer(PoseStack poseStack, float x, float y, float w, float h, int color) {
        Graph.drawFillRect(poseStack.last().pose(), x, y, x + w, y + h, color);
    }
}
