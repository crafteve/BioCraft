package com.github.crafteve.biocraft.client;

import com.github.crafteve.biocraft.BioCraft;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * tooltip 布局调整（Dist.CLIENT，游戏事件总线）
 * <p>
 * vanilla 机制：创造模式物品栏 hover 物品时，物品所属创意标签页的名称
 * （蓝色）会被插入到 tooltip 第二行（物品名之后）。本类将该标签页标题
 * 统一移动到 tooltip 末尾，保持靠前位置为物品名与化学信息
 */
@EventBusSubscriber(modid = BioCraft.MODID, value = Dist.CLIENT)
public class MoleculeTooltipLayout {

    /**
     * 将"生物工艺 · 分子"标签页标题从 tooltip 中段移动到末尾
     * <p>
     * 遍历整个组件列表，移除所有匹配本模组标签页标题的条目并追加到末尾
     * （同一物品可能同时被多个标签页收录，全部处理）；
     * 不影响其他模组与普通物品的 tooltip 布局
     *
     * @param event tooltip 组件收集事件
     */
    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.size() < 2) {
            return;
        }
        String tabTitle = Component.translatable("itemGroup.biocraft.molecules").getString();
        List<Either<FormattedText, TooltipComponent>> moved = new ArrayList<>();
        Iterator<Either<FormattedText, TooltipComponent>> iterator = elements.iterator();
        while (iterator.hasNext()) {
            Either<FormattedText, TooltipComponent> element = iterator.next();
            Optional<FormattedText> left = element.left();
            if (left.isPresent() && left.get() instanceof Component component
                    && tabTitle.equals(component.getString())) {
                moved.add(element);
                iterator.remove();
            }
        }
        elements.addAll(moved);
    }
}
