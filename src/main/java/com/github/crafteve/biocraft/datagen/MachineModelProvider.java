package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 *   <li>酶反应腔（统一机器）：方块状态（FACING 四向旋转）+ 方块模型（多元素：
 *       1 个主元素六面 base 中性贴图 + 10 个贴片元素按 tintindex 分区承载
 *       酶主题色，0=液体/1=灯）+ 物品模型（parent 方块模型，默认 3D 渲染，
 *       ItemColor 返回空机暗灰）</li>
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
        outputs.putAll(sequenceMachineResources());

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
     * 序列机方块资源（MVP 占位：blockstate FACING 四向 + 简单六面立方模型，
     * 复用酶反应腔 base 贴图——正式"显示器式"外观贴图待 texturegen 轮次）
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> sequenceMachineResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        for (String blockName : new String[]{"dna_encoder", "transcriber", "helicase", "loader"}) {
            ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);

            JsonObject blockState = new JsonObject();
            JsonObject variants = new JsonObject();
            String[] facings = {"north", "east", "south", "west"};
            int[] rotations = {0, 90, 180, 270};
            for (int i = 0; i < facings.length; i++) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", blockModel.toString());
                variant.addProperty("y", rotations[i]);
                variants.add("facing=" + facings[i], variant);
            }
            blockState.add("variants", variants);

            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_all");
            JsonObject textures = new JsonObject();
            // 纯几何中心对称单贴图（V4，同 chassis 白箱+黑晶，点缀高饱和色区分）
            String tex;
            if ("dna_encoder".equals(blockName)) tex = "biocraft:block/dna_encoder";
            else if ("transcriber".equals(blockName)) tex = "biocraft:block/transcriber";
            else if ("helicase".equals(blockName)) tex = "biocraft:block/helicase";
            else if ("loader".equals(blockName)) tex = "biocraft:block/loader";
            else tex = "biocraft:block/enzyme_chamber_side";
            textures.addProperty("all", tex);
            textures.addProperty("particle", tex);
            model.add("textures", textures);

            JsonObject itemModel = new JsonObject();
            itemModel.addProperty("parent", blockModel.toString());

            outputs.put(packOutput.getOutputFolder()
                    .resolve("assets/biocraft/blockstates/" + blockName + ".json"), blockState);
            outputs.put(packOutput.getOutputFolder()
                    .resolve("assets/biocraft/models/block/" + blockName + ".json"), model);
            outputs.put(packOutput.getOutputFolder()
                    .resolve("assets/biocraft/models/item/" + blockName + ".json"), itemModel);
        }
        return outputs;
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
     * 生成酶反应腔方块资源：blockstate + 方块模型 + 物品模型（V4 纯几何中心对称）
     * <p>
     * 新管线（2026-08-23）：白箱黑晶单底图六面复用 + 双灰度主题贴片（tint0 中央6高菱形酶窗 / tint1 四角1px灯）
     * 渲染机制：1主元素（6面同 base，无tint）+ 12贴片（6面×2主题，凸出0.002，tintindex分区，灰度×BlockColor）
     * 中心对称 cube_all，六面同图，指示灯/酶窗状态由 BlockColor 每帧染，无 blockstate 变体
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeChamberResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        String blockName = ModBlocks.ENZYME_CHAMBER.getId().getPath();
        ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);

        JsonObject blockState = new JsonObject();
        JsonObject variants = new JsonObject();
        String[] facings = {"north", "east", "south", "west"};
        int[] rotations = {0, 90, 180, 270};
        for (int i = 0; i < facings.length; i++) {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", blockModel.toString());
            variant.addProperty("y", rotations[i]);
            variants.add("facing=" + facings[i], variant);
        }
        blockState.add("variants", variants);

        JsonObject blockModelJson = new JsonObject();
        blockModelJson.addProperty("gui_light", "side");
        blockModelJson.add("display", chamberDisplay());
        blockModelJson.add("textures", chamberTextures());
        JsonArray elements = new JsonArray();
        elements.add(chamberMainElement());
        for (JsonElement patch : chamberPatchElements()) {
            elements.add(patch);
        }
        blockModelJson.add("elements", elements);

        JsonObject itemModel = new JsonObject();
        itemModel.addProperty("parent", blockModel.toString());

        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/blockstates/" + blockName + ".json"), blockState);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/block/" + blockName + ".json"), blockModelJson);
        outputs.put(packOutput.getOutputFolder().resolve("assets/biocraft/models/item/" + blockName + ".json"), itemModel);
        return outputs;
    }

    /**
     * 新贴图引用表：单底图六面复用 + 双灰度主题（窗/灯）
     *
     * @return textures 段的 JSON 对象
     */
    private JsonObject chamberTextures() {
        JsonObject textures = new JsonObject();
        // 单底图六面复用（中心对称 cube_all，白箱+黑晶，无tint）
        textures.addProperty("particle", "biocraft:block/enzyme_chamber");
        textures.addProperty("base", "biocraft:block/enzyme_chamber");
        textures.addProperty("theme_window", "biocraft:block/enzyme_chamber_theme_window");
        textures.addProperty("theme_lamp", "biocraft:block/enzyme_chamber_theme_lamp");
        return textures;
    }

    /**
     * 新主元素：六面同 base（cube_all 语义，cullface 全开）
     *
     * @return 主元素 JSON
     */
    private JsonObject chamberMainElement() {
        JsonObject element = new JsonObject();
        element.add("from", floatArray(0, 0, 0));
        element.add("to", floatArray(16, 16, 16));
        JsonObject faces = new JsonObject();
        faces.add("down", plainFace("#base", "down"));
        faces.add("up", plainFace("#base", "up"));
        faces.add("north", plainFace("#base", "north"));
        faces.add("south", plainFace("#base", "south"));
        faces.add("west", plainFace("#base", "west"));
        faces.add("east", plainFace("#base", "east"));
        element.add("faces", faces);
        return element;
    }

    /**
     * 新贴片：六面×双主题 12贴片（酶窗菱形 + 四角灯），凸出0.002防z-fighting
     * <p>
     * 每面1酶窗+1灯，共12贴片，UV 同 5,5-11,11 中心对称区，透明外不染
     *
     * @return 贴片元素列表
     */
    private JsonArray chamberPatchElements() {
        JsonArray patches = new JsonArray();
        // 酶窗菱形（tint0）六面
        patches.add(patch(5, 5, -0.002f, 11, 11, 0, "north", 5, 5, 11, 11, "#theme_window", 0));
        patches.add(patch(5, 5, 16, 11, 11, 16.002f, "south", 5, 5, 11, 11, "#theme_window", 0));
        patches.add(patch(16, 5, 5, 16.002f, 11, 11, "east", 5, 5, 11, 11, "#theme_window", 0));
        patches.add(patch(-0.002f, 5, 5, 0, 11, 11, "west", 5, 5, 11, 11, "#theme_window", 0));
        patches.add(patch(5, 16, 5, 11, 16.002f, 11, "up", 5, 5, 11, 11, "#theme_window", 0));
        patches.add(patch(5, -0.002f, 5, 11, 0, 11, "down", 5, 5, 11, 11, "#theme_window", 0));
        // 四角灯（tint1）六面
        patches.add(patch(5, 5, -0.002f, 11, 11, 0, "north", 5, 5, 11, 11, "#theme_lamp", 1));
        patches.add(patch(5, 5, 16, 11, 11, 16.002f, "south", 5, 5, 11, 11, "#theme_lamp", 1));
        patches.add(patch(16, 5, 5, 16.002f, 11, 11, "east", 5, 5, 11, 11, "#theme_lamp", 1));
        patches.add(patch(-0.002f, 5, 5, 0, 11, 11, "west", 5, 5, 11, 11, "#theme_lamp", 1));
        patches.add(patch(5, 16, 5, 11, 16.002f, 11, "up", 5, 5, 11, 11, "#theme_lamp", 1));
        patches.add(patch(5, -0.002f, 5, 11, 0, 11, "down", 5, 5, 11, 11, "#theme_lamp", 1));
        return patches;
    }

    /**
     * 物品显示变换（display 段）：复制 vanilla block/block 的标准值
     * <p>
     * 方块模型作为物品模型渲染（物品栏/手持/掉落物）时，vanilla 的
     * block/block 父模型提供这套变换（gui 0.625 缩放 + 旋转、ground 0.25、
     * fixed 0.5、第三人称 0.375 等）——裸 elements 模型缺 display 会以
     * 原始 16×16 尺寸渲染，物品栏里表现为"非默认 3D"的异常方块，
     * 必须显式声明与 vanilla 一致的值
     *
     * @return display 段的 JSON 对象
     */
    private JsonObject chamberDisplay() {
        JsonObject display = new JsonObject();
        display.add("gui", itemTransform(30, 225, 0, 0, 0, 0, 0.625f));
        display.add("ground", itemTransform(0, 0, 0, 0, 3, 0, 0.25f));
        display.add("fixed", itemTransform(0, 0, 0, 0, 0, 0, 0.5f));
        display.add("thirdperson_righthand", itemTransform(75, 45, 0, 0, 2.5f, 0, 0.375f));
        display.add("firstperson_righthand", itemTransform(0, 45, 0, 0, 0, 0, 0.4f));
        display.add("firstperson_lefthand", itemTransform(0, 225, 0, 0, 0, 0, 0.4f));
        return display;
    }

    /**
     * 构造单个物品显示变换（旋转/平移/缩放三元组）
     *
     * @param rx/ry/rz    旋转角（度）
     * @param tx/ty/tz    平移（1/16 格单位）
     * @param scale       三轴统一缩放
     * @return 变换 JSON
     */
    private JsonObject itemTransform(float rx, float ry, float rz, float tx, float ty, float tz, float scale) {
        JsonObject transform = new JsonObject();
        transform.add("rotation", floatArray(rx, ry, rz));
        transform.add("translation", floatArray(tx, ty, tz));
        transform.add("scale", floatArray(scale, scale, scale));
        return transform;
    }

    /**
     * 主元素：整块立方，六面 base 贴图（无 tint），cullface 全部开启
     * <p>
     * 面 UV 方向由 vanilla 约定决定（north: u=x/v=y、south: u=16−x、
     * east: u=z、west: u=16−z、up/down: u=x/v=z），全幅 [0,0,16,16] 无需显式写
     *
     * @return 主元素 JSON
     */
    private JsonObject mainElement() {
        JsonObject element = new JsonObject();
        element.add("from", floatArray(0, 0, 0));
        element.add("to", floatArray(16, 16, 16));
        JsonObject faces = new JsonObject();
        faces.add("down", plainFace("#bottom", "down"));
        faces.add("up", plainFace("#top", "up"));
        faces.add("north", plainFace("#front", "north"));
        faces.add("south", plainFace("#back", "south"));
        faces.add("west", plainFace("#side_m", "west"));
        faces.add("east", plainFace("#side", "east"));
        element.add("faces", faces);
        return element;
    }

    /**
     * 无 tint 的普通面（主元素用）
     *
     * @param texture  贴图引用（# 前缀）
     * @param cullface 剔除面方向
     * @return 面 JSON
     */
    private JsonObject plainFace(String texture, String cullface) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        face.addProperty("cullface", cullface);
        return face;
    }

    /**
     * 全部 13 个贴片元素（主题区，tintindex 0=液体、1=灯）
     * <p>
     * 每个贴片 = 凸出主面 0.001 的薄元素（避免与主面共面 z-fighting），
     * 面 UV 按该面 vanilla 约定映射到 theme 贴图内容区（内容坐标 = 概念稿
     * 主题区坐标，见 ChamberAssets.theme* 系列），采样 1:1 无拉伸。
     * <b>坐标变换（FaceBakery/BlockFaceUV/FaceInfo 源码实证 + 渲染模拟器核对）</b>：
     * <ul>
     *   <li><b>y 翻转</b>：贴图 y 从上往下（y=0 顶部），方块坐标 y 从下往上
     *       （y=0 底部）——元素 y = [16−贴图y1−1, 16−贴图y0]（末尾 −1 补
     *       像素半开区间，否则内容压缩上移 1px）</li>
     *   <li><b>north 面 x 翻转</b>：FaceBakery 把 UV u1（贴图左端）映射到
     *       元素 x_max 端，内容在元素内"右对齐"导致水平镜像——元素
     *       x = [16−贴图x1−1, 16−贴图x0]，UV 不变（贴图左端 → 元素右端
     *       = 东侧 = 观察者左侧，观察者视角不镜像）</li>
     *   <li>其他面（south/east/west/up/down）内容近似对称，x/z 不做镜像
     *       翻转（保持物理位置设计），仅 y 补 −1</li>
     *   <li>底面（down）额外 z 翻转 + UV v 交换（v1 → z_max 南 = 仰视下方）</li>
     * </ul>
     *
     * @return 贴片元素 JSON 列表
     */
    private JsonArray patchElements() {
        JsonArray patches = new JsonArray();
        // 正面凸字形窗口液体（tint0）：上横条 (6,4)-(9,6) + 下主体 (3,7)-(12,10)
        // 两个贴片元素（凸字非矩形，拆两段 UV 各自裁剪 theme_window 对应区）
        patches.add(patch(6, 9, -0.001f, 10, 12, 0, "north", 6, 4, 10, 6, "#theme_window", 0));
        patches.add(patch(3, 5, -0.001f, 13, 9, 0, "north", 3, 7, 13, 10, "#theme_window", 0));
        // 正面两侧指示灯（tint1）：贴图 (3,4)-(4,5) / (11,4)-(12,5)，
        // 用户指定位置；状态灯（黄=等料/红=停摆/绿=运行）由 BE 状态通道染色
        patches.add(patch(11, 10, -0.001f, 13, 12, 0, "north", 3, 4, 5, 6, "#theme_lamp", 1));
        patches.add(patch(3, 10, -0.001f, 5, 12, 0, "north", 11, 4, 13, 6, "#theme_lamp", 1));
        // 背面大法兰内环（tint0）：south 面 x 不做镜像，y = [16−8−1, 16−7]
        patches.add(patch(6, 7, 16, 10, 9, 16.001f, "south", 6, 7, 10, 8, "#theme_flange", 0));
        // 东面：管道/观察孔/灯（z 不做镜像，y 翻转补 −1）
        patches.add(patch(16, 2, 4, 16.001f, 14, 5, "east", 4, 2, 5, 13, "#theme_pipe", 0));
        patches.add(patch(16, 7, 10, 16.001f, 10, 13, "east", 10, 6, 13, 8, "#theme_porthole", 0));
        patches.add(patch(16, 2, 11, 16.001f, 4, 13, "east", 11, 12, 13, 13, "#theme_lamp", 1));
        // 西面：管道/观察孔/灯（块坐标与东面一致，UV 取西面镜像内容区）
        patches.add(patch(-0.001f, 2, 4, 0, 14, 5, "west", 11, 2, 12, 13, "#theme_pipe", 0));
        patches.add(patch(-0.001f, 7, 10, 0, 10, 13, "west", 3, 6, 6, 8, "#theme_porthole", 0));
        patches.add(patch(-0.001f, 2, 11, 0, 4, 13, "west", 3, 12, 5, 13, "#theme_lamp", 1));
        // 顶面观察孔（tint0）：up 面 v=z、v1→z_min 北 = 俯视上方，贴图 y 即 z，无需翻转
        patches.add(patch(6, 16, 6, 10, 16.001f, 10, "up", 6, 6, 10, 10, "#theme_top", 0));
        // 底面接口环（tint0）：down 面 v=z、v1→z_max 南 = 仰视下方，
        // 需 z 翻转（元素 z = [16−贴图y1−1, 16−贴图y0]）+ UV v 交换（v1'=贴图 y1）
        patches.add(patch(7, -0.001f, 7, 9, 0, 9, "down", 7, 9, 9, 7, "#theme_ring", 0));
        return patches;
    }

    /**
     * 构造单个贴片元素
     *
     * @param x0/y0/z0  元素 from 坐标
     * @param x1/y1/z1  元素 to 坐标
     * @param face      面方向（该方向的单面 quad）
     * @param u1/v1/u2/v2 面 UV 裁剪区（theme 贴图内容坐标）
     * @param texture   贴图引用（# 前缀）
     * @param tintIndex tintindex（0=液体、1=灯；BlockColor 按此区分角色）
     * @return 贴片元素 JSON
     */
    private JsonObject patch(float x0, float y0, float z0, float x1, float y1, float z1,
                             String face, int u1, int v1, int u2, int v2, String texture, int tintIndex) {
        JsonObject element = new JsonObject();
        element.add("from", floatArray(x0, y0, z0));
        element.add("to", floatArray(x1, y1, z1));
        JsonObject faces = new JsonObject();
        JsonObject faceJson = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u1);
        uv.add(v1);
        uv.add(u2);
        uv.add(v2);
        faceJson.add("uv", uv);
        faceJson.addProperty("texture", texture);
        faceJson.addProperty("tintindex", tintIndex);
        faces.add(face, faceJson);
        element.add("faces", faces);
        return element;
    }

    /**
     * 构造 float 三元组 JSON 数组
     *
     * @param x/y/z 三个数值
     * @return JSON 数组
     */
    private JsonArray floatArray(float x, float y, float z) {
        JsonArray arr = new JsonArray();
        arr.add(x);
        arr.add(y);
        arr.add(z);
        return arr;
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
