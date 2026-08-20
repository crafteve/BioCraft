package com.github.crafteve.biocraft.blockentity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 机器容器（SimpleContainer 的具名封装）
 * <p>
 * 原先在 MachineBlockEntity 构造器内以匿名内部类覆写 4 个方法，
 * 逻辑分散且难以复用；抽为具名类后职责清晰：容器只做委托，
 * 具体容量与门控由所属 BE 的钩子决定
 */
public class MachineContainer extends SimpleContainer {

    private final MachineBlockEntity owner;

    public MachineContainer(MachineBlockEntity owner, int containerSize) {
        super(containerSize);
        this.owner = owner;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        owner.setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return owner.slotStackLimit();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return owner.slotStackLimit();
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return owner.canPlaceItemInternal(index, stack);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (!owner.canTakeItemInternal(index)) {
            return ItemStack.EMPTY;
        }
        return super.removeItem(index, count);
    }
}
