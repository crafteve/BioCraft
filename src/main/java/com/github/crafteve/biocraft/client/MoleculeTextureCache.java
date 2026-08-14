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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 分子键线式结构图缓存（Dist.CLIENT）
 * <p>
 * 自绘管线：CDK 仅负责 SMILES 解析与 2D 坐标生成，图像由本类编排绘制
 * （化学期刊风格）。绘制细节拆分到四个辅助类：
 * <ul>
 *   <li>MoleculeRenderConstants：渲染常量与色板</li>
 *   <li>MoleculeGeometry：坐标/向量几何运算</li>
 *   <li>MoleculeRingSearch：环键连通分量质心</li>
 *   <li>MoleculeBondRenderer：单/双/三/环内双键绘制</li>
 *   <li>MoleculeSymbolRenderer：杂原子符号与标签碰撞</li>
 * </ul>
 * 本类只负责缓存与编排：解析 → 2D 坐标 → 归一化 → 超采样绘制 → 注册纹理
 * <p>
 * 尺寸自适应：画布缩放使平均键长约为 16px，高度上限 256px、宽度上限 512px；
 * 超过 150 重原子的复杂分子（如大型蛋白质）跳过生成，由 tooltip 显示提示行
 * <p>
 * 首次访问某分子时生成并缓存（DynamicTexture），之后零开销
 */
public final class MoleculeTextureCache {

    /** SMILES -> 生成的分子图 */
    private static final Map<String, MoleculeImage> CACHE = new HashMap<>();

    /** 分子图：纹理引用 + 逻辑尺寸（符号已绘制进纹理，随分子等比缩放） */
    public record MoleculeImage(ResourceLocation texture, int width, int height) {
    }

    /** 杂原子标签文本：主串（含 H）+ 下标数字 + 颜色 */
    record AtomText(String main, String sub, int color) {
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
     * 执行解析阶段：SMILES → 原子容器 → Kekulize → 2D 坐标
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
            if (heavyAtoms > MoleculeRenderConstants.MAX_HEAVY_ATOMS) {
                BioCraft.LOGGER.info("Molecule too complex (heavy atoms {}), skipping structure image: {}", heavyAtoms, smiles);
                return null;
            }

            StructureDiagramGenerator sdg = new StructureDiagramGenerator();
            sdg.setMolecule(container);
            sdg.generateCoordinates();
            IAtomContainer laidOut = sdg.getMolecule();

            return rasterize(smiles, laidOut);
        } catch (CDKException e) {
            BioCraft.LOGGER.warn("Molecule structure image generation failed: {} ({})", smiles, e.getMessage());
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
            if (MoleculeGeometry.isHeavy(bond.getBegin()) && MoleculeGeometry.isHeavy(bond.getEnd())) {
                bondLengthSum += MoleculeGeometry.distance(bond.getBegin(), bond.getEnd());
                bondCount++;
            }
        }

        double scale = bondCount > 0
                ? MoleculeRenderConstants.BOND_LENGTH_PX / (bondLengthSum / bondCount) : 1.0;
        int spanW = (int) Math.ceil((maxX - minX) * scale) + MoleculeRenderConstants.PADDING * 2;
        int spanH = (int) Math.ceil((maxY - minY) * scale) + MoleculeRenderConstants.PADDING * 2;
        // 画布上限约束：超出则整体收缩
        if (spanW > MoleculeRenderConstants.MAX_WIDTH || spanH > MoleculeRenderConstants.TARGET_HEIGHT) {
            double shrink = Math.min((double) MoleculeRenderConstants.MAX_WIDTH / spanW,
                    (double) MoleculeRenderConstants.TARGET_HEIGHT / spanH);
            scale *= shrink;
            spanW = (int) Math.ceil((maxX - minX) * scale) + MoleculeRenderConstants.PADDING * 2;
            spanH = (int) Math.ceil((maxY - minY) * scale) + MoleculeRenderConstants.PADDING * 2;
        }
        // 逻辑尺寸（组件布局用）与超采样像素尺寸（纹理用）
        int width = Math.max(spanW, 16);
        int height = Math.max(spanH, 16);
        int pixelWidth = width * MoleculeRenderConstants.SUPERSAMPLE;
        int pixelHeight = height * MoleculeRenderConstants.SUPERSAMPLE;

        // 原子 -> 画布逻辑坐标（浮点；绘制时统一乘超采样倍率）
        Map<IAtom, double[]> pixelPositions = new HashMap<>();
        // 杂原子标签信息（含显式 H 与颜色；符号绘制进纹理，随分子等比缩放）
        Map<IAtom, AtomText> labelTexts = new HashMap<>();
        for (IAtom atom : molecule.atoms()) {
            if (atom.getAtomicNumber() == 1) {
                continue;
            }
            double px = MoleculeRenderConstants.PADDING + (atom.getPoint2d().x - minX) * scale;
            double py = MoleculeRenderConstants.PADDING + (atom.getPoint2d().y - minY) * scale;
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
                        MoleculeRenderConstants.ATOM_COLORS.getOrDefault(atom.getSymbol(), 0xFFD0D0D0)));
            }
        }

        // 环键连通分量的环质心（供环内双键朝内侧偏移）
        Map<IBond, double[]> ringCenters = MoleculeRingSearch.ringCenters(molecule, pixelPositions);

        // 竖长分子（高 > 宽）旋转 90° 横放，适配 tooltip 布局：
        // tooltip 横向空间通常比纵向充裕（纵向受屏幕高度与鼠标位置限制）。
        // 采用"坐标旋转 + 重绘"而非像素旋转，使元素符号保持竖直（仅位置随旋转移动）
        if (height > width) {
            // 顺时针旋转坐标：old(x,y) -> new(height-1-y, x)（用旋转前的画布高度）
            int oldHeight = height;
            for (Map.Entry<IAtom, double[]> entry : pixelPositions.entrySet()) {
                double[] pos = entry.getValue();
                entry.setValue(new double[]{oldHeight - 1 - pos[1], pos[0]});
            }
            for (Map.Entry<IBond, double[]> entry : ringCenters.entrySet()) {
                double[] c = entry.getValue();
                entry.setValue(new double[]{oldHeight - 1 - c[1], c[0]});
            }
            // 交换尺寸
            int tmp = width;
            width = height;
            height = tmp;
            tmp = pixelWidth;
            pixelWidth = pixelHeight;
            pixelHeight = tmp;
        }

        // Java2D 抗锯齿平滑绘制键线骨架（超采样画布）
        BufferedImage buffered = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffered.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(MoleculeRenderConstants.BOND_COLOR);
            g.setStroke(MoleculeBondRenderer.createStroke(null));

            // 预计算每个标签的实际宽度，缩进 = 底块半宽 + 留白（精确匹配，键线终止于底块边缘，
            // 既不会穿入符号也不会因缩进过大吞掉短键）
            Map<IAtom, Double> labelInsets = new HashMap<>();
            {
                double symbolHeight = MoleculeRenderConstants.BOND_LENGTH_PX
                        * MoleculeRenderConstants.SYMBOL_RATIO * MoleculeRenderConstants.SUPERSAMPLE;
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
                    double inset = (totalWidth / 2.0
                            + MoleculeRenderConstants.SYMBOL_BG_PADDING * MoleculeRenderConstants.SUPERSAMPLE)
                            / MoleculeRenderConstants.SUPERSAMPLE;
                    labelInsets.put(entry.getKey(), inset);
                }
            }

            // 标签碰撞处理：旋转或短键场景下 OH/NH₂ 等标签可能相互重叠，
            // 沿连线方向互相推开（多次迭代收敛），确保各基团可读
            MoleculeSymbolRenderer.resolveLabelCollisions(labelTexts, pixelPositions, labelInsets);

            for (IBond bond : molecule.bonds()) {
                IAtom begin = bond.getBegin();
                IAtom end = bond.getEnd();
                if (!MoleculeGeometry.isHeavy(begin) || !MoleculeGeometry.isHeavy(end)) {
                    continue;
                }
                double[] p1 = pixelPositions.get(begin);
                double[] p2 = pixelPositions.get(end);
                // 杂原子端向内缩进其底块半宽，键线精确终止于符号底块边缘；
                // 缩进上限 = 键长的 40%，保证短键（杂原子密集区）仍有可见的键线段
                if (labelInsets.containsKey(begin)) {
                    double bondLen = Math.max(1e-6, Math.hypot(p2[0] - p1[0], p2[1] - p1[1]));
                    p1 = MoleculeGeometry.shrink(p1, p2, Math.min(labelInsets.get(begin), bondLen * 0.4));
                }
                if (labelInsets.containsKey(end)) {
                    double bondLen = Math.max(1e-6, Math.hypot(p1[0] - p2[0], p1[1] - p2[1]));
                    p2 = MoleculeGeometry.shrink(p2, p1, Math.min(labelInsets.get(end), bondLen * 0.4));
                }
                IBond.Order order = bond.getOrder();
                if (ringCenters.containsKey(bond) && order == IBond.Order.DOUBLE) {
                    // 环上双键（芳香环 Kekulé 或显式 Kekulé 环）统一画在环内侧
                    MoleculeBondRenderer.drawInwardDouble(g, p1, p2, ringCenters.get(bond));
                } else if (order == IBond.Order.DOUBLE) {
                    // 双键偏移方向远离杂原子标签（如 C=O 双键画在碳侧），避免与符号重叠
                    double[] away = MoleculeGeometry.awayFromLabels(p1, p2, begin, end,
                            labelTexts.keySet(), pixelPositions);
                    MoleculeBondRenderer.drawDoubleLine(g, p1, p2, away);
                } else if (order == IBond.Order.TRIPLE) {
                    MoleculeBondRenderer.drawTripleLine(g, p1, p2);
                } else {
                    MoleculeBondRenderer.drawLine(g, p1, p2);
                }
            }

            // 绘制杂原子符号（深色底块 + 彩色文字，随分子等比缩放）
            MoleculeSymbolRenderer.drawSymbols(g, labelTexts, pixelPositions);
        } finally {
            g.dispose();
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
}
