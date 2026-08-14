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
 * 反应进度条自定义元素
 * <p>
 * 展示主产物（最后一个产物物种）的浓度 0~1，填充色用类别主题色（--accent 语义），
 * 轨道浅灰。纯色矩形绘制，无像素贴图，任意缩放清晰
 */
public final class BiocraftProgressElement extends Element {
    public static final String TAG_NAME = "BIOCRAFT-PROGRESS";

    /** 轨道浅灰色 */
    private static final int TRACK = 0xFFE9EDF3;

    public BiocraftProgressElement(Document document) {
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

        // 主产物 = 最后一个产物物种（物种下标 n-1），与 BE 的 cachedProgress 语义一致
        int n = EnzymeGuiContext.speciesCount();
        double progress = EnzymeGuiContext.concentration(n - 1);
        float fillW = (float) (w * Math.max(0.0, Math.min(1.0, progress)));
        if (fillW > 0.5F) {
            Graph.drawUnifiedRoundedRect(poseStack.last().pose(),
                    (float) pos.x, (float) pos.y, fillW, h, radii, EnzymeGuiContext.accentColor());
        }
    }
}
