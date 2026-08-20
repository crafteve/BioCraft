package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceData;
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
        int next = state.position() + 1;
        // 组尾判定：编码后位置为 10 的倍数，或到达链尾（最后一组不足 10
        // 也按整组消耗）——组尾才消耗 1 dNTP + 1 ATP 并产 1 ADP + 1 PPi
        if (next % BASE_PER_GROUP == 0 || next == state.total()) {
            String dnTP = switch (base) {
                case 'A' -> "datp";
                case 'T' -> "dttp";
                case 'C' -> "dctp";
                case 'G' -> "dgtp";
                default -> throw new IllegalStateException("非法碱基: " + base);
            };
            // 先查副产物槽余量（产物回压：槽满不吞输入，玩家抽走即续）
            if (!hasRoom(container, SLOT_OUT_ADP) || !hasRoom(container, SLOT_OUT_PPI)) {
                return StepResult.STALLED;
            }
            // 再查输入（缺任一即停摆，不部分消耗）
            if (!hasAny(container, dnTpSlot(dnTP)) || !hasAny(container, SLOT_ATP)) {
                return StepResult.STALLED;
            }
            SequenceOperation.consumeOne(container, dnTpSlot(dnTP), dnTP);
            SequenceOperation.consumeOne(container, SLOT_ATP, "atp");
            SequenceOperation.addOne(container, SLOT_OUT_ADP, "adp");
            SequenceOperation.addOne(container, SLOT_OUT_PPI, "ppi");
        }
        state.setPosition(next);
        return next >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    /** dNTP 物品名 → 槽位下标 */
    private static int dnTpSlot(String dnTP) {
        return switch (dnTP) {
            case "datp" -> SLOT_DATP;
            case "dttp" -> SLOT_DTTP;
            case "dctp" -> SLOT_DCTP;
            case "dgtp" -> SLOT_DGTP;
            default -> throw new IllegalStateException("未知 dNTP: " + dnTP);
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
            case SLOT_DATP -> SequenceOperation.matchesId(stack, "datp");
            case SLOT_DTTP -> SequenceOperation.matchesId(stack, "dttp");
            case SLOT_DCTP -> SequenceOperation.matchesId(stack, "dctp");
            case SLOT_DGTP -> SequenceOperation.matchesId(stack, "dgtp");
            case SLOT_ATP -> SequenceOperation.matchesId(stack, "atp");
            default -> false; // 产物/副产物槽机器自治，玩家只取不放
        };
    }
}
