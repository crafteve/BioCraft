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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分子键线式结构图缓存（Dist.CLIENT）
 * <p>
 * 自绘管线：CDK 仅负责 SMILES 解析与 2D 坐标生成，图像完全由本类
 * 以 MC 像素风格绘制（2px 无抗锯齿键线 + 透明背景），保证与 tooltip 风格统一：
 * <ul>
 *   <li>单键：2px 亮白直线（Bresenham）</li>
 *   <li>双键：垂直偏移 3px 的平行双线</li>
 *   <li>芳香键：间隔虚线</li>
 *   <li>碳不标符号、氢不绘制（键线式约定），杂原子符号由渲染时叠加 MC 字体</li>
 * </ul>
 * 尺寸自适应：画布缩放使平均键长约为 9px，高度上限 56px、宽度上限 128px，
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
    private static final double BOND_LENGTH_PX = 9.0;
    /** 重原子数上限，超过则判定为过于复杂的分子，不生成结构图 */
    private static final int MAX_HEAVY_ATOMS = 150;
    /** 键线颜色（RGBA 亮白） */
    private static final int BOND_COLOR = 0xE0E0E0FF;

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
     * 执行完整自绘管线：解析 -> 2D 坐标 -> 归一化 -> 像素绘制 -> 注册纹理
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
     * 布局坐标 -> 像素画布：计算缩放比例并绘制键线骨架，收集杂原子标签
     *
     * @param smiles   SMILES（用于纹理命名）
     * @param molecule 已生成 2D 坐标的分子
     * @return 分子图信息
     */
    private static MoleculeImage rasterize(String smiles, IAtomContainer molecule) {
        // 计算原子坐标范围与平均键长
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

        // 原子 -> 像素坐标映射（居中于画布）
        Map<IAtom, int[]> pixelPositions = new HashMap<>();
        for (IAtom atom : molecule.atoms()) {
            if (atom.getAtomicNumber() == 1) {
                continue;
            }
            int px = PADDING + (int) Math.round((atom.getPoint2d().x - minX) * scale);
            int py = PADDING + (int) Math.round((atom.getPoint2d().y - minY) * scale);
            pixelPositions.put(atom, new int[]{px, py});
        }

        // 绘制键线骨架
        NativeImage image = new NativeImage(width, height, true);
        for (IBond bond : molecule.bonds()) {
            IAtom begin = bond.getBegin();
            IAtom end = bond.getEnd();
            if (!isHeavy(begin) || !isHeavy(end)) {
                continue;
            }
            int[] p1 = pixelPositions.get(begin);
            int[] p2 = pixelPositions.get(end);
            IBond.Order order = bond.getOrder();
            if (bond.isAromatic()) {
                drawDashedLine(image, p1, p2, BOND_COLOR);
            } else if (order == IBond.Order.DOUBLE) {
                drawDoubleLine(image, p1, p2, BOND_COLOR);
            } else if (order == IBond.Order.TRIPLE) {
                drawTripleLine(image, p1, p2, BOND_COLOR);
            } else {
                drawThickLine(image, p1, p2, BOND_COLOR);
            }
        }

        // 收集杂原子标签（键线式约定：碳不标符号、氢不绘制）
        List<AtomLabel> labels = new ArrayList<>();
        for (IAtom atom : molecule.atoms()) {
            int atomicNumber = atom.getAtomicNumber();
            if (atomicNumber == 1 || atomicNumber == 6) {
                continue;
            }
            int[] pos = pixelPositions.get(atom);
            labels.add(new AtomLabel(atom.getSymbol(), pos[0], pos[1],
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
     * 绘制 2px 粗直线（Bresenham 采样 + 2x2 像素块）
     *
     * @param image 目标图像
     * @param from  起点像素
     * @param to    终点像素
     * @param color 颜色（RGBA）
     */
    private static void drawThickLine(NativeImage image, int[] from, int[] to, int color) {
        double steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]));
        int pixelSteps = (int) Math.max(1, steps);
        for (int i = 0; i <= pixelSteps; i++) {
            double t = (double) i / pixelSteps;
            int x = (int) Math.round(from[0] + (to[0] - from[0]) * t);
            int y = (int) Math.round(from[1] + (to[1] - from[1]) * t);
            setPixelBlock(image, x, y, color);
        }
    }

    /**
     * 绘制双键：垂直于键方向的 3px 偏移平行双线
     *
     * @param image 目标图像
     * @param from  起点像素
     * @param to    终点像素
     * @param color 颜色（RGBA）
     */
    private static void drawDoubleLine(NativeImage image, int[] from, int[] to, int color) {
        double[] normal = normalVector(from, to, 3);
        drawThickLine(image, offset(from, normal, -1), offset(to, normal, -1), color);
        drawThickLine(image, offset(from, normal, 1), offset(to, normal, 1), color);
    }

    /**
     * 绘制三键：3px 偏移的三条平行线（主键 + 两侧副键）
     *
     * @param image 目标图像
     * @param from  起点像素
     * @param to    终点像素
     * @param color 颜色（RGBA）
     */
    private static void drawTripleLine(NativeImage image, int[] from, int[] to, int color) {
        drawThickLine(image, from, to, color);
        double[] normal = normalVector(from, to, 4);
        drawThickLine(image, offset(from, normal, -1), offset(to, normal, -1), color);
        drawThickLine(image, offset(from, normal, 1), offset(to, normal, 1), color);
    }

    /**
     * 绘制芳香键：沿键方向的间隔虚线
     *
     * @param image 目标图像
     * @param from  起点像素
     * @param to    终点像素
     * @param color 颜色（RGBA）
     */
    private static void drawDashedLine(NativeImage image, int[] from, int[] to, int color) {
        int steps = (int) Math.max(1, Math.hypot(to[0] - from[0], to[1] - from[1]));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(from[0] + (to[0] - from[0]) * t);
            int y = (int) Math.round(from[1] + (to[1] - from[1]) * t);
            if ((i / 4) % 2 == 0) {
                setPixelBlock(image, x, y, color);
            }
        }
    }

    /**
     * 计算键方向的法向量（单位向量 * 指定偏移距离）
     *
     * @param from   起点
     * @param to     终点
     * @param offset 法向偏移距离
     * @return 法向量数组
     */
    private static double[] normalVector(int[] from, int[] to, int offset) {
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
    private static int[] offset(int[] pos, double[] normal, int side) {
        return new int[]{(int) Math.round(pos[0] + normal[0] * side),
                (int) Math.round(pos[1] + normal[1] * side)};
    }

    /**
     * 以指定像素为中心绘制 2x2 像素块（保证 2px 线宽）
     *
     * @param image 目标图像
     * @param x     中心 x
     * @param y     中心 y
     * @param color 颜色（RGBA）
     */
    private static void setPixelBlock(NativeImage image, int x, int y, int color) {
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 2; dx++) {
                int px = x + dx - 1;
                int py = y + dy - 1;
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    image.setPixelRGBA(px, py, color);
                }
            }
        }
    }
}
