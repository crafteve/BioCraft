package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqOps;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 解旋酶操作：1 个双链 DNA → 2 个单链 DNA（原子 TRANSFORM，无链延伸）
 * <p>
 * 输入槽 0：dsDNA（complete=true 的 dna 物品）；输出槽 1/2：ssDNA（dna_single）。
 * 产出序列：slot1 = 原序 S（5'→3' 正链），slot2 = 反向互补链 reverseComplement(S)，
 * 两产物 kind 继承输入，complete=true。双产物因 NBT 序列不同需两张产物卡。
 * <p>
 *  MVP 无催化剂、无 ATP 消耗，原子一步完成（init 置链 → step 消费+产出 → DONE）。
 *  下一轮需两输出槽皆空才开始，避免覆盖未取走的单链。
 */
public class HelicaseOperation implements SequenceOperation {

    public static final int SLOT_IN_DNA = 0;
    public static final int SLOT_OUT_A = 1;
    public static final int SLOT_OUT_B = 2;

    @Override
    public int outputSlot() {
        return SLOT_OUT_A;
    }

    /** 次输出槽（双产物第二槽，BE 的 DONE 判定与槽位校验共用） */
    public static int outputSlotB() {
        return SLOT_OUT_B;
    }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) {
            return true;
        }
        if (state.stage() == SeqStepState.Stage.DONE) {
            return false;
        }
        // IDLE：输入为完整 dsDNA 且两输出槽为空
        ItemStack in = container.getItem(SLOT_IN_DNA);
        if (in.isEmpty()) {
            return false;
        }
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete() || data.type() != SequenceData.SeqType.DNA
                || data.strand() != SequenceData.Strand.DS || !SeqOps.isValidDna(data.seq())) {
            return false;
        }
        return container.getItem(SLOT_OUT_A).isEmpty() && container.getItem(SLOT_OUT_B).isEmpty();
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack in = container.getItem(SLOT_IN_DNA);
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete() || !SeqOps.isValidDna(data.seq())) {
            return false;
        }
        // 借链源模型存输入序列，仅为进度条与物化复用；原子操作 total=seq 长度，step 一步完成
        state.beginExtending(data.seq());
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.chain().isEmpty()) {
            return StepResult.DONE;
        }
        // 双输出槽必须有空位，否则停摆（补空即续，链源保留）
        if (!container.getItem(SLOT_OUT_A).isEmpty() || !container.getItem(SLOT_OUT_B).isEmpty()) {
            return StepResult.STALLED;
        }
        ItemStack in = container.getItem(SLOT_IN_DNA);
        SequenceData inputData = in.get(ModDataComponents.SEQUENCE.get());
        if (inputData == null || !SeqOps.isValidDna(state.chain())) {
            return StepResult.STALLED;
        }
        String seqA = state.chain();
        String seqB = SeqOps.reverseComplement(seqA);
        SequenceData.Kind kind = inputData.kind();

        // 消耗输入 1 个 dsDNA
        in.shrink(1);
        if (in.isEmpty()) {
            container.setItem(SLOT_IN_DNA, ItemStack.EMPTY);
        }

        // 产出两条 ssDNA（NBT 不同，需两槽）
        ItemStack outA = new ItemStack(ModItems.DNA_SINGLE.get());
        outA.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.SS, kind, seqA, true));
        ItemStack outB = new ItemStack(ModItems.DNA_SINGLE.get());
        outB.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.SS, kind, seqB, true));
        container.setItem(SLOT_OUT_A, outA);
        container.setItem(SLOT_OUT_B, outB);

        state.setPosition(state.total());
        return StepResult.DONE;
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        // 原子操作不做逐碱基物化：仅在 EXTENDING/DONE 时保证输出槽与 step 结果一致
        // 输入已被 step 消耗，此处无需额外刷新；空实现避免覆盖 step 已写入的双产物
        if (state.stage() == SeqStepState.Stage.IDLE) {
            // IDLE 时若输出槽有残留（换模板清空遗留），由 BE 的换模板逻辑处理
            return;
        }
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        // 无额外结算
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_IN_DNA) {
            SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
            return data != null && data.complete() && data.type() == SequenceData.SeqType.DNA
                    && data.strand() == SequenceData.Strand.DS && SeqOps.isValidDna(data.seq());
        }
        return false; // 输出槽只出不进
    }
}
