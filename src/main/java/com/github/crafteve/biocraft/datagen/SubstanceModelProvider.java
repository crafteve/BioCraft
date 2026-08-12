package com.github.crafteve.biocraft.datagen;

import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 分子物品模型生成器（datagen）
 * <p>
 * 读取 substances.json，为每个物质生成一层物品模型 JSON：
 * layer0 = 容器内容物贴图（染色）、layer1 = 容器瓶子贴图
 * <p>
 * 模型直接以手写 JsonObject 形式输出，不依赖 NeoForge 模型 API，
 * 结构为 vanilla generated 父模型 + 两个纹理引用；
 * 父模型自带 layer0 tintindex，因此染色无需在 JSON 中声明
 * <p>
 * 新增物质只需改 JSON 表并重跑 runData，本类无需改动
 */
public class SubstanceModelProvider implements DataProvider {
    private final PackOutput packOutput;
    @SuppressWarnings("unused")
    private final ExistingFileHelper existingFileHelper;

    /**
     * @param packOutput          datagen 输出目录包装
     * @param existingFileHelper  已有资源校验器（预留，供后续轮次校验贴图引用使用）
     */
    public SubstanceModelProvider(PackOutput packOutput, ExistingFileHelper existingFileHelper) {
        this.packOutput = packOutput;
        this.existingFileHelper = existingFileHelper;
    }

    /**
     * 为物质表中每个物质生成模型 JSON
     *
     * @param cachedOutput 输出写入器
     * @return 完成信号
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        JsonObject root = SubstanceData.loadRoot();
        Map<String, JsonObject> containers = indexContainers(root);
        JsonArray substances = root.getAsJsonArray("substances");

        Map<Path, JsonObject> outputs = new HashMap<>();
        for (var element : substances) {
            JsonObject substance = element.getAsJsonObject();
            String id = substance.get("id").getAsString();
            JsonObject container = containers.get(substance.get("container").getAsString());
            if (container == null) {
                throw new IllegalStateException("物质 " + id + " 引用了未定义的容器: " + substance.get("container").getAsString());
            }

            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", modItemTexture(container.get("layer0").getAsString()));
            textures.addProperty("layer1", modItemTexture(container.get("layer1").getAsString()));
            model.add("textures", textures);

            Path path = packOutput.getOutputFolder()
                    .resolve("assets/biocraft/models/item/" + id + ".json");
            outputs.put(path, model);
        }

        for (var entry : outputs.entrySet()) {
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
     * 将容器定义中的容器贴图名包装为 mod 命名空间资源路径
     * <p>
     * 容器定义里存放的是 texture 文件名（如 beaker_0），
     * 模型 JSON 中需要完整的命名空间形式（biocraft:item/beaker_0）
     *
     * @param textureName 贴图文件名
     * @return 命名空间资源路径字符串
     */
    private static String modItemTexture(String textureName) {
        return ResourceLocation.fromNamespaceAndPath("biocraft", "item/" + textureName).toString();
    }

    /**
     * 建立容器 id -> 容器定义 的索引
     *
     * @param root 物质表根对象
     * @return 容器索引表
     */
    private static Map<String, JsonObject> indexContainers(JsonObject root) {
        Map<String, JsonObject> containers = new HashMap<>();
        for (var element : root.getAsJsonArray("containers")) {
            JsonObject container = element.getAsJsonObject();
            containers.put(container.get("id").getAsString(), container);
        }
        return containers;
    }

    /**
     * 返回 Provider 名称，用于 datagen 日志与缓存键
     *
     * @return 名称字符串
     */
    @Override
    public String getName() {
        return "BioCraft 物质模型生成";
    }
}
