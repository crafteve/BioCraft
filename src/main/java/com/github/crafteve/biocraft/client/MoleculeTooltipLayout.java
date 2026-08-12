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

import java.util.List;
import java.util.Optional;

/**
 * tooltip 布局调整（Dist.CLIENT，游戏事件总线）
 * <p>
 * vanilla 机制：手持物品 hover 时，tooltip 首行会显示物品所属创意标签页
 * 的名称（蓝色），与物品名挤在一起。本类将该标签页标题移动到 tooltip
 * 末尾，保持首行为物品名
 */
@EventBusSubscriber(modid = BioCraft.MODID, value = Dist.CLIENT)
public class MoleculeTooltipLayout {

    /**
     * 将"生物工艺 · 分子"标签页标题从 tooltip 首行移动到末尾
     * <p>
     * 仅当首行文本恰好等于本模组标签页标题时处理，
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
        Either<FormattedText, TooltipComponent> first = elements.get(0);
        Optional<FormattedText> left = first.left();
        if (left.isPresent() && left.get() instanceof Component component) {
            String tabTitle = Component.translatable("itemGroup.biocraft.molecules").getString();
            if (tabTitle.equals(component.getString())) {
                elements.remove(0);
                elements.add(first);
            }
        }
    }
}
