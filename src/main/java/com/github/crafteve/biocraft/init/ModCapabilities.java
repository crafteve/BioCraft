package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 机器 capability 注册中心（NeoForge 能力系统）
 * <p>
 * 为酶工厂方块实体注册 IItemHandler（工业 IO），
 * 让 AE2/Mekanism/Pipez 等管道模组通过能力系统接入；
 * 原版漏斗走 Container 接口（不受影响，照旧工作）
 * <p>
 * 实现说明：
 * <ul>
 *   <li>side 参数忽略：所有面同权（全槽位可进可出，不做方向限制，
 *       与策划"产物槽也可输入输出"的方案一致）</li>
 *   <li>返回懒加载单例：每 BE 一个适配器实例，避免管道每 tick
 *       查询 capability 时重复分配对象</li>
 *   <li>物种过滤/浓度回写全部在 {@code EnzymeFactoryItemHandler}
 *       内实现，本类只做注册</li>
 * </ul>
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {

    private ModCapabilities() {
    }

    /**
     * 注册酶工厂方块实体的物品能力（mod 事件总线）
     *
     * @param event 能力注册事件
     */
    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlocks.ENZYME_FACTORY_BE.get(),
                (EnzymeFactoryBlockEntity be, net.minecraft.core.Direction side) -> be.getItemHandler());
    }
}
