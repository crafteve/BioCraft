package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.BioCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.openscience.cdk.depict.Depiction;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 分子结构图缓存（Dist.CLIENT），由 CDK 直接渲染输出
 * <p>
 * 渲染管线：CDK 矢量渲染器（DepictionGenerator）直出分子结构图
 * （印刷风格：白底 + 灰黑键线/元素符号），作为浅色卡片嵌入 tooltip：
 * <ul>
 *   <li>符号与键线比例由 CDK 内置（化学期刊标准），无遮挡/缩放问题</li>
 *   <li>芳香环以 Kekulé 单双交替显示（withAromaticDisplay）</li>
 *   <li>杂原子的隐氢转为显式氢原子（羟基 O→OH、氨基 N→NH₂），碳上的氢保持隐式（键线式约定）</li>
 *   <li>尺寸归一：按默认渲染尺寸等比缩放，高度上限 120px、宽度上限 280px，
 *       小分子保持原大小，大分子（如 NADH）等比例缩小</li>
 * </ul>
 * 超过 150 重原子的复杂分子（如大型蛋白质）跳过生成，由 tooltip 显示提示行。
 * 首次访问某分子时生成并缓存（DynamicTexture），之后零开销
 */
public final class MoleculeTextureCache {

    /** 目标最大高度（px） */
    private static final int TARGET_HEIGHT = 120;
    /** 目标最大宽度（px） */
    private static final int MAX_WIDTH = 280;
    /** 重原子数上限，超过则判定为过于复杂的分子，不生成结构图 */
    private static final int MAX_HEAVY_ATOMS = 150;

    /** SMILES -> 生成的分子图 */
    private static final Map<String, MoleculeImage> CACHE = new HashMap<>();

    /** 分子图：纹理引用 + 像素尺寸 */
    public record MoleculeImage(ResourceLocation texture, int width, int height) {
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
     * 执行完整渲染管线：解析 -> 杂原子显式氢 -> CDK 矢量渲染 -> 尺寸归一 -> 注册纹理
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

            // 杂原子隐氢转显式（碳上的氢保持隐式，键线式约定）
            makeHeteroHExplicit(container);

            // 先以默认尺寸渲染，获取原始尺寸后按比例缩放（避免大分子画布溢出）
            DepictionGenerator generator = new DepictionGenerator().withAromaticDisplay();
            BufferedImage original = generator.depict(container).toImg();
            double zoom = Math.min(1.0, Math.min(
                    (double) TARGET_HEIGHT / original.getHeight(),
                    (double) MAX_WIDTH / original.getWidth()));
            BufferedImage rendered;
            if (zoom < 1.0) {
                rendered = new DepictionGenerator().withAromaticDisplay().withZoom(zoom)
                        .depict(container).toImg();
            } else {
                rendered = original;
            }

            // 转换 BufferedImage -> NativeImage（ABGR 字节序：alpha 最高位）
            NativeImage image = new NativeImage(rendered.getWidth(), rendered.getHeight(), true);
            for (int y = 0; y < rendered.getHeight(); y++) {
                for (int x = 0; x < rendered.getWidth(); x++) {
                    int argb = rendered.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >>> 16) & 0xFF;
                    int gr = (argb >>> 8) & 0xFF;
                    int b = argb & 0xFF;
                    image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (gr << 8) | r);
                }
            }

            // 注册纹理（线性过滤：缩放显示时平滑）
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, true);
            texture.upload();
            ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("biocraft/molecule_" + Math.abs(smiles.hashCode()), texture);

            return new MoleculeImage(location, rendered.getWidth(), rendered.getHeight());
        } catch (CDKException e) {
            BioCraft.LOGGER.warn("分子结构图生成失败: {} ({})", smiles, e.getMessage());
            return null;
        }
    }

    /**
     * 把杂原子（非碳非氢）的隐氢转为显式氢原子
     * <p>
     * CDK 键线式渲染默认不显示任何氢，为满足"羟基显示 OH、氨基显示 NH₂"
     * 的需求，将杂原子上的隐氢转换为显式氢原子（CDK 渲染器会自动放置其位置），
     * 碳上的氢保持隐式以符合键线式约定
     *
     * @param container 分子容器
     */
    private static void makeHeteroHExplicit(IAtomContainer container) {
        IChemObjectBuilder builder = SilentChemObjectBuilder.getInstance();
        for (IAtom atom : container.atoms()) {
            int number = atom.getAtomicNumber();
            if (number == 1 || number == 6) {
                continue;
            }
            int hCount = atom.getImplicitHydrogenCount() == null ? 0 : atom.getImplicitHydrogenCount();
            if (hCount <= 0) {
                continue;
            }
            atom.setImplicitHydrogenCount(0);
            for (int i = 0; i < hCount; i++) {
                IAtom h = builder.newAtom();
                h.setAtomicNumber(1);
                h.setSymbol("H");
                container.addAtom(h);
                container.addBond(container.indexOf(atom), container.getAtomCount() - 1, IBond.Order.SINGLE);
            }
        }
    }
}
