package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.base.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 机器方块唯一抽象基类（酶反应腔与序列机共用）
 * <p>
 * 抽取两类机器方块的公共皮层：水平朝向（放置/旋转/镜像）、右键打开 GUI、
 * 破坏掉落（容器按 64 拆堆 + 额外掉落钩子）。
 * 机器的"业务差异"（创建哪种 BE、每 tick 跑哪种逻辑）由子类实现
 * <p>
 * 命名：BioCraft 前缀标明为模组机器基类，区别于原版 Block 与
 * NeoForge 的 BaseEntityBlock 等；两大子类 EnzymeMachineBlock / SequenceMachineBlock 具象并列
 */
public abstract class BioCraftMachineBlock extends Block implements EntityBlock {

    /** 水平朝向：正面放置时面向玩家（熔炉同款） */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected BioCraftMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            player.openMenu(machine, buf -> {
                buf.writeBlockPos(pos);
                machine.writeMenuOpeningData(buf);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
                dropContainerContents(level, pos, machine);
                machine.dropExtraContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 容器内容掉落：按 64 拆堆生成掉落物实体
     * <p>
     * 槽位容量 128 时直接掉 count=128 的堆会在存档时触发 CODEC [1,99] 校验崩溃，
     * 此处按 64 拆堆保证每堆存档安全
     */
    protected static void dropContainerContents(Level level, BlockPos pos, MachineBlockEntity machine) {
        net.minecraft.world.SimpleContainer container = machine.getContainer();
        for (int i = 0; i < container.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            while (!stack.isEmpty()) {
                int drop = Math.min(stack.getCount(), 64);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        stack.split(drop));
            }
        }
    }
}

