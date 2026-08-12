package com.github.crafteve.biocraft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

/**
 * 机器方块实体基类
 * <p>
 * 承载所有机器共享的基础设施：
 * <ul>
 *   <li>容器（SimpleContainer）：槽位数量由机器类型决定</li>
 *   <li>NBT 存档：容器内容随方块实体保存/加载，序列物品 NBT 不丢失</li>
 *   <li>MenuProvider：右键打开 GUI 的统一入口</li>
 * </ul>
 * 各机器的业务逻辑（合成、进度、消耗）由子类实现
 */
public abstract class MachineBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    /** 机器容器，槽位数在子类构造时通过容器大小参数确定 */
    protected final SimpleContainer inventory;

    /**
     * @param type          方块实体类型
     * @param pos           方块位置
     * @param state         方块状态
     * @param containerSize 容器槽位总数（由 MachineType 决定）
     */
    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int containerSize) {
        super(type, pos, state);
        this.inventory = new SimpleContainer(containerSize) {
            /**
             * 容器内容变化时向方块实体转发标记
             * <p>
             * 父容器的默认 setChanged 不联动 BlockEntity，
             * 不转发会导致内容变化（含漏斗/菜单操作）不触发存档
             */
            @Override
            public void setChanged() {
                super.setChanged();
                MachineBlockEntity.this.setChanged();
            }
        };
    }

    /**
     * 获取机器容器（Menu 槽位与破坏掉落共用）
     *
     * @return 容器实例
     */
    public SimpleContainer getContainer() {
        return inventory;
    }

    /**
     * 方块被破坏/替换时的额外掉落钩子（仅服务端）
     * <p>
     * 由 MachineBlock.onRemove 统一调用：容器内容已由 onRemove 掉落，
     * 本方法用于掉落容器之外的特殊库存（如 DNA 编码器的缓冲池碱基）
     * <p>
     * 注意不可依赖 setRemoved() 实现掉落：世界卸载/区块卸载同样触发
     * setRemoved，会导致进出存档时物品爆出（实测 bug）
     *
     * @param level 所在世界
     * @param pos   方块位置
     */
    public void dropExtraContents(Level level, BlockPos pos) {
        // 基类空实现：无额外掉落，子类按需覆盖
    }

    /**
     * 保存方块实体数据（容器内容）
     * <p>
     * 1.20.5+ 的 NBT API 要求传入注册表查找器（物品堆编解码需要解析物品 id）
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.createTag(registries));
    }

    /**
     * 加载方块实体数据（容器内容）
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory", Tag.TAG_LIST)) {
            inventory.fromTag(tag.getList("inventory", Tag.TAG_COMPOUND), registries);
        }
    }

    /**
     * MenuProvider 实现：返回当前玩家使用的菜单容器
     * <p>
     * 方块类右键交互时调用 openMenu，最终到达本方法创建菜单
     *
     * @param containerId 菜单容器编号（服务端分配）
     * @param player      打开菜单的玩家
     * @return 机器对应的菜单实例
     */
    @Override
    public abstract AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, Player player);
}
