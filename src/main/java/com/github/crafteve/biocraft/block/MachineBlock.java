package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.blockentity.MachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineType;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 唯一的机器方块类（AGENTS.md 1.4 硬性规则）
 * <p>
 * 所有机器共用本类，通过构造参数区分两类机器：
 * <ul>
 *   <li>MachineType 构造：中心法则链原始机器（DNA 编码器），手动注册</li>
 *   <li>EnzymeFactoryData 构造：酶工厂（数据驱动注册），方块持有酶数据档案，
 *       放置时由共享的酶工厂方块实体按方块取回数据</li>
 * </ul>
 * BlockEntity 工厂按"是否酶工厂"双分派创建对应实体；
 * 方块类只承载方块行为（放置/右键交互/破坏掉落/硬度/地图色），
 * 机器的业务逻辑（容器/进度/合成）全部在 BlockEntity 层
 */
public class MachineBlock extends Block implements EntityBlock {
    /** 原始机器类型（非酶工厂时为 null） */
    private final MachineType machineType;

    /** 酶工厂数据档案（非酶工厂时为 null） */
    private final EnzymeFactoryData enzymeFactoryData;

    /**
     * 原始机器构造（DNA 编码器）
     *
     * @param machineType 机器类型，决定 BlockEntity 分派与方块外观属性
     */
    public MachineBlock(MachineType machineType) {
        super(Properties.of()
                .mapColor(machineType.getMapColor())
                .strength(1.5F)
                .sound(SoundType.METAL));
        this.machineType = machineType;
        this.enzymeFactoryData = null;
    }

    /**
     * 酶工厂构造（数据驱动注册）
     * <p>
     * 地图色统一取灰色（地图上不做类别区分，视觉区分由方块 tint 承担）；
     * 方块持有数据档案的引用，方块实体放置时从方块取回（共享 BE 类型无需每实例注册）
     *
     * @param enzymeFactoryData 酶数据档案
     */
    public MachineBlock(EnzymeFactoryData enzymeFactoryData) {
        super(Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .sound(SoundType.METAL));
        this.machineType = null;
        this.enzymeFactoryData = enzymeFactoryData;
    }

    /**
     * 按机器种类分派创建对应的 BlockEntity
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 对应类型的实体，未知类型返回 null（快速失败由注册侧保证不会出现）
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (enzymeFactoryData != null) {
            return new EnzymeFactoryBlockEntity(pos, state);
        }
        return switch (machineType) {
            case DNA_ENCODER -> new DNAEncoderBlockEntity(pos, state);
        };
    }

    /**
     * 右键交互：打开机器 GUI
     * <p>
     * BlockEntity 实现 MenuProvider，玩家 openMenu 时写入方块位置，
     * 客户端据此重建容器（Menu 构造从数据包读取 BlockPos）
     *
     * @param state     方块状态
     * @param level     所在世界
     * @param pos       方块位置
     * @param player    交互玩家
     * @param hitResult 命中信息
     * @return 服务端/客户端均返回成功，保证客户端渲染"交互成功"动画
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            player.openMenu(machine, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 方块被破坏时把容器内容全部掉落为实体物品
     * <p>
     * 序列物品的 NBT（序列内容）随物品堆保存，掉落回收不丢失数据；
     * 缓冲池等容器之外的库存由方块实体的 dropExtraContents 处理（服务端）
     * <p>
     * 掉落逻辑必须放在 onRemove 而非 BlockEntity.setRemoved：
     * 世界卸载/区块卸载同样触发 setRemoved，会误爆库存（实测 bug）
     *
     * @param state         原方块状态
     * @param level         所在世界
     * @param pos           方块位置
     * @param newState      新方块状态
     * @param movedByPiston 是否为活塞推动
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
                Containers.dropContents(level, pos, machine.getContainer());
                machine.dropExtraContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 获取原始机器类型（datagen 等按类型生成资源用）
     *
     * @return 机器类型，酶工厂为 null
     */
    @Nullable
    public MachineType getMachineType() {
        return machineType;
    }

    /**
     * 获取酶数据档案（方块实体放置时取回数据）
     *
     * @return 酶数据档案，非酶工厂为 null
     */
    @Nullable
    public EnzymeFactoryData getEnzymeFactoryData() {
        return enzymeFactoryData;
    }
}
