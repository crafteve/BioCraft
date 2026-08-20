package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.MachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 序列机方块类（第二方块类：世界的接线员，与酶反应腔 MachineBlock 并列）
 * <p>
 * 一台序列机一个方块实例（共享本类），kind 决定处理器与容器布局；
 * 序列机是"信息处理设备"（显示器式外观/交互与酶反应腔不同），
 * 故独立成类——方块类只承载放置/右键/掉落/tick 心跳/模型这些"表皮事务"
 */
public class SequenceMachineBlock extends Block implements EntityBlock {

    /** 水平朝向：放置时正面朝玩家 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

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
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public SequenceMachineKind getKind() {
        return kind;
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            // NeoForge 打开数据：BlockPos + 子类钩子（编码器追加编辑器草稿，
            // 与 MachineBlock 同款——漏掉会导致客户端读草稿越界崩溃）
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

    private static void dropContainerContents(Level level, BlockPos pos, MachineBlockEntity machine) {
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
