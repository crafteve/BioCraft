package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * 酶反应腔菜单（统一机器：0 槽 = 酶物品，1..n = 当前酶的物种槽）
 * <p>
 * 槽位布局（固定最大容量，未用槽位禁用）：
 * <ul>
 *   <li>0 槽（酶槽）：isActive=true 由 vanilla 渲染/命中，固定位于标题栏
 *       (8,8)——原酶工厂方块图标位；mayPlace 只接受酶蛋白物品，
 *       堆叠上限 64（堆叠数 = [E]，1 个 = 1 倍速、64 个 = 64 倍速）</li>
 *   <li>1..maxSpecies 槽（物种槽）：isActive 恒 false 使 vanilla 完全跳过，
 *       位置由 Screen 的 CardScrollArea 按滚动偏移手动计算绘制与命中；
 *       mayPlace 查 BE 当前酶的槽位映射（无酶/未用槽位拒绝一切）</li>
 *   <li>玩家背包槽位：起始 (48,174)，x 步进 18；主背包三行 y = 174/192/210，
 *       快捷栏 y = 232</li>
 * </ul>
 * 打开数据包协议（与 writeClientSideData 对齐）：
 * 酶 id（空串 = 无酶）→ 历史长度 → 历史数组 → BlockPos（NeoForge 后写）
 * <p>
 * ContainerData 每 tick 同步：温度/通量/主产物浓度 + 酶 id 索引
 * （DATA_ENZYME：registry 顺序索引 +1，0 = 无酶——GUI 打开期间
 * 放酶/换酶也能实时刷新）+ 每物种槽余量 + 能量存量/产率
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

    /**
     * 酶槽（0 槽）固定位置：标题栏 (7,7)——与 slot.png 背景 blit 位置完全重合，
     * 消除"视觉槽位框与交互命中区错位 1px"（实测：点背景边缘命不中 Slot）
     */
    public static final int ENZYME_SLOT_X = 7, ENZYME_SLOT_Y = 7;

    /** 容器数据下标：温度×100 */
    public static final int DATA_TEMP = 0;
    /** 容器数据下标：净通量×1000 */
    public static final int DATA_FLUX = 1;
    /** 容器数据下标：主产物浓度×1000 */
    public static final int DATA_PROGRESS = 2;
    /** 容器数据下标：酶 id 索引（registry 顺序索引 +1，0 = 无酶；GUI 实时刷新用） */
    public static final int DATA_ENZYME = 3;
    /** 余量数据起始下标（每物种槽一个 int，×1000 定点；4 之后按槽位顺序，0 槽酶无余量） */
    public static final int DATA_REMAINDER_BASE = 4;

    /** 固定物种槽数（最大非 fe 物种数，注册期统计；未用槽位禁用） */
    private final int speciesSlotCount;

    /**
     * 客户端打开包中的酶 id（空串 = 无酶；服务端构造为 null 不使用）
     * <p>
     * 用于客户端 Menu 初始化 DATA_ENZYME：打开数据包已含服务端权威酶 id，
     * 直接换算索引写入 data，打开瞬间 GUI 即有正确酶态——
     * 不依赖 broadcastChanges 的首个同步 tick（实测打开瞬间恒为无酶告示态）
     */
    private final String packetEnzymeId;

    /** 方块实体引用，菜单生命周期内保持存活（stillValid 与物种槽用） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /** 容器数据（服务端权威，每 tick 同步） */
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
        this(containerId, playerInventory, blockEntity, fluxHistory, null);
    }

    /**
     * 统一构造：packetEnzymeId 仅客户端传入（打开包解析），服务端为 null
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param blockEntity     方块实体
     * @param fluxHistory     v-t 历史快照
     * @param packetEnzymeId  客户端打开包中的酶 id（空串 = 无酶，null = 服务端）
     */
    private MachineMenu(int containerId, Inventory playerInventory,
                        EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory, String packetEnzymeId) {
        super(ModBlocks.ENZYME_CHAMBER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.packetEnzymeId = packetEnzymeId;
        this.fluxHistory = fluxHistory == null ? new int[0] : fluxHistory;
        this.speciesSlotCount = EnzymeFactoryRegistry.maxNonFeSpeciesCount();
        this.data = new SimpleContainerData(DATA_REMAINDER_BASE + speciesSlotCount + 2);
        refreshData();
        // 客户端：用打开包酶 id 覆盖 DATA_ENZYME（服务端权威值），
        // 打开瞬间即有正确酶态，后续广播同步继续覆盖
        if (packetEnzymeId != null) {
            data.set(DATA_ENZYME, enzymeIndexById(packetEnzymeId));
        }
        addDataSlots(data);
        addEnzymeSlot();
        addSpeciesSlots();
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
     * @param initData        打开初始化数据（实体 + 历史 + 酶 id）
     */
    private MachineMenu(int containerId, Inventory playerInventory, InitData initData) {
        this(containerId, playerInventory, initData.blockEntity(), initData.fluxHistory(), initData.enzymeId());
    }

    /**
     * 解析打开数据包并定位方块实体（与 EnzymeFactoryBlockEntity.writeClientSideData
     * 的写入顺序严格对应：酶 id → 历史长度 → 历史数组 → BlockPos）
     * <p>
     * 方块已被破坏时按空气状态构造占位实体（防御降级，避免菜单崩溃）；
     * 占位实体无酶（0 槽空），GUI 显示无酶告示态
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
            be = new EnzymeFactoryBlockEntity(pos, Blocks.AIR.defaultBlockState());
        }
        return new InitData(be, history, enzymeId);
    }

    /**
     * 打开初始化数据：方块实体 + v-t 历史快照 + 服务端权威酶 id
     *
     * @param blockEntity 方块实体
     * @param fluxHistory 历史快照（旧→新）
     * @param enzymeId    酶 id（空串 = 无酶）
     */
    private record InitData(EnzymeFactoryBlockEntity blockEntity, int[] fluxHistory, String enzymeId) {
    }

    /**
     * 添加 0 槽（酶槽）：固定位置 (8,8)，isActive=true 由 vanilla 渲染/命中，
     * mayPlace 只接受酶蛋白物品；堆叠上限 64（[E]）
     */
    private void addEnzymeSlot() {
        addSlot(new RestrictedSlot(blockEntity, EnzymeFactoryBlockEntity.ENZYME_SLOT,
                ENZYME_SLOT_X, ENZYME_SLOT_Y, 64, true));
    }

    /**
     * 添加物种槽（1..maxSpecies，固定最大容量）
     * <p>
     * 全部 isActive=false（vanilla 完全跳过渲染/hover/点击），
     * 位置由 Screen 的 CardScrollArea 按当前酶动态绘制与命中；
     * mayPlace 查 BE 当前酶的槽位映射（无酶/未用槽位拒绝一切）
     */
    private void addSpeciesSlots() {
        int slotLimit = 64 * com.github.crafteve.biocraft.reaction.KineticConstants.SLOT_GROUPS;
        for (int slot = EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE;
             slot < EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE + speciesSlotCount; slot++) {
            addSlot(new RestrictedSlot(blockEntity, slot, 0, 0, slotLimit, false));
        }
    }

    /**
     * 添加玩家背包槽位（36 个：主背包 3×9 + 快捷栏 1×9）
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
     * 从方块实体刷新全部容器数据（温度/通量/主产物浓度/酶索引/每槽余量 + 能量）
     *
     * @param offset 无
     */
    private void refreshData() {
        data.set(DATA_TEMP, blockEntity.getCachedTempX100());
        data.set(DATA_FLUX, blockEntity.getCachedFluxX1000());
        data.set(DATA_PROGRESS, blockEntity.getCachedProgressX1000());
        data.set(DATA_ENZYME, enzymeIndex(blockEntity.getEnzymeData()));
        for (int i = 0; i < speciesSlotCount; i++) {
            data.set(DATA_REMAINDER_BASE + i,
                    (int) Math.round(blockEntity.getRemainder(EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE + i) * 1000.0));
        }
        data.set(energyIndex(0), blockEntity.getEnergyStored());
        data.set(energyIndex(1), (int) Math.round(blockEntity.getCachedEnergyRate() * 10.0));
    }

    /**
     * 酶数据 → ContainerData 索引（registry 顺序索引 +1，无酶为 0）
     *
     * @param data 酶数据档案（可为 null）
     * @return 索引值
     */
    private static int enzymeIndex(EnzymeFactoryData data) {
        return data == null ? 0 : enzymeIndexById(data.id());
    }

    /**
     * 酶 id → ContainerData 索引（registry 顺序索引 +1，未知/空串为 0）
     *
     * @param enzymeId 酶注册名（空串 = 无酶）
     * @return 索引值
     */
    private static int enzymeIndexById(String enzymeId) {
        if (enzymeId == null || enzymeId.isEmpty()) {
            return 0;
        }
        int index = 0;
        for (EnzymeFactoryData enzyme : EnzymeFactoryRegistry.ordered()) {
            index++;
            if (enzyme.id().equals(enzymeId)) {
                return index;
            }
        }
        return 0;
    }

    /**
     * 能量数据下标：余量段之后（DATA_REMAINDER_BASE + 物种槽数 + 0/1）
     *
     * @param offset 能量偏移（0 = 存量、1 = 产率）
     * @return 容器数据下标
     */
    private int energyIndex(int offset) {
        return DATA_REMAINDER_BASE + speciesSlotCount + offset;
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
     * 读取物种槽余量（ContainerData 同步值，客户端重建引擎浓度用）
     *
     * @param slot 容器槽位（1..maxSpecies，物种槽）
     * @return 0~1 的余量（浓度小数部分），0 槽/非法槽恒 0
     */
    public double getRemainder(int slot) {
        if (slot < EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE
                || slot >= EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE + speciesSlotCount) {
            return 0.0;
        }
        return data.get(DATA_REMAINDER_BASE + slot - EnzymeFactoryBlockEntity.SPECIES_SLOT_BASE) / 1000.0;
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
     * 获取当前酶数据（动态解析：服务端直查 BE，客户端从 DATA_ENZYME 索引查表——
     * GUI 打开期间放酶/换酶也能实时感知，Screen 据此重建卡片）
     *
     * @return 酶数据档案，无酶为 null
     */
    public EnzymeFactoryData getEnzymeData() {
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide) {
            int index = data.get(DATA_ENZYME);
            if (index <= 0) {
                return null;
            }
            java.util.List<EnzymeFactoryData> ordered = EnzymeFactoryRegistry.ordered();
            return index <= ordered.size() ? ordered.get(index - 1) : null;
        }
        return blockEntity.getEnzymeData();
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
     * Shift 点击转移逻辑：机器槽（酶槽 + 物种槽）→ 背包；背包 → 机器槽
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
            int machineSlots = 1 + speciesSlotCount;
            if (index < machineSlots) {
                if (!this.moveItemStackTo(original, machineSlots, machineSlots + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(original, 0, machineSlots, false)) {
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
     * 受限槽位：酶槽只接受酶蛋白物品；物种槽只接受当前酶的对应物种
     * <p>
     * 物种槽 isActive 恒 false：vanilla 的槽位遍历（渲染/hover/点击命中）全部跳过，
     * 其滚动位置由 Screen 手动计算；酶槽 isActive=true（固定位置，vanilla 全权处理）
     */
    private static class RestrictedSlot extends Slot {
        private final EnzymeFactoryBlockEntity blockEntity;
        private final int maxStack;
        private final boolean active;
        private final boolean enzymeSlot;

        RestrictedSlot(EnzymeFactoryBlockEntity blockEntity, int slot, int x, int y,
                       int maxStack, boolean enzymeSlot) {
            super(blockEntity.getContainer(), slot, x, y);
            this.blockEntity = blockEntity;
            this.maxStack = maxStack;
            this.enzymeSlot = enzymeSlot;
            this.active = !enzymeSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (enzymeSlot) {
                return stack.getItem() instanceof EnzymeItem;
            }
            String speciesId = blockEntity.getSpeciesId(index);
            return speciesId != null && stack.is(ModItems.byId(speciesId).get());
        }

        /**
         * 槽位堆叠上限（按物品查询）：酶槽 64（[E] 上限），物种槽槽位容量
         * <p>
         * vanilla 默认是 min(容器容量, 物品自身 getMaxStackSize)——分子物品
         * 自身上限 64 会把容量参数化后的 128 钳回 64；safeInsert（拖拽）与
         * moveItemStackTo（shift）都经本方法取上限，必须返回槽位容量
         */
        @Override
        public int getMaxStackSize(ItemStack stack) {
            return maxStack;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
