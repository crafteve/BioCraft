package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.BioCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.openscience.cdk.aromaticity.Kekulization;
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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分子键线式结构图缓存（Dist.CLIENT）
 * <p>
 * 自绘管线：CDK 仅负责 SMILES 解析与 2D 坐标生成，图像由本类以
 * 化学期刊风格绘制（Java2D 抗锯齿平滑细键线，圆头端点）：
 * <ul>
 *   <li>单键：细抗锯齿直线（显示线宽 1.0px）</li>
 *   <li>双键：法向偏移的平行双线</li>
 *   <li>三键：主键 + 两侧副键</li>
 *   <li>芳香键：按 Kekulé 风格在环上交替标记单/双键（非虚线）</li>
 *   <li>碳不标符号、氢不绘制（键线式约定）</li>
 *   <li>杂原子端键线向内缩进，避免线与元素符号重叠</li>
 * </ul>
 * 抗锯齿策略：以 2 倍超采样分辨率绘制（SUPERSAMPLE），显示时缩小，
 * 获得高密度采样下的平滑细线；杂原子元素符号不画入纹理，
 * 由渲染时用 MC 像素字体叠加（字体本身是像素风，不受超采样影响）
 * <p>
 * 尺寸自适应：画布缩放使平均键长约为 10px，高度上限 56px、宽度上限 128px，
 * 小分子自然缩小、大分子封顶；超过 150 重原子的复杂分子（如大型蛋白质）
 * 跳过生成，由 tooltip 显示提示行
 * <p>
 * 首次访问某分子时生成并缓存（DynamicTexture），之后零开销
 */
public final class MoleculeTextureCache {

    /** 超采样倍率：以 4x 分辨率绘制后线性缩小显示，细线平滑无锯齿、不错位 */
    public static final int SUPERSAMPLE = 4;

    /** 目标最大高度（px，逻辑尺寸），大分子允许更高画布以免压缩糊成一团 */
    private static final int TARGET_HEIGHT = 256;
    /** 目标最大宽度（px，逻辑尺寸），大分子允许更宽画布以免压缩糊成一团 */
    private static final int MAX_WIDTH = 512;
    /** 画布四周留白（px，逻辑尺寸），为原子符号与键线厚度预留空间 */
    private static final int PADDING = 12;
    /** 目标平均键长（px，逻辑尺寸），决定分子的显示大小；16px 使键长与符号比例舒展 */
    private static final double BOND_LENGTH_PX = 16.0;
    /** 键线显示宽度（px，逻辑尺寸） */
    private static final float BOND_STROKE_WIDTH = 0.8f;
    /** 双键平行线偏移距离（px，逻辑尺寸） */
    private static final double DOUBLE_BOND_OFFSET = 1.4;
    /** 三键副键偏移距离（px，逻辑尺寸） */
    private static final double TRIPLE_BOND_OFFSET = 2.6;
    /** 符号高度与键长的比例（化学期刊约 0.4~0.5；过大导致符号遮挡短键） */
    private static final double SYMBOL_RATIO = 0.45;
    /** 符号深色底块与文字边缘的留白（px，逻辑尺寸） */
    private static final double SYMBOL_BG_PADDING = 1.0;
    /** 重原子数上限，超过则判定为过于复杂的分子，不生成结构图 */
    private static final int MAX_HEAVY_ATOMS = 150;
    /** 键线颜色（亮白，深色 tooltip 背景上清晰） */
    private static final Color BOND_COLOR = new Color(0xE0, 0xE0, 0xE0);
    /** 符号深色底块颜色（不透明，接近 tooltip 深色背景，用于截断键线） */
    private static final Color SYMBOL_BG_COLOR = new Color(0x10, 0x10, 0x18);

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

    /** 分子图：纹理引用 + 逻辑尺寸（符号已绘制进纹理，随分子等比缩放） */
    public record MoleculeImage(ResourceLocation texture, int width, int height) {
    }

    /** 杂原子标签文本：主串（含 H）+ 下标数字 + 颜色 */
    private record AtomText(String main, String sub, int color) {
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
     * 执行完整自绘管线：解析 -> 2D 坐标 -> 归一化 -> 超采样平滑绘制 -> 注册纹理
     *
     * @param smiles SMILES 结构式
     * @return 分子图信息，复杂分子或解析失败为 null
     */
    private static MoleculeImage generate(String smiles) {
        try {
            SmilesParser parser = new SmilesParser(SilentChemObjectBuilder.getInstance());
            IAtomContainer container = parser.parseSmiles(smiles);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(container);

            // 用 CDK 专业 Kekulize 算法把芳香键转为显式单/双键（带价态检查，
            // 化学正确），替代自研 BFS 交替；isAromatic 标志保留用于识别环内双键
            Kekulization.kekulize(container);

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
     * 布局坐标 -> 画布：计算缩放比例，超采样平滑绘制键线骨架，收集杂原子标签
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
        // 逻辑尺寸（组件布局用）与超采样像素尺寸（纹理用）
        int width = Math.max(spanW, 16);
        int height = Math.max(spanH, 16);
        int pixelWidth = width * SUPERSAMPLE;
        int pixelHeight = height * SUPERSAMPLE;

        // 原子 -> 画布逻辑坐标（浮点；绘制时统一乘超采样倍率）
        Map<IAtom, double[]> pixelPositions = new HashMap<>();
        // 杂原子标签信息（含显式 H 与颜色；符号绘制进纹理，随分子等比缩放）
        Map<IAtom, AtomText> labelTexts = new HashMap<>();
        for (IAtom atom : molecule.atoms()) {
            if (atom.getAtomicNumber() == 1) {
                continue;
            }
            double px = PADDING + (atom.getPoint2d().x - minX) * scale;
            double py = PADDING + (atom.getPoint2d().y - minY) * scale;
            pixelPositions.put(atom, new double[]{px, py});
            if (atom.getAtomicNumber() != 6) {
                // 显式 H：杂原子的隐氢写入标签（羟基 O→OH、氨基 N→NH₂），
                // 键线式仅省略碳上的氢，杂原子上的氢应当可见
                int hCount = atom.getImplicitHydrogenCount() == null ? 0 : atom.getImplicitHydrogenCount();
                String main = atom.getSymbol();
                String sub = "";
                if (hCount > 0) {
                    main += "H";
                    if (hCount > 1) {
                        sub = String.valueOf(hCount);
                    }
                }
                labelTexts.put(atom, new AtomText(main, sub,
                        ATOM_COLORS.getOrDefault(atom.getSymbol(), 0xFFD0D0D0)));
            }
        }

        // 芳香键连通分量的环质心（供 Kekulé 双键朝环内侧偏移）
        Map<IBond, double[]> ringCenters = ringCenters(molecule, pixelPositions);

        // Java2D 抗锯齿平滑绘制键线骨架（超采样画布）
        BufferedImage buffered = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(BOND_COLOR);
            g.setStroke(createStroke(null));

            // 预计算每个标签的实际宽度，缩进 = 底块半宽 + 留白（精确匹配，键线终止于底块边缘，
            // 既不会穿入符号也不会因缩进过大吞掉短键）
            Map<IAtom, Double> labelInsets = new HashMap<>();
            {
                double symbolHeight = BOND_LENGTH_PX * SYMBOL_RATIO * SUPERSAMPLE;
                FontMetrics metrics = g.getFontMetrics(
                        new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight)));
                for (Map.Entry<IAtom, AtomText> entry : labelTexts.entrySet()) {
                    AtomText text = entry.getValue();
                    int totalWidth = metrics.stringWidth(text.main());
                    if (!text.sub().isEmpty()) {
                        FontMetrics subMetrics = g.getFontMetrics(
                                new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight * 0.55)));
                        totalWidth += subMetrics.stringWidth(text.sub());
                    }
                    double inset = (totalWidth / 2.0 + SYMBOL_BG_PADDING * SUPERSAMPLE) / SUPERSAMPLE;
                    labelInsets.put(entry.getKey(), inset);
                }
            }

            for (IBond bond : molecule.bonds()) {
                IAtom begin = bond.getBegin();
                IAtom end = bond.getEnd();
                if (!isHeavy(begin) || !isHeavy(end)) {
                    continue;
                }
                double[] p1 = pixelPositions.get(begin);
                double[] p2 = pixelPositions.get(end);
                // 杂原子端向内缩进其底块半宽，键线精确终止于符号底块边缘
                if (labelInsets.containsKey(begin)) {
                    p1 = shrink(p1, p2, labelInsets.get(begin));
                }
                if (labelInsets.containsKey(end)) {
                    p2 = shrink(p2, p1, labelInsets.get(end));
                }
                IBond.Order order = bond.getOrder();
                if (bond.isAromatic() && order == IBond.Order.DOUBLE) {
                    // CDK Kekulize 标记的环内双键（isAromatic 仍为 true），画在环内侧
                    drawInwardDouble(g, p1, p2, ringCenters.get(bond));
                } else if (order == IBond.Order.DOUBLE) {
                    // 双键偏移方向远离杂原子标签（如 C=O 双键画在碳侧），避免与符号重叠
                    double[] away = awayFromLabels(p1, p2, begin, end, labelTexts, pixelPositions);
                    drawDoubleLine(g, p1, p2, away);
                } else if (order == IBond.Order.TRIPLE) {
                    drawTripleLine(g, p1, p2);
                } else {
                    drawLine(g, p1, p2);
                }
            }

            // 绘制杂原子符号（深色底块 + 彩色文字，随分子等比缩放）
            drawSymbols(g, labelTexts, pixelPositions);
        } finally {
            g.dispose();
        }

        // 竖长分子（高 > 宽）顺时针旋转 90° 横放，适配 tooltip 布局：
        // tooltip 横向空间通常比纵向充裕（纵向受屏幕高度与鼠标位置限制），
        // 旋转后高度缩小、宽度增大，整体更容易完整放下
        if (height > width) {
            buffered = rotateClockwise(buffered);
            int tmp = width;
            width = height;
            height = tmp;
            tmp = pixelWidth;
            pixelWidth = pixelHeight;
            pixelHeight = tmp;
        }

        // 转换 BufferedImage（ARGB）-> NativeImage
        // 字节序：NativeImage.setPixelRGBA 期望 ABGR（alpha 最高位，小端字节序 [R,G,B,A]）；
        // 此前误用 RGBA 序导致半透明边缘偏色（未预乘时泛蓝、预乘后泛红）
        // 预乘 alpha：MC 的 GUI 渲染假定纹理预乘（混合模式 GL_ONE, GL_ONE_MINUS_SRC_ALPHA），
        // 抗锯齿半透明边缘不预乘会在深色背景上出现杂色边缘
        NativeImage image = new NativeImage(pixelWidth, pixelHeight, true);
        for (int y = 0; y < pixelHeight; y++) {
            for (int x = 0; x < pixelWidth; x++) {
                int argb = buffered.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gr = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                r = r * a / 255;
                gr = gr * a / 255;
                b = b * a / 255;
                image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (gr << 8) | r);
            }
        }

        // 注册纹理
        DynamicTexture texture = new DynamicTexture(image);
        // 必须使用线性过滤：超采样纹理缩放到逻辑尺寸时，线性插值才能平滑显示细线；
        // 最近邻过滤会逐像素抽稀，导致细线断裂、位置错位、粗细不均
        texture.setFilter(true, true);
        texture.upload();
        ResourceLocation location = Minecraft.getInstance().getTextureManager()
                .register("biocraft/molecule_" + Math.abs(smiles.hashCode()), texture);

        return new MoleculeImage(location, width, height);
    }

    /**
     * 绘制杂原子符号：深色不透明底块 + 彩色文字，随分子等比缩放
     * <p>
     * 符号绘制进纹理（而非渲染期叠加 MC 字体），使符号与键线随分子
     * 一起缩放（符号高 = 键长 × 0.6），避免固定字号符号遮挡短键；
     * 深色底块采用化学期刊惯例：截断穿过符号区域的键线，视觉干净
     *
     * @param g              Graphics2D 上下文（超采样画布）
     * @param labelTexts     标签表
     * @param pixelPositions 原子坐标表
     */
    private static void drawSymbols(Graphics2D g, Map<IAtom, AtomText> labelTexts,
                                    Map<IAtom, double[]> pixelPositions) {
        double symbolHeight = BOND_LENGTH_PX * SYMBOL_RATIO * SUPERSAMPLE;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight));
        Font subFont = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(symbolHeight * 0.55));
        FontMetrics mainMetrics = g.getFontMetrics(font);
        FontMetrics subMetrics = g.getFontMetrics(subFont);

        for (Map.Entry<IAtom, AtomText> entry : labelTexts.entrySet()) {
            double[] pos = pixelPositions.get(entry.getKey());
            AtomText text = entry.getValue();
            double cx = pos[0] * SUPERSAMPLE;
            double cy = pos[1] * SUPERSAMPLE;

            String full = text.main() + text.sub();
            int mainWidth = mainMetrics.stringWidth(text.main());
            int totalWidth = mainWidth + (text.sub().isEmpty() ? 0 : subMetrics.stringWidth(text.sub()));
            int textHeight = mainMetrics.getAscent() + mainMetrics.getDescent();

            // 深色不透明底块（圆角矩形），盖住穿过符号的键线
            int bgX = (int) Math.round(cx - totalWidth / 2.0 - SYMBOL_BG_PADDING * SUPERSAMPLE);
            int bgY = (int) Math.round(cy - textHeight / 2.0 - SYMBOL_BG_PADDING * SUPERSAMPLE);
            int bgW = (int) Math.round(totalWidth + SYMBOL_BG_PADDING * 2 * SUPERSAMPLE);
            int bgH = (int) Math.round(textHeight + SYMBOL_BG_PADDING * 2 * SUPERSAMPLE);
            g.setColor(SYMBOL_BG_COLOR);
            g.fillRoundRect(bgX, bgY, bgW, bgH,
                    (int) Math.round(symbolHeight * 0.3), (int) Math.round(symbolHeight * 0.3));

            // 主串居中绘制
            Color symbolColor = new Color(text.color());
            g.setColor(symbolColor);
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
     * 创建键线画笔（线宽按超采样倍率放大）
     *
     * @param dash 虚线模式（null 表示实线）
     * @return 画笔
     */
    private static BasicStroke createStroke(float[] dash) {
        if (dash == null) {
            return new BasicStroke(BOND_STROKE_WIDTH * SUPERSAMPLE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        float[] scaledDash = new float[dash.length];
        for (int i = 0; i < dash.length; i++) {
            scaledDash[i] = dash[i] * SUPERSAMPLE;
        }
        return new BasicStroke(BOND_STROKE_WIDTH * SUPERSAMPLE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, scaledDash, 0f);
    }

    /**
     * 绘制单键：细抗锯齿直线（坐标从逻辑尺寸换算到超采样画布）
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素（逻辑坐标）
     * @param to   终点像素（逻辑坐标）
     */
    private static void drawLine(Graphics2D g, double[] from, double[] to) {
        g.drawLine((int) Math.round(from[0] * SUPERSAMPLE), (int) Math.round(from[1] * SUPERSAMPLE),
                (int) Math.round(to[0] * SUPERSAMPLE), (int) Math.round(to[1] * SUPERSAMPLE));
    }

    /**
     * 绘制双键：垂直于键方向的平行双线（化学期刊风格）
     * <p>
     * 偏移方向由调用方指定（朝环内侧或远离杂原子标签），
     * 避免双键线与元素符号或环外区域冲突
     *
     * @param g       Graphics2D 上下文
     * @param from    起点像素（逻辑坐标）
     * @param to      终点像素（逻辑坐标）
     * @param dirUnit 偏移方向单位向量（法向方向）
     */
    private static void drawDoubleLine(Graphics2D g, double[] from, double[] to, double[] dirUnit) {
        drawLine(g, offset(from, dirUnit, -DOUBLE_BOND_OFFSET), offset(to, dirUnit, -DOUBLE_BOND_OFFSET));
        drawLine(g, offset(from, dirUnit, DOUBLE_BOND_OFFSET), offset(to, dirUnit, DOUBLE_BOND_OFFSET));
    }

    /**
     * 绘制环内双键（Kekulé 风格）：键轴一条线 + 朝环内侧偏移一条线
     * <p>
     * 化学结构式惯例：苯环等芳香环的双键画在环内侧，
     * 双键整体位于环内半区而非对称分布在键轴两侧
     *
     * @param g          Graphics2D 上下文
     * @param from       起点像素（逻辑坐标）
     * @param to         终点像素（逻辑坐标）
     * @param ringCenter 所属环的质心（用于确定内侧方向）
     */
    private static void drawInwardDouble(Graphics2D g, double[] from, double[] to, double[] ringCenter) {
        double[] inward = inwardDirection(from, to, ringCenter);
        drawLine(g, from, to);
        // 内侧偏移线两端各缩短 2px（化学期刊画法：环内双键的内侧线较短）
        double shorten = 2.0;
        double[] inFrom = shrink(from, to, shorten);
        double[] inTo = shrink(to, from, shorten);
        drawLine(g, offset(inFrom, inward, DOUBLE_BOND_OFFSET * 2), offset(inTo, inward, DOUBLE_BOND_OFFSET * 2));
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
    private static double[] inwardDirection(double[] from, double[] to, double[] ringCenter) {
        // 键方向单位向量
        double bx = to[0] - from[0];
        double by = to[1] - from[1];
        double blen = Math.max(1e-6, Math.hypot(bx, by));
        bx /= blen;
        by /= blen;
        // 键中点 -> 环心的向量
        double midX = (from[0] + to[0]) / 2;
        double midY = (from[1] + to[1]) / 2;
        double cx = ringCenter[0] - midX;
        double cy = ringCenter[1] - midY;
        // 朝环心向量在键方向上的投影系数
        double proj = cx * bx + cy * by;
        // 垂直分量（恒指向环心一侧）
        double vx = cx - proj * bx;
        double vy = cy - proj * by;
        double vlen = Math.max(1e-9, Math.hypot(vx, vy));
        return new double[]{vx / vlen, vy / vlen};
    }

    /**
     * 计算双键偏移方向：远离杂原子标签的一侧（如 C=O 双键画在碳侧）
     *
     * @param from         键起点
     * @param to           键终点
     * @param begin        起点原子
     * @param end          终点原子
     * @param labelTexts   杂原子标签表（用于判断哪些端点有标签）
     * @param pixelPositions 原子坐标表
     * @return 偏移方向单位向量（法向，远离标签侧）
     */
    private static double[] awayFromLabels(double[] from, double[] to,
                                           IAtom begin, IAtom end,
                                           Map<IAtom, AtomText> labelTexts,
                                           Map<IAtom, double[]> pixelPositions) {
        double[] normal = normalVector(from, to, 1);
        // 无标签端点：默认方向即可（对称双线无方向性）
        if (!labelTexts.containsKey(begin) && !labelTexts.containsKey(end)) {
            return normal;
        }
        double midX = (from[0] + to[0]) / 2;
        double midY = (from[1] + to[1]) / 2;
        // 取有标签端点的位置作为"远离"参考
        double[] labelPos = labelTexts.containsKey(begin) ? pixelPositions.get(begin) : pixelPositions.get(end);
        double toLabelX = labelPos[0] - midX;
        double toLabelY = labelPos[1] - midY;
        // 选择法向中远离标签的一侧
        if (normal[0] * toLabelX + normal[1] * toLabelY > 0) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
        }
        return normal;
    }

    /**
     * 计算芳香键连通分量的环质心（供 Kekulé 双键朝内侧偏移）
     *
     * @param molecule       分子
     * @param pixelPositions 原子坐标表
     * @return 芳香键 -> 所属环质心
     */
    private static Map<IBond, double[]> ringCenters(IAtomContainer molecule, Map<IAtom, double[]> pixelPositions) {
        Map<IBond, double[]> centers = new HashMap<>();
        List<IBond> aromatic = new ArrayList<>();
        for (IBond bond : molecule.bonds()) {
            if (bond.isAromatic()) {
                aromatic.add(bond);
            }
        }
        // 按共享原子分组（连通分量）
        Set<IBond> visited = new HashSet<>();
        for (IBond start : aromatic) {
            if (!visited.add(start)) {
                continue;
            }
            List<IBond> component = new ArrayList<>();
            ArrayDeque<IBond> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                IBond bond = queue.poll();
                component.add(bond);
                for (IBond neighbor : aromatic) {
                    // 共享端点原子判定相邻
                    boolean sharesAtom = neighbor.getBegin() == bond.getBegin()
                            || neighbor.getBegin() == bond.getEnd()
                            || neighbor.getEnd() == bond.getBegin()
                            || neighbor.getEnd() == bond.getEnd();
                    if (!visited.contains(neighbor) && sharesAtom) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            // 分量内所有端点原子的平均坐标作为质心
            double sumX = 0, sumY = 0;
            int count = 0;
            Set<IAtom> atoms = new HashSet<>();
            for (IBond bond : component) {
                atoms.add(bond.getBegin());
                atoms.add(bond.getEnd());
            }
            for (IAtom atom : atoms) {
                double[] pos = pixelPositions.get(atom);
                if (pos != null) {
                    sumX += pos[0];
                    sumY += pos[1];
                    count++;
                }
            }
            double[] centroid = count > 0 ? new double[]{sumX / count, sumY / count} : new double[]{0, 0};
            for (IBond bond : component) {
                centers.put(bond, centroid);
            }
        }
        return centers;
    }

    /**
     * 绘制三键：主键 + 两侧副键
     *
     * @param g    Graphics2D 上下文
     * @param from 起点像素（逻辑坐标）
     * @param to   终点像素（逻辑坐标）
     */
    private static void drawTripleLine(Graphics2D g, double[] from, double[] to) {
        drawLine(g, from, to);
        double[] normal = normalVector(from, to, TRIPLE_BOND_OFFSET);
        drawLine(g, offset(from, normal, -1), offset(to, normal, -1));
        drawLine(g, offset(from, normal, 1), offset(to, normal, 1));
    }

    /**
     * 坐标沿键方向向目标点缩进指定距离（用于杂原子符号处键线留白）
     *
     * @param pos   待缩进的坐标
     * @param toward 缩进方向参考点
     * @param inset 缩进距离（逻辑像素）
     * @return 缩进后的新坐标
     */
    private static double[] shrink(double[] pos, double[] toward, double inset) {
        double dx = toward[0] - pos[0];
        double dy = toward[1] - pos[1];
        double length = Math.max(1e-6, Math.hypot(dx, dy));
        return new double[]{pos[0] + dx / length * inset, pos[1] + dy / length * inset};
    }

    /**
     * 图像顺时针旋转 90°
     * <p>
     * 像素级旋转（不重新布局），键线/符号/底块整体随图像旋转，
     * 映射关系：old(x,y) -> new(h-1-y, x)
     *
     * @param src 原图像
     * @return 旋转后的图像（宽高互换）
     */
    private static BufferedImage rotateClockwise(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setRGB(h - 1 - y, x, src.getRGB(x, y));
            }
        }
        return dst;
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
     * 坐标按法向向量偏移
     *
     * @param pos    原坐标
     * @param normal 法向向量（可为单位向量，偏移量由 side 控制）
     * @param side   偏移量（正/负任意浮点）
     * @return 新坐标
     */
    private static double[] offset(double[] pos, double[] normal, double side) {
        return new double[]{pos[0] + normal[0] * side, pos[1] + normal[1] * side};
    }
}
