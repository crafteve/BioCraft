package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineType;
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
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 唯一的机器方块类（AGENTS.md 1.4 硬性规则）
 * <p>
 * 所有机器共用本类，方块注册时通过构造参数 MachineType 区分；
 * BlockEntity 工厂按类型分派创建对应的实体子类
 * <p>
 * 方块类只承载方块行为（放置/右键交互/破坏掉落/硬度/地图色），
 * 机器的业务逻辑（容器/进度/合成）全部在 BlockEntity 层，
 * 因此不同机器共享方块类不会损失任何功能差异的表达能力
 */
public class MachineBlock extends Block implements EntityBlock {
    private final MachineType machineType;

    /**
     * @param machineType 机器类型，决定 BlockEntity 分派与方块外观属性
     */
    public MachineBlock(MachineType machineType) {
        super(Properties.of()
                .mapColor(machineType.getMapColor())
                .strength(1.5F)
                .sound(SoundType.METAL));
        this.machineType = machineType;
    }

    /**
     * 按机器类型分派创建对应的 BlockEntity
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 对应类型的实体，未知类型返回 null（快速失败由注册侧保证不会出现）
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
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
     * 获取机器类型（供 datagen 等工具按类型生成对应资源）
     *
     * @return 机器类型
     */
    public MachineType getMachineType() {
        return machineType;
    }
}
