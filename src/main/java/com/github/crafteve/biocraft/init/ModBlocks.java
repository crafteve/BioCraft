package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 机器方块与相关注册中心
 * <p>
 * 统一管理四件套：方块（DeferredBlock）、方块实体类型（BlockEntityType）、
 * 菜单类型（MenuType）与方块物品，三者共享同一命名空间与注册生命周期
 * <p>
 * 酶工厂方块时代结束：机器收敛为唯一的"酶反应腔"（enzyme_chamber）——
 * 酶以物品形式插入 0 槽，BE 动态解析当前酶；方块/BE/菜单各只有一个实例
 */
public final class ModBlocks {
    /** 方块注册表 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BioCraft.MODID);

    /** 方块实体类型注册表 */
    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BioCraft.MODID);

    /** 菜单类型注册表 */
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BioCraft.MODID);

    /** 酶反应腔方块（唯一机器方块，酶由 0 槽物品动态解析） */
    public static final DeferredBlock<MachineBlock> ENZYME_CHAMBER = BLOCKS.register(
            "enzyme_chamber",
            () -> new MachineBlock());

    /**
     * 酶反应腔方块物品（手持放置方块用）
     * <p>
     * 方块默认不自动生成 BlockItem，缺失时 Block.asItem() 返回空气物品，
     * 导致合成配方/创意标签页出现 air 错误，必须显式注册
     */
    public static final DeferredItem<BlockItem> ENZYME_CHAMBER_ITEM = ModItems.ITEMS.register(
            "enzyme_chamber",
            () -> new BlockItem(ENZYME_CHAMBER.get(), new Item.Properties()));

    /** 酶反应腔方块实体类型（唯一 BE，酶从 0 槽动态解析） */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnzymeFactoryBlockEntity>> ENZYME_CHAMBER_BE =
            BE_TYPES.register("enzyme_chamber",
                    () -> BlockEntityType.Builder.of(EnzymeFactoryBlockEntity::new, ENZYME_CHAMBER.get())
                            .build(null));

    /**
     * 酶反应腔共享菜单类型（唯一 MenuType）
     * <p>
     * 打开数据包内容：酶 id（空 = 无酶）→ v-t 历史数组（writeClientSideData 写入）→
     * BlockPos（NeoForge 自动写入）
     */
    public static final DeferredHolder<MenuType<?>, MenuType<com.github.crafteve.biocraft.gui.MachineMenu>> ENZYME_CHAMBER_MENU =
            MENUS.register("enzyme_chamber",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            com.github.crafteve.biocraft.gui.MachineMenu::new));

    private ModBlocks() {
    }
}
