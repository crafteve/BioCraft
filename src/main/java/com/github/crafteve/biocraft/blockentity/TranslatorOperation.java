package com.github.crafteve.biocraft.blockentity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 翻译机操作占位（步2空壳，步3填实）
 * <p>
 * 槽位：0 mRNA 模板、1 GTP 置顶、2..21 20种 aa-tRNA 专槽、22 多肽 23 tRNA 24 GDP 25 Pi
 * </p>
 */
public class TranslatorOperation implements SequenceOperation {

    public static final int SLOT_MRNA = 0;
    public static final int SLOT_GTP = 1;
    public static final int SLOT_AATRNA_START = 2;
    public static final int SLOT_AATRNA_END = 21;
    public static final int SLOT_OUT_POLYPEPTIDE = 22;
    public static final int SLOT_OUT_TRNA = 23;
    public static final int SLOT_OUT_GDP = 24;
    public static final int SLOT_OUT_PI = 25;

    @Override
    public int outputSlot() { return SLOT_OUT_POLYPEPTIDE; }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) { return false; }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) { return false; }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) { return StepResult.STALLED; }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {}

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        // 步2宽松放行，步3细化
        return true;
    }
}
