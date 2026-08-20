package com.github.crafteve.biocraft.datagen;

import com.google.common.hash.HashCode;
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
 * 序列物品模型生成器（datagen）
 * <p>
 * 为序列物品族生成两层物品模型（与分子物品同款：layer0 容器内容物、
 * layer1 容器瓶身），复用现有分子容器贴图资源（beaker/test_tube/flask），
 * 视觉上序列物品与分子体系一致
 */
public class SequenceItemModelProvider implements DataProvider {

    /** 序列物品 id → 容器贴图前缀（复用 substances.json 的容器定义） */
    private static final Map<String, String> CONTAINERS = Map.of(
            "dna", "flask",
            "dna_single", "test_tube",
            "mrna", "flask",
            "polypeptide", "test_tube",
            "trna_gene", "test_tube",
            "trna", "flask",
            "misfolded_protein", "beaker",
            "rna_polymerase", "beaker");

    private final PackOutput packOutput;
    @SuppressWarnings("unused")
    private final ExistingFileHelper existingFileHelper;

    public SequenceItemModelProvider(PackOutput packOutput, ExistingFileHelper existingFileHelper) {
        this.packOutput = packOutput;
        this.existingFileHelper = existingFileHelper;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (Map.Entry<String, String> entry : CONTAINERS.entrySet()) {
            String id = entry.getKey();
            String container = entry.getValue();
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", modItemTexture(container + "_0"));
            textures.addProperty("layer1", modItemTexture(container + "_1"));
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
                throw new RuntimeException("写出序列物品模型文件失败: " + entry.getKey(), e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private static String modItemTexture(String textureName) {
        return ResourceLocation.fromNamespaceAndPath("biocraft", "item/" + textureName).toString();
    }

    @Override
    public String getName() {
        return "BioCraft 序列物品模型生成";
    }
}
