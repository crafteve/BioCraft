package com.github.crafteve.biocraft.gui.sequence;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.sequence.operation.DnaSynthesisOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.FolderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.HelicaseOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.LoaderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranscriptionOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation;

import java.util.List;

/**
 * 序列机槽位规格表（单一事实源）：每台序列机一份"槽位描述"列表，
 * 三处消费方（容器 size / 菜单槽位表 / 屏幕卡片列表）全部由此派生，而非各自手写
 * <p>
 * 背景：翻译机 26 = 1(mRNA 固定槽) + 21(输入滚动卡) + 4(输出卡)，这一联动此前在
 * SequenceMachineKind 的容器 size、Menu.slotPositions 数组、Screen.buildInputCards/
 * buildOutputCards 三处各写一遍，靠人眼对齐。任一处漏改数字即与其余错位，
 * 表现为"背包首格被误当成 PPi 输出槽"（槽位表长度 24/25 ≠ 容器 26）。
 * <p>
 * 本类把"每机各槽的角色、默认展示物品 id、卡宽、内容样式、固定槽坐标"收敛为一份表：
 * <ul>
 *   <li>列表下标 = 容器槽位下标（0 起，无洞）；{@link Machine#size()} 即容器 size</li>
 *   <li>FIXED：顶栏/固定坐标槽（mRNA 模板 9,8、转录 DNA 模板 9,8），不参与滚动</li>
 *   <li>INPUT_SCROLL：输入纵向滚动卡（坐标由 Screen 每帧覆写，此处为占位）</li>
 *   <li>OUTPUT_CARD：输出卡（含卡宽/内容样式，坐标由 Screen 每帧覆写，此处为占位）</li>
 * </ul>
 * 新增/改槽一律改本表，Menu 与 Screen 零改动；注册期断言校验与操作层槽常量一致。
 */
public final class SequenceSlotSpec {

    /** 槽位角色（非数值的"性质"，用枚举承载） */
    public enum Role {
        /** 顶栏/固定坐标槽（如 mRNA 模板），不参与滚动 */
        FIXED,
        /** 输入纵向滚动卡 */
        INPUT_SCROLL,
        /** 输出卡（横向或右竖排，由布局决定方向） */
        OUTPUT_CARD
    }

    /** 输出卡内容样式（原 Screen 内 int 常量 STYLE_* 的语义收口为枚举） */
    public enum CardStyle { NONE, STOCK, DNA, PEPTIDE }

    /**
     * 单个槽：角色 + 默认展示物品 id + 卡宽 + 内容样式 + 固定坐标
     * <p>{@code itemId}/{@code width}/{@code style} 仅 INPUT_SCROLL 与 OUTPUT_CARD 有意义；
     * FIXED 槽用 {@code fx}/{@code fy}（Screen 渲染该槽的固定位置，不参与滚动）</p>
     */
    public record Slot(Role role, String itemId, int width, CardStyle style, int fx, int fy) {
    }

    /** 一机的槽位表：列表长度 = 容器 size，列表下标 = 容器槽位下标 */
    public record Machine(List<Slot> slots) {
        public int size() {
            return slots.size();
        }
    }

    /** 输入纵向滚动卡占位坐标（Screen 每帧按滚动偏移重写，此处仅供菜单占位） */
    private static final int INPUT_X = SequenceMachineMenu.INPUT_SCROLL_X + SequenceMachineMenu.SLOT_X;
    private static final int INPUT_Y = SequenceMachineMenu.INPUT_SCROLL_Y + SequenceMachineMenu.SLOT_Y;
    /** 输出卡占位坐标（Screen 每帧按布局重写，此处仅供菜单占位） */
    private static final int OUT_X = SequenceMachineMenu.OUT_X + SequenceMachineMenu.SLOT_X;
    private static final int OUT_Y = SequenceMachineMenu.OUT_Y + SequenceMachineMenu.SLOT_Y;

    /** 固定槽：mRNA / DNA 模板（顶栏 9,8，与 slot.png 偏移相关） */
    private static final int FIX_X = 9;
    private static final int FIX_Y = 8;

    private SequenceSlotSpec() {
    }

    /** 机器类型 → 规格表 */
    public static Machine of(SequenceMachineKind kind) {
        return switch (kind) {
            case DNA_ENCODER -> dnaEncoder();
            case TRANSCRIBER -> transcriber();
            case HELICASE -> helicase();
            case LOADER -> loader();
            case TRANSLATOR -> translator();
            case FOLDER -> folder();
        };
    }

    private static Machine dnaEncoder() {
        return new Machine(List.of(
                new Slot(Role.INPUT_SCROLL, "datp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "dttp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "dctp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "dgtp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "atp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.OUTPUT_CARD, "dna", 104, CardStyle.DNA, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "adp", 56, CardStyle.STOCK, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "ppi", 56, CardStyle.STOCK, OUT_X, OUT_Y)
        ));
    }

    private static Machine transcriber() {
        return new Machine(List.of(
                new Slot(Role.FIXED, "", 0, CardStyle.NONE, FIX_X, FIX_Y),
                new Slot(Role.INPUT_SCROLL, "atp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "utp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "ctp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "gtp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.OUTPUT_CARD, "mrna", 104, CardStyle.DNA, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "adp", 56, CardStyle.STOCK, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "ppi", 56, CardStyle.STOCK, OUT_X, OUT_Y)
        ));
    }

    private static Machine helicase() {
        return new Machine(List.of(
                new Slot(Role.INPUT_SCROLL, "dna", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.OUTPUT_CARD, "dna_single", 56, CardStyle.DNA, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "dna_single", 56, CardStyle.DNA, OUT_X, OUT_Y)
        ));
    }

    private static Machine loader() {
        return new Machine(List.of(
                new Slot(Role.INPUT_SCROLL, "trna", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "glycine", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.INPUT_SCROLL, "atp", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.OUTPUT_CARD, "trna_ala", 56, CardStyle.STOCK, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "amp", 56, CardStyle.STOCK, OUT_X, OUT_Y),
                new Slot(Role.OUTPUT_CARD, "ppi", 56, CardStyle.STOCK, OUT_X, OUT_Y)
        ));
    }

    private static Machine translator() {
        // 26 槽：0 固定 mRNA(9,8) + 1~21 输入滚动(GTP + 20 aa-tRNA) + 22~25 输出(多肽/tRNA/GDP/Pi)
        List<Slot> slots = new java.util.ArrayList<>(26);
        slots.add(new Slot(Role.FIXED, "", 0, CardStyle.NONE, FIX_X, FIX_Y));
        slots.add(new Slot(Role.INPUT_SCROLL, "gtp", 0, CardStyle.NONE, INPUT_X, INPUT_Y));
        // 20 种 aa-tRNA：id 与操作层同源（TranslatorOperation.TRNA_IDS），不重复声明
        for (String id : TranslatorOperation.TRNA_IDS) {
            slots.add(new Slot(Role.INPUT_SCROLL, id, 0, CardStyle.NONE, INPUT_X, INPUT_Y));
        }
        slots.add(new Slot(Role.OUTPUT_CARD, "polypeptide", 104, CardStyle.PEPTIDE, OUT_X, OUT_Y));
        slots.add(new Slot(Role.OUTPUT_CARD, "trna", 56, CardStyle.STOCK, OUT_X, OUT_Y));
        slots.add(new Slot(Role.OUTPUT_CARD, "gdp", 56, CardStyle.STOCK, OUT_X, OUT_Y));
        slots.add(new Slot(Role.OUTPUT_CARD, "phosphate_ion", 56, CardStyle.STOCK, OUT_X, OUT_Y));
        return new Machine(List.copyOf(slots));
    }

    private static Machine folder() {
        return new Machine(List.of(
                new Slot(Role.INPUT_SCROLL, "polypeptide", 0, CardStyle.NONE, INPUT_X, INPUT_Y),
                new Slot(Role.OUTPUT_CARD, "misfolded_protein", 56, CardStyle.STOCK, OUT_X, OUT_Y)
        ));
    }

    /**
     * 注册期一致性断言防火墙：规格表长度必须与操作层声明的槽常量吻合（快速失败），
     * 防止"改规格表漏改某机 / 改操作层漏改规格表"。维护时新增/删槽需同步本断言。
     * <p>校验口径：每机列表长度 == 其操作层最后一个槽下标 + 1；翻译机额外校验
     * SLOT_AATRNA_START 恰好为 2（GTP 占 1 槽 + 20 aa-tRNA 从 2 起）。</p>
     */
    static {
        assertSize(dnaEncoder(), DnaSynthesisOperation.SLOT_OUT_PPI + 1, "DNA_ENCODER");
        assertSize(transcriber(), TranscriptionOperation.SLOT_OUT_PPI + 1, "TRANSCRIBER");
        assertSize(helicase(), HelicaseOperation.SLOT_OUT_B + 1, "HELICASE");
        assertSize(loader(), LoaderOperation.SLOT_OUT_PPI + 1, "LOADER");
        assertSize(translator(), TranslatorOperation.SLOT_OUT_PI + 1, "TRANSLATOR");
        assertSize(folder(), FolderOperation.SLOT_OUT + 1, "FOLDER");
        // 翻译机：20 aa-tRNA 恰从 SLOT_AATRNA_START(2) 起，共 SLOT_AATRNA_START+ids=22 槽后接输出
        if (TranslatorOperation.SLOT_AATRNA_START != 2) {
            throw new IllegalStateException("SequenceSlotSpec: TRANSLATOR aa-tRNA 起始槽需为 2，实际 "
                    + TranslatorOperation.SLOT_AATRNA_START);
        }
    }

    private static void assertSize(Machine machine, int expectedSize, String kind) {
        if (machine.size() != expectedSize) {
            throw new IllegalStateException("SequenceSlotSpec: " + kind + " 规格表长度 " + machine.size()
                    + " 与操作层声明 " + expectedSize + " 不一致");
        }
    }
}
