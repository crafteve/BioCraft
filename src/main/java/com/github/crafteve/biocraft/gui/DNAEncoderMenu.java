package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.SynthesisStatus;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
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
 * DNA 编码器菜单
 * <p>
 * 槽位布局（容器索引）：0=A 1=T 2=C 3=G（碱基吸收槽，槽位限制对应碱基）
 * 4=DNA模板输出（槽位限制仅可放入 dna_template），5-40=玩家背包
 * <p>
 * 同步机制（ContainerData 5 个 int）：
 * <ul>
 *   <li>data[0..3]：四种碱基的缓冲池库存，驱动 GUI 进度条填充</li>
 *   <li>data[4]：合成状态码，驱动 GUI 状态文本</li>
 * </ul>
 * 所有数据每 tick 由 broadcastChanges 从方块实体刷新后广播，
 * 客户端无需额外网络包即可实时看到进度条与状态
 */
public class DNAEncoderMenu extends AbstractContainerMenu {
    /** 容器数据槽 0-3：碱基缓冲池库存 */
    public static final int DATA_BUFFER_A = 0;
    public static final int DATA_BUFFER_T = 1;
    public static final int DATA_BUFFER_C = 2;
    public static final int DATA_BUFFER_G = 3;
    /** 容器数据槽 4：合成状态码 */
    public static final int DATA_STATUS = 4;

    /** 方块实体引用，菜单生命周期内保持存活（玩家与方块距离校验由 stillValid 保证） */
    private final DNAEncoderBlockEntity blockEntity;

    /** 缓冲池与状态码容器数据，服务端写入、客户端每 tick 接收 */
    private final ContainerData data;

    /**
     * 服务端数据包工厂构造（MenuType 注册使用）
     * <p>
     * 从数据包读取方块位置，再按位置从世界中取回方块实体；
     * 方块已不存在时构造空容器菜单，避免崩溃
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param buffer          数据包缓冲（含 openMenu 时写入的 BlockPos）
     */
    public DNAEncoderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, findBlockEntity(playerInventory, buffer));
    }

    /**
     * 常规构造（服务端与客户端共用）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param blockEntity     方块实体（客户端取回或服务端直接持有）
     */
    public DNAEncoderMenu(int containerId, Inventory playerInventory, DNAEncoderBlockEntity blockEntity) {
        super(ModBlocks.DNA_ENCODER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = new SimpleContainerData(5);
        refreshData();
        addDataSlots(data);

        Container container = blockEntity.getContainer();

        // 碱基吸收槽（0-3）：各槽位仅允许放入对应的碱基分子（y=106）
        addSlot(new RestrictedSlot(container, DNAEncoderBlockEntity.SLOT_BASE_A, 26, 106, ModItems.byId("adenine").get()));
        addSlot(new RestrictedSlot(container, DNAEncoderBlockEntity.SLOT_BASE_T, 44, 106, ModItems.byId("thymine").get()));
        addSlot(new RestrictedSlot(container, DNAEncoderBlockEntity.SLOT_BASE_C, 62, 106, ModItems.byId("cytosine").get()));
        addSlot(new RestrictedSlot(container, DNAEncoderBlockEntity.SLOT_BASE_G, 80, 106, ModItems.byId("guanine").get()));
        // 输出槽（4）：仅可放入 DNA模板（合成按钮正下方）
        addSlot(new RestrictedSlot(container, DNAEncoderBlockEntity.SLOT_OUTPUT, 134, 62, ModItems.DNA_TEMPLATE.get()));

        // 玩家背包（5-40）：3×9 主背包 + 1×9 快捷栏（y=130 起）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 130 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 184));
        }
    }

    /**
     * 从数据包读取方块位置并取回方块实体
     *
     * @param playerInventory 玩家物品栏（用于获取客户端/服务端世界）
     * @param buffer          数据包缓冲
     * @return 方块实体；位置处不是 DNA 编码器时返回空容器实体（防御降级）
     */
    private static DNAEncoderBlockEntity findBlockEntity(Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        var pos = buffer.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof DNAEncoderBlockEntity be) {
            return be;
        }
        // 防御降级：方块被破坏后仍打开菜单时给出空实体，避免空指针崩溃
        return new DNAEncoderBlockEntity(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    /**
     * 从方块实体刷新全部容器数据（缓冲池 + 状态码）
     */
    private void refreshData() {
        this.data.set(DATA_BUFFER_A, blockEntity.getBuffer(0));
        this.data.set(DATA_BUFFER_T, blockEntity.getBuffer(1));
        this.data.set(DATA_BUFFER_C, blockEntity.getBuffer(2));
        this.data.set(DATA_BUFFER_G, blockEntity.getBuffer(3));
        this.data.set(DATA_STATUS, blockEntity.getStatus().ordinal());
    }

    /**
     * 每 tick 从方块实体刷新数据再广播
     * <p>
     * 吸收（槽位变化）与合成（网络包触发）都直接改方块实体字段，
     * 本方法保证客户端在不额外发包的情况下数 tick 内看到最新进度条与状态
     */
    @Override
    public void broadcastChanges() {
        refreshData();
        super.broadcastChanges();
    }

    /**
     * 获取方块实体（网络包处理器从玩家当前菜单取回合成目标）
     *
     * @return 方块实体
     */
    public DNAEncoderBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * 获取指定碱基的缓冲库存（客户端进度条渲染与预校验用）
     *
     * @param index 碱基索引（0=A 1=T 2=C 3=G）
     * @return 缓冲计数
     */
    public int getBuffer(int index) {
        return data.get(index);
    }

    /**
     * 菜单有效性校验：玩家距离方块 8 格内才可继续操作
     *
     * @param player 操作玩家
     * @return 是否有效
     */
    @Override
    public boolean stillValid(Player player) {
        return blockEntity.getBlockPos().closerToCenterThan(player.position(), 8.0);
    }

    /**
     * Shift 点击转移逻辑
     * <p>
     * 机器槽（0-4）→ 玩家背包；玩家背包 → 机器槽（受槽位 mayPlace 限制）
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
            if (index < 5) {
                if (!this.moveItemStackTo(original, 5, 41, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(original, 0, 5, false)) {
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
     * 获取当前合成状态（客户端渲染状态文本用）
     *
     * @return 状态枚举（按数据槽序号映射）
     */
    public SynthesisStatus getStatus() {
        int ordinal = data.get(DATA_STATUS);
        if (ordinal < 0 || ordinal >= SynthesisStatus.values().length) {
            return SynthesisStatus.IDLE;
        }
        return SynthesisStatus.values()[ordinal];
    }

    /**
     * 受限槽位：仅允许放入指定物品
     * <p>
     * 用于碱基槽（只收对应碱基分子）与输出槽（只收 DNA模板），
     * 玩家手动放置与 Shift 转移都会经过 mayPlace 校验
     */
    private static class RestrictedSlot extends Slot {
        private final Item acceptedItem;

        RestrictedSlot(Container container, int slot, int x, int y, Item acceptedItem) {
            super(container, slot, x, y);
            this.acceptedItem = acceptedItem;
        }

        /**
         * 槽位放置校验
         *
         * @param stack 待放置的物品堆
         * @return 物品是否为允许的类型
         */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(acceptedItem);
        }
    }
}
