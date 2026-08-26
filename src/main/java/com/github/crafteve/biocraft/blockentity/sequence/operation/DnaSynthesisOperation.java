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

import java.util.Set;

/**
 * DNA 编码器操作：程序文本 → 程序 DNA（链源延伸）
 * <p>
 * 化学计量（每 10 个碱基一组）：1 dNTP + 1 ATP → 10 碱基链 + 1 ADP + 1 PPi。
 * 每组在组尾（第 10/20/… 个碱基，最后一组不足 10 也按整组计）消耗 1 个
 * 对应 dNTP + 1 个 ATP；ATP 为编码供能（磷酸基团转移），ADP/PPi 为必须
 * 回收的副产物——副产物槽满（64 堆叠 = 640 碱基）即停摆回压，玩家抽走即续
 * （模组招牌物流玩法）。
 * <p>
 * 槽位布局（8 槽）：
 * <ul>
 *   <li>0~3：dATP/dTTP/dCTP/dGTP 单体槽</li>
 *   <li>4：ATP 供能槽</li>
 *   <li>5：DNA 产物槽（物化链前缀）</li>
 *   <li>6~7：ADP / PPi 副产物槽（每组各 +1）</li>
 * </ul>
 */
public class DnaSynthesisOperation implements SequenceOperation {

    public static final int SLOT_DATP = 0;
    public static final int SLOT_DTTP = 1;
    public static final int SLOT_DCTP = 2;
    public static final int SLOT_DGTP = 3;
    public static final int SLOT_ATP = 4;
    public static final int SLOT_OUT_DNA = 5;
    public static final int SLOT_OUT_ADP = 6;
    public static final int SLOT_OUT_PPI = 7;

    /** 每组碱基数：1 dNTP + 1 ATP 编码的碱基个数（组尾消耗） */
    public static final int BASE_PER_GROUP = 10;

    private static final Set<String> DNTP = Set.of("datp", "dttp", "dctp", "dgtp");

    @Override
    public int outputSlot() {
        return SLOT_OUT_DNA;
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
            String encoded = Codec.encodeText(program);
            // 链结构：启动子 TATAAT + 起始密码子 ATG + 真正程序 + 终止子 TTTTT——
            // 起始密码子保证 mRNA 以 AUG 开头（翻译机 AUG 扫描 0 位命中，
            // 整链翻译；缺它则首现 AUG 位置随字节运气漂移，多肽缺魔数头，
            // Ctrl 反推必失败，实测 AUG@17/@45 两例）
            // 模板链 3' ATATTA...AAAAA 5' 供转录识别
            String withPromoter = com.github.crafteve.biocraft.central.Codec.PROMOTER_CODING
                    + com.github.crafteve.biocraft.central.Codec.START_CODON_CODING + encoded
                    + com.github.crafteve.biocraft.central.Codec.TERMINATOR_CODING;
            if (withPromoter.length() > com.github.crafteve.biocraft.central.Codec.MAX_DNA_BP) {
                throw new IllegalArgumentException("编码后含启动子/终止子超出长度上限");
            }
            state.beginExtending(withPromoter);
            state.setPendingProgram("");
            return true;
        } catch (IllegalArgumentException e) {
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
        int slot = dnTpSlotFor(base);
        String dnTP = dnTPName(base);
        // 分子余量（酶工厂同款模式）：1 分子 = 10 碱基，每碱基余量 +0.1，
        // 满 1.0 才真正消耗/产出——槽位物品是整数，小数余量存 SeqStepState
        double inc = 1.0 / BASE_PER_GROUP;
        double dRem = state.remainder(slot) + inc;
        double aRem = state.remainder(SLOT_ATP) + inc;
        double adpRem = state.remainder(SLOT_OUT_ADP) + inc;
        double ppiRem = state.remainder(SLOT_OUT_PPI) + inc;
        boolean needDntp = dRem >= 1.0 - 1e-9;
        boolean needAtp = aRem >= 1.0 - 1e-9;
        boolean makeAdp = adpRem >= 1.0 - 1e-9;
        boolean makePpi = ppiRem >= 1.0 - 1e-9;
        // 先查后动：缺料/产物槽满 → STALLED（余量与位置均不推进，补料即续）
        if (needDntp && !hasAny(container, slot)) {
            return StepResult.STALLED;
        }
        if (needAtp && !hasAny(container, SLOT_ATP)) {
            return StepResult.STALLED;
        }
        if (makeAdp && !hasRoom(container, SLOT_OUT_ADP)) {
            return StepResult.STALLED;
        }
        if (makePpi && !hasRoom(container, SLOT_OUT_PPI)) {
            return StepResult.STALLED;
        }
        if (needDntp) {
            if (!SequenceContainerUtil.consumeOne(container, slot, dnTP)) {
                return StepResult.STALLED;
            }
            state.setRemainder(slot, 0.0);
        } else {
            state.setRemainder(slot, dRem);
        }
        if (needAtp) {
            if (!SequenceContainerUtil.consumeOne(container, SLOT_ATP, "atp")) {
                return StepResult.STALLED;
            }
            state.setRemainder(SLOT_ATP, 0.0);
        } else {
            state.setRemainder(SLOT_ATP, aRem);
        }
        if (makeAdp) {
            if (!SequenceContainerUtil.addOne(container, SLOT_OUT_ADP, "adp")) {
                return StepResult.STALLED;
            }
            state.setRemainder(SLOT_OUT_ADP, 0.0);
        } else {
            state.setRemainder(SLOT_OUT_ADP, adpRem);
        }
        if (makePpi) {
            if (!SequenceContainerUtil.addOne(container, SLOT_OUT_PPI, "ppi")) {
                return StepResult.STALLED;
            }
            state.setRemainder(SLOT_OUT_PPI, 0.0);
        } else {
            state.setRemainder(SLOT_OUT_PPI, ppiRem);
        }
        int next = state.position() + 1;
        state.setPosition(next);
        return next >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    /** 碱基 → 对应 dNTP 槽位下标 */
    private static int dnTpSlotFor(char base) {
        return switch (base) {
            case 'A' -> SLOT_DATP;
            case 'T' -> SLOT_DTTP;
            case 'C' -> SLOT_DCTP;
            case 'G' -> SLOT_DGTP;
            default -> throw new IllegalStateException("非法碱基: " + base);
        };
    }

    /** 碱基 → dNTP 物品注册名 */
    private static String dnTPName(char base) {
        return switch (base) {
            case 'A' -> "datp";
            case 'T' -> "dttp";
            case 'C' -> "dctp";
            case 'G' -> "dgtp";
            default -> throw new IllegalStateException("非法碱基: " + base);
        };
    }

    private static boolean hasAny(SimpleContainer container, int slot) {
        return !container.getItem(slot).isEmpty();
    }

    private static boolean hasRoom(SimpleContainer container, int slot) {
        ItemStack stack = container.getItem(slot);
        return stack.isEmpty() || stack.getCount() < stack.getMaxStackSize();
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        ItemStack stack = new ItemStack(ModItems.DNA.get());
        String seq = state.chain().substring(0, Math.min(state.position(), state.chain().length()));
        boolean complete = state.position() >= state.total();
        stack.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                SequenceData.SeqType.DNA, SequenceData.Strand.DS, SequenceData.Kind.PROGRAM, seq, complete));
        container.setItem(SLOT_OUT_DNA, stack);
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
        // complete 标记由 materialize 按 position/total 判定，无需额外结算
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_DATP -> SequenceContainerUtil.matchesId(stack, "datp");
            case SLOT_DTTP -> SequenceContainerUtil.matchesId(stack, "dttp");
            case SLOT_DCTP -> SequenceContainerUtil.matchesId(stack, "dctp");
            case SLOT_DGTP -> SequenceContainerUtil.matchesId(stack, "dgtp");
            case SLOT_ATP -> SequenceContainerUtil.matchesId(stack, "atp");
            default -> false; // 产物/副产物槽机器自治，玩家只取不放
        };
    }
}


