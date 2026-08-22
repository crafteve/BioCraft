package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.block.SequenceMachineBlock;
import com.github.crafteve.biocraft.gui.SequenceMachineMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * 序列机类型：方块实例 ↔ 处理器/容器容量 的硬绑定注册表
 * <p>
 * 每台序列机 = 一个方块实例（共享 SequenceMachineBlock 类）+ 一个处理器；
 * BE 从方块状态解析自身 kind（一台机器干一件事，不做 0 槽动态解析）
 */
public enum SequenceMachineKind {
    DNA_ENCODER("dna_encoder", DnaSynthesisOperation::new, 8),
    TRANSCRIBER("transcriber", TranscriptionOperation::new, 8),
    HELICASE("helicase", HelicaseOperation::new, 3),
    LOADER("loader", LoaderOperation::new, 6),
    TRANSLATOR("translator", TranslatorOperation::new, 26);

    private final String blockId;
    private final Supplier<SequenceOperation> operationFactory;
    private final int containerSize;
    private DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> menuHolder;

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

    /** 绑定菜单类型（由 ModBlocks 注册后注入，消除 switch 硬编码） */
    public void setMenuHolder(DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> holder) {
        this.menuHolder = holder;
    }

    /** 获取本机菜单类型（未绑定时返回 null） */
    public MenuType<SequenceMachineMenu> menuType() {
        return menuHolder != null ? menuHolder.get() : null;
    }

    /** 从方块状态解析 kind（非序列机方块返回 null） */
    public static SequenceMachineKind fromBlockState(BlockState state) {
        if (state.getBlock() instanceof SequenceMachineBlock block) {
            return block.getKind();
        }
        return null;
    }
}
