package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.block.SequenceMachineBlock;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.gui.SequenceMachineMenu;
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

    // ------------------------------------------------------------------
    // 序列机（中心法则信息层）：每台机器一个方块实例，共享 SequenceMachineBlock 类；
    // 共享一个 BE 类型（kind 由方块状态解析）+ 每机器一个 MenuType（共享菜单类）
    // ------------------------------------------------------------------

    /** DNA 编码器（程序文本 → 程序 DNA） */
    public static final DeferredBlock<SequenceMachineBlock> DNA_ENCODER = BLOCKS.register(
            "dna_encoder", () -> new SequenceMachineBlock(SequenceMachineKind.DNA_ENCODER));

    /** 转录仪（DNA → mRNA） */
    public static final DeferredBlock<SequenceMachineBlock> TRANSCRIBER = BLOCKS.register(
            "transcriber", () -> new SequenceMachineBlock(SequenceMachineKind.TRANSCRIBER));

    /** DNA 解旋酶（dsDNA → 2 ssDNA，原子 TRANSFORM） */
    public static final DeferredBlock<SequenceMachineBlock> HELICASE = BLOCKS.register(
            "helicase", () -> new SequenceMachineBlock(SequenceMachineKind.HELICASE));

    public static final DeferredItem<BlockItem> DNA_ENCODER_ITEM = ModItems.ITEMS.register(
            "dna_encoder", () -> new BlockItem(DNA_ENCODER.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> TRANSCRIBER_ITEM = ModItems.ITEMS.register(
            "transcriber", () -> new BlockItem(TRANSCRIBER.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> HELICASE_ITEM = ModItems.ITEMS.register(
            "helicase", () -> new BlockItem(HELICASE.get(), new Item.Properties()));

    /** 共享序列机 BE 类型（kind 由方块状态解析，无需每机器一个 BE） */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SequenceMachineBlockEntity>> SEQUENCE_BE =
            BE_TYPES.register("sequence_machine",
                    () -> BlockEntityType.Builder.of(SequenceMachineBlockEntity::new,
                            DNA_ENCODER.get(), TRANSCRIBER.get(), HELICASE.get()).build(null));

    /** DNA 编码器菜单类型（工厂捕获 kind，避免初始化自引用） */
    public static final DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> DNA_ENCODER_MENU =
            MENUS.register("dna_encoder", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inv, buf) -> new SequenceMachineMenu(SequenceMachineKind.DNA_ENCODER, id, inv, buf)));

    /** 转录仪菜单类型 */
    public static final DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> TRANSCRIBER_MENU =
            MENUS.register("transcriber", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inv, buf) -> new SequenceMachineMenu(SequenceMachineKind.TRANSCRIBER, id, inv, buf)));

    /** 解旋酶菜单类型 */
    public static final DeferredHolder<MenuType<?>, MenuType<SequenceMachineMenu>> HELICASE_MENU =
            MENUS.register("helicase", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id, inv, buf) -> new SequenceMachineMenu(SequenceMachineKind.HELICASE, id, inv, buf)));

    static {
        SequenceMachineKind.DNA_ENCODER.setMenuHolder(DNA_ENCODER_MENU);
        SequenceMachineKind.TRANSCRIBER.setMenuHolder(TRANSCRIBER_MENU);
        SequenceMachineKind.HELICASE.setMenuHolder(HELICASE_MENU);
    }

    /**
     * 序列机 kind → 菜单类型（委托 kind 持有的 holder，新增机器只需在 kind 枚举与本类注册处各加一行，无需改 switch）
     */
    public static MenuType<SequenceMachineMenu> sequenceMenuType(SequenceMachineKind kind) {
        MenuType<SequenceMachineMenu> type = kind.menuType();
        return type != null ? type : DNA_ENCODER_MENU.get();
    }

    private ModBlocks() {
    }
}
