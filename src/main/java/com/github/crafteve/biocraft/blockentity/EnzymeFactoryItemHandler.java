package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.item.EnzymeItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 酶反应腔的工业 IO 适配器（IItemHandlerModifiable），供管道类模组接入
 * <p>
 * 设计要点：
 * <ul>
 *   <li>槽位协议：0 = 酶槽（只接受酶蛋白物品，堆叠上限 64 = [E]），
 *       1..n = 物种槽（当前酶的物种，与 GUI/漏斗共用同一容器实例）</li>
 *   <li>物种过滤：insertItem/setStackInSlot 校验槽位对应物种，
 *       不匹配直接拒绝（返回原 stack / 置空），与 GUI 的 RestrictedSlot 同规则；
 *       酶槽物种校验 = 必须是酶蛋白物品（任意酶种，registry 由 BE 解析）</li>
 *   <li>酶槽（0）管道侧单向：只可抽入（insertItem 接受任意酶 tag 物品），
 *       禁止抽出（extractItem 恒返回空）——换酶只能走 GUI/漏斗手动操作，
 *       防管道自动把酶抽走触发换酶清空物种槽（物流自动化意外清机）；
 *       GUI 酶槽取出不受影响（走 RestrictedSlot，不经本类）</li>
 *   <li>全槽位可进可出（除酶槽管道侧单向外）：产物槽同样允许输入（可逆
 *       反应逆向供料）、反应物槽允许抽出（回收），不做方向/面区分</li>
 *   <li>复用 setChanged 链：容器操作 → BE.setChanged() → syncFromSlots()
 *       回写引擎浓度，零额外同步代码</li>
 *   <li>性能：全部方法 O(1) 直接索引，无循环扫描；每 BE 懒加载单例</li>
 * </ul>
 * 本类不改动容器结构：SimpleContainer 仍是内部权威存储，
 * 玩家 GUI / 原版漏斗 / 工业管道三路共用同一容器
 */
public class EnzymeFactoryItemHandler implements IItemHandlerModifiable {
    /** 宿主方块实体（浓度回写经其 setChanged 链完成） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /**
     * @param blockEntity 宿主酶反应腔方块实体
     */
    public EnzymeFactoryItemHandler(EnzymeFactoryBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * 槽位总数（= 固定容量：酶槽 + 最大非 fe 物种数）
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
        int limit = getSlotLimit(slot);
        int canInsert;
        if (inSlot.isEmpty()) {
            canInsert = Math.min(limit, stack.getCount());
        } else {
            // 槽位已有物品时必须同种才能合并（vanilla ItemStackHandler 同款语义）：
            // 不同种直接拒绝插入，否则 grow 会静默吞掉入参物品并把槽内物品数量
            // 错误累加（实测 Pipez 把 HK 插进已有 PGI 的酶槽 → PGI 数量 +1、HK 消失）
            if (!ItemStack.isSameItemSameComponents(inSlot, stack)) {
                return stack;
            }
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
     * 抽取物品：物种槽全槽位可抽（不限物种），酶槽禁止抽出，支持模拟
     * <p>
     * 酶槽（0）管道侧单向：只可抽入不可抽出——换酶只能走 GUI/漏斗手动
     * 操作，防管道自动把酶抽走触发换酶清空物种槽（物流自动化意外清机）；
     * 管道"模拟抽 1 探测"（simulate=true）同样被拒 → 不会发起真实抽取
     * <p>
     * IO 模式门控：区域为"仅输入"时禁止抽出（INPUT_ONLY.allowsExtract=false），
     * 与 GUI mayPickup/漏斗 removeItem 同规则；模拟与真实抽取同样门控
     * （管道"模拟抽 1 探测"被拒 → 不会发起真实抽取）
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
        if (slot == EnzymeFactoryBlockEntity.ENZYME_SLOT) {
            return ItemStack.EMPTY;
        }
        if (!blockEntity.canExtractFromSlot(slot)) {
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
     * 槽位容量上限：酶槽 = 64（[E] 上限），物种槽 = 64×SLOT_GROUPS 组
     *
     * @param slot 槽位下标
     * @return 槽位容量
     */
    @Override
    public int getSlotLimit(int slot) {
        if (slot == EnzymeFactoryBlockEntity.ENZYME_SLOT) {
            return 64;
        }
        return 64 * com.github.crafteve.biocraft.reaction.KineticConstants.SLOT_GROUPS;
    }

    /**
     * 物种合法性校验（insertItem/setStackInSlot 共用）
     * <p>
     * IO 模式门控在前：区域为"仅输出"时拒绝一切插入（与 GUI mayPlace/
     * 漏斗 canPlaceItem 同规则，防管道把物品塞进产物槽造成容量冻结）；
     * 酶槽：只接受酶 tag 内物品（biocraft:enzyme，含全部酶蛋白物品；
     * 未来非酶催化剂物品加 tag 即可被接受）；
     * 物种槽：必须是当前酶对应物种（无酶/未用槽位拒绝一切）
     *
     * @param slot  槽位下标
     * @param stack 待校验物品堆
     * @return true 表示该槽位接受此物品
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSlots() || stack.isEmpty()) {
            return false;
        }
        if (!blockEntity.canInsertIntoSlot(slot)) {
            return false;
        }
        if (slot == EnzymeFactoryBlockEntity.ENZYME_SLOT) {
            return stack.is(com.github.crafteve.biocraft.init.ModTags.ENZYME_ITEMS);
        }
        String speciesId = blockEntity.getSpeciesId(slot);
        if (speciesId == null) {
            return false;
        }
        return stack.is(com.github.crafteve.biocraft.init.ModItems.byId(speciesId).get());
    }

    /**
     * 强制写入槽位（IItemHandlerModifiable 契约）
     * <p>
     * 补 InvWrapper 的漏洞：这里同样做物种校验，
     * 非法物种直接置空槽位；合法写入经 setChanged 链回写引擎浓度
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
