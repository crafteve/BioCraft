package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.SequenceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * DNA 编码器方块实体
 * <p>
 * 中心法则链的起点：玩家在 GUI 输入 DNA 序列文本，机器按序列字符
 * 消耗对应碱基（A/T/C/G），即时产出带序列 NBT 的 DNA模板物品
 * <p>
 * 与转录仪/翻译仪不同，本机器是"即时合成"模式：
 * <ul>
 *   <li>无进度条——合成由网络包触发，校验通过立即扣料产出</li>
 *   <li>槽位布局：0=A 1=T 2=C 3=G（碱基输入），4=DNA模板输出</li>
 *   <li>合成结果状态码通过 Menu 的 ContainerData 同步给 GUI 显示</li>
 * </ul>
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

    /** DNA 允许的碱基字符集 */
    private static final String VALID_BASES = "ACGT";

    /** 碱基字符 -> 输入槽索引 的映射，合成时按序列统计消耗 */
    private static final Map<Character, Integer> BASE_SLOT_MAP = Map.of(
            'A', SLOT_BASE_A,
            'T', SLOT_BASE_T,
            'C', SLOT_BASE_C,
            'G', SLOT_BASE_G);

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
     * 执行合成（仅在服务端调用）
     * <p>
     * 合成流程：校验序列合法性 → 统计碱基需求 → 校验槽位充足与输出槽空余
     * → 扣碱基 → 产出 DNA模板。任一步失败立即返回失败原因，不扣任何材料
     * <p>
     * 设计为"先全量校验后统一扣料"的事务式处理，避免校验中途部分扣料
     * 导致玩家材料损失；校验失败时槽位内容保持原样
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

        // 校验输入槽碱基数量是否充足
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int slot = BASE_SLOT_MAP.get(entry.getKey());
            if (inventory.getItem(slot).getCount() < entry.getValue()) {
                status = SynthesisStatus.INSUFFICIENT_BASE;
                return status;
            }
        }

        // 输出槽必须为空（DNA模板不可堆叠，槽满则无法放置产物）
        if (!inventory.getItem(SLOT_OUTPUT).isEmpty()) {
            status = SynthesisStatus.OUTPUT_FULL;
            return status;
        }

        // 事务提交：先扣碱基再产出
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int slot = BASE_SLOT_MAP.get(entry.getKey());
            inventory.removeItem(slot, entry.getValue());
        }
        inventory.setItem(SLOT_OUTPUT, SequenceItem.create(ModItems.DNA_TEMPLATE.get(), sequence));

        status = SynthesisStatus.SUCCESS;
        return status;
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
     * 存档：额外保存合成状态码（重载后 GUI 恢复显示上次结果）
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("status", status.ordinal());
    }

    /**
     * 读档：恢复合成状态码
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
