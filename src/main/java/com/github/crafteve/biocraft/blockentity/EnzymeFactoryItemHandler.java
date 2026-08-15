package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 酶工厂的工业 IO 适配器（IItemHandlerModifiable），供管道类模组接入
 * <p>
 * 设计要点（与策划确认的方案）：
 * <ul>
 *   <li>槽位顺序 = 引擎物种顺序（反应物 0,1,2… 后接产物），与 GUI/漏斗共用
 *       同一容器实例，天然一致</li>
 *   <li>物种过滤：insertItem/setStackInSlot 校验槽位对应物种，
 *       不匹配直接拒绝（返回原 stack / 置空），与 GUI 的 RestrictedSlot 同规则</li>
 *   <li>全槽位可进可出：产物槽同样允许输入（可逆反应逆向供料）、
 *       反应物槽允许抽出（回收），不做方向/面区分（side 参数忽略）</li>
 *   <li>复用 setChanged 链：容器操作 → BE.setChanged() → syncFromSlots()
 *       回写引擎浓度，零额外同步代码</li>
 *   <li>性能：全部方法 O(1) 直接索引，无循环扫描；每 BE 懒加载单例
 *       （getItemHandler 返回同一实例），避免管道每 tick 查询时分配对象</li>
 * </ul>
 * 本类不改动容器结构：SimpleContainer 仍是内部权威存储，
 * 玩家 GUI / 原版漏斗 / 工业管道三路共用同一容器
 */
public class EnzymeFactoryItemHandler implements IItemHandlerModifiable {
    /** 宿主方块实体（浓度回写经其 setChanged 链完成） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /**
     * @param blockEntity 宿主酶工厂方块实体
     */
    public EnzymeFactoryItemHandler(EnzymeFactoryBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * 槽位总数（= 物种数，反应物 + 产物）
     *
     * @return 槽位数
     */
    @Override
    public int getSlots() {
        return blockEntity.getContainer().getContainerSize();
    }

    /**
     * 读取槽位物品（禁止修改返回值，IItemHandler 契约）
     *
     * @param slot 槽位下标
     * @return 槽位当前物品堆
     */
    @Override
    public ItemStack getStackInSlot(int slot) {
        return blockEntity.getContainer().getItem(slot);
    }

    /**
     * 插入物品：物种过滤 + 容量合并
     * <p>
     * 槽位只接受对应物种（reaction 中该槽位的分子物品），
     * 不匹配返回原 stack 拒绝；匹配则按槽位剩余容量合并，
     * 空槽直接放置。simulate=true 仅计算不修改
     *
     * @param slot     槽位下标
     * @param stack    待插入物品堆（不可修改）
     * @param simulate true 仅模拟
     * @return 未能插入的剩余物品堆（全部插入成功返回空堆）
     */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= getSlots()) {
            return stack;
        }
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        SimpleContainer container = blockEntity.getContainer();
        ItemStack inSlot = container.getItem(slot);
        int limit = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
        int canInsert;
        if (inSlot.isEmpty()) {
            canInsert = Math.min(limit, stack.getCount());
        } else {
            // 槽位非空：必须同物种才能叠加（isItemValid 已保证是合法物种）
            canInsert = Math.min(limit - inSlot.getCount(), stack.getCount());
            if (canInsert <= 0) {
                return stack;
            }
        }
        if (simulate) {
            ItemStack remainder = stack.copy();
            remainder.shrink(canInsert);
            return remainder;
        }
        if (inSlot.isEmpty()) {
            container.setItem(slot, stack.copy().split(canInsert));
        } else {
            inSlot.grow(canInsert);
            container.setChanged();
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(canInsert);
        return remainder;
    }

    /**
     * 抽取物品：全槽位可抽（不限物种），支持模拟
     * <p>
     * 产物槽可抽走产物、反应物槽也可抽走反应物（回收）；
     * 返回数量 ≤ amount 的副本，容器减量走原生 API 触发浓度回写
     *
     * @param slot     槽位下标
     * @param amount   抽取数量
     * @param simulate true 仅模拟
     * @return 抽取到的物品堆副本（空堆表示无可抽）
     */
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= getSlots()) {
            return ItemStack.EMPTY;
        }
        SimpleContainer container = blockEntity.getContainer();
        ItemStack inSlot = container.getItem(slot);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int toExtract = Math.min(amount, inSlot.getCount());
        if (simulate) {
            return inSlot.copy().split(toExtract);
        }
        return container.removeItem(slot, toExtract);
    }

    /**
     * 槽位容量上限（64 = 满堆叠）
     *
     * @param slot 槽位下标
     * @return 槽位容量
     */
    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    /**
     * 物种合法性校验（insertItem/setStackInSlot 共用）
     *
     * @param slot  槽位下标
     * @param stack 待校验物品堆
     * @return true 表示该槽位接受此物种
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSlots()) {
            return false;
        }
        return stack.is(ModItems.byId(blockEntity.getSpeciesId(slot)).get());
    }

    /**
     * 强制写入槽位（IItemHandlerModifiable 契约）
     * <p>
     * 补 InvWrapper 的漏洞：这里同样做物种校验，
     * 非法物种直接置空槽位（防管道类模组绕过 insertItem 过滤）；
     * 合法写入经 setChanged 链回写引擎浓度
     *
     * @param slot  槽位下标
     * @param stack 待写入物品堆
     */
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSlots()) {
            return;
        }
        SimpleContainer container = blockEntity.getContainer();
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            container.setItem(slot, ItemStack.EMPTY);
        } else {
            ItemStack copy = stack.copy();
            copy.setCount(Math.min(copy.getCount(), getSlotLimit(slot)));
            container.setItem(slot, copy);
        }
    }
}
