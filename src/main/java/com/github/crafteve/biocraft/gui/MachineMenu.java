package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 酶工厂菜单（248×360 卡片式单页仪表盘）
 * <p>
 * 槽位布局（容器索引）：
 * <ul>
 *   <li>0..n-1：物种槽（每物种一槽，n = 反应物+产物数，顺序与物种索引一致），
 *       mayPlace 锁死对应物品；输入卡条目 = 反应物槽（x=13），
 *       输出卡条目 = 产物槽（x=179），y = 82 + 行号×42</li>
 *   <li>n..n+35：玩家背包（主背包 3×9 @x=12 y=248 起，行距 20）+ 快捷栏 @y=308</li>
 * </ul>
 * 同步通道（ContainerData 4 个 int，服务端权威）：
 * <ul>
 *   <li>DATA_TEMP：温度×100</li>
 *   <li>DATA_FLUX：净通量×1000（速率条与 v-t 图实时增补）</li>
 *   <li>DATA_PROGRESS：主产物浓度×1000</li>
 *   <li>DATA_STALL：停摆编码（0 正常 / 1 停摆，文案客户端查酶数据表）</li>
 * </ul>
 * v-t 历史：打开 GUI 时经 writeClientSideData 一次性下发 100 tick（5 秒）环形缓冲，
 * 打开期间客户端每 tick 从 DATA_FLUX 追加（零额外常态流量）
 */
public class MachineMenu extends AbstractContainerMenu {
    /** 容器数据下标：温度×100 */
    public static final int DATA_TEMP = 0;
    /** 容器数据下标：净通量×1000 */
    public static final int DATA_FLUX = 1;
    /** 容器数据下标：主产物浓度×1000 */
    public static final int DATA_PROGRESS = 2;
    /** 容器数据下标：停摆编码 */
    public static final int DATA_STALL = 3;

    /** 方块实体引用，菜单生命周期内保持存活 */
    private final EnzymeFactoryBlockEntity blockEntity;

    /** 酶数据档案（客户端与服务端同源查表） */
    private final EnzymeFactoryData enzymeData;

    /** 容器数据（4 个 int，服务端写入客户端每 tick 接收） */
    private final ContainerData data;

    /** v-t 通量历史（打开时下发，客户端环形缓冲追加用；服务端不使用） */
    private final int[] fluxHistory;

    /**
     * 客户端构造（MenuType 数据包工厂）：从 buffer 读 BlockPos → 酶 id → 历史
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     */
    public MachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, parseOpenBuffer(playerInventory, buffer));
    }

    /**
     * 统一构造（服务端 createMenu 直接调用，客户端经数据包解析调用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param initData        打开初始化数据（实体 + 历史）
     */
    private MachineMenu(int containerId, Inventory playerInventory, InitData initData) {
        this(containerId, playerInventory, initData.blockEntity(), initData.fluxHistory());
    }

    /**
     * 服务端主构造（createMenu 直接调用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param blockEntity     方块实体
     * @param fluxHistory     v-t 历史快照（服务端不使用，可为空数组）
     */
    public MachineMenu(int containerId, Inventory playerInventory,
                       EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory) {
        super(ModBlocks.ENZYME_FACTORY_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.enzymeData = blockEntity.getEnzymeData();
        this.fluxHistory = fluxHistory == null ? new int[0] : fluxHistory;
        this.data = new SimpleContainerData(4);
        refreshData();
        addDataSlots(data);

        Container container = blockEntity.getContainer();
        int reactantCount = enzymeData.reactants().size();
        int totalCount = enzymeData.reactants().size() + enzymeData.products().size();

        // 物种槽：输入卡（反应物 x=13）与输出卡（产物 x=179），y = 82 + 行号×42
        for (int i = 0; i < totalCount; i++) {
            int x = i < reactantCount ? 13 : 179;
            int y = 82 + (i < reactantCount ? i : i - reactantCount) * 42;
            addSlot(new RestrictedSlot(container, i, x, y,
                    ModItems.byId(blockEntity.getSpeciesId(i)).get()));
        }

        // 玩家背包：主背包 3×9（x=12 y=248 起，行距 20）+ 快捷栏 1×9（y=308）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 12 + col * 18, 248 + row * 20));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 12 + col * 18, 308));
        }
    }

    /**
     * 解析打开数据包（客户端）
     * <p>
     * 读取顺序必须与 NeoForge 服务端写入顺序对齐：IPlayerExtension.openMenu 的
     * extraDataWriter（BlockPos）在 writeClientSideData（酶 id + 历史）之后写入，
     * 故此处先读酶 id → 历史，最后读 BlockPos（与 DNA 编码器相反——它未覆写
     * writeClientSideData，buffer 只有 BlockPos 所以先读）
     *
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     * @return 初始化数据（实体 + 历史）
     */
    private static InitData parseOpenBuffer(Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        String enzymeId = buffer.readUtf();
        int historyLength = buffer.readVarInt();
        int[] history = new int[historyLength];
        for (int i = 0; i < historyLength; i++) {
            history[i] = buffer.readVarInt();
        }
        BlockPos pos = buffer.readBlockPos();
        EnzymeFactoryBlockEntity be = playerInventory.player.level().getBlockEntity(pos)
                instanceof EnzymeFactoryBlockEntity factory ? factory : null;
        if (be == null) {
            // 防御降级：方块已被破坏时按数据表档案构造占位实体，避免菜单崩溃
            EnzymeFactoryData data = EnzymeFactoryRegistry.byId(enzymeId);
            if (data == null) {
                throw new IllegalStateException("打开数据包含未知酶 id: " + enzymeId);
            }
            be = new EnzymeFactoryBlockEntity(pos, data);
        }
        if (!enzymeId.equals(be.getEnzymeData().id())) {
            throw new IllegalStateException("酶 id 不一致: 包内 " + enzymeId + " / 实体 " + be.getEnzymeData().id());
        }
        return new InitData(be, history);
    }

    /**
     * 从方块实体刷新全部容器数据（温度/通量/进度/停摆）
     */
    private void refreshData() {
        data.set(DATA_TEMP, blockEntity.getCachedTempX100());
        data.set(DATA_FLUX, blockEntity.getCachedFluxX1000());
        data.set(DATA_PROGRESS, blockEntity.getCachedProgressX1000());
        data.set(DATA_STALL, blockEntity.getCachedStallCode());
    }

    /**
     * 每 tick 从方块实体刷新数据再广播（客户端无需额外网络包）
     */
    @Override
    public void broadcastChanges() {
        refreshData();
        super.broadcastChanges();
    }

    /**
     * 菜单有效性校验：玩家距离方块 8 格内
     *
     * @param player 操作玩家
     * @return 是否有效
     */
    @Override
    public boolean stillValid(Player player) {
        return blockEntity.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }

    /**
     * Shift 点击转移逻辑：物种槽 → 背包；背包 → 物种槽（受 mayPlace 限制）
     *
     * @param player 操作玩家
     * @param index  被点击的槽位索引
     * @return 转移后的物品堆（空堆表示全部转移成功）
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        int speciesCount = enzymeData.reactants().size() + enzymeData.products().size();
        if (slot != null && slot.hasItem()) {
            ItemStack original = slot.getItem();
            moved = original.copy();
            if (index < speciesCount) {
                if (!this.moveItemStackTo(original, speciesCount, speciesCount + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(original, 0, speciesCount, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (original.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (original.getCount() == moved.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, original);
        }
        return moved;
    }

    /**
     * 打开初始化数据：方块实体 + v-t 历史快照
     */
    private record InitData(EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory) {
    }

    /** 获取酶数据档案（Screen 渲染用） */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }

    /** 获取方块实体（Screen 读物种名/余量用） */
    public EnzymeFactoryBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** 获取 v-t 通量历史（打开时下发，客户端 v-t 图初始化用） */
    public int[] getFluxHistory() {
        return fluxHistory;
    }

    /** 当前温度（K，ContainerData 还原） */
    public double getTemperature() {
        return data.get(DATA_TEMP) / 100.0;
    }

    /** 当前净通量（堆叠分数/s，ContainerData 还原） */
    public double getFlux() {
        return data.get(DATA_FLUX) / 1000.0;
    }

    /** 主产物浓度（0~1，ContainerData 还原） */
    public double getMainProductProgress() {
        return data.get(DATA_PROGRESS) / 1000.0;
    }

    /** 停摆编码（0 正常 / 1 停摆） */
    public int getStallCode() {
        return data.get(DATA_STALL);
    }

    /** 物种槽位总数 */
    public int getSpeciesSlotCount() {
        return enzymeData.reactants().size() + enzymeData.products().size();
    }

    /**
     * 受限槽位：仅允许放入对应物种物品（玩家放置与 Shift 转移都经 mayPlace）
     */
    private static class RestrictedSlot extends Slot {
        private final Item acceptedItem;

        RestrictedSlot(Container container, int slot, int x, int y, Item acceptedItem) {
            super(container, slot, x, y);
            this.acceptedItem = acceptedItem;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(acceptedItem);
        }
    }
}
