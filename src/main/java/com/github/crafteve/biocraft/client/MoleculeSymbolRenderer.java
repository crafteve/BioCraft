package com.github.crafteve.biocraft.client;

import org.openscience.cdk.interfaces.IAtom;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 杂原子符号绘制器：深色底块 + 彩色文字，以及标签碰撞处理
 * <p>
 * 从 MoleculeTextureCache 抽出的符号绘制逻辑，全部为无状态静态方法，
 * 常量取自 MoleculeRenderConstants
 */
final class MoleculeSymbolRenderer {

    private MoleculeSymbolRenderer() {
    }

    /**
     * 绘制杂原子符号：深色不透明底块 + 彩色文字，随分子等比缩放
     * <p>
     * 符号绘制进纹理（而非渲染期叠加 MC 字体），使符号与键线随分子
     * 一起缩放；深色底块采用化学期刊惯例：截断穿过符号区域的键线
     *
     * @param g              Graphics2D 上下文（超采样画布）
     * @param labelTexts     标签表
     * @param pixelPositions 原子坐标表
     */
    static void drawSymbols(Graphics2D g, Map<IAtom, MoleculeTextureCache.AtomText> labelTexts,
                            Map<IAtom, double[]> pixelPositions) {
        int s = MoleculeRenderConstants.SUPERSAMPLE;
        double symbolHeight = MoleculeRenderConstants.BOND_LENGTH_PX * MoleculeRenderConstants.SYMBOL_RATIO * s;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight));
        Font subFont = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight * 0.55));
        FontMetrics mainMetrics = g.getFontMetrics(font);
        FontMetrics subMetrics = g.getFontMetrics(subFont);

        for (Map.Entry<IAtom, MoleculeTextureCache.AtomText> entry : labelTexts.entrySet()) {
            double[] pos = pixelPositions.get(entry.getKey());
            MoleculeTextureCache.AtomText text = entry.getValue();
            double cx = pos[0] * s;
            double cy = pos[1] * s;

            int mainWidth = mainMetrics.stringWidth(text.main());
            int totalWidth = mainWidth + (text.sub().isEmpty() ? 0 : subMetrics.stringWidth(text.sub()));
            int textHeight = mainMetrics.getAscent() + mainMetrics.getDescent();

            // 深色不透明底块（圆角矩形），盖住穿过符号的键线
            int bgX = (int) Math.round(cx - totalWidth / 2.0 - MoleculeRenderConstants.SYMBOL_BG_PADDING * s);
            int bgY = (int) Math.round(cy - textHeight / 2.0 - MoleculeRenderConstants.SYMBOL_BG_PADDING * s);
            int bgW = (int) Math.round(totalWidth + MoleculeRenderConstants.SYMBOL_BG_PADDING * 2 * s);
            int bgH = (int) Math.round(textHeight + MoleculeRenderConstants.SYMBOL_BG_PADDING * 2 * s);
            g.setColor(MoleculeRenderConstants.SYMBOL_BG_COLOR);
            g.fillRoundRect(bgX, bgY, bgW, bgH,
                    (int) Math.round(symbolHeight * 0.3), (int) Math.round(symbolHeight * 0.3));

            // 主串居中绘制
            g.setColor(new Color(text.color()));
            g.setFont(font);
            int baseline = (int) Math.round(cy + (mainMetrics.getAscent() - mainMetrics.getDescent()) / 2.0);
            g.drawString(text.main(), (int) Math.round(cx - totalWidth / 2.0), baseline);
            // 下标数字（小号，绘制在主串右下）
            if (!text.sub().isEmpty()) {
                g.setFont(subFont);
                g.drawString(text.sub(),
                        (int) Math.round(cx - totalWidth / 2.0 + mainWidth),
                        (int) Math.round(baseline + subMetrics.getAscent() * 0.2));
            }
        }
    }

    /**
     * 标签碰撞处理：把相互重叠的杂原子标签沿连线方向推开
     * <p>
     * 旋转或短键场景下（如磷酸链、OH 邻接环原子），标签底块可能重叠。
     * 判定阈值 = 两标签缩进半径之和 × 0.75；迭代 3 次收敛。
     * 注意：此方法在键线绘制前调用，键线缩进基于推开后的位置，视觉一致
     *
     * @param labelTexts   标签表
     * @param positions    原子坐标表（直接修改坐标值）
     * @param labelInsets  标签缩进（底块半径）
     */
    static void resolveLabelCollisions(Map<IAtom, MoleculeTextureCache.AtomText> labelTexts,
                                       Map<IAtom, double[]> positions,
                                       Map<IAtom, Double> labelInsets) {
        List<IAtom> atoms = new ArrayList<>(labelTexts.keySet());
        for (int iteration = 0; iteration < 3; iteration++) {
            for (int i = 0; i < atoms.size(); i++) {
                for (int j = i + 1; j < atoms.size(); j++) {
                    IAtom a = atoms.get(i);
                    IAtom b = atoms.get(j);
                    double[] pa = positions.get(a);
                    double[] pb = positions.get(b);
                    double dx = pb[0] - pa[0];
                    double dy = pb[1] - pa[1];
                    double dist = Math.hypot(dx, dy);
                    double minDist = (labelInsets.getOrDefault(a, 4.0)
                            + labelInsets.getOrDefault(b, 4.0)) * 0.75;
                    if (dist > 1e-6 && dist < minDist) {
                        double push = (minDist - dist) / 2;
                        double ux = dx / dist;
                        double uy = dy / dist;
                        pa[0] -= ux * push;
                        pa[1] -= uy * push;
                        pb[0] += ux * push;
                        pb[1] += uy * push;
                    }
                }
            }
        }
    }
}
