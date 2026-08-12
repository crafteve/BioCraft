package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.init.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

/**
 * 机器合成配方生成器（datagen）
 * <p>
 * 三台原始机器用原版材料在工作台合成（AGENTS.md 纪元一定位），
 * 金属递进方案：DNA编码器=铁锭，转录仪=金锭，翻译仪=钻石（后续追加），
 * 玻璃框架 + 红石核心
 * <p>
 * 配方图案：GGG / IRI / III（G=玻璃框架，I=金属主体，R=红石核心）
 */
public class MachineRecipeProvider extends RecipeProvider {

    /**
     * @param packOutput datagen 输出目录包装
     * @param registries 注册表查找器（配方物品引用解析需要）
     */
    public MachineRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
        super(packOutput, registries);
    }

    /**
     * 生成全部机器配方
     *
     * @param recipeOutput 配方输出器
     */
    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // DNA 编码器：玻璃 + 铁锭 + 红石（电子逻辑感，蓝色主题）
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DNA_ENCODER.asItem())
                .pattern("GGG")
                .pattern("IRI")
                .pattern("III")
                .define('G', Items.GLASS)
                .define('I', Items.IRON_INGOT)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_glass", has(Items.GLASS))
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(recipeOutput);
    }
}
