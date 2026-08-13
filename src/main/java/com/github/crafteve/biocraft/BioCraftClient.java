package com.github.crafteve.biocraft;

import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.gui.DNAEncoderScreen;
import com.github.crafteve.biocraft.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BioCraft.MODID, dist = Dist.CLIENT)
// 客户端装配事件统一挂在 mod 总线上（FML 生命周期事件与菜单屏幕注册事件均在此总线派发）
@EventBusSubscriber(modid = BioCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class BioCraftClient {
    public BioCraftClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // 预留客户端装配入口（物品染色等客户端初始化已在各模块独立注册）
        BioCraft.LOGGER.info("HELLO FROM CLIENT SETUP");
        BioCraft.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 将机器菜单类型与对应的屏幕类绑定，打开 GUI 时客户端按 MenuType 实例化屏幕
        // NeoForge 1.21.1 的 MenuScreens.register 为私有方法，必须经本事件注册
        event.register(ModBlocks.DNA_ENCODER_MENU.get(), DNAEncoderScreen::new);
        event.register(ModBlocks.ENZYME_FACTORY_MENU.get(), com.github.crafteve.biocraft.gui.MachineScreen::new);
    }

    /**
     * 酶工厂方块染色注册：按机器类别的主题色整块着色（形色分离的第一版实现）
     * <p>
     * 方块模型为白底 cube + tintindex 0，本处按 MachineCategory 返回类别色；
     * 物品栏染色（ItemColors）同步注册，方块物品在手中/栏内与世界中颜色一致
     *
     * @param event 颜色处理器注册事件
     */
    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (state.getBlock() instanceof MachineBlock machine && machine.getEnzymeFactoryData() != null) {
                MachineCategory category = MachineCategory.byId(machine.getEnzymeFactoryData().category());
                return category.getThemeColor();
            }
            return 0xFFFFFFFF;
        }, ModBlocks.enzymeBlocks().stream().map(b -> b.get()).toArray(net.minecraft.world.level.block.Block[]::new));
    }

    /**
     * 酶工厂方块物品染色注册（与方块染色同色源）
     *
     * @param event 物品颜色处理器注册事件
     */
    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                    && blockItem.getBlock() instanceof MachineBlock machine
                    && machine.getEnzymeFactoryData() != null) {
                MachineCategory category = MachineCategory.byId(machine.getEnzymeFactoryData().category());
                return category.getThemeColor();
            }
            return 0xFFFFFFFF;
        }, ModBlocks.enzymeItems().stream().map(i -> i.get()).toArray(net.minecraft.world.item.Item[]::new));
    }
}
