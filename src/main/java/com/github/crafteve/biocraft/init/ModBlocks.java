package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineType;
import com.github.crafteve.biocraft.gui.DNAEncoderMenu;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 机器方块与相关注册中心
 * <p>
 * 统一管理四件套：方块（DeferredBlock）、方块实体类型（BlockEntityType）、
 * 菜单类型（MenuType）与方块物品，三者共享同一命名空间与注册生命周期
 * <p>
 * 两类机器：
 * <ul>
 *   <li>DNA 编码器：MachineType 枚举手动注册（中心法则链原始机器）</li>
 *   <li>酶工厂：由 EnzymeFactoryRegistry 数据驱动循环注册，全部实例共享
 *       一个 BlockEntityType（实体从方块取回酶数据）与一个 MenuType
 *       （数据包缓冲传 enzymeId，M3 实现 Menu）</li>
 * </ul>
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

    /**
     * 酶工厂共享菜单类型（全部酶实例一个 MenuType）
     * <p>
     * 打开数据包内容：BlockPos（NeoForge 自动写入）→ 酶 id → v-t 历史数组
     * （由 EnzymeFactoryBlockEntity.writeClientSideData 写入）
     */
    public static final DeferredHolder<MenuType<?>, MenuType<com.github.crafteve.biocraft.gui.MachineMenu>> ENZYME_FACTORY_MENU =
            MENUS.register("enzyme_factory",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            com.github.crafteve.biocraft.gui.MachineMenu::new));

    /** 全部酶工厂方块（数据驱动注册，注册名 = 酶 id） */
    private static final List<DeferredBlock<MachineBlock>> ENZYME_BLOCKS = new ArrayList<>();

    /** 全部酶工厂方块物品 */
    private static final List<DeferredItem<BlockItem>> ENZYME_ITEMS = new ArrayList<>();

    static {
        registerEnzymeFactories();
    }

    /**
     * 酶工厂共享方块实体类型：全部酶实例注册进同一类型
     * <p>
     * 实体工厂从 BlockState 取回本机酶数据（MachineBlock 持有），
     * 因此无需为每个酶单独注册 BE 类型
     * <p>
     * 注意：1.21.1 的 ticker 机制不在 BlockEntityType 侧（该类无 getTicker），
     * 而是 EntityBlock 接口的 getTicker 方法（MachineBlock 覆写），见方块类
     * <p>
     * 声明顺序依赖：ENZYME_BLOCKS/ENZYME_ITEMS 列表必须在上方静态块
     * registerEnzymeFactories() 填充完成后声明本字段，保证注册解析期
     * 的 lambda 读到完整方块列表
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnzymeFactoryBlockEntity>> ENZYME_FACTORY_BE =
            BE_TYPES.register("enzyme_factory",
                    () -> BlockEntityType.Builder.of(
                                    EnzymeFactoryBlockEntity::new,
                                    ENZYME_BLOCKS.stream().map(DeferredBlock::get).toArray(Block[]::new))
                            .build(null));

    private ModBlocks() {
    }

    /**
     * 循环注册全部酶工厂方块（数据驱动，无需逐个手写）
     * <p>
     * 注册名 = 酶 id（lower_snake_case），方块持有酶数据档案；
     * 方块物品注册进物品注册表（BlockItem 需显式注册，缺失会 air 报错）
     */
    private static void registerEnzymeFactories() {
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            DeferredBlock<MachineBlock> block = BLOCKS.register(
                    data.id(), () -> new MachineBlock(data));
            ENZYME_BLOCKS.add(block);
            ENZYME_ITEMS.add(ModItems.ITEMS.register(data.id(),
                    () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }

    /**
     * 获取全部酶工厂方块（datagen 模型生成与 tint 注册用）
     *
     * @return 只读列表
     */
    public static List<DeferredBlock<MachineBlock>> enzymeBlocks() {
        return Collections.unmodifiableList(ENZYME_BLOCKS);
    }

    /**
     * 获取全部酶工厂方块物品（创意标签页展示用）
     *
     * @return 只读列表
     */
    public static List<DeferredItem<BlockItem>> enzymeItems() {
        return Collections.unmodifiableList(ENZYME_ITEMS);
    }
}
