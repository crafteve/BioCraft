package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.init.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 序列载体物品基类（DNA模板 / mRNA / 新生肽链共用）
 * <p>
 * 与 MoleculeItem 不同，序列物品没有固定化学结构（序列内容可变），
 * 因此不走 substances.json 物质表注册，序列本身存储在物品数据组件中
 * （1.20.5+ 物品数据组件化，自定义数据必须注册 DataComponentType）
 * <p>
 * 设计要点：
 * <ul>
 *   <li>不可堆叠——每个物品代表一段独立序列，堆叠会丢失序列区分</li>
 *   <li>tooltip 显示序列内容，超长序列自动换行（每行长度由常量控制）</li>
 *   <li>无序列组件的空物品也能被 tooltip 安全显示（显示空序列占位）</li>
 * </ul>
 */
public class SequenceItem extends Item {
    /** tooltip 中每行显示的序列字符数，超过则换行 */
    private static final int CHARS_PER_LINE = 24;

    /**
     * @param properties 物品属性（注册时需设置 maxStackSize 为 1）
     */
    public SequenceItem(Properties properties) {
        super(properties);
    }

    /**
     * 组装 tooltip：显示"序列："前缀与序列内容，超长自动换行
     * <p>
     * 序列是本模组序列链（中心法则）的核心数据，tooltip 直接展示内容，
     * 便于玩家手工核对转录/翻译的输入输出
     *
     * @param stack       当前物品堆
     * @param context     tooltip 上下文
     * @param tooltip     待填充的 tooltip 行列表
     * @param tooltipFlag tooltip 标志
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        String sequence = getSequence(stack);
        if (sequence.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.biocraft.sequence", "—")
                    .withStyle(style -> style.withColor(0x9E9E9E)));
            return;
        }
        // 超长序列按固定宽度截断换行，避免 tooltip 单行过长
        for (int start = 0; start < sequence.length(); start += CHARS_PER_LINE) {
            int end = Math.min(start + CHARS_PER_LINE, sequence.length());
            String line = sequence.substring(start, end);
            if (start == 0) {
                tooltip.add(Component.translatable("tooltip.biocraft.sequence", line)
                        .withStyle(style -> style.withColor(0xFFD700)));
            } else {
                tooltip.add(Component.literal(line).withStyle(style -> style.withColor(0xFFD700)));
            }
        }
    }

    /**
     * 读取物品堆中的序列内容
     * <p>
     * 空物品或缺失组件时返回空字符串（而非 null），避免调用方空指针
     *
     * @param stack 物品堆
     * @return 序列字符串（可能为空字符串）
     */
    public static String getSequence(ItemStack stack) {
        String sequence = stack.get(ModDataComponents.DNA_SEQUENCE.get());
        return sequence == null ? "" : sequence;
    }

    /**
     * 创建带指定序列的物品堆
     * <p>
     * 生产方（DNA编码器）产出序列物品时的统一入口，保证组件键名一致
     *
     * @param item     序列物品类型
     * @param sequence 序列内容（DNA 用 A/T/C/G）
     * @return 带序列组件的物品堆
     */
    public static ItemStack create(Item item, String sequence) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.DNA_SEQUENCE.get(), sequence);
        return stack;
    }
}
