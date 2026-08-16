package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.client.MoleculeItemDecorator;
import com.github.crafteve.biocraft.client.MoleculeTooltipComponent;
import com.github.crafteve.biocraft.init.ModItems;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * 分子物品的客户端染色与 tooltip 组件注册器
 * <p>
 * 双层贴图模式下 layer0（内容物层）需要通过 ItemColor 按物质着色，
 * 以区分不同物质；vanilla 的 generated 父模型自带 layer0 tintindex，
 * 因此只需要为所有 MoleculeItem 注册一个统一的颜色处理器即可
 * <p>
 * 键线式结构图组件（MoleculeTooltipComponent）通过
 * RegisterClientTooltipComponentFactoriesEvent 注册工厂：
 * NeoForge 1.21.1 的自定义 TooltipComponent 必须查表转换为
 * ClientTooltipComponent，不注册工厂会抛 Unknown TooltipComponent
 * <p>
 * 本类仅存在于客户端（Dist.CLIENT），不会在服务端加载
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MoleculeColors {

    /**
     * 注册所有分子物品的 ItemColor
     * <p>
     * 颜色处理器只对 layer0 生效（layer1 瓶子层返回 -1 即不染色），
     * 染色值直接取自各 MoleculeItem 上配置的物质颜色
     *
     * @param event 物品颜色注册事件
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor colorHandler = (ItemStack stack, int layer) -> {
            if (layer != 0 || !(stack.getItem() instanceof MoleculeItem molecule)) {
                return -1;
            }
            return molecule.getTintColor();
        };
        event.register(colorHandler,
                ModItems.all().values().stream().map(holder -> holder.get()).toArray(Item[]::new));

        // 酶蛋白物品：layer0 内容物按 EC 类别主题色染色（与酶方块 tint 同色源，形色分离）
        ItemColor enzymeColorHandler = (ItemStack stack, int layer) -> {
            if (layer != 0 || !(stack.getItem() instanceof EnzymeItem enzyme)) {
                return -1;
            }
            return enzyme.getTintColor();
        };
        event.register(enzymeColorHandler,
                ModItems.enzymeAll().values().stream().map(holder -> holder.get()).toArray(Item[]::new));
    }

    /**
     * 注册键线式结构图组件的客户端工厂
     * <p>
     * MoleculeTooltipComponent 同时实现了 TooltipComponent 与
     * ClientTooltipComponent，工厂直接返回自身即可
     *
     * @param event tooltip 组件工厂注册事件
     */
    @SubscribeEvent
    public static void onRegisterTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        // MoleculeTooltipComponent 同时实现了 TooltipComponent 与 ClientTooltipComponent，
        // 工厂返回组件自身即可（不能使用构造器引用：record 构造器参数是 SMILES 字符串）
        event.register(MoleculeTooltipComponent.class, component -> component);
    }

    /**
     * 为全部分子物品注册图标缩写装饰器（左上角显示 ATP/GLUC 等缩写）
     *
     * @param event 物品装饰器注册事件
     */
    @SubscribeEvent
    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        ModItems.all().values().forEach(holder -> event.register(holder.get(), MoleculeItemDecorator.INSTANCE));
        // 酶蛋白物品：缩写装饰器复用（数据源 AbbreviationProvider 接口）
        ModItems.enzymeAll().values().forEach(holder -> event.register(holder.get(), MoleculeItemDecorator.INSTANCE));
    }
}

