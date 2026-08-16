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
     * 生成酶反应腔方块资源：blockstate + 方块模型 + 物品模型
     * <p>
     * 渲染机制（设计文档《酶容器方块概念设计_2026-08-16.md》）：
     * <ul>
     *   <li>主元素：六面 base 中性贴图（front/side/back/top/bottom，西面用
     *       side 镜像——西面 UV u=16−z 与东面互为镜像，镜像贴图让管道在
     *       东西两侧都位于前端，物理镜像对称），无 tint</li>
     *   <li>贴片元素：每主题区一个凸出主面 0.001 的薄元素（BlockElement 校验
     *       允许 [-16,32]，已核对反编译源码），独立 tintindex（0=液体、1=灯），
     *       UV 裁剪对应 theme 贴图内容区，灰度反照率 × BlockColor 酶色 =
     *       "带左上光照的主题色"；无酶时 BlockColor 返回暗灰 → 空机外观，
     *       同一模型零 blockstate 表达状态</li>
     *   <li>blockstate：FACING 四向（y 0/90/180/270），放置时正面朝玩家</li>
     * </ul>
     *
     * @return 输出路径到 JSON 内容的映射
     */
    private Map<Path, JsonObject> enzymeChamberResources() {
        Map<Path, JsonObject> outputs = new HashMap<>();
        String blockName = ModBlocks.ENZYME_CHAMBER.getId().getPath();
        ResourceLocation blockModel = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "block/" + blockName);

        // 方块状态：FACING 四向旋转（正面贴图在 north，朝玩家放置靠 getStateForPlacement）
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
        elements.add(mainElement());
        for (JsonElement patch : patchElements()) {
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
     * 模型贴图引用表：base 中性贴图 6 张（西面用 side 镜像）+ theme 灰度贴图 7 张
     * <p>
     * particle 单独指向正面贴图（破坏方块粒子效果用；vanilla 缺省取第一个
     * 面纹理，显式声明避免歧义）
     *
     * @return textures 段的 JSON 对象
     */
    private JsonObject chamberTextures() {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "biocraft:block/enzyme_chamber_front");
        textures.addProperty("front", "biocraft:block/enzyme_chamber_front");
        textures.addProperty("side", "biocraft:block/enzyme_chamber_side");
        textures.addProperty("side_m", "biocraft:block/enzyme_chamber_side_mirrored");
        textures.addProperty("back", "biocraft:block/enzyme_chamber_back");
        textures.addProperty("top", "biocraft:block/enzyme_chamber_top");
        textures.addProperty("bottom", "biocraft:block/enzyme_chamber_bottom");
        textures.addProperty("theme_window", "biocraft:block/enzyme_chamber_theme_window");
        textures.addProperty("theme_pipe", "biocraft:block/enzyme_chamber_theme_pipe");
        textures.addProperty("theme_porthole", "biocraft:block/enzyme_chamber_theme_porthole");
        textures.addProperty("theme_flange", "biocraft:block/enzyme_chamber_theme_flange");
        textures.addProperty("theme_top", "biocraft:block/enzyme_chamber_theme_top");
        textures.addProperty("theme_ring", "biocraft:block/enzyme_chamber_theme_ring");
        textures.addProperty("theme_lamp", "biocraft:block/enzyme_chamber_theme_lamp");
        return textures;
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
     * 主题区坐标，见 ChamberAssets.theme* 系列），采样 1:1 无拉伸：
     * <ul>
     *   <li>**y 翻转关键约定**：贴图坐标 y 从上往下（y=0 顶部），而方块
     *       模型坐标 y 从下往上（y=0 底部）——贴片元素的 from/to.y 必须
     *       用 16−贴图 y（元素 y0 = 16−贴图y1、y1 = 16−贴图y0），否则
     *       贴图上方的内容（灯/窗口横条）会被放到方块下部（实测"灯在
     *       下方、上方黑色空穴"根因）；UV 的 v 保持贴图 y 不变（FaceBakery
     *       把 v1 映射到元素顶 = 方块高 y 端 = 贴图顶部）</li>
     *   <li>正面（north，u=x/v=y）：凸字形窗口液体两段（上横条贴图
     *       (6,4)-(9,6)、下主体 (3,7)-(12,10)）、两侧状态灯 2×2
     *       贴图 (3,4)/(11,4)</li>
     *   <li>背面（south，u=16−x）：大法兰内环 (6,7)-(9,8)</li>
     *   <li>东面（east，u=z）：管道 (4,2)-(4,13)、观察孔 (10,6)-(12,8)、灯 (11,12)</li>
     *   <li>西面（west，u=16−z）：管道 (11,2)-(11,13)、观察孔 (3,6)-(5,8)、灯 (3,12)
     *       ——西面 UV 镜像，贴片元素块坐标与东面一致（物理镜像对称），
     *       UV 取 theme 贴图西面内容区</li>
     *   <li>顶面（up，u=x/v=z）：中央观察孔 (6,6)-(9,9)——z 轴即贴图 y
     *       方向（v1 → z_min 北 = 俯视上方），无需翻转</li>
     *   <li>底面（down，u=x/v=z）：中央接口环 (7,7)-(8,8)——v1 → z_max 南
     *       = 仰视下方，需 z 翻转（16−z）+ UV v 交换</li>
     * </ul>
     *
     * @return 贴片元素 JSON 列表
     */
    private JsonArray patchElements() {
        JsonArray patches = new JsonArray();
        // 正面凸字形窗口液体（tint0）：上横条 (6,4)-(9,6) + 下主体 (3,7)-(12,10)
        // 两个贴片元素（凸字非矩形，拆两段 UV 各自裁剪 theme_window 对应区）；
        // 元素 y = 16 − 贴图 y（贴图坐标与方块坐标 y 方向相反，见类 javadoc）
        patches.add(patch(6, 10, -0.001f, 10, 12, 0, "north", 6, 4, 10, 6, "#theme_window", 0));
        patches.add(patch(3, 6, -0.001f, 13, 9, 0, "north", 3, 7, 13, 10, "#theme_window", 0));
        // 正面两侧指示灯（tint1）：2×2 贴图 (3,4)-(4,5) / (11,4)-(12,5)，
        // 用户指定位置；状态灯（黄=等料/红=停摆/绿=运行）由 BE 状态通道染色
        patches.add(patch(3, 11, -0.001f, 5, 12, 0, "north", 3, 4, 5, 6, "#theme_lamp", 1));
        patches.add(patch(11, 11, -0.001f, 13, 12, 0, "north", 11, 4, 13, 6, "#theme_lamp", 1));
        // 背面大法兰内环（tint0）
        patches.add(patch(6, 8, 16, 10, 9, 16.001f, "south", 6, 7, 10, 8, "#theme_flange", 0));
        // 东面：管道/观察孔/灯（元素 y = 16 − 贴图 y）
        patches.add(patch(16, 3, 4, 16.001f, 14, 5, "east", 4, 2, 5, 13, "#theme_pipe", 0));
        patches.add(patch(16, 8, 10, 16.001f, 10, 13, "east", 10, 6, 13, 8, "#theme_porthole", 0));
        patches.add(patch(16, 3, 11, 16.001f, 4, 13, "east", 11, 12, 13, 13, "#theme_lamp", 1));
        // 西面：管道/观察孔/灯（块坐标与东面一致，UV 取西面镜像内容区）
        patches.add(patch(-0.001f, 3, 4, 0, 14, 5, "west", 11, 2, 12, 13, "#theme_pipe", 0));
        patches.add(patch(-0.001f, 8, 10, 0, 10, 13, "west", 3, 6, 6, 8, "#theme_porthole", 0));
        patches.add(patch(-0.001f, 3, 11, 0, 4, 13, "west", 3, 12, 5, 13, "#theme_lamp", 1));
        // 顶面观察孔（tint0）：up 面 v=z、v1→z_min 北 = 俯视上方，贴图 y 即 z，无需翻转
        patches.add(patch(6, 16, 6, 10, 16.001f, 10, "up", 6, 6, 10, 10, "#theme_top", 0));
        // 底面接口环（tint0）：down 面 v=z、v1→z_max 南 = 仰视下方，
        // 需 z 翻转（元素 z = 16 − 贴图 y）+ UV v 交换（v1'=贴图 y1）
        patches.add(patch(7, -0.001f, 8, 9, 0, 9, "down", 7, 9, 9, 7, "#theme_ring", 0));
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
