package com.github.crafteve.biocraft;

import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModCreativeTabs;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.network.ModNetwork;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * BioCraft 主类，只做纯装配工作
 * <p>
 * 注册各 init 注册中心（物品、方块、菜单、数据组件、创意标签页），
 * 不承载任何具体功能实现，具体注册逻辑见各 init 类
 */
@Mod(BioCraft.MODID)
public class BioCraft {
    /** 模组 ID */
    public static final String MODID = "biocraft";

    /** 模组日志器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 主类构造器，FML 自动注入 mod 事件总线
     *
     * @param modEventBus mod 生命周期事件总线
     */
    public BioCraft(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BE_TYPES.register(modEventBus);
        ModBlocks.MENUS.register(modEventBus);
        ModDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
