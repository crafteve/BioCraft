package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineType;
import com.github.crafteve.biocraft.gui.DNAEncoderMenu;
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
 * 统一管理三件套：方块（DeferredBlock）、方块实体类型（BlockEntityType）、
 * 菜单类型（MenuType），三者共享同一命名空间与注册生命周期
 * <p>
 * 所有机器方块共用 MachineBlock 类，仅通过 MachineType 构造参数区分
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

    /** DNA 编码器方块 */
    public static final DeferredBlock<MachineBlock> DNA_ENCODER = BLOCKS.register(
            MachineType.DNA_ENCODER.getId(),
            () -> new MachineBlock(MachineType.DNA_ENCODER));

    /**
     * DNA 编码器方块物品（手持放置方块用）
     * <p>
     * 方块默认不自动生成 BlockItem，缺失时 Block.asItem() 返回空气物品，
     * 导致合成配方/创意标签页出现 air 错误，必须显式注册
     */
    public static final DeferredItem<BlockItem> DNA_ENCODER_ITEM = ModItems.ITEMS.register(
            MachineType.DNA_ENCODER.getId(),
            () -> new BlockItem(DNA_ENCODER.get(), new Item.Properties()));

    /** DNA 编码器方块实体类型（工厂按类型创建对应实体子类） */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DNAEncoderBlockEntity>> DNA_ENCODER_BE =
            BE_TYPES.register(MachineType.DNA_ENCODER.getId(),
                    () -> BlockEntityType.Builder.of(DNAEncoderBlockEntity::new, DNA_ENCODER.get()).build(null));

    /** DNA 编码器菜单类型（IContainerFactory 三参工厂：容器 id + 物品栏 + 数据包缓冲） */
    public static final DeferredHolder<MenuType<?>, MenuType<DNAEncoderMenu>> DNA_ENCODER_MENU =
            MENUS.register(MachineType.DNA_ENCODER.getId(),
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(DNAEncoderMenu::new));

    private ModBlocks() {
    }
}
