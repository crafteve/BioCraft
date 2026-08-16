package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.google.common.hash.HashCode;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredItem;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 机器方块与酶物品模型生成器（datagen）
 * <p>
 * 生成两类资源：
 * <ul>
 *   <li>酶反应腔（统一机器）：方块状态 + 方块模型（cube_all 纯色占位贴图，
 *       后续美化轮次替换为正式贴图）+ 物品模型</li>
 *   <li>酶蛋白物品（数据驱动）：双层 generated 模型（layer0 内容物染色 +
 *       layer1 烧杯瓶身），与分子物品同款视觉语言</li>
 * </ul>
 * 模型以手写 JsonObject 形式输出（与 SubstanceModelProvider 风格一致）
 */
public class MachineModelProvider implements DataProvider {
    private final PackOutput packOutput;

    /**
     * @param packOutput datagen 输出目录包装
     */
    public MachineModelProvider(PackOutput packOutput) {
        this.packOutput = packOutput;
    }

    /**
     * 生成全部机器与酶物品的模型资源
     *
     * @param cachedOutput 输出写入器
     * @return 完成信号
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<Path, JsonObject> outputs = new HashMap<>();
        outputs.putAll(enzymeChamberResources());
        outputs.putAll(enzymeItemResources());

        for (Map.Entry<Path, JsonObject> entry : outputs.entrySet()) {
            byte[] bytes = entry.getValue().toString().getBytes(StandardCharsets.UTF_8);
            try {
                cachedOutput.writeIfNeeded(entry.getKey(), bytes, HashCode.fromBytes(bytes));
            } catch (java.io.IOException e) {
                throw new RuntimeException("写出模型文件失败: " + entry.getKey(), e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 生成全部酶蛋白物品模型（与分子物品同款双层结构）
     * <p>
     * layer0 = 烧杯内容物贴图（按数据表 color 字段 ItemColor 染色）、
     * layer1 = 烧杯瓶身贴图——与分子的视觉语言完全一致，
     * 复用现有 beaker 容器贴图（零新增贴图资源）；
     * 父模型 generated 自带 layer0 tintindex，染色无需在 JSON 中声明
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeItemResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (DeferredItem<EnzymeItem> item : ModItems.enzymeOrdered()) {
            String itemName = item.getId().getPath();
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0",
                    ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "item/beaker_0").toString());
            textures.addProperty("layer1",
                    ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "item/beaker_1").toString());
            model.add("textures", textures);
            outputs.put(packOutput.getOutputFolder()
                    .resolve("assets/biocraft/models/item/" + itemName + ".json"), model);
        }
        return outputs;
    }

    /**
     * 生成酶反应腔方块模型（统一机器，纯色占位贴图）
     * <p>
     * 方块状态（单一 variant）+ 方块模型（cube_all 引用纯色占位贴图）+
     * 物品模型（parent 指方块模型，3D 立体渲染）；无 tint——
     * 纯色贴图自带颜色，等正式贴图轮次再决定是否引入 tint
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeChamberResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        String blockName = ModBlocks.ENZYME_CHAMBER.getId().getPath();
        ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);

        JsonObject blockState = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject variant = new JsonObject();
        variant.addProperty("model", blockModel.toString());
        variants.add("", variant);
        blockState.add("variants", variants);

        JsonObject blockModelJson = new JsonObject();
        blockModelJson.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", blockModel + "_texture");
        blockModelJson.add("textures", textures);

        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("parent", blockModel.toString());

        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/blockstates/" + blockName + ".json"), blockState);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/block/" + blockName + ".json"), blockModelJson);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/item/" + blockName + ".json"), itemModel);
        return outputs;
    }

    /**
     * 返回 Provider 名称，用于 datagen 日志与缓存键
     *
     * @return 名称字符串
     */
    @Override
    public String getName() {
        return "BioCraft 机器模型生成";
    }
}
