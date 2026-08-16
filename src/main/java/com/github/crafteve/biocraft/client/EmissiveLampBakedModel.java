package com.github.crafteve.biocraft.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.QuadTransformers;

import java.util.ArrayList;
import java.util.List;

/**
 * 自发光指示灯模型包装：把酶反应腔的"灯"贴片 quad 光照强制全亮
 * <p>
 * vanilla 方块模型没有"自发光"属性（贴图亮度不产生光照），要实现
 * 夜晚指示灯真实发光，只能在烘焙完成后包装 BakedModel：getQuads 时
 * 对 tintindex==1（灯贴片）的 quad 用 QuadTransformers 重写顶点光照
 * 为全亮（0xF000F0）——发光色仍由 BlockColor 的酶主题色/停摆红决定，
 * 本包装只改光照不改颜色
 * <p>
 * 由 BioCraftClient 的 ModelEvent.ModifyBakingResult 把 enzyme_chamber
 * 全部朝向 variant 的烘焙模型替换为本包装；物品模型不命中（key 路径
 * 为 item/ 前缀），物品栏/手持不受影响
 */
public class EmissiveLampBakedModel implements BakedModel {

    /** 灯贴片的 tintindex（与模型 JSON 的贴片元素一致，1 = 指示灯） */
    private static final int LAMP_TINT_INDEX = 1;

    /** 被包装的原始烘焙模型（非灯 quad 原样透传） */
    private final BakedModel delegate;

    /**
     * @param delegate 原始烘焙模型（blockstate variant 的模型）
     */
    public EmissiveLampBakedModel(BakedModel delegate) {
        this.delegate = delegate;
    }

    /**
     * 返回 quad 列表：灯 quad 应用全亮光照，其余原样
     * <p>
     * 先扫描是否存在灯 quad，没有则直接透传（无分配开销）；
     * 灯 quad 每次调用重建（每帧每面最多 4 个灯 quad，开销可接受，
     * 不做缓存——模型变化由 BlockColor 染色负责，quad 本身不变）
     *
     * @param state  方块状态
     * @param side   面方向
     * @param random 随机源
     * @return 处理后的 quad 列表
     */
    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        List<BakedQuad> quads = delegate.getQuads(state, side, random);
        boolean hasLamp = false;
        for (BakedQuad quad : quads) {
            if (quad.getTintIndex() == LAMP_TINT_INDEX) {
                hasLamp = true;
                break;
            }
        }
        if (!hasLamp) {
            return quads;
        }
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (quad.getTintIndex() == LAMP_TINT_INDEX) {
                result.add(QuadTransformers.settingMaxEmissivity().process(quad));
            } else {
                result.add(quad);
            }
        }
        return result;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return delegate.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return delegate.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return delegate.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return delegate.isCustomRenderer();
    }

    @Override
    public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
        return delegate.getParticleIcon();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
        return delegate.getTransforms();
    }

    @Override
    public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
        return delegate.getOverrides();
    }
}
