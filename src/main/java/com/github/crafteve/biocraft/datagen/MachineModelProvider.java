package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.blockentity.MachineType;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 机器方块与序列物品模型生成器（datagen）
 * <p>
 * 生成三类资源：
 * <ul>
 *   <li>原始机器（MachineType 枚举）：方块状态 + 方块模型（cube_bottom_top
 *       三面分离贴图）+ 物品模型，每类型一份</li>
 *   <li>酶工厂（数据驱动）：方块状态 + 方块模型（简化白底 cube + tintindex
 *       按类别色染色）+ 物品模型，每实例一份</li>
 *   <li>序列物品：item/generated 单层模型</li>
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
        outputs.putAll(enzymeFactoryResources());
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
            JsonArray elements = new JsonArray();
            // tintindex 0：整块方块按类别主题色染色（BlockColors 注册）
            JsonObject tintedElement = new JsonObject();
            JsonObject faces = new JsonObject();
            for (String direction : new String[]{"north", "south", "east", "west", "up", "down"}) {
                JsonObject face = new JsonObject();
                face.addProperty("uv", "[0.0, 0.0, 16.0, 16.0]");
                JsonArray tint = new JsonArray();
                tint.add(0);
                face.add("tintindex", tint);
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
     * 为单个机器类型生成方块状态/方块模型/物品模型三个文件
     *
     * @param type 机器类型
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> machineResources(MachineType type) {
        Map<Path, JsonObject> outputs = new HashMap<>();
        String blockName = type.getId();
        ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);
        // 三面分离贴图：顶面设备面板（屏幕/指示灯）、侧面金属外壳、底面底座
        ResourceLocation topTexture = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/machine_" + blockName + "_top");
        ResourceLocation sideTexture = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/machine_" + blockName + "_side");
        ResourceLocation bottomTexture = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/machine_" + blockName + "_bottom");

        // 方块状态：无朝向，单一 variant 指向方块模型
        JsonObject blockState = new JsonObject();
        JsonObject variants = new JsonObject();
        JsonObject variant = new JsonObject();
        variant.addProperty("model", blockModel.toString());
        variants.add("", variant);
        blockState.add("variants", variants);

        // 方块模型：cube_bottom_top，顶/侧/底三面不同贴图（比 cube_all 更有立体观感）
        JsonObject blockModelJson = new JsonObject();
        blockModelJson.addProperty("parent", "minecraft:block/cube_bottom_top");
        JsonObject textures = new JsonObject();
        textures.addProperty("top", topTexture.toString());
        textures.addProperty("side", sideTexture.toString());
        textures.addProperty("bottom", bottomTexture.toString());
        blockModelJson.add("textures", textures);

        // 方块物品模型：parent 直接指向方块模型——物品栏/手持时以 3D 视角
        // 渲染立体方块（vanilla blockitem 统一风格，与石头/草方块一致），
        // 纹理由方块模型自带，无需在此重复声明
        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("parent", blockModel.toString());

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
