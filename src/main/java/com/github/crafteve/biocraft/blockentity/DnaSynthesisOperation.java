package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * DNA 编码器操作：程序文本 → 程序 DNA（链源延伸，每步消耗 1 dNTP）
 * <p>
 * 槽位：0 = dNTP 单体池（四种均可进），1 = 产物（dna 物品，物化链前缀）
 * <p>
 * 换文本 = 换模板语义（submitProgram 归零 + 旧产物弹出，见 BE）；任何文本
 * 都能编码（编解码器是全函数），错误全部留给将来的折叠机裁决
 */
public class DnaSynthesisOperation implements SequenceOperation {

    public static final int SLOT_MONOMER = 0;
    public static final int SLOT_OUTPUT = 1;

    private static final Set<String> DNTP = Set.of("datp", "dttp", "dctp", "dgtp");

    @Override
    public int outputSlot() {
        return SLOT_OUTPUT;
    }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) {
            return true;
        }
        return state.stage() == SeqStepState.Stage.IDLE && !state.pendingProgram().isEmpty();
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        String program = state.pendingProgram();
        if (program == null || program.isEmpty()) {
            return false;
        }
        try {
            String encoded = SeqCodec.encodeText(program);
            state.beginExtending(encoded);
            state.setPendingProgram("");
            return true;
        } catch (IllegalArgumentException e) {
            // 超上限程序：拒绝并清空待处理（防无限重试）
            state.setPendingProgram("");
            return false;
        }
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) {
            return StepResult.DONE;
        }
        char base = state.chain().charAt(state.position());
        String dnTP = switch (base) {
            case 'A' -> "datp";
            case 'T' -> "dttp";
            case 'C' -> "dctp";
            case 'G' -> "dgtp";
            default -> throw new IllegalStateException("非法碱基: " + base);
        };
        if (!SequenceOperation.consumeOne(container, SLOT_MONOMER, dnTP)) {
            return StepResult.STALLED;
        }
        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        ItemStack stack = new ItemStack(ModItems.DNA.get());
        String seq = state.chain().substring(0, Math.min(state.position(), state.chain().length()));
        boolean complete = state.position() >= state.total();
        stack.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.DS, SequenceData.Kind.PROGRAM, seq, complete));
        container.setItem(SLOT_OUTPUT, stack);
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        // complete 标记由 materialize 按 position/total 判定，无需额外结算
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_MONOMER) {
            return DNTP.stream().anyMatch(id -> SequenceOperation.matchesId(stack, id));
        }
        return false; // 产物槽机器自治，玩家只取不放
    }
}
