package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.github.crafteve.biocraft.item.MoleculeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创意标签页注册中心，采用多标签页架构
 * <p>
 * 每个功能域一个独立标签页：分子页（物质表驱动）、酶页（酶数据表驱动，
 * 酶蛋白物品）、机器页（统一酶反应腔），
 * 后续纪元的功能页只需追加新的 DeferredHolder
 */
public final class ModCreativeTabs {
    /** 创意标签页注册表 */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BioCraft.MODID);

    /** 生物工艺 · 分子标签页：按物质表顺序展示全部分子物品 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOLECULES = TABS.register(
            "biocraft_molecules",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.biocraft.molecules"))
                    .icon(() -> new ItemStack(ModItems.byId("atp").get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<MoleculeItem> item : ModItems.ordered()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    /** 生物工艺 · 酶标签页：酶蛋白物品（数据驱动注册，按酶数据表顺序展示） */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENZYMES = TABS.register(
            "biocraft_enzymes",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.biocraft.enzymes"))
                    .icon(() -> new ItemStack(ModItems.enzymeOrdered().get(0).get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<EnzymeItem> item : ModItems.enzymeOrdered()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    /** 生物工艺 · 机器标签页：统一酶反应腔方块 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES = TABS.register(
            "biocraft_machines",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.biocraft.machines"))
                    .icon(() -> new ItemStack(ModBlocks.ENZYME_CHAMBER))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ENZYME_CHAMBER_ITEM.get());
                    })
                    .build());

    /** 生物工艺 · 序列标签页：序列物品族（DNA/mRNA/多肽/tRNA/错误折叠蛋白） */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SEQUENCES = TABS.register(
            "biocraft_sequences",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.biocraft.sequences"))
                    .icon(() -> new ItemStack(ModItems.DNA.get()))
                    .displayItems((parameters, output) -> {
                        for (DeferredItem<? extends Item> item : ModItems.sequenceOrdered()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
