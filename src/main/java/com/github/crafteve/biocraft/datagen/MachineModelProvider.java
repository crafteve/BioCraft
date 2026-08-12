package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.MachineType;
import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 机器方块与序列物品模型生成器（datagen）
 * <p>
 * 为每种机器类型生成三类资源：
 * <ul>
 *   <li>方块状态 JSON（无朝向，单一 variant）</li>
 *   <li>方块模型 JSON（cube_all，引用方块贴图）</li>
 *   <li>物品模型 JSON（item/generated，方块物品直接引用方块贴图）</li>
 * </ul>
 * 序列物品（DNA模板等）生成单层 item/generated 模型
 * <p>
 * 模型以手写 JsonObject 形式输出（与 SubstanceModelProvider 风格一致），
 * 新增机器只需在 MachineType 枚举追加条目并放入对应贴图
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
     * 生成全部机器与序列物品的模型资源
     *
     * @param cachedOutput 输出写入器
     * @return 完成信号
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (MachineType type : MachineType.values()) {
            outputs.putAll(machineResources(type));
        }
        outputs.putAll(sequenceItemResources());

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
     * 为单个机器类型生成方块状态/方块模型/物品模型三个文件
     *
     * @param type 机器类型
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> machineResources(MachineType type) {
        Map<Path, JsonObject> outputs = new HashMap<>();
        String blockName = type.getId();
        ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/machine_" + blockName);

        // 方块状态：无朝向，单一 variant 指向方块模型
        JsonObject blockState = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject variant = new JsonObject();
        variant.addProperty("model", blockModel.toString());
        variants.add("", variant);
        blockState.add("variants", variants);

        // 方块模型：cube_all，六面同一贴图
        JsonObject blockModelJson = new JsonObject();
        blockModelJson.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", texture.toString());
        blockModelJson.add("textures", textures);

        // 方块物品模型：item/generated 直接引用方块贴图
        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("parent", "minecraft:item/generated");
        JsonObject itemTextures = new JsonObject();
        itemTextures.addProperty("layer0", texture.toString());
        itemModel.add("textures", itemTextures);

        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/blockstates/" + blockName + ".json"), blockState);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/block/" + blockName + ".json"), blockModelJson);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/item/" + blockName + ".json"), itemModel);
        return outputs;
    }

    /**
     * 为序列载体物品生成物品模型
     * <p>
     * 序列物品数量较少且结构固定，此处直接枚举（与分子物品的
     * 物质表驱动方式不同，序列物品不走 substances.json）
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> sequenceItemResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (String itemName : new String[]{"dna_template"}) {
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "item/" + itemName).toString());
            model.add("textures", textures);
            outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/item/" + itemName + ".json"), model);
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
