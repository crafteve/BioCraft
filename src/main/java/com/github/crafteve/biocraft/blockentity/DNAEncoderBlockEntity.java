package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.SequenceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * DNA 编码器方块实体（缓冲池模型）
 * <p>
 * 中心法则链的起点。GUI 由"槽位直扣"重构为"缓冲池"模型：
 * <ul>
 *   <li>碱基槽是吸收器：放入对应碱基立即吸收进缓冲池（槽位清空），
 *       缓冲上限 MAX_BUFFER，吸收由容器内容变化事件驱动（AGENTS.md 事件驱动机制）</li>
 *   <li>缓冲池数据经 Menu 的 ContainerData 同步到 GUI 进度条（每 tick）</li>
 *   <li>合成从缓冲池扣碱基（而非槽位），文本框序列写入 DNA模板的序列组件</li>
 *   <li>方块被破坏时缓冲池换算回碱基物品掉落，不吞库存</li>
 * </ul>
 * 槽位布局：0=A 1=T 2=C 3=G（碱基吸收），4=DNA模板输出
 */
public class DNAEncoderBlockEntity extends MachineBlockEntity {
    /** 碱基输入槽索引：腺嘌呤 A */
    public static final int SLOT_BASE_A = 0;
    /** 碱基输入槽索引：胸腺嘧啶 T */
    public static final int SLOT_BASE_T = 1;
    /** 碱基输入槽索引：胞嘧啶 C */
    public static final int SLOT_BASE_C = 2;
    /** 碱基输入槽索引：鸟嘌呤 G */
    public static final int SLOT_BASE_G = 3;
    /** 输出槽索引：DNA模板 */
    public static final int SLOT_OUTPUT = 4;

    /** 序列长度上限（与 GUI 输入框 maxLength 一致） */
    public static final int MAX_SEQUENCE_LENGTH = 64;

    /** 缓冲池单项上限：单种碱基最多缓存 4096 个 */
    public static final int MAX_BUFFER = 4096;

    /** DNA 允许的碱基字符集 */
    private static final String VALID_BASES = "ACGT";

    /** 碱基字符 -> 缓冲池索引 的映射（与槽位布局同序） */
    private static final Map<Character, Integer> BASE_INDEX_MAP = Map.of(
            'A', SLOT_BASE_A,
            'T', SLOT_BASE_T,
            'C', SLOT_BASE_C,
            'G', SLOT_BASE_G);

    /** 缓冲池：四种碱基各自的库存计数（A/T/C/G），合成与吸收的权威数据源 */
    private final int[] buffer = new int[4];

    /** 吸收防递归标志：吸收内部修改容器会再次触发 setChanged，需拦截 */
    private boolean absorbing;

    /** 最近一次合成结果，Menu 每 tick 读取同步给 GUI（状态码） */
    private SynthesisStatus status = SynthesisStatus.IDLE;

    /**
     * @param pos   方块位置
     * @param state 方块状态
     */
    public DNAEncoderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DNA_ENCODER_BE.get(), pos, state, 5);
    }

    /**
     * 容器内容变化回调：触发碱基吸收
     * <p>
     * 基类 SimpleContainer 的 setChanged 回调链最终到达本方法，
     * 任何对容器的修改（玩家放置/菜单操作/存档标记）都会经过这里
     *
     * @see #absorbSlots()
     */
    @Override
    public void setChanged() {
        super.setChanged();
        absorbSlots();
    }

    /**
     * 容器内容变化时尝试吸收碱基进缓冲池（服务端）
     * <p>
     * 吸收语义：碱基槽中的物品属于对应碱基时，全部数量并入缓冲池（截断到上限），
     * 槽位随即清空——槽位只是"投料口"，库存一律以缓冲池为准
     * <p>
     * 仅在服务端执行：客户端容器是同步副本，客户端执行会导致与服务端分叉
     * （客户端改动会被服务端同步覆盖）；吸收内部修改容器会递归触发本方法，
     * 由 absorbing 标志拦截
     */
    private void absorbSlots() {
        if (absorbing || level == null || level.isClientSide) {
            return;
        }
        absorbing = true;
        try {
            for (int slot = SLOT_BASE_A; slot <= SLOT_BASE_G; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || !isBaseForSlot(slot, stack)) {
                    continue;
                }
                int count = Math.min(stack.getCount(), MAX_BUFFER - buffer[slot]);
                if (count > 0) {
                    buffer[slot] += count;
                }
                // 吸收完清空槽位（含达到上限的情况，多余部分丢弃）
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        } finally {
            absorbing = false;
        }
    }

    /**
     * 校验物品是否为指定碱基槽允许的碱基
     *
     * @param slot  碱基槽索引
     * @param stack 待校验物品
     * @return true 表示匹配
     */
    private static boolean isBaseForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BASE_A -> stack.is(ModItems.byId("adenine").get());
            case SLOT_BASE_T -> stack.is(ModItems.byId("thymine").get());
            case SLOT_BASE_C -> stack.is(ModItems.byId("cytosine").get());
            case SLOT_BASE_G -> stack.is(ModItems.byId("guanine").get());
            default -> false;
        };
    }

    /**
     * 执行合成（仅在服务端调用）
     * <p>
     * 合成流程：校验序列合法性 → 统计碱基需求 → 校验缓冲池充足与输出槽空余
     * → 扣缓冲 → 产出 DNA模板。任一步失败立即返回失败原因，不消耗任何库存
     * <p>
     * 事务式设计：先全量校验后统一扣减，失败时缓冲池与槽位保持原样
     *
     * @param sequence 玩家输入的 DNA 序列（仅含 A/T/C/G）
     * @return 合成结果状态，SUCCESS 表示已产出 DNA模板
     */
    public SynthesisStatus synthesize(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            status = SynthesisStatus.EMPTY_SEQUENCE;
            return status;
        }
        if (sequence.length() > MAX_SEQUENCE_LENGTH) {
            status = SynthesisStatus.INVALID_SEQUENCE;
            return status;
        }

        // 统计序列中各碱基的需求数量
        Map<Character, Integer> needed = new HashMap<>();
        for (int i = 0; i < sequence.length(); i++) {
            char base = sequence.charAt(i);
            if (VALID_BASES.indexOf(base) < 0) {
                status = SynthesisStatus.INVALID_SEQUENCE;
                return status;
            }
            needed.merge(base, 1, Integer::sum);
        }

        // 校验缓冲池库存是否充足
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int index = BASE_INDEX_MAP.get(entry.getKey());
            if (buffer[index] < entry.getValue()) {
                status = SynthesisStatus.INSUFFICIENT_BASE;
                return status;
            }
        }

        // 输出槽必须为空（DNA模板不可堆叠，槽满则无法放置产物）
        if (!inventory.getItem(SLOT_OUTPUT).isEmpty()) {
            status = SynthesisStatus.OUTPUT_FULL;
            return status;
        }

        // 事务提交：先扣缓冲池再产出
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int index = BASE_INDEX_MAP.get(entry.getKey());
            buffer[index] -= entry.getValue();
        }
        inventory.setItem(SLOT_OUTPUT, SequenceItem.create(ModItems.DNA_TEMPLATE.get(), sequence));

        status = SynthesisStatus.SUCCESS;
        return status;
    }

    /**
     * 获取指定碱基的缓冲库存（GUI 进度条渲染用）
     *
     * @param index 碱基索引（0=A 1=T 2=C 3=G）
     * @return 缓冲计数
     */
    public int getBuffer(int index) {
        return buffer[index];
    }

    /**
     * 获取最近一次合成结果状态
     *
     * @return 状态枚举
     */
    public SynthesisStatus getStatus() {
        return status;
    }

    /**
     * 方块实体被移除时把缓冲池换算回碱基物品掉落
     * <p>
     * 容器内容（含未取走的 DNA模板）由 MachineBlock.onRemove 负责掉落，
     * 本方法只处理缓冲池库存；按每堆 64 拆堆，避免生成海量物品实体
     * <p>
     * 1.20.5+ 的移除回调已由 onRemoved 改名为 setRemoved
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level == null || level.isClientSide) {
            return;
        }
        Item[] baseItems = {
                ModItems.byId("adenine").get(),
                ModItems.byId("thymine").get(),
                ModItems.byId("cytosine").get(),
                ModItems.byId("guanine").get()
        };
        for (int i = 0; i < buffer.length; i++) {
            int count = buffer[i];
            while (count > 0) {
                int drop = Math.min(count, 64);
                Containers.dropItemStack(level, getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                        getBlockPos().getZ() + 0.5, new ItemStack(baseItems[i], drop));
                count -= drop;
            }
        }
    }

    /**
     * 存档：缓冲池库存与合成状态码
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("buffer", buffer);
        tag.putInt("status", status.ordinal());
    }

    /**
     * 读档：恢复缓冲池库存与合成状态码
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("buffer")) {
            int[] saved = tag.getIntArray("buffer");
            System.arraycopy(saved, 0, buffer, 0, Math.min(saved.length, buffer.length));
        }
        if (tag.contains("status")) {
            int ordinal = tag.getInt("status");
            if (ordinal >= 0 && ordinal < SynthesisStatus.values().length) {
                status = SynthesisStatus.values()[ordinal];
            }
        }
    }

    /**
     * 获取方块显示名（GUI 标题与玩家反馈共用）
     *
     * @return 方块翻译组件
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.biocraft.dna_encoder");
    }

    /**
     * 创建菜单：DNA 编码器菜单
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param player          打开菜单的玩家
     * @return DNA 编码器菜单实例
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.github.crafteve.biocraft.gui.DNAEncoderMenu(containerId, playerInventory, this);
    }
}
