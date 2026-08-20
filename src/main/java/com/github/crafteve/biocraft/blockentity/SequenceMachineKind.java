package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.block.SequenceMachineBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 序列机类型：方块实例 ↔ 处理器/容器容量 的硬绑定注册表
 * <p>
 * 每台序列机 = 一个方块实例（共享 SequenceMachineBlock 类）+ 一个处理器；
 * BE 从方块状态解析自身 kind（一台机器干一件事，不做 0 槽动态解析）
 */
public enum SequenceMachineKind {
    DNA_ENCODER("dna_encoder", DnaSynthesisOperation::new, 2),
    TRANSCRIBER("transcriber", TranscriptionOperation::new, 4);

    private final String blockId;
    private final Supplier<SequenceOperation> operationFactory;
    private final int containerSize;

    SequenceMachineKind(String blockId, Supplier<SequenceOperation> operationFactory, int containerSize) {
        this.blockId = blockId;
        this.operationFactory = operationFactory;
        this.containerSize = containerSize;
    }

    public String blockId() {
        return blockId;
    }

    public SequenceOperation createOperation() {
        return operationFactory.get();
    }

    public int containerSize() {
        return containerSize;
    }

    /** 从方块状态解析 kind（非序列机方块返回 null） */
    public static SequenceMachineKind fromBlockState(BlockState state) {
        if (state.getBlock() instanceof SequenceMachineBlock block) {
            return block.getKind();
        }
        return null;
    }
}
