package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.EnzymeSimulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 酶工厂方块实体（共享一个 BlockEntityType，全酶实例共用）
 * <p>
 * 设计（M2 骨架阶段，M3 填充完整逻辑）：
 * <ul>
 *   <li>方块实体类型共享：全部酶工厂方块注册进同一个 BlockEntityType，
 *       实体从 BlockState 取回本机酶数据（无需每实例注册）</li>
 *   <li>容器：每物种一槽（物种数 = 反应物+产物数，槽位顺序 = JSON 顺序），
 *       由 EnzymeFactoryData 在构造时换算</li>
 *   <li>引擎：持有 EnzymeSimulator 实例（注册期构建，含全部数据断言）</li>
 *   <li>M3 将填充：槽位↔浓度桥接（引擎权威 + 事件回写 + 进度条可视化）、
 *       策略层活性、serverTick 流水线、ContainerData 同步</li>
 * </ul>
 * 槽位布局：物种槽在前（物种数由酶数据决定），玩家背包槽由 Menu 追加
 */
public class EnzymeFactoryBlockEntity extends MachineBlockEntity {
    /** 本机酶数据档案（从方块取回，构造时不可空） */
    private final EnzymeFactoryData enzymeData;

    /** 引擎模拟器实例（注册期构建，含数据防火墙断言） */
    private final EnzymeSimulator simulator;

    /**
     * @param pos   方块位置
     * @param state 方块状态（其方块必须是酶工厂 MachineBlock）
     */
    public EnzymeFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ENZYME_FACTORY_BE.get(), pos, state,
                enzymeContainerSize(state));
        MachineBlock block = (MachineBlock) state.getBlock();
        this.enzymeData = block.getEnzymeFactoryData();
        this.simulator = enzymeData.buildSimulator();
    }

    /**
     * 从方块状态取回酶数据并换算容器槽位数（每物种一槽）
     *
     * @param state 方块状态
     * @return 物种总数（反应物 + 产物）
     */
    private static int enzymeContainerSize(BlockState state) {
        MachineBlock block = (MachineBlock) state.getBlock();
        EnzymeFactoryData data = block.getEnzymeFactoryData();
        return data.reactants().size() + data.products().size();
    }

    /**
     * 获取本机酶数据档案（GUI/网络/策略层共用）
     *
     * @return 酶数据档案
     */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }

    /**
     * 获取引擎模拟器实例（M3 桥接逻辑使用）
     *
     * @return 引擎模拟器
     */
    public EnzymeSimulator getSimulator() {
        return simulator;
    }

    /**
     * 存档（M2 骨架：M3 填充浓度/余量等引擎状态）
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    /**
     * 读档（M2 骨架：M3 填充）
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }

    /**
     * 获取方块显示名（GUI 标题与玩家反馈共用）
     * <p>
     * 直接取酶数据的中文名，无需语言文件键
     *
     * @return 方块翻译组件
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal(enzymeData.name());
    }

    /**
     * 创建菜单（M3 实现 MachineMenu，本阶段返回 null 占位——方块 GUI 暂不可开）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param player          打开菜单的玩家
     * @return 酶工厂菜单实例
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }
}
