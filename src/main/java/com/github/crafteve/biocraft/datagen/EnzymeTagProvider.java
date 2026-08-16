package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.init.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

/**
 * 酶 tag 生成器（datagen）
 * <p>
 * 生成 data/biocraft/tags/items/enzyme.json：含全部酶蛋白物品
 * （enzymes.json 数据驱动——新增酶自动进 tag，酶槽 0 槽的
 * 输入输出限制即按本 tag 判定）
 */
public class EnzymeTagProvider extends ItemTagsProvider {

    /**
     * @param output         datagen 输出目录包装
     * @param lookupProvider 注册表查找器
     * @param blockTags      方块 tag 查找器（本 provider 不使用，传 null）
     */
    public EnzymeTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                             CompletableFuture<net.minecraft.data.tags.TagsProvider.TagLookup<Block>> blockTags) {
        super(output, lookupProvider, blockTags);
    }

    /**
     * 生成酶 tag：遍历酶数据表注册的酶蛋白物品，全部加入 biocraft:enzyme
     *
     * @param provider 注册表查找器
     */
    @Override
    protected void addTags(HolderLookup.Provider provider) {
        TagAppender<Item> appender = tag(ModTags.ENZYME_ITEMS);
        for (var item : ModItems.enzymeOrdered()) {
            appender.add(item.getKey());
        }
    }

    /**
     * 返回 Provider 名称，用于 datagen 日志与缓存键
     *
     * @return 名称字符串
     */
    @Override
    public String getName() {
        return "BioCraft 酶 tag 生成";
    }
}
