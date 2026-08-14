package com.github.crafteve.biocraft.compat.jei;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * BioCraft 的 JEI 插件入口（@JeiPlugin 注解由 JEI 自动发现）
 * <p>
 * JEI 未安装时本类永远不会被加载（无人扫描），主 mod 零引用本类，
 * 因此 JEI 是纯可选依赖；JEI 与 EMI 双装时各自注册各自显示，互不干扰
 * <p>
 * 配方数据源：EnzymeFactoryRegistry（enzymes.json 数据驱动）经
 * EnzymeRecipeDisplay 转换——新增酶自动出现在 JEI，无需改插件代码
 */
@JeiPlugin
public class BioCraftJeiPlugin implements IModPlugin {

    /** 酶工厂配方类型（JEI 的 RecipeType，非 MC 的；JEI 侧类别 id 与 EMI 独立） */
    public static final RecipeType<EnzymeRecipeDisplay> ENZYME_FACTORY =
            RecipeType.create(BioCraft.MODID, "enzyme_factory", EnzymeRecipeDisplay.class);

    /**
     * 插件唯一标识（JEI 日志与调试定位用）
     *
     * @return 插件 uid
     */
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "jei_plugin");
    }

    /**
     * 注册配方类别（酶工厂配方卡）
     * <p>
     * 类别构造时传入 JEI 图形助手（提供标准槽纹理等绘制资源）
     *
     * @param registration 类别注册器
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new EnzymeFactoryRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * 注册全部酶工厂配方（数据驱动：遍历酶数据表实时转换）
     * <p>
     * 点击用途追溯（U 键）由 JEI 根据槽位的 INPUT/OUTPUT 角色自动建立反向索引，
     * 此处只需提供配方列表
     *
     * @param registration 配方注册器
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<EnzymeRecipeDisplay> recipes = EnzymeFactoryRegistry.ordered().stream()
                .map(EnzymeRecipeDisplay::from)
                .toList();
        registration.addRecipes(ENZYME_FACTORY, recipes);
    }

    /**
     * 注册配方催化剂：全部酶工厂方块物品挂到酶工厂配方类型
     * <p>
     * 玩家在 JEI 中点击/拖拽任意酶机器方块物品，即显示该酶的配方
     *
     * @param registration 催化剂注册器
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var item : ModBlocks.enzymeItems()) {
            registration.addRecipeCatalyst(item.get(), ENZYME_FACTORY);
        }
    }
}
