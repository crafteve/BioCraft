package com.github.crafteve.biocraft;

import com.github.crafteve.biocraft.gui.MachineScreen;
import com.github.crafteve.biocraft.init.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BioCraft.MODID, dist = Dist.CLIENT)
// 客户端装配事件统一挂在 mod 总线上（菜单屏幕注册事件均在此总线派发）
@EventBusSubscriber(modid = BioCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class BioCraftClient {

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 将机器菜单类型与对应的屏幕类绑定，打开 GUI 时客户端按 MenuType 实例化屏幕
        // NeoForge 1.21.1 的 MenuScreens.register 为私有方法，必须经本事件注册
        event.register(ModBlocks.ENZYME_CHAMBER_MENU.get(), MachineScreen::new);
    }
}
