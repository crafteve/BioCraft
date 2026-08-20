package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.gui.SequenceMachineMenu;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 序列机 BE 基类：只做编排（tick 步进/存档/停摆/物化），不懂具体操作
 * <p>
 * 链源模型（设计稿 §5）：SeqStepState（stage/position/chain）= 唯一真相，
 * 产物槽物品 = 物化（每步同步刷新）；取走产物自动重建新物品继续、
 * 原料不够停止（state 保留，补料即续）、换模板/换程序归零 + 旧产物弹出；
 * 步进频率 K = STEP_TICKS（配置常量，Phase 3/4 工程读速从这挂入）
 */
public class SequenceMachineBlockEntity extends MachineBlockEntity {

    private final SequenceOperation operation;
    private final SeqStepState stepState = new SeqStepState();
    private int stepCooldown = 0;

    public SequenceMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, resolveContainerSize(state));
        this.operation = resolveOperation(state);
    }

    /** 方块实体工厂构造（BlockEntityType.Builder.of 需要 (BlockPos, BlockState) 签名） */
    public SequenceMachineBlockEntity(BlockPos pos, BlockState state) {
        this(com.github.crafteve.biocraft.init.ModBlocks.SEQUENCE_BE.get(), pos, state);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    private static int resolveContainerSize(BlockState state) {
        SequenceMachineKind kind = SequenceMachineKind.fromBlockState(state);
        return kind != null ? kind.containerSize() : 2;
    }

    private static SequenceOperation resolveOperation(BlockState state) {
        SequenceMachineKind kind = SequenceMachineKind.fromBlockState(state);
        return kind != null ? kind.createOperation() : new DnaSynthesisOperation();
    }

    public SequenceMachineKind kind() {
        return SequenceMachineKind.fromBlockState(getBlockState());
    }

    public SequenceOperation operation() {
        return operation;
    }

    public SeqStepState stepState() {
        return stepState;
    }

    /** 服务端每 tick 调度（SequenceMachineBlock.getTicker 挂载） */
    public static void serverTick(Level level, BlockPos pos, BlockState blockState, SequenceMachineBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        switch (stepState.stage()) {
            case IDLE -> {
                if (operation.canStart(inventory, stepState) && operation.init(inventory, stepState)) {
                    materialize();
                    setChanged();
                }
            }
            case EXTENDING -> {
                if (--stepCooldown > 0) {
                    return;
                }
                stepCooldown = SequenceConstants.STEP_TICKS;
                SequenceOperation.StepResult result = operation.step(inventory, stepState);
                if (result == SequenceOperation.StepResult.DONE) {
                    operation.finish(inventory, stepState);
                    stepState.setStage(SeqStepState.Stage.DONE);
                }
                materialize();
                setChanged();
            }
            case DONE -> {
                // 完成态：产物可被取走；取走后自动回 IDLE——
                // 转录仪（模板 KEEP）随即用同一模板自动开始新一轮；
                // 编码器（pendingProgram 已空）停在 IDLE 等玩家重新提交程序
                if (inventory.getItem(operation.outputSlot()).isEmpty()) {
                    stepState.setStage(SeqStepState.Stage.IDLE);
                    setChanged();
                }
            }
        }
    }

    /** 物化链前缀到产物槽（产物被取走后自动重建新物品） */
    private void materialize() {
        operation.materialize(inventory, stepState);
    }

    /**
     * 编码器提交程序文本（网络包到达）：换文本 = 换模板语义
     * <p>先编码验证（超上限直接拒绝）；旧产物弹出；状态归零 + 写入新程序</p>
     */
    public void submitProgram(String program) {
        if (program == null || program.isEmpty()) {
            return;
        }
        try {
            SeqCodec.encodeText(program);
        } catch (IllegalArgumentException e) {
            return; // 超上限：拒绝
        }
        ItemStack old = inventory.getItem(DnaSynthesisOperation.SLOT_OUTPUT);
        if (!old.isEmpty()) {
            inventory.setItem(DnaSynthesisOperation.SLOT_OUTPUT, ItemStack.EMPTY);
            if (level != null) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, old);
            }
        }
        stepState.reset();
        stepState.setPendingProgram(program);
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SequenceMachineMenu(kind(), containerId, playerInventory, this);
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("seqState", stepState.save(new CompoundTag()));
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("seqState", Tag.TAG_COMPOUND)) {
            stepState.load(tag.getCompound("seqState"));
        }
    }

    @Override
    protected boolean canPlaceItemInternal(int slot, ItemStack stack) {
        return operation.isItemValidForSlot(slot, stack);
    }

    /** 简化：所有槽位可抽（玩家手动管理模板/单体）；产物槽机器自治只取不放由 isItemValidForSlot 兜底 */
    @Override
    protected boolean canTakeItemInternal(int slot) {
        return true;
    }
}
