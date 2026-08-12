package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.BioCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分子键线式结构图缓存（Dist.CLIENT）
 * <p>
 * 自绘管线：CDK 仅负责 SMILES 解析与 2D 坐标生成，图像由本类以
 * 化学期刊风格绘制（Java2D 抗锯齿平滑键线，圆头端点），
 * 杂原子元素符号不画入纹理，改由渲染时用 MC 像素字体叠加，保证风格统一：
 * <ul>
 *   <li>单键：1.8px 抗锯齿直线（平滑、无像素锯齿）</li>
 *   <li>双键：法向偏移的平行双线</li>
 *   <li>三键：主键 + 两侧副键</li>
 *   <li>芳香键：虚线</li>
 *   <li>碳不标符号、氢不绘制（键线式约定）</li>
 * </ul>
 * 尺寸自适应：画布缩放使平均键长约为 10px，高度上限 56px、宽度上限 128px，
 * 小分子自然缩小、大分子封顶；超过 150 重原子的复杂分子（如大型蛋白质）
 * 跳过生成，由 tooltip 显示提示行
 * <p>
 * 首次访问某分子时生成并缓存（DynamicTexture），之后零开销
 */
public final class MoleculeTextureCache {

    /** 目标最大高度（px） */
    private static final int TARGET_HEIGHT = 56;
    /** 目标最大宽度（px） */
    private static final int MAX_WIDTH = 128;
    /** 画布四周留白（px），为原子符号与键线厚度预留空间 */
    private static final int PADDING = 12;
    /** 目标平均键长（px），决定分子的显示大小 */
    private static final double BOND_LENGTH_PX = 10.0;
    /** 键线宽度（px） */
    private static final float BOND_STROKE_WIDTH = 1.8f;
    /** 双键平行线偏移距离（px） */
    private static final double DOUBLE_BOND_OFFSET = 1.6;
    /** 三键副键偏移距离（px） */
    private static final double TRIPLE_BOND_OFFSET = 3.0;
    /** 重原子数上限，超过则判定为过于复杂的分子，不生成结构图 */
    private static final int MAX_HEAVY_ATOMS = 150;
    /** 键线颜色（亮白，深色 tooltip 背景上清晰） */
    private static final Color BOND_COLOR = new Color(0xE0, 0xE0, 0xE0);

    /** 杂原子 MC 风格色板：元素符号 -> ARGB 颜色 */
    private static final Map<String, Integer> ATOM_COLORS = new HashMap<>();

    static {
        ATOM_COLORS.put("O", 0xFFFF5555);
        ATOM_COLORS.put("N", 0xFF5555FF);
        ATOM_COLORS.put("P", 0xFFFFAA00);
        ATOM_COLORS.put("S", 0xFFFFFF55);
        ATOM_COLORS.put("Cl", 0xFF55FF55);
        ATOM_COLORS.put("Na", 0xFFFFD700);
        ATOM_COLORS.put("K", 0xFFAA66FF);
        ATOM_COLORS.put("Fe", 0xFFFF8C42);
        ATOM_COLORS.put("Mg", 0xFF7CFC00);
        ATOM_COLORS.put("Ca", 0xFFE6E6E6);
        ATOM_COLORS.put("B", 0xFFE6E6E6);
        ATOM_COLORS.put("I", 0xFFAA66CC);
    }

    /** SMILES -> 生成的分子图 */
    private static final Map<String, MoleculeImage> CACHE = new HashMap<>();

    /** 分子图：纹理引用 + 像素尺寸 + 杂原子标签列表 */
    public record MoleculeImage(ResourceLocation texture, int width, int height, List<AtomLabel> labels) {
    }

    /** 杂原子标签：符号 + 纹理内像素坐标 + 颜色（渲染时用 MC 字体叠加） */
    public record AtomLabel(String symbol, int x, int y, int color) {
    }

    private MoleculeTextureCache() {
    }

    /**
     * 获取分子图（带缓存，首次生成后不再计算）
     * <p>
     * 复杂分子（重原子 &gt; 150）或解析失败返回 null，调用方降级为提示行
     *
     * @param smiles SMILES 结构式
     * @return 分子图信息，无法生成时为 null
     */
    public static MoleculeImage get(String smiles) {
        if (!CACHE.containsKey(smiles)) {
            CACHE.put(smiles, generate(smiles));
        }
        return CACHE.get(smiles);
    }

    /**
     * 执行完整自绘管线：解析 -> 2D 坐标 -> 归一化 -> 平滑绘制 -> 注册纹理
     *
     * @param smiles SMILES 结构式
     * @return 分子图信息，复杂分子或解析失败为 null
     */
    private static MoleculeImage generate(String smiles) {
        try {
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer container = parser.parseSmiles(smiles);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);

            int heavyAtoms = 0;
            for (IAtom atom : container.atoms()) {
                if (atom.getAtomicNumber() != 1) {
                    heavyAtoms++;
                }
            }
            if (heavyAtoms > MAX_HEAVY_ATOMS) {
                BioCraft.LOGGER.info("分子过于复杂（重原子 {}），跳过结构图生成: {}", heavyAtoms, smiles);
                return null;
            }

            StructureDiagramGenerator sdg = new StructureDiagramGenerator();
            sdg.setMolecule(container);
            sdg.generateCoordinates();
            IAtomContainer laidOut = sdg.getMolecule();

            return rasterize(smiles, laidOut);
        } catch (CDKException e) {
            BioCraft.LOGGER.warn("分子结构图生成失败: {} ({})", smiles, e.getMessage());
            return null;
        }
    }

    /**
     * 布局坐标 -> 画布：计算缩放比例，用 Java2D 平滑绘制键线骨架，收集杂原子标签
     *
     * @param smiles   SMILES（用于纹理命名）
     * @param molecule 已生成 2D 坐标的分子
     * @return 分子图信息
     */
    private static MoleculeImage rasterize(String smiles, IAtomContainer molecule) {
        // 计算原子坐标范围与平均键长（Å）
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double bondLengthSum = 0;
        int bondCount = 0;
        for (IAtom atom : molecule.atoms()) {
            if (atom.getAtomicNumber() == 1) {
                continue;
            }
            double x = atom.getPoint2d().x;
            double y = atom.getPoint2d().y;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        for (IBond bond : molecule.bonds()) {
            if (isHeavy(bond.getBegin()) && isHeavy(bond.getEnd())) {
                bondLengthSum += distance(bond.getBegin(), bond.getEnd());
                bondCount++;
            }
        }

        double scale = bondCount > 0 ? BOND_LENGTH_PX / (bondLengthSum / bondCount) : 1.0;
        int spanW = (int) Math.ceil((maxX - minX) * scale) + PADDING * 2;
        int spanH = (int) Math.ceil((maxY - minY) * scale) + PADDING * 2;
        // 画布上限约束：超出则整体收缩
        if (spanW > MAX_WIDTH || spanH > TARGET_HEIGHT) {
            double shrink = Math.min((double) MAX_WIDTH / spanW, (double) TARGET_HEIGHT / spanH);
            scale *= shrink;
            spanW = (int) Math.ceil((maxX - minX) * scale) + PADDING * 2;
            spanH = (int) Math.ceil((maxY - minY) * scale) + PADDING * 2;
        }
        int width = Math.max(spanW, 16);
        int height = Math.max(spanH, 16);

        // 原子 -> 画布像素坐标（浮点，供平滑键线精确对接；标签坐标取整）
        Map<IAtom, double[]> pixelPositions = new HashMap<>();
        for (IAtom atom : molecule.atoms()) {
            if (atom.getAtomicNumber() == 1) {
                continue;
            }
            double px = PADDING + (atom.getPoint2d().x - minX) * scale;
            double py = PADDING + (atom.getPoint2d().y - minY) * scale;
            pixelPositions.put(atom, new double[]{px, py});
        }

        // Java2D 抗锯齿平滑绘制键线骨架
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(BOND_COLOR);
            g.setStroke(new BasicStroke(BOND_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            for (IBond bond : molecule.bonds()) {
                IAtom begin = bond.getBegin();
                IAtom end = bond.getEnd();
                if (!isHeavy(begin) || !isHeavy(end)) {
                    continue;
                }
                double[] p1 = pixelPositions.get(begin);
                double[] p2 = pixelPositions.get(end);
                IBond.Order order = bond.getOrder();
                if (bond.isAromatic()) {
                    drawDashedLine(g, p1, p2);
                } else if (order == IBond.Order.DOUBLE) {
                    drawDoubleLine(g, p1, p2);
                } else if (order == IBond.Order.TRIPLE) {
                    drawTripleLine(g, p1, p2);
                } else {
                    drawLine(g, p1, p2);
                }
            }
        } finally {
            g.dispose();
        }

        // 转换 BufferedImage（ARGB）-> NativeImage（RGBA 字节序）
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = buffered.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gr = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                image.setPixelRGBA(x, y, (r << 24) | (gr << 16) | (b << 8) | a);
            }
        }

        // 收集杂原子标签（键线式约定：碳不标符号、氢不绘制）
        List<AtomLabel> labels = new ArrayList<>();
        for (IAtom atom : molecule.atoms()) {
            int atomicNumber = atom.getAtomicNumber();
            if (atomicNumber == 1 || atomicNumber == 6) {
                continue;
            }
            double[] pos = pixelPositions.get(atom);
            labels.add(new AtomLabel(atom.getSymbol(), (int) Math.round(pos[0]), (int) Math.round(pos[1]),
                    ATOM_COLORS.getOrDefault(atom.getSymbol(), 0xFFD0D0D0)));
        }

        // 注册纹理
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        texture.upload();
        ResourceLocation location = Minecraft.getInstance().getTextureManager()
                .register("biocraft/molecule_" + Math.abs(smiles.hashCode()), texture);

        return new MoleculeImage(location, width, height, labels);
    }

    /**
     * 绘制单键：抗锯齿直线
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素（浮点）
     * @param to   终点像素（浮点）
     */
    private static void drawLine(Graphics2D g, double[] from, double[] to) {
        g.drawLine((int) Math.round(from[0]), (int) Math.round(from[1]),
                (int) Math.round(to[0]), (int) Math.round(to[1]));
    }

    /**
     * 绘制双键：垂直于键方向的平行双线（化学期刊风格）
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素
     * @param to   终点像素
     */
    private static void drawDoubleLine(Graphics2D g, double[] from, double[] to) {
        double[] normal = normalVector(from, to, DOUBLE_BOND_OFFSET);
        drawLine(g, offset(from, normal, -1), offset(to, normal, -1));
        drawLine(g, offset(from, normal, 1), offset(to, normal, 1));
    }

    /**
     * 绘制三键：主键 + 两侧副键
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素
     * @param to   终点像素
     */
    private static void drawTripleLine(Graphics2D g, double[] from, double[] to) {
        drawLine(g, from, to);
        double[] normal = normalVector(from, to, TRIPLE_BOND_OFFSET);
        drawLine(g, offset(from, normal, -1), offset(to, normal, -1));
        drawLine(g, offset(from, normal, 1), offset(to, normal, 1));
    }

    /**
     * 绘制芳香键：沿键方向的虚线
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素
     * @param to   终点像素
     */
    private static void drawDashedLine(Graphics2D g, double[] from, double[] to) {
        g.setStroke(new BasicStroke(BOND_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{5f, 4f}, 0f));
        drawLine(g, from, to);
        g.setStroke(new BasicStroke(BOND_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    /**
     * 判断原子是否非氢
     *
     * @param atom 原子
     * @return true 表示非氢原子
     */
    private static boolean isHeavy(IAtom atom) {
        return atom.getAtomicNumber() != 1;
    }

    /**
     * 计算两个原子的布局距离（Å）
     *
     * @param a 原子 a
     * @param b 原子 b
     * @return 距离
     */
    private static double distance(IAtom a, IAtom b) {
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
    private static double[] normalVector(double[] from, double[] to, double offset) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.max(1e-6, Math.hypot(dx, dy));
        return new double[]{-dy / length * offset, dx / length * offset};
    }

    /**
     * 坐标按法向量偏移
     *
     * @param pos    原坐标
     * @param normal 法向量
     * @param side   偏移方向（-1 或 1）
     * @return 新坐标
     */
    private static double[] offset(double[] pos, double[] normal, int side) {
        return new double[]{pos[0] + normal[0] * side, pos[1] + normal[1] * side};
    }
}
