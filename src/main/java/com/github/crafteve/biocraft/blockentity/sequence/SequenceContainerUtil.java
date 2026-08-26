package com.github.crafteve.biocraft.blockentity.sequence;

import com.github.crafteve.biocraft.BioCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 序列机容器工具（纯容器操作，与接口解耦）
 * <p>
 * 原先作为 {@link SequenceOperation} 的接口静态方法，需写
 * {@code SequenceOperation.matchesId(...)} 前缀调用，语义拗口
 * 且不符合 MC 惯例；抽为独立工具类后调用更直观
 */
public final class SequenceContainerUtil {

    private SequenceContainerUtil() {
    }

    /** 从单体槽消耗 1 个指定物品（失败返回 false = 缺料停摆） */
    public static boolean consumeOne(SimpleContainer container, int slot, String itemId) {
        ItemStack stack = container.getItem(slot);
        if (stack.isEmpty() || !matchesId(stack, itemId)) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            container.setItem(slot, ItemStack.EMPTY);
        }
        return true;
    }

    /** 向副产物槽追加 1 个指定物品（槽满返回 false = 产物回压停摆） */
    public static boolean addOne(SimpleContainer container, int slot, String itemId) {
        ItemStack stack = container.getItem(slot);
        if (stack.isEmpty()) {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, itemId));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return false;
            }
            container.setItem(slot, new ItemStack(item, 1));
            return true;
        }
        if (!matchesId(stack, itemId) || stack.getCount() >= stack.getMaxStackSize()) {
            return false;
        }
        stack.grow(1);
        return true;
    }

    /** 物品注册名是否匹配（biocraft 命名空间内按注册名比对） */
    public static boolean matchesId(ItemStack stack, String itemId) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(BioCraft.MODID) && key.getPath().equals(itemId);
    }
}

