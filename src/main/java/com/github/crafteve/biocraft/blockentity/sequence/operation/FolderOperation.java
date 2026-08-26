package com.github.crafteve.biocraft.blockentity.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceOperation;
import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceContainerUtil;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.central.DslParser;
import com.github.crafteve.biocraft.data.EnzymeProgramChecker;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.SequenceData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 折叠机操作：多肽链 → 酶蛋白 / 错误折叠蛋白
 * <p>
 * 输入 1 槽（0 多肽，必须 complete），输出 1 槽（1 酶或错折）；
 * 即时完成（total=1，单 tick 消耗输入产出输出）；
 * 逻辑：多肽序列 → Codec 多肽解码 → 程序文本 → DslParser + EnzymeProgramChecker
 * 校验 → 成功输出对应酶物品，失败输出错折蛋白
 * </p>
 */
public class FolderOperation implements SequenceOperation {

    public static final int SLOT_IN_POLYPEPTIDE = 0;
    public static final int SLOT_OUT = 1;

    @Override
    public int outputSlot() {
        return SLOT_OUT;
    }

    @Override
    public boolean canStart(SimpleContainer container, SeqStepState state) {
        if (state.stage() == SeqStepState.Stage.EXTENDING) {
            return true;
        }
        if (state.stage() == SeqStepState.Stage.DONE) {
            return false;
        }
        ItemStack in = container.getItem(SLOT_IN_POLYPEPTIDE);
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (in.isEmpty() || data == null || !data.complete() || data.type() != SequenceData.SeqType.POLYPEPTIDE) {
            return false;
        }
        if (data.seq() == null || data.seq().isEmpty()) {
            return false;
        }
        // 输出槽需有空间
        if (!hasRoom(container, SLOT_OUT)) {
            return false;
        }
        // 若输出已有物品，允许同种叠加，异种则阻塞
        ItemStack out = container.getItem(SLOT_OUT);
        if (!out.isEmpty()) {
            // 预检：解码一次看预期输出是否与现有输出同种
            String expected = expectedOutputId(data.seq());
            if (expected == null) {
                return false;
            }
            if (!SequenceContainerUtil.matchesId(out, expected)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean init(SimpleContainer container, SeqStepState state) {
        ItemStack in = container.getItem(SLOT_IN_POLYPEPTIDE);
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete()) {
            return false;
        }
        String seq = data.seq();
        // 保存多肽链，pendingProgram 存预期输出 id
        String expected = expectedOutputId(seq);
        if (expected == null) {
            expected = "misfolded_protein";
        }
        state.beginExtending(seq);
        state.setTotal(1);
        state.setPendingProgram(expected);
        return true;
    }

    @Override
    public StepResult step(SimpleContainer container, SeqStepState state) {
        if (state.position() >= state.total()) {
            return StepResult.DONE;
        }
        ItemStack in = container.getItem(SLOT_IN_POLYPEPTIDE);
        if (in.isEmpty()) {
            return StepResult.STALLED;
        }
        SequenceData data = in.get(ModDataComponents.SEQUENCE.get());
        if (data == null || !data.complete()) {
            return StepResult.STALLED;
        }
        String expected = state.pendingProgram();
        if (expected == null || expected.isEmpty()) {
            expected = expectedOutputId(data.seq());
            if (expected == null) {
                expected = "misfolded_protein";
            }
        }
        if (!hasRoom(container, SLOT_OUT)) {
            return StepResult.STALLED;
        }
        ItemStack out = container.getItem(SLOT_OUT);
        if (!out.isEmpty() && !SequenceContainerUtil.matchesId(out, expected)) {
            return StepResult.STALLED;
        }
        // 原子消耗输入（多肽统一按 polypeptide 匹配，忽略 NBT）
        if (!SequenceContainerUtil.consumeOne(container, SLOT_IN_POLYPEPTIDE, "polypeptide")) {
            return StepResult.STALLED;
        }
        // 产出输出
        if (!SequenceContainerUtil.addOne(container, SLOT_OUT, expected)) {
            SequenceContainerUtil.addOne(container, SLOT_IN_POLYPEPTIDE, "polypeptide");
            return StepResult.STALLED;
        }
        state.setPosition(state.position() + 1);
        return state.position() >= state.total() ? StepResult.DONE : StepResult.ADVANCED;
    }

    @Override
    public void materialize(SimpleContainer container, SeqStepState state) {
        // 即时机，输出已在 step 中产出，无需物化
    }

    @Override
    public void finish(SimpleContainer container, SeqStepState state) {
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_IN_POLYPEPTIDE) {
            SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
            return data != null && data.complete() && data.type() == SequenceData.SeqType.POLYPEPTIDE;
        }
        // 输出槽不接受玩家放入
        return false;
    }

    /** 根据多肽序列推导预期输出 id（酶 id 或错折） */
    private static String expectedOutputId(String aaSeq) {
        Codec.DecodeResult decoded = Codec.decodeFromPolypeptide(aaSeq);
        if (!decoded.ok()) {
            return "misfolded_protein";
        }
        String programText = decoded.text();
        DslParser.ParseResult parsed = DslParser.parse(programText);
        java.util.List<DslParser.ProgramError> errors = EnzymeProgramChecker.check(parsed);
        if (!errors.isEmpty()) {
            return "misfolded_protein";
        }
        String id = parsed.program().value(com.github.crafteve.biocraft.central.DslField.ID);
        if (id == null) {
            return "misfolded_protein";
        }
        // 规范化为酶物品注册名（enzymes.json id 可能为缩写，需解析为正式 id）
        // EnzymeProgramChecker 已做缩写回退，取解析后的正式 id
        // 为简化，直接按查找结果取正式 id
        String formalId = findFormalEnzymeId(id);
        if (formalId == null) {
            return "misfolded_protein";
        }
        return "enzyme_" + formalId;
    }

    private static String findFormalEnzymeId(String id) {
        for (var data : com.github.crafteve.biocraft.init.EnzymeFactoryRegistry.ordered()) {
            if (data.id().equals(id)) {
                return data.id();
            }
            String abbr = data.abbreviation();
            if (abbr != null && abbr.equalsIgnoreCase(id)) {
                return data.id();
            }
        }
        return null;
    }

    private static boolean hasRoom(SimpleContainer c, int slot) {
        ItemStack s = c.getItem(slot);
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }
}
