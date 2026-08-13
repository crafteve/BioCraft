package com.github.crafteve.biocraft.client;

import org.openscience.cdk.interfaces.IAtom;

import java.util.Map;
import java.util.Set;

/**
 * 分子键线式几何运算工具（纯函数，无状态）
 * <p>
 * 原散落在 MoleculeTextureCache 中的坐标/向量计算集中于此：
 * 键线缩进、法向偏移、环内侧方向、双键远离标签方向等，
 * 全部是无状态的纯几何运算，供键线与符号绘制复用
 */
final class MoleculeGeometry {

    private MoleculeGeometry() {
    }

    /**
     * 判断原子是否非氢
     *
     * @param atom 原子
     * @return true 表示非氢原子
     */
    static boolean isHeavy(IAtom atom) {
        return atom.getAtomicNumber() != 1;
    }

    /**
     * 计算两个原子的布局距离（Å）
     *
     * @param a 原子 a
     * @param b 原子 b
     * @return 距离
     */
    static double distance(IAtom a, IAtom b) {
        double dx = a.getPoint2d().x - b.getPoint2d().x;
        double dy = a.getPoint2d().y - b.getPoint2d().y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 计算键方向的法向量（单位向量 * 指定偏移距离）
     *
     * @param from   起点
     * @param to     终点
     * @param offset 法向偏移距离
     * @return 法向量数组
     */
    static double[] normalVector(double[] from, double[] to, double offset) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.max(1e-6, Math.hypot(dx, dy));
        return new double[]{-dy / length * offset, dx / length * offset};
    }

    /**
     * 坐标按法向向量偏移
     *
     * @param pos    原坐标
     * @param normal 法向向量
     * @param side   偏移量（正/负任意浮点）
     * @return 新坐标
     */
    static double[] offset(double[] pos, double[] normal, double side) {
        return new double[]{pos[0] + normal[0] * side, pos[1] + normal[1] * side};
    }

    /**
     * 坐标沿键方向向目标点缩进指定距离（用于杂原子符号处键线留白）
     *
     * @param pos    待缩进的坐标
     * @param toward 缩进方向参考点
     * @param inset  缩进距离（逻辑像素）
     * @return 缩进后的新坐标
     */
    static double[] shrink(double[] pos, double[] toward, double inset) {
        double dx = toward[0] - pos[0];
        double dy = toward[1] - pos[1];
        double length = Math.max(1e-6, Math.hypot(dx, dy));
        return new double[]{pos[0] + dx / length * inset, pos[1] + dy / length * inset};
    }

    /**
     * 计算朝向环质心的内侧方向（单位向量）
     * <p>
     * 方法：把"键中点指向环心"的向量分解为平行键方向与垂直键方向两个分量，
     * 垂直分量恒指向环心一侧（无需正负号判断），
     * 对五元环/融合环等法向与环心方向接近垂直的边数值稳定
     *
     * @param from       键起点
     * @param to         键终点
     * @param ringCenter 环质心
     * @return 指向环心一侧的垂直单位向量
     */
    static double[] inwardDirection(double[] from, double[] to, double[] ringCenter) {
        double bx = to[0] - from[0];
        double by = to[1] - from[1];
        double blen = Math.max(1e-6, Math.hypot(bx, by));
        bx /= blen;
        by /= blen;
        double midX = (from[0] + to[0]) / 2;
        double midY = (from[1] + to[1]) / 2;
        double cx = ringCenter[0] - midX;
        double cy = ringCenter[1] - midY;
        double proj = cx * bx + cy * by;
        double vx = cx - proj * bx;
        double vy = cy - proj * by;
        double vlen = Math.max(1e-9, Math.hypot(vx, vy));
        return new double[]{vx / vlen, vy / vlen};
    }

    /**
     * 计算双键偏移方向：远离杂原子标签的一侧（如 C=O 双键画在碳侧）
     *
     * @param from          键起点
     * @param to            键终点
     * @param begin         起点原子
     * @param end           终点原子
     * @param labeledAtoms  带标签的原子集合（用于判断端点是否有标签）
     * @param pixelPositions 原子坐标表
     * @return 偏移方向单位向量（法向，远离标签侧）
     */
    static double[] awayFromLabels(double[] from, double[] to,
                                   IAtom begin, IAtom end,
                                   Set<IAtom> labeledAtoms,
                                   Map<IAtom, double[]> pixelPositions) {
        double[] normal = normalVector(from, to, 1);
        if (!labeledAtoms.contains(begin) && !labeledAtoms.contains(end)) {
            return normal;
        }
        double midX = (from[0] + to[0]) / 2;
        double midY = (from[1] + to[1]) / 2;
        double[] labelPos = labeledAtoms.contains(begin) ? pixelPositions.get(begin) : pixelPositions.get(end);
        double toLabelX = labelPos[0] - midX;
        double toLabelY = labelPos[1] - midY;
        if (normal[0] * toLabelX + normal[1] * toLabelY > 0) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
        }
        return normal;
    }
}
