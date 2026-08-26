package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * 序列机方块类（中心法则信息线，与酶反应腔 {@link MachineBlock} 并列）
 * <p>
 * 机器两大族之一：信息处理线。一台序列机一个方块实例（共享本类），
 * kind 决定处理器与容器布局；序列机是"信息处理设备"（显示器式外观/交互
 * 与酶反应腔不同），故独立成类——方块类只承载皮层行为（放置/右键/掉落/
 * tick 心跳/模型），业务逻辑全部在 BlockEntity 层
 * <p>
 * 与 {@link MachineBlock} 并列，共享 {@link BioCraftMachineBlock} 基类
 */
public class SequenceMachineBlock extends BioCraftMachineBlock {

    private final SequenceMachineKind kind;

    public SequenceMachineBlock(SequenceMachineKind kind) {
        this(Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .sound(SoundType.METAL), kind);
    }

    public SequenceMachineBlock(Properties properties, SequenceMachineKind kind) {
        super(properties);
        this.kind = kind;
    }

    public SequenceMachineKind getKind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SequenceMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) ->
                SequenceMachineBlockEntity.serverTick(lvl, pos, st, (SequenceMachineBlockEntity) be);
    }
}
