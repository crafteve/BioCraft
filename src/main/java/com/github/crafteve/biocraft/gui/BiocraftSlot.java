package com.github.crafteve.biocraft.gui;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 机器槽位基类（消除 MachineMenu/SequenceMachineMenu 的重复样板）
 * <p>
 * 统一处理：堆叠上限按槽位容量（非物品自带上限）与 isActive 恒 true。
 * 具体放置/取出门控由子类覆写 mayPlace/mayPickup 决定
 */
public abstract class BiocraftSlot extends Slot {

    private final int maxStack;

    protected BiocraftSlot(Container container, int slot, int x, int y, int maxStack) {
        super(container, slot, x, y);
        this.maxStack = maxStack;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return maxStack;
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
