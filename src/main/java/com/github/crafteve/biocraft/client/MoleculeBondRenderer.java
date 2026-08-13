package com.github.crafteve.biocraft.client;

import java.awt.BasicStroke;
import java.awt.Graphics2D;

/**
 * 键线绘制器：单键/双键/三键/环内双键的 Java2D 绘制（超采样画布）
 * <p>
 * 从 MoleculeTextureCache 抽出的键线绘制逻辑，全部为无状态静态方法，
 * 常量取自 MoleculeRenderConstants，几何运算取自 MoleculeGeometry
 */
final class MoleculeBondRenderer {

    private MoleculeBondRenderer() {
    }

    /**
     * 创建键线画笔（线宽按超采样倍率放大）
     *
     * @param dash 虚线模式（null 表示实线）
     * @return 画笔
     */
    static BasicStroke createStroke(float[] dash) {
        int s = MoleculeRenderConstants.SUPERSAMPLE;
        if (dash == null) {
            return new BasicStroke(MoleculeRenderConstants.BOND_STROKE_WIDTH * s,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        float[] scaledDash = new float[dash.length];
        for (int i = 0; i < dash.length; i++) {
            scaledDash[i] = dash[i] * s;
        }
        return new BasicStroke(MoleculeRenderConstants.BOND_STROKE_WIDTH * s,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, scaledDash, 0f);
    }

    /**
     * 绘制单键：细抗锯齿直线（坐标从逻辑尺寸换算到超采样画布）
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素（逻辑坐标）
     * @param to   终点像素（逻辑坐标）
     */
    static void drawLine(Graphics2D g, double[] from, double[] to) {
        int s = MoleculeRenderConstants.SUPERSAMPLE;
        g.drawLine((int) Math.round(from[0] * s), (int) Math.round(from[1] * s),
                (int) Math.round(to[0] * s), (int) Math.round(to[1] * s));
    }

    /**
     * 绘制双键：垂直于键方向的平行双线（化学期刊风格）
     * <p>
     * 偏移方向由调用方指定（朝环内侧或远离杂原子标签）
     *
     * @param g       Graphics2D 上下文
     * @param from    起点像素（逻辑坐标）
     * @param to      终点像素（逻辑坐标）
     * @param dirUnit 偏移方向单位向量（法向方向）
     */
    static void drawDoubleLine(Graphics2D g, double[] from, double[] to, double[] dirUnit) {
        double d = MoleculeRenderConstants.DOUBLE_BOND_OFFSET;
        drawLine(g, MoleculeGeometry.offset(from, dirUnit, -d), MoleculeGeometry.offset(to, dirUnit, -d));
        drawLine(g, MoleculeGeometry.offset(from, dirUnit, d), MoleculeGeometry.offset(to, dirUnit, d));
    }

    /**
     * 绘制环内双键（Kekulé 风格）：键轴一条线 + 朝环内侧偏移一条线
     * <p>
     * 化学结构式惯例：苯环等芳香环的双键画在环内侧
     *
     * @param g          Graphics2D 上下文
     * @param from       起点像素（逻辑坐标）
     * @param to         终点像素（逻辑坐标）
     * @param ringCenter 所属环的质心（用于确定内侧方向）
     */
    static void drawInwardDouble(Graphics2D g, double[] from, double[] to, double[] ringCenter) {
        double[] inward = MoleculeGeometry.inwardDirection(from, to, ringCenter);
        drawLine(g, from, to);
        // 内侧偏移线两端各缩短 2px（化学期刊画法：环内双键的内侧线较短）
        double shorten = 2.0;
        double[] inFrom = MoleculeGeometry.shrink(from, to, shorten);
        double[] inTo = MoleculeGeometry.shrink(to, from, shorten);
        double d = MoleculeRenderConstants.DOUBLE_BOND_OFFSET * 2;
        drawLine(g, MoleculeGeometry.offset(inFrom, inward, d), MoleculeGeometry.offset(inTo, inward, d));
    }

    /**
     * 绘制三键：主键 + 两侧副键
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素（逻辑坐标）
     * @param to   终点像素（逻辑坐标）
     */
    static void drawTripleLine(Graphics2D g, double[] from, double[] to) {
        drawLine(g, from, to);
        double[] normal = MoleculeGeometry.normalVector(from, to, MoleculeRenderConstants.TRIPLE_BOND_OFFSET);
        drawLine(g, MoleculeGeometry.offset(from, normal, -1), MoleculeGeometry.offset(to, normal, -1));
        drawLine(g, MoleculeGeometry.offset(from, normal, 1), MoleculeGeometry.offset(to, normal, 1));
    }
}
