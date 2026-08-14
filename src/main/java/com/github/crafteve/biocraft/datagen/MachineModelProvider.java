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
     * 生成全部酶工厂方块模型（像素拼接分层：外壳 cube + 内容面片凸出覆盖）
     * <p>
     * 分层架构（与贴图脚本 EnzymeMachineScript 配套，规避 solid 渲染通道
     * 透明像素不可靠的坑——曾致"叠加失败黑成一片"）：
     * <ul>
     *   <li>外壳 cube [0,0,0]-[16,16,16]：六面全不透明 shell 贴图，不染色；
     *       被内容覆盖的区域画机身色（防穿帮，反正被盖）</li>
     *   <li>内容面片：方块外侧凸出 0.01~0.04（各面片 z 层错开 0.01 防 z-fight），
     *       贴图与外壳对应区域像素精确拼接（无透明依赖，纯深度测试叠加），
     *       tintindex 0 运行时按类别主题色染色</li>
     *   <li>内容面片清单：正面观察窗液体（window）/ 铭牌底（nameplate，
     *       文字由 BER 渲染）/ 按钮（button）；两侧管道（pipe，east/west 各一片）；
     *       顶面舱口芯（port）</li>
     * </ul>
     * 面片元素坐标 = 64 贴图像素 / 64（MC 模型浮点坐标），贴图 y 向下 → 方块 y 向上
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeFactoryResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (DeferredBlock<MachineBlock> block : ModBlocks.enzymeBlocks()) {
            String blockName = block.getId().getPath();
            ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);

            // 方块状态：facing 四变体（模型 y 旋转，vanilla 惯例 north=0/south=180/east=270/west=90）
            JsonObject blockState = new JsonObject();
            JsonObject variants = new JsonObject();
            int[] rotations = {0, 180, 270, 90};
            String[] facings = {"north", "south", "east", "west"};
            for (int i = 0; i < facings.length; i++) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", blockModel.toString());
                if (rotations[i] != 0) {
                    variant.addProperty("y", rotations[i]);
                }
                variants.add("facing=" + facings[i], variant);
            }
            blockState.add("variants", variants);

            // 模型：外壳 cube + 内容面片
            JsonObject blockModelJson = new JsonObject();
            JsonObject textures = new JsonObject();
            textures.addProperty("shell_front", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_shell_front").toString());
            textures.addProperty("shell_side", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_shell_side").toString());
            textures.addProperty("shell_back", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_shell_back").toString());
            textures.addProperty("shell_top", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_shell_top").toString());
            textures.addProperty("shell_bottom", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_shell_bottom").toString());
            textures.addProperty("window", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_layer_window").toString());
            textures.addProperty("nameplate", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_layer_nameplate").toString());
            textures.addProperty("button", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_layer_button").toString());
            textures.addProperty("pipe", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_layer_pipe").toString());
            textures.addProperty("port", ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/enzyme_layer_port").toString());
            blockModelJson.add("textures", textures);

            JsonArray elements = new JsonArray();
            // 外壳 cube：六面独立贴图，不染色（内容覆盖区画机身色，被面片盖住）
            JsonObject shell = cubeElement(0, 0, 0, 16, 16, 16);
            shell.getAsJsonObject("faces").add("north", face("shell_front", "north"));
            shell.getAsJsonObject("faces").add("south", face("shell_back", "south"));
            shell.getAsJsonObject("faces").add("east", face("shell_side", "east"));
            shell.getAsJsonObject("faces").add("west", face("shell_side", "west"));
            shell.getAsJsonObject("faces").add("up", face("shell_top", "up"));
            shell.getAsJsonObject("faces").add("down", face("shell_bottom", "down"));
            elements.add(shell);
            // 正面观察窗液体面片：贴图区域 x12..40 y28..48（29x21）
            elements.add(panelElement(12f / 64f, 1f - 48f / 64f, -0.02f, 40f / 64f, 1f - 28f / 64f, -0.01f,
                    "window", "north", 0));
            // 正面铭牌底面片：贴图区域 x20..44 y10..16（25x7），文字由 BER 渲染
            elements.add(panelElement(20f / 64f, 1f - 16f / 64f, -0.03f, 44f / 64f, 1f - 10f / 64f, -0.02f,
                    "nameplate", "north", 0));
            // 正面按钮面片：贴图区域 x46..54 y40..48（9x9）
            elements.add(panelElement(46f / 64f, 1f - 48f / 64f, -0.04f, 54f / 64f, 1f - 40f / 64f, -0.03f,
                    "button", "north", 0));
            // 侧面管道面片：两侧各一片，贴图区域 x20..44 y8..56（25x49）
            elements.add(panelElement(16.02f, 1f - 56f / 64f, 0f, 16.04f, 1f - 8f / 64f, 16f,
                    "pipe", "east", 0));
            elements.add(panelElement(-0.04f, 1f - 56f / 64f, 0f, -0.02f, 1f - 8f / 64f, 16f,
                    "pipe", "west", 0));
            // 顶面舱口芯面片：贴图区域 x24..40 y24..40（17x17）
            elements.add(panelElement(24f / 64f, 16.02f, 24f / 64f, 40f / 64f, 16.04f, 40f / 64f,
                    "port", "up", 0));
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
     * 构造一个完整立方体元素（六面占位，调用方逐面覆写）
     *
     * @param x0 左下角横坐标
     * @param y0 左下角纵坐标
     * @param z0 左下角纵坐标（深度）
     * @param x1 右上角横坐标
     * @param y1 右上角纵坐标
     * @param z1 右上角深度坐标
     * @return 元素 JSON
     */
    private static JsonObject cubeElement(float x0, float y0, float z0, float x1, float y1, float z1) {
        JsonObject element = new JsonObject();
        element.add("from", vec3(x0, y0, z0));
        element.add("to", vec3(x1, y1, z1));
        element.add("faces", new JsonObject());
        return element;
    }

    /**
     * 构造一个单面面片元素（仅指定方向有贴图，用于方块外侧凸出覆盖）
     * <p>
     * 贴面位于凸出侧（north 面在 z0、east 面在 x1、west 面在 x0、up 面在 y1），
     * uv 恒整贴图 [0,0,16,16]；其余五面省略（不渲染）
     *
     * @param x0        面片左边界
     * @param y0        面片下边界
     * @param z0        面片近侧 z
     * @param x1        面片右边界
     * @param y1        面片上边界
     * @param z1        面片远侧 z
     * @param texture   贴图键（textures 中的 # 引用名）
     * @param faceDir   贴图所在方向（north/east/west/up）
     * @param tintIndex 染色下标，-1 表示不染色
     * @return 元素 JSON
     */
    private static JsonObject panelElement(float x0, float y0, float z0, float x1, float y1, float z1,
                                           String texture, String faceDir, int tintIndex) {
        JsonObject element = cubeElement(x0, y0, z0, x1, y1, z1);
        element.getAsJsonObject("faces").add(faceDir, face(texture, null, tintIndex));
        return element;
    }

    /**
     * 构造一个方块面的 face JSON
     *
     * @param texture   贴图键（textures 中的 # 引用名）
     * @param cullface  剔除方向，null 表示不剔除
     * @param tintIndex 染色下标，-1 表示不染色
     * @return face JSON
     */
    private static JsonObject face(String texture, String cullface, int tintIndex) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", "#" + texture);
        if (cullface != null) {
            face.addProperty("cullface", cullface);
        }
        JsonArray uv = new JsonArray();
        uv.add(0);
        uv.add(0);
        uv.add(16);
        uv.add(16);
        face.add("uv", uv);
        if (tintIndex >= 0) {
            face.addProperty("tintindex", tintIndex);
        }
        return face;
    }

    /**
     * 构造一个面 JSON（无染色）
     *
     * @param texture  贴图键
     * @param cullface 剔除方向
     * @return face JSON
     */
    private static JsonObject face(String texture, String cullface) {
        return face(texture, cullface, -1);
    }

    /**
     * 构造三维坐标数组
     *
     * @param x x 坐标
     * @param y y 坐标
     * @param z z 坐标
     * @return [x, y, z] JSON 数组
     */
    private static JsonArray vec3(float x, float y, float z) {
        JsonArray arr = new JsonArray();
        arr.add(x);
        arr.add(y);
        arr.add(z);
        return arr;
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
