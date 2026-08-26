package com.github.crafteve.biocraft.blockentity.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceOperation;
import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceContainerUtil;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 装载机操作（B 方案 20 种静态 aa-tRNA）：tRNA + 任意 AA + ATP → aa-tRNA（对应 AA）+ AMP + PPi
 * <p>输入 3 槽（左）：0 tRNA 通用、1 AA 任意 20、2 ATP；输出 3 槽（右）：3 aa-tRNA 对应种、4 AMP、5 PPi</p>
 */
public class LoaderOperation implements SequenceOperation {

    public static final int SLOT_TRNA = 0;
    public static final int SLOT_AA = 1;
    public static final int SLOT_ATP = 2;
    public static final int SLOT_OUT_AATRNA = 3;
    public static final int SLOT_OUT_AMP = 4;
    public static final int SLOT_OUT_PPI = 5;

    private static final Set<String> AA_IDS = Set.of(
            "glycine", "alanine", "valine", "leucine", "isoleucine", "proline", "phenylalanine", "tryptophan",
            "methionine", "serine", "threonine", "cysteine", "tyrosine", "asparagine", "glutamine",
            "aspartic_acid", "glutamic_acid", "lysine", "arginine", "histidine"
    );

    private static final Map<String, String> AA_TO_TRNA = Map.ofEntries(
            Map.entry("glycine", "trna_gly"), Map.entry("alanine", "trna_ala"), Map.entry("valine", "trna_val"),
            Map.entry("leucine", "trna_leu"), Map.entry("isoleucine", "trna_ile"), Map.entry("proline", "trna_pro"),
            Map.entry("phenylalanine", "trna_phe"), Map.entry("tryptophan", "trna_trp"), Map.entry("methionine", "trna_met"),
            Map.entry("serine", "trna_ser"), Map.entry("threonine", "trna_thr"), Map.entry("cysteine", "trna_cys"),
            Map.entry("tyrosine", "trna_tyr"), Map.entry("asparagine", "trna_asn"), Map.entry("glutamine", "trna_gln"),
            Map.entry("aspartic_acid", "trna_asp"), Map.entry("glutamic_acid", "trna_glu"), Map.entry("lysine", "trna_lys"),
            Map.entry("arginine", "trna_arg"), Map.entry("histidine", "trna_his")
    );

    private String lastAaId = "";

    public String lastAaId() { return lastAaId; }

    @Override
    public int outputSlot() { return SLOT_OUT_AATRNA; }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) return true;
        if (state.stage() == SeqStepState.Stage.DONE) return false;
        ItemStack trna = container.getItem(SLOT_TRNA);
        ItemStack aa = container.getItem(SLOT_AA);
        ItemStack atp = container.getItem(SLOT_ATP);
        if (trna.isEmpty() || aa.isEmpty() || atp.isEmpty()) return false;
        String aaId = findAaId(aa);
        if (aaId == null) return false;
        String outId = AA_TO_TRNA.get(aaId);
        if (outId == null) return false;
        ItemStack out = container.getItem(SLOT_OUT_AATRNA);
        if (!out.isEmpty() && !SequenceContainerUtil.matchesId(out, outId)) return false;
        if (!hasRoom(container, SLOT_OUT_AATRNA) || !hasRoom(container, SLOT_OUT_AMP) || !hasRoom(container, SLOT_OUT_PPI)) return false;
        if (!SequenceContainerUtil.matchesId(trna, "trna")) return false;
        if (!SequenceContainerUtil.matchesId(atp, "atp")) return false;
        return true;
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack aa = container.getItem(SLOT_AA);
        String aaId = findAaId(aa);
        if (aaId == null) return false;
        lastAaId = aaId;
        state.beginExtending(aaId);
        // 装载机 1:1 合成（1 tRNA + 1 aa + 1 ATP → 1 aa-tRNA + AMP + PPi），
        // 一次 step 即完成一轮装载；beginExtending 默认 total=chain.length()
        // 会把 aaId 字符串长度误当步数，须强制 total=1 保证 step 一次即 DONE
        state.setTotal(1);
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) return StepResult.DONE;
        String aaId = state.chain();
        String outId = AA_TO_TRNA.get(aaId);
        if (outId == null) return StepResult.STALLED;
        if (!hasAny(container, SLOT_TRNA) || !hasAny(container, SLOT_AA) || !hasAny(container, SLOT_ATP)) return StepResult.STALLED;
        if (!hasRoom(container, SLOT_OUT_AATRNA) || !hasRoom(container, SLOT_OUT_AMP) || !hasRoom(container, SLOT_OUT_PPI)) return StepResult.STALLED;
        ItemStack out = container.getItem(SLOT_OUT_AATRNA);
        if (!out.isEmpty() && !SequenceContainerUtil.matchesId(out, outId)) return StepResult.STALLED;
        if (!SequenceContainerUtil.consumeOne(container, SLOT_TRNA, "trna")) return StepResult.STALLED;
        if (!SequenceContainerUtil.consumeOne(container, SLOT_AA, aaId)) {
            SequenceContainerUtil.addOne(container, SLOT_TRNA, "trna");
            return StepResult.STALLED;
        }
        if (!SequenceContainerUtil.consumeOne(container, SLOT_ATP, "atp")) {
            SequenceContainerUtil.addOne(container, SLOT_TRNA, "trna");
            SequenceContainerUtil.addOne(container, SLOT_AA, aaId);
            return StepResult.STALLED;
        }
        if (!SequenceContainerUtil.addOne(container, SLOT_OUT_AATRNA, outId)) {
            SequenceContainerUtil.addOne(container, SLOT_TRNA, "trna");
            SequenceContainerUtil.addOne(container, SLOT_AA, aaId);
            SequenceContainerUtil.addOne(container, SLOT_ATP, "atp");
            return StepResult.STALLED;
        }
        if (!SequenceContainerUtil.addOne(container, SLOT_OUT_AMP, "amp")) {
            ItemStack s = container.getItem(SLOT_OUT_AATRNA); if (!s.isEmpty()) { s.shrink(1); if (s.isEmpty()) container.setItem(SLOT_OUT_AATRNA, ItemStack.EMPTY); }
            SequenceContainerUtil.addOne(container, SLOT_TRNA, "trna");
            SequenceContainerUtil.addOne(container, SLOT_AA, aaId);
            SequenceContainerUtil.addOne(container, SLOT_ATP, "atp");
            return StepResult.STALLED;
        }
        if (!SequenceContainerUtil.addOne(container, SLOT_OUT_PPI, "ppi")) {
            ItemStack s = container.getItem(SLOT_OUT_AATRNA); if (!s.isEmpty()) { s.shrink(1); if (s.isEmpty()) container.setItem(SLOT_OUT_AATRNA, ItemStack.EMPTY); }
            ItemStack s2 = container.getItem(SLOT_OUT_AMP); if (!s2.isEmpty()) { s2.shrink(1); if (s2.isEmpty()) container.setItem(SLOT_OUT_AMP, ItemStack.EMPTY); }
            SequenceContainerUtil.addOne(container, SLOT_TRNA, "trna");
            SequenceContainerUtil.addOne(container, SLOT_AA, aaId);
            SequenceContainerUtil.addOne(container, SLOT_ATP, "atp");
            return StepResult.STALLED;
        }
        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    private static boolean hasAny(SimpleContainer c, int slot) { return !c.getItem(slot).isEmpty(); }
    private static boolean hasRoom(SimpleContainer c, int slot) {
        ItemStack s = c.getItem(slot);
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }

    /**
     * 每 tick 工作状态检测（与 GUI checkWorkable 同口径）：
     * 输入三槽有货 + 类型正确 + AA 为 20 种之一 + 输出对应 aa-tRNA 空或同种 + 三输出槽均有空间
     * → 当前 tick 可工作（绿灯），否则停止（红灯）
     */
    public static boolean isWorkable(SimpleContainer container) {
        return isWorkable(
                container.getItem(SLOT_TRNA),
                container.getItem(SLOT_AA),
                container.getItem(SLOT_ATP),
                container.getItem(SLOT_OUT_AATRNA),
                container.getItem(SLOT_OUT_AMP),
                container.getItem(SLOT_OUT_PPI));
    }

    /** 工作状态检测重载：直接按 6 槽 ItemStack 判定（GUI menu 槽位复用，避免 SimpleContainer 包装） */
    public static boolean isWorkable(ItemStack trna, ItemStack aa, ItemStack atp,
                                     ItemStack outAatrna, ItemStack outAmp, ItemStack outPpi) {
        if (trna.isEmpty() || aa.isEmpty() || atp.isEmpty()) return false;
        if (!SequenceContainerUtil.matchesId(trna, "trna")) return false;
        if (!SequenceContainerUtil.matchesId(atp, "atp")) return false;
        String aaId = findAaId(aa);
        if (aaId == null) return false;
        String outId = AA_TO_TRNA.get(aaId);
        if (outId == null) return false;
        if (!outAatrna.isEmpty() && !SequenceContainerUtil.matchesId(outAatrna, outId)) return false;
        return hasRoom(outAatrna) && hasRoom(outAmp) && hasRoom(outPpi);
    }

    private static boolean hasRoom(ItemStack stack) {
        return stack.isEmpty() || stack.getCount() < stack.getMaxStackSize();
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        // aa-tRNA 输出已在 step 中直接产出，此处无需物化（保持空实现避免覆盖）
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_TRNA -> SequenceContainerUtil.matchesId(stack, "trna");
            case SLOT_AA -> findAaId(stack) != null;
            case SLOT_ATP -> SequenceContainerUtil.matchesId(stack, "atp");
            case SLOT_OUT_AATRNA, SLOT_OUT_AMP, SLOT_OUT_PPI -> false;
            default -> false;
        };
    }

    private static String findAaId(ItemStack stack) {
        for (String aa : AA_IDS) {
            if (SequenceContainerUtil.matchesId(stack, aa)) return aa;
        }
        return null;
    }

    /** 判断物品是否为 20 种氨基酸之一（GUI 工作状态检测用） */
    public static boolean isAa(ItemStack stack) {
        return findAaId(stack) != null;
    }

    /** 返回物品对应的氨基酸 id（非氨基酸返回 null；GUI 工作状态检测用） */
    public static String aaIdOf(ItemStack stack) {
        return findAaId(stack);
    }

    public static String trnaIdForAa(String aaId) {
        return AA_TO_TRNA.get(aaId);
    }
}


