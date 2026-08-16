package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredBlock;
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
 *   <li>酶工厂（数据驱动）：方块状态 + 方块模型（简化白底 cube + tintindex
 *       按数据表 color 字段染色）+ 物品模型，每实例一份</li>
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
        outputs.putAll(enzymeFactoryResources());
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
     * layer0 = 烧杯内容物贴图（按 EC 类别主题色 ItemColor 染色）、
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
     * 生成全部酶工厂方块模型（第一版简化：白底 cube + tint 上色）
     * <p>
     * 简化策略（里程碑 A 已确认）：每实例生成 blockstate（单一 variant）与
     * 方块模型（cube_all 引用原版白混凝土贴图 + tintindex 0，由 BlockColors
     * 按类别主题色染色），物品模型直接引用方块模型（立体渲染）；
     * EC 六类形状拼装模型（双罐/塔式/V 形/环台）在里程碑 E 美化
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeFactoryResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (DeferredBlock<MachineBlock> block : ModBlocks.enzymeBlocks()) {
            String blockName = block.getId().getPath();
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
            textures.addProperty("all", "minecraft:block/white_concrete");
            blockModelJson.add("textures", textures);
            // tintindex 0：整块方块按酶数据表 color 字段染色（BlockColors 注册）
            // 必须提供 from/to 六面立方体坐标，否则模型 JSON 非法（missing from）
            JsonArray elements = new JsonArray();
            JsonObject tintedElement = new JsonObject();
            JsonArray from = new JsonArray();
            from.add(0);
            from.add(0);
            from.add(0);
            tintedElement.add("from", from);
            JsonArray to = new JsonArray();
            to.add(16);
            to.add(16);
            to.add(16);
            tintedElement.add("to", to);
            JsonObject faces = new JsonObject();
            for (String direction : new String[]{"north", "south", "east", "west", "up", "down"}) {
                JsonObject face = new JsonObject();
                face.addProperty("texture", "#all");
                face.addProperty("cullface", direction);
                JsonArray uv = new JsonArray();
                uv.add(0);
                uv.add(0);
                uv.add(16);
                uv.add(16);
                face.add("uv", uv);
                face.addProperty("tintindex", 0);
                faces.add(direction, face);
            }
            tintedElement.add("faces", faces);
            elements.add(tintedElement);
            blockModelJson.add("elements", elements);

            JsonObject itemModel = new JsonObject();
            itemModel.addProperty("parent", blockModel.toString());

            outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/blockstates/" + blockName + ".json"), blockState);
            outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/block/" + blockName + ".json"), blockModelJson);
            outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/item/" + blockName + ".json"), itemModel);
        }
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
