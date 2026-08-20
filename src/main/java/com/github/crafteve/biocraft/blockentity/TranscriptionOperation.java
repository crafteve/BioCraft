package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqOps;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 转录仪操作：DNA 模板（KEEP）→ mRNA（链源延伸，每步消耗 1 NTP）
 * <p>
 * 槽位：0 = RNA 聚合酶（催化剂），1 = 模板（完整 DNA），2 = NTP 单体池，3 = 产物
 * <p>
 * 模板保留不消耗；产物 mRNA 序列 = 模板互补链（T→U），kind 继承模板；
 * 缺 NTP（如缺 UTP）→ STALLED 停摆，补料即续
 */
public class TranscriptionOperation implements SequenceOperation {

    public static final int SLOT_CATALYST = 0;
    public static final int SLOT_TEMPLATE = 1;
    public static final int SLOT_MONOMER = 2;
    public static final int SLOT_OUTPUT = 3;

    @Override
    public String catalystItemId() {
        return "rna_polymerase";
    }

    @Override
    public int outputSlot() {
        return SLOT_OUTPUT;
    }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) {
            return true;
        }
        if (state.stage() == SeqStepState.Stage.DONE) {
            return false;
        }
        // IDLE：催化剂在位 + 模板为完整 DNA
        if (container.getItem(SLOT_CATALYST).isEmpty()
                || !SequenceContainerUtil.matchesId(container.getItem(SLOT_CATALYST), catalystItemId())) {
            return false;
        }
        SequenceData data = container.getItem(SLOT_TEMPLATE).get(ModDataComponents.SEQUENCE.get());
        return data != null && data.complete() && SeqOps.isValidDna(data.seq());
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        SequenceData data = container.getItem(SLOT_TEMPLATE).get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete() || !SeqOps.isValidDna(data.seq())) {
            return false;
        }
        state.beginExtending(SeqOps.toMrna(SeqOps.complementDna(data.seq())));
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) {
            return StepResult.DONE;
        }
        char base = state.chain().charAt(state.position());
        String ntp = switch (base) {
            case 'A' -> "atp";
            case 'U' -> "utp";
            case 'C' -> "ctp";
            case 'G' -> "gtp";
            default -> throw new IllegalStateException("非法碱基: " + base);
        };
        if (!SequenceContainerUtil.consumeOne(container, SLOT_MONOMER, ntp)) {
            return StepResult.STALLED;
        }
        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        SequenceData template = container.getItem(SLOT_TEMPLATE).get(ModDataComponents.SEQUENCE.get());
        SequenceData.Kind kind = template != null ? template.kind() : SequenceData.Kind.GENE;
        ItemStack stack = new ItemStack(ModItems.MRNA.get());
        String seq = state.chain().substring(0, Math.min(state.position(), state.chain().length()));
        boolean complete = state.position() >= state.total();
        stack.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.MRNA, null, kind, seq, complete));
        container.setItem(SLOT_OUTPUT, stack);
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        // 无额外结算：complete 标记由 materialize 判定
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_CATALYST -> SequenceContainerUtil.matchesId(stack, "rna_polymerase");
            case SLOT_TEMPLATE -> {
                SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
                yield data != null && data.complete() && SeqOps.isValidDna(data.seq());
            }
            case SLOT_MONOMER -> SequenceContainerUtil.matchesId(stack, "atp") || SequenceContainerUtil.matchesId(stack, "utp")
                    || SequenceContainerUtil.matchesId(stack, "ctp") || SequenceContainerUtil.matchesId(stack, "gtp");
            default -> false; // 产物槽机器自治
        };
    }
}
