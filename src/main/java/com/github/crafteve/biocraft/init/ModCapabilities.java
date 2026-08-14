package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 方块实体 capability 注册中心
 * <p>
 * NeoForge 1.21.1 的 capability 系统改为事件注册制（不再覆写 getCapability），
 * 本类在 mod 事件总线注册酶工厂方块实体的 IItemHandler capability，
 * 供 AUI 的 blockEntity 容器数据源读取（槽位下标 = 物种下标，isItemValid 物种锁定）
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    private ModCapabilities() {
    }

    /**
     * 注册酶工厂的 IItemHandler 方块 capability
     *
     * @param event capability 注册事件
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlocks.ENZYME_FACTORY_BE.get(),
                (factory, side) -> factory.getItemHandler());
    }
}
