package com.github.crafteve.biocraft.blockentity.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceOperation;
import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceContainerUtil;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.init.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 翻译机操作：mRNA + 20种 aa-tRNA + GTP → 多肽 + 空载 tRNA + GDP + Pi
 * <p>
 * 槽位 26：0 mRNA 模板（9,8）、1 GTP 置顶、2..21 20种 aa-tRNA 专槽、22 多肽 23 tRNA 24 GDP 25 Pi
 * </p>
 * <p>
 * 计量（小数余量 0.1 口径，转录仪同款）：每肽键 0.1 aa-tRNA（对应种）+0.2 GTP → 0.1 tRNA +0.2 GDP +0.2 Pi，
 * 起始额外 2.5 GTP（0.25 首步，取 2~3 均值）+ 终止额外 1 GTP（0.1 尾步），起始(2.5)+延伸(2n)+终止(1) 模型
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

    /**
     * 翻译节奏：每密码子 3 tick（核糖体逐碱基读移的意象，1 tick 1 碱基），
     * 由 BE 步进冷却读取；GUI 的逐碱基揭示动画按同值驱动（两端共享常量）
     */
    public static final int TICKS_PER_CODON = 3;

    // 20 aa-tRNA 专槽顺序（与 CANONICAL_AA3 顺序对齐，GTP 置顶后 20 槽）——对外暴露供 GUI 卡片构建
    public static final String[] TRNA_IDS = {
            "trna_ala", "trna_arg", "trna_asn", "trna_asp", "trna_cys",
            "trna_gln", "trna_glu", "trna_gly", "trna_his", "trna_ile",
            "trna_leu", "trna_lys", "trna_met", "trna_phe", "trna_pro",
            "trna_ser", "trna_thr", "trna_trp", "trna_tyr", "trna_val"
    };

    // 1字母 → trna id（用于 codon 映射）
    private static final Map<Character, String> AA1_TO_TRNA = new HashMap<>();
    // trna id → slot
    private static final Map<String, Integer> TRNA_TO_SLOT = new HashMap<>();
    // trna id → aa 三字母（用于多肽卡着色回查，保持与 loader 同色）
    private static final Map<String, String> TRNA_TO_AA3 = Map.ofEntries(
            Map.entry("trna_ala", "Ala"), Map.entry("trna_arg", "Arg"), Map.entry("trna_asn", "Asn"),
            Map.entry("trna_asp", "Asp"), Map.entry("trna_cys", "Cys"), Map.entry("trna_gln", "Gln"),
            Map.entry("trna_glu", "Glu"), Map.entry("trna_gly", "Gly"), Map.entry("trna_his", "His"),
            Map.entry("trna_ile", "Ile"), Map.entry("trna_leu", "Leu"), Map.entry("trna_lys", "Lys"),
            Map.entry("trna_met", "Met"), Map.entry("trna_phe", "Phe"), Map.entry("trna_pro", "Pro"),
            Map.entry("trna_ser", "Ser"), Map.entry("trna_thr", "Thr"), Map.entry("trna_trp", "Trp"),
            Map.entry("trna_tyr", "Tyr"), Map.entry("trna_val", "Val")
    );

    static {
        for (int i = 0; i < TRNA_IDS.length; i++) {
            TRNA_TO_SLOT.put(TRNA_IDS[i], SLOT_AATRNA_START + i);
        }
        // CANONICAL_AA1 顺序与 TRNA_IDS 顺序一致，直接映射
        for (int i = 0; i < Codec.CANONICAL_AA1.length; i++) {
            AA1_TO_TRNA.put(Codec.CANONICAL_AA1[i], TRNA_IDS[i]);
        }
    }

    @Override
    public int outputSlot() { return SLOT_OUT_POLYPEPTIDE; }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) return true;
        if (state.stage() == SeqStepState.Stage.DONE) return false;
        ItemStack mrna = container.getItem(SLOT_MRNA);
        SequenceData data = mrna.get(ModDataComponents.SEQUENCE.get());
        if (mrna.isEmpty() || data == null || !data.complete() || data.type() != SequenceData.SeqType.MRNA) return false;
        String seq = data.seq();
        if (seq == null || seq.isEmpty() || !seq.matches("[AUCG]+")) return false;
        int start = seq.indexOf("AUG");
        if (start < 0) return false;
        // 输出槽需有空间（多肽为空或未完成不允许再起，tRNA/GDP/Pi 需 hasRoom）
        if (!container.getItem(SLOT_OUT_POLYPEPTIDE).isEmpty()) return false;
        if (!hasRoom(container, SLOT_OUT_TRNA) || !hasRoom(container, SLOT_OUT_GDP) || !hasRoom(container, SLOT_OUT_PI)) return false;
        // 输入至少 GTP 有一点，真正缺料由 step 逐密码子 STALLED 细化
        return true;
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack mrna = container.getItem(SLOT_MRNA);
        SequenceData data = mrna.get(ModDataComponents.SEQUENCE.get());
        if (data == null) return false;
        String seq = data.seq();
        int start = seq.indexOf("AUG");
        if (start < 0) return false;
        int stop = -1;
        for (String s : Codec.STOP_CODONS) {
            int idx = seq.indexOf(s, start + 3);
            if (idx >= 0 && idx % 3 == start % 3) {
                if (stop < 0 || idx < stop) stop = idx;
            }
        }
        int end = stop >= 0 ? stop : seq.length();
        // 按阅读框对齐截断
        int codonCount = (end - start) / 3;
        if (codonCount <= 0) return false;
        StringBuilder codons = new StringBuilder(codonCount * 3);
        StringBuilder aa = new StringBuilder(codonCount);
        for (int i = 0; i < codonCount; i++) {
            String cod = seq.substring(start + i * 3, start + i * 3 + 3);
            if (Codec.isStop(cod)) break;
            codons.append(cod);
            aa.append(Codec.codonToAa(cod));
        }
        if (aa.length() == 0) return false;
        state.beginExtending(aa.toString());
        state.setTotal(aa.length());
        // 用 pendingProgram 存密码子串（供 step 查 trna）
        state.setPendingProgram(codons.toString());
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) return StepResult.DONE;
        String aaChain = state.chain();
        String codons = state.pendingProgram();
        if (codons == null || codons.length() < (state.position() + 1) * 3) return StepResult.STALLED;
        String codon = codons.substring(state.position() * 3, state.position() * 3 + 3);
        char aa1 = aaChain.charAt(state.position());
        String trnaId = AA1_TO_TRNA.get(aa1);
        if (trnaId == null) return StepResult.STALLED;
        Integer slot = TRNA_TO_SLOT.get(trnaId);
        if (slot == null) return StepResult.STALLED;

        // 余量增量：每肽键 0.1 aa-tRNA 0.2 GTP → 0.1 tRNA 0.2 GDP 0.2 Pi
        double aaInc = 0.1;
        double gtpInc = 0.2;
        double trnaInc = 0.1;
        double gdpInc = 0.2;
        double piInc = 0.2;
        // 起止额外：首步 +0.05（2.5 的 0.5 额外），末步 +0.1
        if (state.position() == 0) gtpInc += 0.05;
        if (state.position() == state.total() - 1) gtpInc += 0.1;

        double aaRem = state.remainder(slot) + aaInc;
        double gtpRem = state.remainder(SLOT_GTP) + gtpInc;
        double trnaRem = state.remainder(SLOT_OUT_TRNA) + trnaInc;
        double gdpRem = state.remainder(SLOT_OUT_GDP) + gdpInc;
        double piRem = state.remainder(SLOT_OUT_PI) + piInc;

        boolean needAa = aaRem >= 1.0 - 1e-9;
        boolean needGtp = gtpRem >= 1.0 - 1e-9;
        boolean needTrna = trnaRem >= 1.0 - 1e-9;
        boolean needGdp = gdpRem >= 1.0 - 1e-9;
        boolean needPi = piRem >= 1.0 - 1e-9;

        // 前置全查：所有消耗/产出条件在动物品前一次性判定——
        // 槽位内容已由 isItemValidForSlot 过滤保证 id 正确，hasRoom/hasAny
        // 即充分条件，任一不满足直接停摆，绝不出现"动了半步要回滚"
        // （旧实现边动边退、退账余量写错白送/欠账分子，已废弃）
        if (needAa && !hasAny(container, slot)) return StepResult.STALLED;
        if (needGtp && !hasAny(container, SLOT_GTP)) return StepResult.STALLED;
        if (needTrna && !hasRoom(container, SLOT_OUT_TRNA)) return StepResult.STALLED;
        if (needGdp && !hasRoom(container, SLOT_OUT_GDP)) return StepResult.STALLED;
        if (needPi && !hasRoom(container, SLOT_OUT_PI)) return StepResult.STALLED;

        // 前置全查通过，顺序结算（消耗侧先于产出侧，余量满额归零为既定口径）
        if (needAa) {
            SequenceContainerUtil.consumeOne(container, slot, trnaId);
            state.setRemainder(slot, 0.0);
        } else state.setRemainder(slot, aaRem);
        if (needGtp) {
            SequenceContainerUtil.consumeOne(container, SLOT_GTP, "gtp");
            state.setRemainder(SLOT_GTP, 0.0);
        } else state.setRemainder(SLOT_GTP, gtpRem);

        if (needTrna) {
            SequenceContainerUtil.addOne(container, SLOT_OUT_TRNA, "trna");
            state.setRemainder(SLOT_OUT_TRNA, 0.0);
        } else state.setRemainder(SLOT_OUT_TRNA, trnaRem);
        if (needGdp) {
            SequenceContainerUtil.addOne(container, SLOT_OUT_GDP, "gdp");
            state.setRemainder(SLOT_OUT_GDP, 0.0);
        } else state.setRemainder(SLOT_OUT_GDP, gdpRem);
        if (needPi) {
            SequenceContainerUtil.addOne(container, SLOT_OUT_PI, "phosphate_ion");
            state.setRemainder(SLOT_OUT_PI, 0.0);
        } else state.setRemainder(SLOT_OUT_PI, piRem);

        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    private static boolean hasAny(SimpleContainer c, int slot) { return !c.getItem(slot).isEmpty(); }
    private static boolean hasRoom(SimpleContainer c, int slot) {
        ItemStack s = c.getItem(slot);
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.IDLE) return;
        String seq = state.chain().substring(0, Math.min(state.position(), state.chain().length()));
        boolean complete = state.position() >= state.total();
        // kind 继承 mRNA 模板（转录仪继承 DNA 模板同款惯例，折叠机按 kind 过滤不错位）
        ItemStack mrna = container.getItem(SLOT_MRNA);
        SequenceData mrnaData = mrna.get(ModDataComponents.SEQUENCE.get());
        SequenceData.Kind kind = mrnaData != null ? mrnaData.kind() : SequenceData.Kind.GENE;
        ItemStack out = new ItemStack(ModItems.POLYPEPTIDE.get());
        out.set(ModDataComponents.SEQUENCE.get(), new SequenceData(SequenceData.SeqType.POLYPEPTIDE, null, kind, seq, complete));
        container.setItem(SLOT_OUT_POLYPEPTIDE, out);
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_MRNA) {
            SequenceData d = stack.get(ModDataComponents.SEQUENCE.get());
            return d != null && d.complete() && d.type() == SequenceData.SeqType.MRNA;
        }
        if (slot == SLOT_GTP) return SequenceContainerUtil.matchesId(stack, "gtp");
        if (slot >= SLOT_AATRNA_START && slot <= SLOT_AATRNA_END) {
            String trna = TRNA_IDS[slot - SLOT_AATRNA_START];
            return SequenceContainerUtil.matchesId(stack, trna);
        }
        return false;
    }

    // 暴露供 GUI 卡片着色
    public static String trnaForSlot(int slot) {
        if (slot < SLOT_AATRNA_START || slot > SLOT_AATRNA_END) return null;
        return TRNA_IDS[slot - SLOT_AATRNA_START];
    }

    public static String aa3ForTrna(String trnaId) { return TRNA_TO_AA3.get(trnaId); }

    public static int slotForAa1(char aa1) {
        String t = AA1_TO_TRNA.get(aa1);
        if (t == null) return -1;
        Integer s = TRNA_TO_SLOT.get(t);
        return s == null ? -1 : s;
    }

    /** 每 tick 工作判定（输入齐+输出有空间+模板合法） */
    public static boolean isWorkable(SimpleContainer c) {
        return isWorkable(c.getItem(SLOT_MRNA), c.getItem(SLOT_GTP), c);
    }

    public static boolean isWorkable(ItemStack mrna, ItemStack gtp, SimpleContainer c) {
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        if (mrna.isEmpty() || d == null || !d.complete() || d.type() != SequenceData.SeqType.MRNA) return false;
        String seq = d.seq();
        int start = seq.indexOf("AUG");
        if (start < 0) return false;
        if (!hasRoom(c, SLOT_OUT_POLYPEPTIDE) && !c.getItem(SLOT_OUT_POLYPEPTIDE).isEmpty()) {
            // 多肽槽需空（或已空）才起
            return false;
        }
        if (!hasRoom(c, SLOT_OUT_TRNA) || !hasRoom(c, SLOT_OUT_GDP) || !hasRoom(c, SLOT_OUT_PI)) return false;
        // 至少一种 aa-tRNA 存在且 GTP 有余量判断在 step 细化，这里粗判 GTP 非空
        if (gtp.isEmpty()) return false;
        return true;
    }
}


