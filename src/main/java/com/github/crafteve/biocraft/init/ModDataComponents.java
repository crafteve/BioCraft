package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义物品数据组件注册中心
 * <p>
 * 1.20.5+ 物品数据全面组件化，自定义 NBT 必须注册 DataComponentType：
 * 序列载体物品（DNA模板/mRNA/新生肽链）的序列内容即存于组件中，
 * 组件声明 persistent（存档）与 networkSynchronized（物品栏同步）编解码器
 */
public final class ModDataComponents {
    /** 数据组件类型注册表 */
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(BioCraft.MODID);

    /** DNA 序列组件：字符串类型，存储 DNA模板等序列物品的序列内容 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DNA_SEQUENCE =
            DATA_COMPONENT_TYPES.register("dna_sequence",
                    () -> DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    private ModDataComponents() {
    }
}
