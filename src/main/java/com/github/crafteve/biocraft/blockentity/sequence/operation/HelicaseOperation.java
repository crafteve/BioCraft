package com.github.crafteve.biocraft.blockentity.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceOperation;
import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.central.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 解旋酶操作：1 个双链 DNA → 2 个单链 DNA（逐碱基对动态解旋，每 tick 1 bp）
 * <p>
 * 输入槽 0：dsDNA（complete=true 的 dna 物品）；输出槽 1/2：ssDNA（dna_single）。
 * 产出序列：编码链 = 原序 S 前缀 S[0:pos]（5'→3'，与 dsDNA 完全一致），
 * 模板链 = complement(S[0:pos]) 严格按 3'→5' 方向追加互补碱基（NBT 倒着写，不反向），
 * 两产物 kind 继承输入，complete = pos==total。
 * 双产物因 NBT 序列不同需两张产物卡，解旋中逐碱基生长可见（编码器同款动态）。
 * <p>
 *  MVP 无催化剂、无 ATP 消耗，每 tick 解旋 1 碱基对（与编码器每 tick 1 碱基同节奏），
 *  输入 dsDNA 在解旋完成时消耗，输出 ssDNA 在未完成前为半成品（complete=false，
 *  取出被门控拦截），完成后可取出；下一轮需两输出槽皆空才开始。
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
        ItemStack in = container.getItem(SLOT_IN_DNA);
        if (in.isEmpty()) {
            return false;
        }
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete() || data.type() != SequenceData.SeqType.DNA
                || data.strand() != SequenceData.Strand.DS || !Codec.isValidDna(data.seq())) {
            return false;
        }
        return container.getItem(SLOT_OUT_A).isEmpty() && container.getItem(SLOT_OUT_B).isEmpty();
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack in = container.getItem(SLOT_IN_DNA);
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete() || !Codec.isValidDna(data.seq())) {
            return false;
        }
        state.beginExtending(data.seq());
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) {
            return StepResult.DONE;
        }
        ItemStack in = container.getItem(SLOT_IN_DNA);
        if (in.isEmpty()) {
            return StepResult.STALLED;
        }
        SequenceData inputData = in.get(ModDataComponents.SEQUENCE.get());
        if (inputData == null || !inputData.complete()) {
            return StepResult.STALLED;
        }
        int next = state.position() + 1;
        state.setPosition(next);
        return next >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.IDLE) {
            return;
        }
        String chain = state.chain();
        int pos = Math.min(state.position(), chain.length());
        if (pos <= 0) {
            return;
        }
        ItemStack in = container.getItem(SLOT_IN_DNA);
        SequenceData inputData = in.isEmpty() ? null : in.get(ModDataComponents.SEQUENCE.get());
        SequenceData.Kind kind = inputData != null ? inputData.kind() : SequenceData.Kind.GENE;
        boolean complete = pos >= state.total();
        String codingSeq = chain.substring(0, pos);
        // 模板链严格按 3'→5' 方向写：每 tick 读 S[pos-1] 追加互补碱基（complement，不反向）
        String templateSeq = Codec.complementDna(codingSeq);
        ItemStack outTemplate = new ItemStack(ModItems.DNA_SINGLE.get());
        outTemplate.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.SS, kind, templateSeq, complete));
        outTemplate.set(ModDataComponents.IS_TEMPLATE.get(), false);
        ItemStack outCoding = new ItemStack(ModItems.DNA_SINGLE.get());
        outCoding.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.SS, kind, codingSeq, complete));
        outCoding.set(ModDataComponents.IS_TEMPLATE.get(), true);
        container.setItem(SLOT_OUT_A, outTemplate);
        container.setItem(SLOT_OUT_B, outCoding);
        // feat1：转录同时同步剪除 input 5'→3'（剩余 dsDNA，随 pos 前推进）
        if (!complete) {
            String remain = chain.substring(pos);
            ItemStack inStack = container.getItem(SLOT_IN_DNA);
            if (!inStack.isEmpty()) {
                SequenceData inData = inStack.get(ModDataComponents.SEQUENCE.get());
                if (inData != null) {
                    inStack.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                            SequenceData.SeqType.DNA, SequenceData.Strand.DS, kind, remain, true));
                }
            }
        }
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        ItemStack in = container.getItem(SLOT_IN_DNA);
        if (!in.isEmpty()) {
            in.shrink(1);
            if (in.isEmpty()) {
                container.setItem(SLOT_IN_DNA, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_IN_DNA) {
            SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
            return data != null && data.complete() && data.type() == SequenceData.SeqType.DNA
                    && data.strand() == SequenceData.Strand.DS && Codec.isValidDna(data.seq());
        }
        return false;
    }
}


