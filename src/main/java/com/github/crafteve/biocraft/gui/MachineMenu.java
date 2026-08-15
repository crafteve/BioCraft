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

import java.util.List;

/**
 * 酶工厂菜单（experiment/gui-remake 分支全新重建，与 main 合并前定稿）
 * <p>
 * 槽位布局（全酶工厂统一，写死源码不做 json 解析）：
 * <ul>
 *   <li>物种槽（反应物 + 产物）：槽位数 = JSON 条目数之和；每张滚动卡片
 *       一个槽位，isActive 恒 false 使 vanilla 完全跳过（slot.x/y 为 final
 *       无法动态移动），位置由 Screen 的 CardScrollArea 按滚动偏移手动
 *       计算绘制与命中</li>
 *   <li>玩家背包槽位：起始 (48,174)，x 步进 18；主背包三行 y = 174/192/210，
 *       快捷栏 y = 232</li>
 * </ul>
 * 打开数据包协议（与 writeClientSideData 对齐）：
 * 酶 id → 历史长度 → 历史数组 → BlockPos（NeoForge 后写）
 */
public class MachineMenu extends AbstractContainerMenu {
    /** 背包槽起始 x（16×16 内容区左上角） */
    private static final int INV_X0 = 48;

    /** 背包槽起始 y（主背包第一行） */
    private static final int INV_Y0 = 174;

    /** 槽位步进：水平方向同列距，垂直方向同行距 */
    private static final int INV_STEP = 18;

    /** 快捷栏起始 y（与主背包行距不同，固定 232） */
    private static final int HOTBAR_Y = 232;

    // 滚动卡片容器布局常量（Menu 与 Screen 共享，全酶工厂统一写死）
    /** 输入滚动容器左上角 (7,41)，区域 y 41~162，宽 56 */
    public static final int SCROLL_X = 7, SCROLL_Y = 41, SCROLL_W = 56, SCROLL_H = 121;

    /** 输出滚动容器左上角 (193,41)，其余约束与输入完全相同 */
    public static final int OUTPUT_SCROLL_X = 193;

    /** 卡片尺寸 56×28，间距 1，卡片色 #c6c6c6 */
    public static final int CARD_W = 56, CARD_H = 28, CARD_GAP = 1;

    /** 卡片步进（高 + 间距） */
    public static final int CARD_STEP = CARD_H + CARD_GAP;

    /** 槽位贴图（slot.png 18×18）在卡片内的相对位置 (1,2)（png 左上顶点） */
    public static final int SLOT_PNG_X = 1, SLOT_PNG_Y = 2;

    /** 16×16 可交互 Slot 在卡片内的相对位置 (2,3)（居中于 18×18 贴图内） */
    public static final int SLOT_X = SLOT_PNG_X + 1, SLOT_Y = SLOT_PNG_Y + 1;

    /** 槽位物品缩写/浓度文字相对槽位贴图左侧：png 右侧 4px */
    public static final int NAME_DX = 18 + 4;

    /** 容器数据下标：温度×100 */
    public static final int DATA_TEMP = 0;
    /** 容器数据下标：净通量×1000 */
    public static final int DATA_FLUX = 1;
    /** 容器数据下标：主产物浓度×1000 */
    public static final int DATA_PROGRESS = 2;
    /** 余量数据起始下标（每槽一个 int，×1000 定点；3 之后按槽位顺序排列，fe 物种无槽位） */
    public static final int DATA_REMAINDER_BASE = 3;

    /** 本机槽位数（非 fe 物种数，Menu 构造时固化） */
    private final int slotCount;

    /** 方块实体引用，菜单生命周期内保持存活（stillValid 与物种槽用） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /** 酶数据档案（客户端与服务端同源查表，Screen 渲染用） */
    private final EnzymeFactoryData enzymeData;

    /** 容器数据（服务端权威，每 tick 同步：温度/通量/主产物浓度 + 每槽余量×1000 + 能量） */
    private final ContainerData data;

    /** v-t 通量历史（服务端打开时下发，Screen 构造时初始化折线图；服务端不使用） */
    private final int[] fluxHistory;

    /**
     * 服务端主构造（createMenu 直接调用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param blockEntity     方块实体
     * @param fluxHistory     v-t 历史快照（旧→新，每 tick 通量×1000）
     */
    public MachineMenu(int containerId, Inventory playerInventory,
                       EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory) {
        super(ModBlocks.ENZYME_FACTORY_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.enzymeData = blockEntity.getEnzymeData();
        this.fluxHistory = fluxHistory == null ? new int[0] : fluxHistory;
        this.slotCount = blockEntity.getContainer().getContainerSize();
        this.data = new SimpleContainerData(DATA_REMAINDER_BASE + slotCount + 2);
        refreshData();
        addDataSlots(data);
        addSpeciesSlots(blockEntity);
        addPlayerInventory(playerInventory);
    }

    /**
     * 客户端构造（MenuType 数据包工厂）：按服务端写入顺序读取
     * 酶 id → 历史数组 → BlockPos，再经查表定位实体
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     */
    public MachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, parseOpenBuffer(playerInventory, buffer));
    }

    /**
     * 统一私有构造（客户端经数据包解析调用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param initData        打开初始化数据（实体 + 历史）
     */
    private MachineMenu(int containerId, Inventory playerInventory, InitData initData) {
        this(containerId, playerInventory, initData.blockEntity(), initData.fluxHistory());
    }

    /**
     * 解析打开数据包并定位方块实体（与 EnzymeFactoryBlockEntity.writeClientSideData
     * 的写入顺序严格对应：酶 id → 历史长度 → 历史数组 → BlockPos）
     * <p>
     * 方块已被破坏时按数据表档案构造占位实体（防御降级，避免菜单崩溃）；
     * 历史数组保留并随初始化数据传入 Screen（打开瞬间折线图即有数据）
     *
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     * @return 初始化数据（实体 + 历史快照）
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
     * 打开初始化数据：方块实体 + v-t 历史快照
     *
     * @param blockEntity 方块实体
     * @param fluxHistory 历史快照（旧→新）
     */
    private record InitData(EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory) {
    }

    /**
     * 添加物种槽（反应物 + 产物，槽位数 = JSON 条目数之和 − fe 能量物种数）
     * <p>
     * fe（能量物种）不建槽位：其"存量"由能量存储承载，GUI 由能量卡片显示；
     * 容器槽位序号是"非 fe 物种连续序号"（与 BE 的 slotToSpeciesIndex 一致），
     * 输入卡 = 非 fe 反应物（容器索引 0 起），输出卡 = 非 fe 产物（容器索引
     * = 非 fe 反应物数 + i）
     * <p>
     * 位置约定：Slot.x/y 是 final 静态字段（vanilla 渲染与命中直接读字段），
     * 滚动卡片槽位位置由 Screen 全接管——槽位 isActive() 恒 false 使 vanilla
     * 完全跳过（渲染/hover/点击），Screen 按滚动偏移手动计算位置绘制与
     * 命中（见 MachineScreen 的 CardScrollArea）
     *
     * @param blockEntity 方块实体（提供容器与酶数据）
     */
    private void addSpeciesSlots(EnzymeFactoryBlockEntity blockEntity) {
        Container container = blockEntity.getContainer();
        int containerSlot = 0;
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.reactants()) {
            if (com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item())) {
                continue;
            }
            Item item = ModItems.byId(spec.item()).get();
            addSlot(new RestrictedSlot(container, containerSlot++, 0, 0, item));
        }
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.products()) {
            if (com.github.crafteve.biocraft.reaction.EnergyKinetics.isEnergySpecies(spec.item())) {
                continue;
            }
            Item item = ModItems.byId(spec.item()).get();
            addSlot(new RestrictedSlot(container, containerSlot++, 0, 0, item));
        }
    }

    /**
     * 添加玩家背包槽位（36 个：主背包 3×9 + 快捷栏 1×9）
     * <p>
     * 槽位坐标 = 16×16 内容区左上角（Slot 对象定位点，用户给定的像素值）
     *
     * @param playerInventory 玩家物品栏
     */
    private void addPlayerInventory(Inventory playerInventory) {
        // 主背包（物品栏索引 9~35）：三行 y = 174 / 192 / 210
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        INV_X0 + col * INV_STEP, INV_Y0 + row * INV_STEP));
            }
        }
        // 快捷栏（物品栏索引 0~8）：一行 y = 232（不与背包行共用行距公式）
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X0 + col * INV_STEP, HOTBAR_Y));
        }
    }

    /**
     * 从方块实体刷新全部容器数据（温度/通量/主产物浓度/每槽余量 + 能量）
     * <p>
     * 余量 = 引擎连续浓度的尾数（浓度×64 − 槽位整数），经 ContainerData
     * 同步到客户端后，客户端浓度 = (槽位数量 + 余量)/64 即可重建引擎
     * 连续浓度——解决客户端 BE 引擎浓度恒 0 导致的进度条/读数不显示；
     * 能量数据：存量（FE）+ 产率（FE/tick ×10 定点，无能量酶恒 0）
     */
    private void refreshData() {
        data.set(DATA_TEMP, blockEntity.getCachedTempX100());
        data.set(DATA_FLUX, blockEntity.getCachedFluxX1000());
        data.set(DATA_PROGRESS, blockEntity.getCachedProgressX1000());
        for (int i = 0; i < slotCount; i++) {
            data.set(DATA_REMAINDER_BASE + i, (int) Math.round(blockEntity.getRemainder(i) * 1000.0));
        }
        data.set(energyIndex(0), blockEntity.getEnergyStored());
        data.set(energyIndex(1), (int) Math.round(blockEntity.getCachedEnergyRate() * 10.0));
    }

    /**
     * 能量数据下标：余量段之后（DATA_REMAINDER_BASE + 槽位数 + 0/1）
     *
     * @param offset 能量偏移（0 = 存量、1 = 产率）
     * @return 容器数据下标
     */
    private int energyIndex(int offset) {
        return DATA_REMAINDER_BASE + slotCount + offset;
    }

    /**
     * 读取能量存量（ContainerData 同步值）
     *
     * @return FE 存量（无能量酶恒 0）
     */
    public int getEnergyStored() {
        return data.get(energyIndex(0));
    }

    /**
     * 读取能量产率（ContainerData 同步值）
     *
     * @return FE/tick（×10 定点还原，正 = 充能、负 = 消耗）
     */
    public double getEnergyRate() {
        return data.get(energyIndex(1)) / 10.0;
    }

    /**
     * 每 tick 从方块实体刷新数据再广播（服务端执行，客户端 data 由
     * ContainerData 机制同步，无需额外网络包）
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
     * 读取物种余量（ContainerData 同步值，客户端重建引擎浓度用）
     *
     * @param slot 反应物槽位下标
     * @return 0~1 的余量（浓度小数部分）
     */
    public double getRemainder(int slot) {
        return data.get(DATA_REMAINDER_BASE + slot) / 1000.0;
    }

    /**
     * 读取当前净通量（ContainerData 同步值，v-t 折线图数据源）
     *
     * @return 净通量（堆叠分数/s，负值为逆向）
     */
    public double getFlux() {
        return data.get(DATA_FLUX) / 1000.0;
    }

    /**
     * 获取 v-t 通量历史（打开时服务端下发，Screen 初始化折线图用）
     *
     * @return 历史快照（旧→新，每 tick 通量×1000）
     */
    public int[] getFluxHistory() {
        return fluxHistory;
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
        if (slot != null && slot.hasItem()) {
            ItemStack original = slot.getItem();
            moved = original.copy();
            if (index < slotCount) {
                if (!this.moveItemStackTo(original, slotCount, slotCount + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(original, 0, slotCount, false)) {
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
     * 获取酶数据档案（Screen 渲染用）
     *
     * @return 酶数据档案
     */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }

    /**
     * 获取方块实体（Screen 读物种名/余量用）
     *
     * @return 方块实体
     */
    public EnzymeFactoryBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * 受限槽位：仅允许放入对应物种物品（玩家放置与 Shift 转移都经 mayPlace）
     * <p>
     * isActive 恒 false：vanilla 的槽位遍历（渲染/hover/点击命中）全部跳过
     * 本槽位，其滚动位置由 Screen 手动计算（slot.x/y 为 final 无法动态移动，
     * 见 MachineScreen 的手动绘制与命中方案）
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

        /**
         * 槽位堆叠上限（按物品查询）：直接返回容器容量
         * <p>
         * vanilla 默认是 min(容器容量, 物品自身 getMaxStackSize)——分子物品
         * 自身上限 64 会把容量参数化后的 128 钳回 64（"槽位只能放一组"根因）。
         * safeInsert（拖拽）与 moveItemStackTo（shift）都经本方法取上限，
         * 必须返回槽位容量才能放入多组物品
         */
        @Override
        public int getMaxStackSize(ItemStack stack) {
            return getMaxStackSize();
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
