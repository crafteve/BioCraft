package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 酶工厂铭牌渲染器（BER）：在方块正面铭牌框内动态渲染酶缩写
 * <p>
 * 设计（与用户确认的分层贴图架构配套）：
 * <ul>
 *   <li>铭牌文字不烘焙进贴图：运行时读取酶数据档案的 abbreviation
 *       （enzymes.json 的 GPI/HK/GAPDH 等），数据驱动零资源成本</li>
 *   <li>文字色 = 类别主题色提亮 75%（与染色后的铭牌深底形成对比），
 *       8 方向黑描边保证任何亮度底色下可读</li>
 *   <li>位置：铭牌面片位于正面 64 贴图 (22,5) 起 19x8 区域，
 *       文字按面片中心居中（字高占铭牌高 90%）</li>
 *   <li>朝向：按方块 FACING 旋转（-toYRot，与模型 y 旋转约定一致），
 *       任何朝向下面向观察者</li>
 * </ul>
 */
public class EnzymeNamePlateRenderer implements BlockEntityRenderer<EnzymeFactoryBlockEntity> {

    /** 铭牌区域方块坐标（64 尺寸贴图规格换算，MC y 向上） */
    private static final float PLATE_X0 = 22f / 64f;
    /** 铭牌右边界（19px 宽：22+19=41） */
    private static final float PLATE_X1 = 41f / 64f;
    /** 铭牌下边界（贴图 y 13 → 方块 y 1-13/64） */
    private static final float PLATE_Y0 = 1f - 13f / 64f;
    /** 铭牌上边界（贴图 y 5 → 方块 y 1-5/64） */
    private static final float PLATE_Y1 = 1f - 5f / 64f;
    /** 文字 z 凸出量：位于外壳 north 面（z=0）与铭牌面片（z=-0.01）之间 */
    private static final float TEXT_Z = -0.005f;
    /** 文字高度占铭牌高度的比例 */
    private static final float TEXT_FILL = 0.9f;

    /**
     * 渲染铭牌缩写文字
     * <p>
     * 渲染顺序：平移到方块中心 → 按 facing 旋转 → 平移到铭牌中心 →
     * 缩放（字高占铭牌高 90%，MC 字体基准 8px）→ 居中绘制
     *
     * @param be            酶工厂方块实体（携带酶数据档案）
     * @param partialTick   帧间插值系数（本渲染不使用）
     * @param pose          渲染矩阵栈
     * @param buffer        顶点缓冲源
     * @param packedLight   光照打包值
     * @param packedOverlay 覆盖层打包值（本渲染不使用）
     */
    @Override
    public void render(EnzymeFactoryBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        String abbreviation = be.getEnzymeData().abbreviation();
        if (abbreviation.isEmpty()) {
            return;
        }
        // 防御：方块被摧毁/替换后 BE 残留一帧渲染，此时方块状态为 AIR，
        // 直接读取 FACING 会抛 IllegalArgumentException（实测崩溃），先校验属性存在
        BlockState state = level.getBlockState(be.getBlockPos());
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }

        // 文字色：铭牌底板素材为浅色（灰度 188~211），染色后呈亮主题色，
        // 文字须用主题色压暗 + 深描边才能形成对比（亮字配亮底会看不清）
        int theme = MachineCategory.byId(be.getEnzymeData().category()).getThemeColor();
        int textColor = 0xFF000000 | shade(theme, 0.35f);
        int outlineColor = 0xFF101014;

        pose.pushPose();
        // 旋转对齐方块朝向：与模型 blockstate y 旋转完全一致（vanilla 惯例
        // north=0/south=180/east=270/west=90，逆时针），文字面（-Z 凸出）随模型
        // front 贴图同步转到对应朝向；勿用 -toYRot（那是 SignRenderer 的立牌约定）
        float yRot = switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case EAST -> 270f;
            case WEST -> 90f;
            default -> 0f;
        };
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5, -0.5, -0.5);
        // 平移到铭牌中心，z 略凸出于外壳正面
        pose.translate((PLATE_X0 + PLATE_X1) / 2f, (PLATE_Y0 + PLATE_Y1) / 2f, TEXT_Z);
        // 缩放：字高占铭牌高 90%（MC 字体 8px 基准高度）
        float scale = (PLATE_Y1 - PLATE_Y0) * TEXT_FILL / 8f;
        pose.scale(scale, scale, scale);

        // 居中绘制：x 偏移半字宽、y 偏移半字高（字体坐标 y 向下，方块坐标 y 向上）
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(abbreviation);
        font.drawInBatch8xOutline(Component.literal(abbreviation).getVisualOrderText(),
                -textWidth / 2f, -4f, textColor, outlineColor,
                pose.last().pose(), buffer, packedLight);
        pose.popPose();
    }

    /**
     * 颜色乘法压暗：rgb 各通道乘系数（保留不透明 alpha）
     * <p>
     * 用于从主题色推导铭牌文字色（亮底配深字）
     *
     * @param rgb 0xRRGGBB 颜色
     * @param f   亮度系数 0~1
     * @return 压暗后的 0xRRGGBB
     */
    private static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
