package com.github.crafteve.biocraft.blockentity.sequence;

import com.github.crafteve.biocraft.block.SequenceMachineBlock;
import com.github.crafteve.biocraft.blockentity.sequence.operation.DnaSynthesisOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.FolderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.HelicaseOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.LoaderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranscriptionOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.SequenceSlotSpec;
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
    DNA_ENCODER("dna_encoder", DnaSynthesisOperation::new),
    TRANSCRIBER("TRANSCRIBER", TranscriptionOperation::new),
    HELICASE("HELICASE", HelicaseOperation::new),
    LOADER("LOADER", LoaderOperation::new),
    TRANSLATOR("TRANSLATOR", TranslatorOperation::new),
    FOLDER("folder", FolderOperation::new);

    private final String blockId;
    private final Supplier<SequenceOperation> operationFactory;
    private DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> menuHolder;

    SequenceMachineKind(String blockId, Supplier<SequenceOperation> operationFactory) {
        this.blockId = blockId;
        this.operationFactory = operationFactory;
    }

    public String blockId() {
        return blockId;
    }

    public SequenceOperation createOperation() {
        return operationFactory.get();
    }

    /**
     * 容器槽总数 = 槽位规格表长度（单一事实源，见 SequenceSlotSpec）
     * <p>原为枚举构造参数手写整数，与 Menu.slotPositions 数组、Screen 卡片列表三处
     * 各写一遍、靠人眼对齐，翻译机曾因槽位表 24/25 ≠ 26 导致背包首格被误当 PPi 输出槽。
     * 现改由规格表 {@link SequenceSlotSpec#of} 派生，三处恒等</p>
     */
    public int containerSize() {
        return SequenceSlotSpec.of(this).size();
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

