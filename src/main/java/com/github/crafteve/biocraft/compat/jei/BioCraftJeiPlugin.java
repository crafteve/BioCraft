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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BioCraft 的 JEI 插件入口（@JeiPlugin 注解由 JEI 自动发现）
 * <p>
 * JEI 未安装时本类永远不会被加载（无人扫描），主 mod 零引用本类，
 * 因此 JEI 是纯可选依赖；JEI 与 EMI 双装时各自注册各自显示，互不干扰
 * <p>
 * 每酶一个专属配方类型与类别（配方 id 形如 biocraft:enzyme_factory/&lt;酶id&gt;）：
 * 查看某酶方块（如 PGI）的用途时只显示该酶的专属配方，而不是所有酶工厂
 * 配方混在同一类别；配方数据源为 EnzymeFactoryRegistry（enzymes.json 数据驱动）
 * 经 EnzymeRecipeDisplay 转换——新增酶自动出现，无需改插件代码
 */
@JeiPlugin
public class BioCraftJeiPlugin implements IModPlugin {

    /** 酶 id -> 专属配方类型（注册顺序 = 酶数据表顺序） */
    private static final Map<String, RecipeType<EnzymeRecipeDisplay>> RECIPE_TYPES = new LinkedHashMap<>();

    /**
     * 获取酶的专属配方类型（类别与催化剂注册共用）
     *
     * @param enzymeId 酶注册名
     * @return 该酶的配方类型
     */
    public static RecipeType<EnzymeRecipeDisplay> recipeTypeOf(String enzymeId) {
        return RECIPE_TYPES.get(enzymeId);
    }

    /**
     * 获取全部酶的配方类型（只读，按酶数据表顺序）
     *
     * @return 配方类型映射
     */
    public static Map<String, RecipeType<EnzymeRecipeDisplay>> recipeTypes() {
        return Collections.unmodifiableMap(RECIPE_TYPES);
    }

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
     * 注册配方类别：每个酶一个专属类别（标题 = 酶显示名，图标 = 该酶方块）
     * <p>
     * 类别与配方类型按酶数据表顺序一一注册，查看用途时各酶配方互不混淆
     *
     * @param registration 类别注册器
     */
    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        RECIPE_TYPES.clear();
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            RecipeType<EnzymeRecipeDisplay> type = RecipeType.create(
                    BioCraft.MODID, "enzyme_factory/" + data.id(), EnzymeRecipeDisplay.class);
            RECIPE_TYPES.put(data.id(), type);
            registration.addRecipeCategories(
                    new EnzymeFactoryRecipeCategory(type, EnzymeRecipeDisplay.from(data),
                            registration.getJeiHelpers().getGuiHelper()));
        }
    }

    /**
     * 注册全部酶工厂配方：每酶一条配方挂到该酶专属类型
     * <p>
     * 点击用途追溯（U 键）由 JEI 根据槽位的 INPUT/OUTPUT 角色自动建立反向索引，
     * 跨类别全局索引：点 ATP 会列出所有以 ATP 为底物/产物的酶配方（各一条）
     *
     * @param registration 配方注册器
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            List<EnzymeRecipeDisplay> recipes = List.of(EnzymeRecipeDisplay.from(data));
            registration.addRecipes(recipeTypeOf(data.id()), recipes);
        }
    }

    /**
     * 注册配方催化剂：每个酶方块物品挂到该酶专属配方类型
     * <p>
     * 玩家在 JEI 中点击/拖拽某酶机器方块物品，只显示该酶的专属配方
     *
     * @param registration 催化剂注册器
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        List<EnzymeFactoryData> ordered = EnzymeFactoryRegistry.ordered();
        for (int i = 0; i < ordered.size(); i++) {
            registration.addRecipeCatalyst(ModBlocks.enzymeItems().get(i).get(),
                    recipeTypeOf(ordered.get(i).id()));
        }
    }
}
