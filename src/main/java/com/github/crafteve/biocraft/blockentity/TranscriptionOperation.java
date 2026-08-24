package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqOps;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 转录仪操作（重做：encoder 风格，状态栏模板 + 左 NTP/ATP + 右 mRNA/PPi + 启动子/终止子）
 * <p>
 * 槽位（8 槽，0 为状态栏模板槽固定 GUI）：
 * 0: 模板 dna_single（单链，3'→5'，IS_TEMPLATE=false 的模板链，helicase 产）
 * 1-4: atp/utp/ctp/gtp（左 4 NTP，逐碱基消耗）
 * 5: ATP 能量（左第5，暂与 atp 同物品，分槽以复用 encoder 能量回压）
 * 6: mRNA 产物（右宽卡，5'→3'，T→U，完成前 complete=false 锁）
 * 7: PPi 副产物（右窄卡，每碱基 +1）
 * <p>
 * 启动子/终止子在模板链 3'→5' 上：PROMOTER_TEMPLATE="ATATTA" / TERMINATOR_TEMPLATE="AAAAA"，
 * 未找到启动子则 init 失败并由 GUI 红叹号提示（同 dnaEncoder），找到则从启动子后 6 开始抄，
 * 碰终止子即停，否则到模板末尾；mRNA = complement(模板 3'→5' 模板段) → T→U，与编码链一致
 */
public class TranscriptionOperation implements SequenceOperation {

    public static final int SLOT_TEMPLATE = 0;
    public static final int SLOT_ATP = 1;
    public static final int SLOT_UTP = 2;
    public static final int SLOT_CTP = 3;
    public static final int SLOT_GTP = 4;
    public static final int SLOT_OUT_MRNA = 5;
    public static final int SLOT_OUT_ADP = 6;
    public static final int SLOT_OUT_PPI = 7;

    private String lastError = "";

    public String lastError() {
        return lastError;
    }

    @Override
    public int outputSlot() {
        return SLOT_OUT_MRNA;
    }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) {
            return true;
        }
        if (state.stage() == SeqStepState.Stage.DONE) {
            return false;
        }
        ItemStack tmpl = container.getItem(SLOT_TEMPLATE);
        SequenceData data = tmpl.get(ModDataComponents.SEQUENCE.get());
        Boolean isTemplate = tmpl.get(ModDataComponents.IS_TEMPLATE.get());
        if (tmpl.isEmpty() || data == null || !data.complete() || data.type() != SequenceData.SeqType.DNA
                || data.strand() != SequenceData.Strand.SS || !SeqOps.isValidDna(data.seq())
                || isTemplate == null) {
            return false;
        }
        if (isTemplate) {
            lastError = "编码链不可转录，请放入模板链(3'→5')";
            return false;
        }
        if (!container.getItem(SLOT_OUT_MRNA).isEmpty() || !hasRoom(container, SLOT_OUT_ADP) || !hasRoom(container, SLOT_OUT_PPI)) {
            return false;
        }
        if (!data.seq().contains(SeqOps.PROMOTER_TEMPLATE)) {
            lastError = "未找到启动子 " + SeqOps.PROMOTER_TEMPLATE + "（模板链 3'→5'，旧链请用新编码器重制）";
            return false;
        }
        lastError = "";
        return true;
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack tmpl = container.getItem(SLOT_TEMPLATE);
        SequenceData data = tmpl.get(ModDataComponents.SEQUENCE.get());
        Boolean isTemplate = tmpl.get(ModDataComponents.IS_TEMPLATE.get());
        if (isTemplate != null && isTemplate) {
            lastError = "编码链不可转录，请放入模板链(3'→5')";
            return false;
        }
        if (data == null || !SeqOps.isValidDna(data.seq())) {
            return false;
        }
        String template = data.seq();
        int start = template.indexOf(SeqOps.PROMOTER_TEMPLATE);
        if (start < 0) {
            lastError = "未找到启动子 " + SeqOps.PROMOTER_TEMPLATE + "（模板链 3'→5'，旧链请用新编码器重制）";
            return false;
        }
        int from = start + SeqOps.PROMOTER_TEMPLATE.length();
        // 终止子帧对齐搜索：AAAAA 只认编码框边界（距启动子末端为 3 的倍数）的命中——
        // 编码流末位恰为 T 时，模板上其互补 A 与终止子 AAAAA 连成提前 1 位的 A 连串，
        // 无帧约束会把 mRNA 截短 1 碱基，Ctrl 解码报"内容长度不足"（探针实测复现）
        int term = -1;
        int search = from;
        while ((search = template.indexOf(SeqOps.TERMINATOR_TEMPLATE, search)) >= 0) {
            if ((search - from) % 3 == 0) {
                term = search;
                break;
            }
            search++;
        }
        int to = term >= 0 ? term : template.length();
        String templateSegment = template.substring(from, to); // 3'→5' 模板段
        // mRNA 5'→3' = complement(模板 3'→5') → T→U
        String mrna = SeqOps.toMrna(SeqOps.complementDna(templateSegment));
        if (mrna.isEmpty()) {
            lastError = "启动子后无可转录序列";
            return false;
        }
        state.beginExtending(mrna);
        lastError = "";
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
        int ntpSlot = switch (base) {
            case 'A' -> SLOT_ATP;
            case 'U' -> SLOT_UTP;
            case 'C' -> SLOT_CTP;
            case 'G' -> SLOT_GTP;
            default -> SLOT_ATP;
        };
        // 仿 dnaEncoder：每碱基 0.1 NTP + 0.1 ATP，满 1.0 才真正消耗/产出（10 碱基 = 1 组）
        double inc = 0.1;
        boolean isA = base == 'A';
        double ntpRem, atpRem, adpRem, ppiRem;
        if (isA) {
            ntpRem = state.remainder(SLOT_ATP) + 0.2;
            atpRem = ntpRem;
        } else {
            ntpRem = state.remainder(ntpSlot) + inc;
            atpRem = state.remainder(SLOT_ATP) + inc;
        }
        adpRem = state.remainder(SLOT_OUT_ADP) + inc;
        ppiRem = state.remainder(SLOT_OUT_PPI) + inc;
        boolean needNtp = ntpRem >= 1.0 - 1e-9;
        boolean needAtp = isA ? needNtp : atpRem >= 1.0 - 1e-9;
        boolean needAdp = adpRem >= 1.0 - 1e-9;
        boolean needPpi = ppiRem >= 1.0 - 1e-9;
        // 前置全查：所有消耗/产出条件在动物品前一次性判定——槽位内容已由
        // isItemValidForSlot 过滤保证 id 正确，hasRoom/hasAny 即充分条件，
        // 任一不满足直接停摆，绝不出现"动了半步要回滚"（旧实现边动边退、
        // 退账余量有的路径写回原值有的清零，账目不平白送/欠账分子，已废弃）
        if (needNtp && !hasAny(container, ntpSlot)) return StepResult.STALLED;
        if (!isA && needAtp && !hasAny(container, SLOT_ATP)) return StepResult.STALLED;
        if (needAdp && !hasRoom(container, SLOT_OUT_ADP)) return StepResult.STALLED;
        if (needPpi && !hasRoom(container, SLOT_OUT_PPI)) return StepResult.STALLED;
        // 前置全查通过，顺序结算（余量满额归零为既定口径）
        if (needNtp) {
            SequenceContainerUtil.consumeOne(container, ntpSlot, ntp);
            state.setRemainder(isA ? SLOT_ATP : ntpSlot, 0.0);
        } else {
            state.setRemainder(isA ? SLOT_ATP : ntpSlot, ntpRem);
        }
        if (!isA) {
            if (needAtp) {
                SequenceContainerUtil.consumeOne(container, SLOT_ATP, "atp");
                state.setRemainder(SLOT_ATP, 0.0);
            } else {
                state.setRemainder(SLOT_ATP, atpRem);
            }
        }
        if (needAdp) {
            SequenceContainerUtil.addOne(container, SLOT_OUT_ADP, "adp");
            state.setRemainder(SLOT_OUT_ADP, 0.0);
        } else {
            state.setRemainder(SLOT_OUT_ADP, adpRem);
        }
        if (needPpi) {
            SequenceContainerUtil.addOne(container, SLOT_OUT_PPI, "ppi");
            state.setRemainder(SLOT_OUT_PPI, 0.0);
        } else {
            state.setRemainder(SLOT_OUT_PPI, ppiRem);
        }
        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    private static boolean hasAny(SimpleContainer c, int slot) {
        return !c.getItem(slot).isEmpty();
    }

    private static boolean hasRoom(SimpleContainer c, int slot) {
        ItemStack s = c.getItem(slot);
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.IDLE) {
            return;
        }
        String seq = state.chain().substring(0, Math.min(state.position(), state.chain().length()));
        boolean complete = state.position() >= state.total();
        // kind 继承模板
        ItemStack tmpl = container.getItem(SLOT_TEMPLATE);
        SequenceData tmplData = tmpl.get(ModDataComponents.SEQUENCE.get());
        SequenceData.Kind kind = tmplData != null ? tmplData.kind() : SequenceData.Kind.GENE;
        ItemStack out = new ItemStack(ModItems.MRNA.get());
        out.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.MRNA, null, kind, seq, complete));
        container.setItem(SLOT_OUT_MRNA, out);
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        // 模板 KEEP，不消耗；PPi 已在 step 中产出
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_TEMPLATE -> {
                SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
                Boolean isTemplate = stack.get(ModDataComponents.IS_TEMPLATE.get());
                yield data != null && data.complete() && data.type() == SequenceData.SeqType.DNA
                        && data.strand() == SequenceData.Strand.SS && SeqOps.isValidDna(data.seq())
                        && isTemplate != null;
            }
            case SLOT_ATP -> SequenceContainerUtil.matchesId(stack, "atp");
            case SLOT_UTP -> SequenceContainerUtil.matchesId(stack, "utp");
            case SLOT_CTP -> SequenceContainerUtil.matchesId(stack, "ctp");
            case SLOT_GTP -> SequenceContainerUtil.matchesId(stack, "gtp");
            case SLOT_OUT_MRNA, SLOT_OUT_ADP, SLOT_OUT_PPI -> false;
            default -> false;
        };
    }
}
