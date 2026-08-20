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
 * 序列机通用菜单：槽位布局由机器 kind 决定（编码器 2 槽 / 转录仪 4 槽）
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

    /** 玩家背包起始 y（imageHeight 192 布局：主背包 110/128/146，快捷栏 164） */
    private static final int INV_Y0 = 110;

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
        this.machineSlotCount = slotPositions(kind).length;
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
        return new ClientInit(new SimpleContainer(kind.containerSize()), pos);
    }

    private void addMachineSlots(Container container, SequenceMachineKind kind) {
        SequenceOperation op = kind.createOperation();
        int[][] positions = slotPositions(kind);
        for (int i = 0; i < positions.length; i++) {
            int index = i;
            addSlot(new Slot(container, index, positions[i][0], positions[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return op.isItemValidForSlot(index, stack);
                }
            });
        }
    }

    /** 每机器的槽位坐标（编码器 2 槽 / 转录仪 4 槽） */
    private static int[][] slotPositions(SequenceMachineKind kind) {
        return switch (kind) {
            case DNA_ENCODER -> new int[][]{{62, 56}, {98, 56}};              // 单体池, 产物
            case TRANSCRIBER -> new int[][]{{62, 34}, {98, 34}, {62, 56}, {98, 56}}; // 催化剂, 模板, 单体池, 产物
        };
    }

    private void addPlayerInventory(Inventory playerInventory) {
        // 主背包 3 行 × 9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INV_Y0 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, INV_Y0 + 54));
        }
    }

    /** 菜单类型 → 机器 kind（客户端构造用；缺省回落编码器） */
    static SequenceMachineKind kindOf(MenuType<?> menuType) {
        if (menuType == ModBlocks.TRANSCRIBER_MENU.get()) {
            return SequenceMachineKind.TRANSCRIBER;
        }
        return SequenceMachineKind.DNA_ENCODER;
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
