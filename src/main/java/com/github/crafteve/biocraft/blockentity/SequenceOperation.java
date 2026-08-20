package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.BioCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * 序列机处理器接口：机器逻辑的唯一抽象（机器 ↔ 处理器硬绑定）
 * <p>
 * BE 只做编排（tick 步进/存档/物化/停摆信号），具体"读模板 → 延伸 → 结算"
 * 由各处理器决定；纯序列算法（编解码/互补/密码子）在 seq/ 包保持零依赖门禁
 */
public interface SequenceOperation {

    /** 单步结果 */
    enum StepResult { ADVANCED, STALLED, DONE }

    /** 催化所需物品 id（null = 不需要催化剂） */
    default String catalystItemId() {
        return null;
    }

    /** 产物槽下标（BE 物化与 IO 门控用） */
    int outputSlot();

    /** 是否可开始/继续（催化剂在位、模板完整等） */
    boolean canStart(SimpleContainer container, SeqStepState state);

    /** 前置初始化（编码器：编码程序文本；转录：从模板构建互补链）——成功返回 true */
    boolean init(SimpleContainer container, SeqStepState state);

    /** 推进一个单元（接 1 碱基），消费单体；返回是否继续 */
    StepResult step(SimpleContainer container, SeqStepState state);

    /** 物化当前链前缀为产物物品（BE 每步调用，刷新产物槽；取走后自动重建） */
    void materialize(SimpleContainer container, SeqStepState state);

    /** 收尾（完成态结算） */
    void finish(SimpleContainer container, SeqStepState state);

    /** 槽位过滤（GUI Slot.mayPlace / 漏斗 canPlaceItem / 管道 isItemValid 同规则） */
    default boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    /** 从单体槽消耗 1 个指定物品（失败返回 false = 缺料停摆） */
    static boolean consumeOne(SimpleContainer container, int slot, String itemId) {
        ItemStack stack = container.getItem(slot);
        if (stack.isEmpty() || !matchesId(stack, itemId)) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            container.setItem(slot, ItemStack.EMPTY);
        }
        return true;
    }

    /** 物品注册名是否匹配（biocraft 命名空间内按注册名比对） */
    static boolean matchesId(ItemStack stack, String itemId) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(BioCraft.MODID) && key.getPath().equals(itemId);
    }
}
