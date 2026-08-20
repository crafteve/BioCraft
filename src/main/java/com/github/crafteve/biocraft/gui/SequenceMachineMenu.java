package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.DnaSynthesisOperation;
import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.SequenceOperation;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 序列机通用菜单：槽位布局由机器 kind 决定（编码器 8 槽 / 转录仪 4 槽）
 * <p>
 * 窗口 256×256（与酶工厂同尺寸），玩家背包坐标抄酶工厂（主背包 x48 y174 起、
 * 快捷栏 y232）。编码器输入 5 槽为滚动卡片（isActive=false，坐标由 Screen
 * 每帧写入，见 SequenceScreen 的滚动区）；输出 3 槽固定位置（vanilla 渲染）。
 * <p>
 * ContainerData 每 tick 同步：0=stage 序号、1=position、2=total；
 * 服务端 get 实时读 BE 状态（无需手动刷新），客户端收广播；
 * 客户端构造从打开数据包读 BlockPos（NeoForge 自动写入）定位客户端 BE 容器
 * （方块已破坏时用占位空容器防御降级）
 */
public class SequenceMachineMenu extends AbstractContainerMenu {

    public static final int DATA_STAGE = 0;
    public static final int DATA_POSITION = 1;
    public static final int DATA_TOTAL = 2;
    /** 分子余量数据起始下标（每槽一个 ×1000 定点，酶工厂同款） */
    public static final int DATA_REMAINDER_BASE = 3;
    private static final int DATA_COUNT = DATA_REMAINDER_BASE + 8;

    /** 窗口尺寸（贴图 256×256 全屏） */
    public static final int WINDOW_W = 256;
    public static final int WINDOW_H = 256;

    /** 玩家背包坐标（抄酶工厂）：主背包起始 x/y、行距、快捷栏 y */
    public static final int INV_X0 = 48;
    public static final int INV_Y0 = 174;
    public static final int INV_STEP = 18;
    public static final int HOTBAR_Y = 232;

    /**
     * 编码器输入滚动卡片区（酶工厂同定位）：左上 (7,41)，视口高 122
     * （下边界 163，与输出卡片区底边对齐，贴近画布分隔线）
     */
    public static final int INPUT_SCROLL_X = 7;
    public static final int INPUT_SCROLL_Y = 41;
    public static final int INPUT_SCROLL_W = 56;
    public static final int INPUT_SCROLL_H = 122;
    public static final int CARD_W = 56;
    public static final int CARD_H = 28;
    public static final int CARD_GAP = 1;
    public static final int CARD_STEP = CARD_H + CARD_GAP;
    public static final int SLOT_PNG_X = 1;
    public static final int SLOT_PNG_Y = 2;
    public static final int SLOT_X = SLOT_PNG_X + 1;
    public static final int SLOT_Y = SLOT_PNG_Y + 1;

    /** 编码区（深色编辑器面板，Screen 子类绘制内容）：用户定位 69,31-247,126 */
    public static final int EDIT_X = 69;
    public static final int EDIT_Y = 31;
    public static final int EDIT_W = 178;
    public static final int EDIT_H = 95;

    /**
     * 输出横向滚动卡片区（用户定稿 2026-08-19）：OUTPUT 标签 y=132，
     * 卡片区紧跟标签下方 y=140 起、下边界 y=163（压缩卡片高度 23，
     * 与输入滚动区底边对齐）
     */
    public static final int OUT_X = 70;
    public static final int OUT_Y = 140;
    public static final int OUT_W = 176;
    public static final int OUT_H = 23;

    /** 输出卡片高度（压缩版，输入卡片保持 28） */
    public static final int OUT_CARD_H = 23;

    /** 输出卡片宽度（DNA 加宽放序列预览，ADP/PPi 标准宽） */
    public static final int OUT_CARD_DNA_W = 104;
    public static final int OUT_CARD_SUB_W = 56;

    /** 输出标签（英文大写，y=132 起始绘制） */
    public static final int OUTPUT_LABEL_X = 70;
    public static final int OUTPUT_LABEL_Y = 132;

    private final SequenceMachineKind kind;
    private final BlockPos pos;
    private final ContainerData data;
    /** 机器槽数量（Screen 绘制槽位底时区分玩家背包槽） */
    public final int machineSlotCount;

    /** 编辑器草稿（编码器打开数据包下发；转录仪恒空串） */
    private final String programDraft;

    /** 服务端构造（BE.createMenu 调用），data 实时读 BE 状态 */
    public SequenceMachineMenu(SequenceMachineKind kind, int containerId,
                               Inventory playerInventory, SequenceMachineBlockEntity be) {
        this(ModBlocks.sequenceMenuType(kind), kind, containerId, playerInventory, be.getContainer(), be.getBlockPos(),
                new ContainerData() {
                    @Override
                    public int get(int index) {
                        if (index >= DATA_REMAINDER_BASE && index < DATA_COUNT) {
                            return (int) Math.round(
                                    be.stepState().remainder(index - DATA_REMAINDER_BASE) * 1000.0);
                        }
                        return switch (index) {
                            case DATA_STAGE -> be.stepState().stage().ordinal();
                            case DATA_POSITION -> be.stepState().position();
                            case DATA_TOTAL -> be.stepState().total();
                            default -> 0;
                        };
                    }

                    @Override
                    public void set(int index, int value) {
                    }

                    @Override
                    public int getCount() {
                        return DATA_COUNT;
                    }
                }, be.programDraft());
    }

    /** 客户端构造（MenuType 数据包工厂）：读 BlockPos → 客户端 BE 容器 */
    public SequenceMachineMenu(SequenceMachineKind kind, int containerId,
                               Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(ModBlocks.sequenceMenuType(kind), kind, containerId, playerInventory,
                parseClientInit(playerInventory, buffer, kind),
                new SimpleContainerData(DATA_COUNT));
    }

    /** 客户端统一构造 */
    private SequenceMachineMenu(MenuType<?> menuType, SequenceMachineKind kind, int containerId,
                                Inventory playerInventory, ClientInit init, ContainerData data) {
        this(menuType, kind, containerId, playerInventory, init.container(), init.pos(), data, init.draft());
    }

    /** 统一私有构造 */
    private SequenceMachineMenu(MenuType<?> menuType, SequenceMachineKind kind, int containerId,
                                Inventory playerInventory, Container container, BlockPos pos, ContainerData data,
                                String programDraft) {
        super(menuType, containerId);
        this.kind = kind;
        this.pos = pos;
        this.data = data;
        this.programDraft = programDraft;
        addDataSlots(data);
        this.machineSlotCount = containerSizeFor(kind);
        addMachineSlots(container, kind);
        addPlayerInventory(playerInventory);
    }

    private record ClientInit(Container container, BlockPos pos, String draft) {
    }

    private static ClientInit parseClientInit(Inventory playerInventory, RegistryFriendlyByteBuf buffer,
                                              SequenceMachineKind kind) {
        BlockPos pos = buffer.readBlockPos();
        // 编码器打开数据包追加草稿（MachineBlock 写入顺序：pos → writeMenuOpeningData）
        String draft = kind == SequenceMachineKind.DNA_ENCODER ? buffer.readUtf() : "";
        if (playerInventory.player.level().getBlockEntity(pos) instanceof SequenceMachineBlockEntity be) {
            return new ClientInit(be.getContainer(), pos, draft);
        }
        // 方块已破坏：占位空容器（防御降级，避免菜单崩溃）
        return new ClientInit(new SimpleContainer(containerSizeFor(kind)), pos, draft);
    }

    private static int containerSizeFor(SequenceMachineKind kind) {
        return kind.containerSize();
    }

    /**
     * 机器槽：编码器 = 5 输入（纵向滚动卡片）+ 3 输出（横向滚动卡片），
     * 转录仪 = 4 固定槽。
     * <p>
     * 交互层（源码实证）：vanilla findSlot/render 循环要求 slot.isActive()==true
     * 才命中/渲染——滚动槽必须 isActive=true（坐标由 Screen 每帧写入，AT 已拆
     * final），否则点击放不进物品；isHighlightable=true 交 vanilla 绘制悬停
     * 高亮（方向 B：与酶工厂统一，删自绘；滚动边缘 ≤9px 溢出属可接受小瑕疵，
     * 见 MachineMenu 同款注释）。
     * <p>
     * 门控（固定方向，无切换按钮——与酶工厂 IO 模式按钮的区别）：
     * 编码器输入槽只进不出（mayPickup=false）、输出槽只出不进（mayPlace 由
     * 操作层拒绝）、DNA 槽仅完全编码（complete）可取出。vanilla 全部取走路径
     * （PICKUP / QUICK_MOVE / SWAP / PICKUP_ALL / THROW）都经 mayPickup 询问
     * （AbstractContainerMenu.doClick 源码实证：L385/L409/L445/L461/L499，
     * THROW 经 safeTake→checkTakeConditions→mayPickup）——门控一处生效全局
     */
    private void addMachineSlots(Container container, SequenceMachineKind kind) {
        SequenceOperation op = kind.createOperation();
        int[][] positions = slotPositions(kind);
        for (int i = 0; i < positions.length; i++) {
            addSlot(new MachineSlot(container, i, positions[i][0], positions[i][1], op));
        }
    }

    /**
     * 序列机槽位（对齐酶工厂 RestrictedSlot 的完整子类模式）
     * <p>
     * 与酶工厂 RestrictedSlot 的差异：无 IO 模式三态（本机器方向固定）、
     * 无 128 容量参数化（64 上限无需 remove 钳制）——只保留方向门控与
     * DNA 完成态门控
     */
    private final class MachineSlot extends BiocraftSlot {
        private final SequenceOperation op;

        MachineSlot(Container container, int index, int x, int y, SequenceOperation op) {
            super(container, index, x, y, 64);
            this.op = op;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return op.isItemValidForSlot(index, stack);
        }

        /**
         * 取走门控：产物 DNA 仅完全编码可取出；其余槽（输入 dNTP/ATP、
         * 副产物 ADP/PPi）GUI 均可取出——输入槽"只禁管道输出"由 BE 的
         * canTakeItemInternal 门控（漏斗/管道同规则），玩家 GUI 可自由
         * 取出放错的单体。转录仪保持全可抽（重做时定）
         */
        @Override
        public boolean mayPickup(Player player) {
            if (kind != SequenceMachineKind.DNA_ENCODER) {
                return true;
            }
            if (index == DnaSynthesisOperation.SLOT_OUT_DNA) {
                // 产物 DNA：仅完全编码（complete）才可输出；半成品锁在槽内
                SequenceData data = getItem().get(ModDataComponents.SEQUENCE.get());
                return data != null && data.complete();
            }
            return true; // 输入槽与 ADP/PPi：GUI 可取出
        }

        /**
         * 取物绕过容器 removeItem 门控（GUI 专属）：
         * <p>
         * vanilla 取物链 tryRemove → remove → container.removeItem（源码实证
         * Slot.java L87-88/L99-106），而容器 removeItem 被 canTakeItemInternal
         * 门控（拦漏斗/管道）——GUI 点击取物也走同链，会被误拦（实测
         * "点击输入槽取不出"根因，上一轮只改 mayPickup 不够）。
         * 本覆写直接经 setItem 减量（setItem 未门控）：GUI 取物生效，
         * 漏斗/管道仍走容器 removeItem 被拦截——实现"GUI 可取、管道禁抽"
         * 的差异化门控（酶工厂 IO 模式三路同规则，本机器刻意不同）
         */
        @Override
        public ItemStack remove(int amount) {
            ItemStack stack = this.getItem();
            int take = Math.min(amount, stack.getCount());
            ItemStack result = stack.split(take);
            if (stack.isEmpty()) {
                this.set(ItemStack.EMPTY);
            } else {
                this.setChanged();
            }
            return result;
        }

        @Override
        public boolean isHighlightable() {
            return true; // 方向 B：高亮交 vanilla renderSlotHighlight（与酶工厂统一）
        }
    }

    /** 每机器的槽位坐标（GUI 相对；编码器输入/输出槽坐标由 Screen 滚动区覆写） */
    private static int[][] slotPositions(SequenceMachineKind kind) {
        return switch (kind) {
            case DNA_ENCODER -> new int[][]{
                    // 输入 5 槽（纵向滚动卡片，坐标由 Screen 写入，此处为占位）
                    {INPUT_SCROLL_X + SLOT_X, INPUT_SCROLL_Y + SLOT_Y},
                    {INPUT_SCROLL_X + SLOT_X, INPUT_SCROLL_Y + SLOT_Y},
                    {INPUT_SCROLL_X + SLOT_X, INPUT_SCROLL_Y + SLOT_Y},
                    {INPUT_SCROLL_X + SLOT_X, INPUT_SCROLL_Y + SLOT_Y},
                    {INPUT_SCROLL_X + SLOT_X, INPUT_SCROLL_Y + SLOT_Y},
                    // 输出 3 槽（横向滚动卡片，坐标由 Screen 写入，此处为占位）
                    {OUT_X + SLOT_X, OUT_Y + SLOT_Y},
                    {OUT_X + SLOT_X, OUT_Y + SLOT_Y},
                    {OUT_X + SLOT_X, OUT_Y + SLOT_Y},
            };
            case TRANSCRIBER -> new int[][]{
                    {EDIT_X + 2, 34}, {EDIT_X + 2, 56}, {EDIT_X + 2, 78}, {EDIT_X + 40, 56},
            };
        };
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // 主背包 3 行 × 9（抄酶工厂坐标）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X0 + col * 18, INV_Y0 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X0 + col * 18, HOTBAR_Y));
        }
    }

    public SequenceMachineKind getKind() {
        return kind;
    }

    public BlockPos getPos() {
        return pos;
    }

    /** 编辑器草稿（编码器：服务端 BE 存档的未提交文本；转录仪/无草稿恒空串） */
    public String getProgramDraft() {
        return programDraft;
    }

    public ContainerData getData() {
        return data;
    }

    /**
     * 读取槽位分子余量（ContainerData 同步值，×1000 定点还原）
     * <p>
     * 1 分子 = 10 碱基时，GUI 卡片显示 count + 余量（如 x32.50）、
     * 进度条按 (count + 余量)/64 归一化——酶工厂浓度重建同款口径
     */
    public double getRemainder(int slot) {
        if (slot < 0 || slot >= 8) {
            return 0.0;
        }
        return data.get(DATA_REMAINDER_BASE + slot) / 1000.0;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.blockPosition().distSqr(pos) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift 快速转移：机器槽 ↔ 玩家背包（moveItemStackTo 尊重 Slot.mayPlace 过滤，
        // 产物槽不可放入、催化剂/单体槽按操作规则过滤）
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index < this.machineSlotCount) {
                if (!this.moveItemStackTo(stack, this.machineSlotCount, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, this.machineSlotCount, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return itemstack;
    }
}
