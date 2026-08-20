package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.SequenceOperation;
import com.github.crafteve.biocraft.init.ModBlocks;
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
    private static final int DATA_COUNT = 3;

    /** 窗口尺寸（贴图 256×256 全屏） */
    public static final int WINDOW_W = 256;
    public static final int WINDOW_H = 256;

    /** 玩家背包坐标（抄酶工厂）：主背包起始 x/y、行距、快捷栏 y */
    public static final int INV_X0 = 48;
    public static final int INV_Y0 = 174;
    public static final int INV_STEP = 18;
    public static final int HOTBAR_Y = 232;

    /** 编码器输入滚动卡片区（酶工厂同定位）：左上 (7,41)，56×112 视口 */
    public static final int INPUT_SCROLL_X = 7;
    public static final int INPUT_SCROLL_Y = 41;
    public static final int INPUT_SCROLL_W = 56;
    public static final int INPUT_SCROLL_H = 112;
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

    /** 输出横向滚动卡片区（用户定位 70,133-246,161）：176×28 视口，横向滚动 */
    public static final int OUT_X = 70;
    public static final int OUT_Y = 133;
    public static final int OUT_W = 176;
    public static final int OUT_H = 28;

    /** 输出卡片宽度（DNA 加宽放序列预览，ADP/PPi 标准宽） */
    public static final int OUT_CARD_DNA_W = 104;
    public static final int OUT_CARD_SUB_W = 56;

    /** 输出标签（英文大写，y 与 INPUT 同基准、左上角） */
    public static final int OUTPUT_LABEL_X = 70;
    public static final int OUTPUT_LABEL_Y = 129;

    private final SequenceMachineKind kind;
    private final BlockPos pos;
    private final ContainerData data;
    /** 机器槽数量（Screen 绘制槽位底时区分玩家背包槽） */
    public final int machineSlotCount;

    /** 服务端构造（BE.createMenu 调用），data 实时读 BE 状态 */
    public SequenceMachineMenu(SequenceMachineKind kind, int containerId,
                               Inventory playerInventory, SequenceMachineBlockEntity be) {
        this(ModBlocks.sequenceMenuType(kind), kind, containerId, playerInventory, be.getContainer(), be.getBlockPos(),
                new ContainerData() {
                    @Override
                    public int get(int index) {
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
                });
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
        this(menuType, kind, containerId, playerInventory, init.container(), init.pos(), data);
    }

    /** 统一私有构造 */
    private SequenceMachineMenu(MenuType<?> menuType, SequenceMachineKind kind, int containerId,
                                Inventory playerInventory, Container container, BlockPos pos, ContainerData data) {
        super(menuType, containerId);
        this.kind = kind;
        this.pos = pos;
        this.data = data;
        addDataSlots(data);
        this.machineSlotCount = containerSizeFor(kind);
        addMachineSlots(container, kind);
        addPlayerInventory(playerInventory);
    }

    private record ClientInit(Container container, BlockPos pos) {
    }

    private static ClientInit parseClientInit(Inventory playerInventory, RegistryFriendlyByteBuf buffer,
                                              SequenceMachineKind kind) {
        BlockPos pos = buffer.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof SequenceMachineBlockEntity be) {
            return new ClientInit(be.getContainer(), pos);
        }
        // 方块已破坏：占位空容器（防御降级，避免菜单崩溃）
        return new ClientInit(new SimpleContainer(containerSizeFor(kind)), pos);
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
     * final），否则点击放不进物品；isHighlightable=false 关闭 vanilla 高亮
     * （renderSlotHighlight 是 static 且不裁剪，滚动边缘会溢出到视口外），
     * 悬停高亮由 Screen 自绘
     */
    private void addMachineSlots(Container container, SequenceMachineKind kind) {
        SequenceOperation op = kind.createOperation();
        int[][] positions = slotPositions(kind);
        boolean scrollAll = kind == SequenceMachineKind.DNA_ENCODER;
        for (int i = 0; i < positions.length; i++) {
            int index = i;
            addSlot(new Slot(container, index, positions[i][0], positions[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return op.isItemValidForSlot(index, stack);
                }

                @Override
                public boolean isActive() {
                    return true; // 命中/渲染前提（vanilla findSlot 源码实证）
                }

                @Override
                public boolean isHighlightable() {
                    return !scrollAll; // 滚动槽高亮自绘（防 vanilla 高亮溢出视口）
                }
            });
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

    public ContainerData getData() {
        return data;
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
