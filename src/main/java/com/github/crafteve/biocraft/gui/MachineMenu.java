package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 酶工厂菜单（experiment/gui-remake 分支全新重建）
 * <p>
 * 重建第一版（v1）：仅含玩家背包槽位 + 基底贴图 gui_v1.png，
 * 物种槽与仪表盘待后续逐项追加
 * <p>
 * 背包槽布局（全酶工厂统一，写死源码不做 json 解析）：
 * <ul>
 *   <li>槽位定位点 = 16×16 可交互 Slot 对象内容区左上角（非 18px 贴图位置）</li>
 *   <li>起始 (48,174)，x 步进 18（48, 66, 84, ... 每行 9 个）</li>
 *   <li>行距 18：主背包三行 y = 174 / 192 / 210，快捷栏 y = 232</li>
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

    /** 方块实体引用，菜单生命周期内保持存活（stillValid 与后续物种槽用） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /** 酶数据档案（客户端与服务端同源查表，Screen 渲染用） */
    private final EnzymeFactoryData enzymeData;

    /**
     * 服务端主构造（createMenu 直接调用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param blockEntity     方块实体
     */
    public MachineMenu(int containerId, Inventory playerInventory, EnzymeFactoryBlockEntity blockEntity) {
        super(ModBlocks.ENZYME_FACTORY_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.enzymeData = blockEntity.getEnzymeData();
        addPlayerInventory(playerInventory);
    }

    /**
     * 客户端构造（MenuType 数据包工厂）：按服务端写入顺序读取
     * 酶 id → 历史数组（当前版本弃用仅消费）→ BlockPos，再经查表定位实体
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     */
    public MachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, parseOpenBuffer(playerInventory, buffer));
    }

    /**
     * 解析打开数据包并定位方块实体（与 EnzymeFactoryBlockEntity.writeClientSideData
     * 的写入顺序严格对应：酶 id → 历史长度 → 历史数组 → BlockPos）
     * <p>
     * 方块已被破坏时按数据表档案构造占位实体（防御降级，避免菜单崩溃）；
     * 历史数组本版不保留（v-t 图重建后再接回）
     *
     * @param playerInventory 玩家物品栏
     * @param buffer          打开数据包缓冲
     * @return 定位到的方块实体（可能为占位实体）
     */
    private static EnzymeFactoryBlockEntity parseOpenBuffer(Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        String enzymeId = buffer.readUtf();
        int historyLength = buffer.readVarInt();
        for (int i = 0; i < historyLength; i++) {
            buffer.readVarInt();
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
        return be;
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
        // 快捷栏（物品栏索引 0~8）：一行 y = 232
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X0 + col * INV_STEP, INV_Y0 + 3 * INV_STEP));
        }
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
     * Shift 点击转移：当前无物种槽，暂无转移目标，直接返回空堆（不做任何转移）
     *
     * @param player 操作玩家
     * @param index  被点击的槽位索引
     * @return 空堆（无转移发生）
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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
}
