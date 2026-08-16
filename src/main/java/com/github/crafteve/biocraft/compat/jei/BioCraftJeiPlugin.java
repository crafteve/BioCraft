package com.github.crafteve.biocraft.compat.jei;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
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
 * 酶工厂方块时代结束后的统一配方模型：
 * <ul>
 *   <li>全部酶配方挂在同一个 RecipeType（enzyme_factory）与同一个
 *       配方类别（EnzymeFactoryRecipeCategory）下——机器收敛为统一
 *       酶反应腔后，配方不再按酶分类型</li>
 *   <li>追溯语义：酶蛋白物品作为各自配方的酶槽 ingredient（精确匹配，
 *       对酶物品按 U 只显示含它的唯一配方）；enzyme_chamber 方块物品
 *       作为催化剂挂统一类型（对反应腔按 U 显示全部酶配方）</li>
 * </ul>
 * 配方数据源为 EnzymeFactoryRegistry（enzymes.json 数据驱动）经
 * EnzymeRecipeDisplay 转换——新增酶自动出现，无需改插件代码
 */
@JeiPlugin
public class BioCraftJeiPlugin implements IModPlugin {

    /** 统一配方类型（全部酶配方共用） */
    public static final RecipeType<EnzymeRecipeDisplay> RECIPE_TYPE = RecipeType.create(
            BioCraft.MODID, "enzyme_factory", EnzymeRecipeDisplay.class);

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
     * 注册配方类别：统一酶工厂类别（标题 = 酶反应腔，图标 = 反应腔方块）
     *
     * @param registration 类别注册器
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new EnzymeFactoryRecipeCategory(RECIPE_TYPE, registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * 注册全部酶工厂配方：每酶一条配方，全部挂统一类型
     *
     * @param registration 配方注册器
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            registration.addRecipes(RECIPE_TYPE, List.of(EnzymeRecipeDisplay.from(data)));
        }
    }

    /**
     * 注册配方催化剂：enzyme_chamber 方块物品 → 统一类型
     * <p>
     * 对反应腔方块按 U 显示全部酶配方；酶蛋白物品不注册为催化剂——
     * 它们作为各自配方的酶槽 ingredient 参与精确追溯（唯一配方）
     *
     * @param registration 催化剂注册器
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.ENZYME_CHAMBER_ITEM.get(), RECIPE_TYPE);
    }
}
