package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 模组自定义 tag 注册中心
 * <p>
 * tag 是物品分类的权威接口：0 槽酶槽的输入输出限制用 tag 判定，
 * 未来非酶蛋白的催化剂物品（如酶插件修饰体）只需给物品标本 tag
 * 即可被酶槽接受——比 instanceof 硬编码更开放
 */
public final class ModTags {
    /** 酶蛋白物品 tag（由 datagen 生成，含全部 enzyme_&lt;酶id&gt; 物品） */
    public static final TagKey<Item> ENZYME_ITEMS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "enzyme"));

    private ModTags() {
    }
}
