package com.github.crafteve.biocraft;

import com.github.crafteve.biocraft.init.ModCreativeTabs;
import com.github.crafteve.biocraft.init.ModItems;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * BioCraft 主类，只做纯装配工作
 * <p>
 * 注册各 init 注册中心（物品、创意标签页）与配置，
 * 不承载任何具体功能实现，具体注册逻辑见各 init 类
 */
@Mod(BioCraft.MODID)
public class BioCraft {
    /** 模组 ID */
    public static final String MODID = "biocraft";

    /** 模组日志器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 主类构造器，FML 自动注入 mod 事件总线与容器
     *
     * @param modEventBus  mod 生命周期事件总线
     * @param modContainer mod 容器，用于注册配置
     */
    public BioCraft(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
