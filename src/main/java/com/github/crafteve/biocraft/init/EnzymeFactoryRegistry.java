package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 酶工厂注册中心，由酶数据表 enzymes.json 驱动
 * <p>
 * 在类加载时解析 data/biocraft/enzyme/enzymes.json（打包于 mod jar 内），
 * 为每条酶记录构建 EnzymeFactoryData（数据容器，纯字段），
 * 并保持 JSON 顺序用于方块注册与创意标签页展示
 * <p>
 * 解析失败直接抛异常快速失败（与 ModItems 同款策略）：
 * 数据错误应在启动期暴露，而不是静默产出空注册表
 * <p>
 * 数值溯源：所有热力学/动力学数值来自仓库《糖酵解热力学数据库》md 文档
 * （eQuilibrator I=0.25 的 ΔG°′ 换算 Keq；BRENDA 人源几何中位数；
 * ΔH 仅 HK 有量热实测 ≈ −64 kJ/mol，其余未测量为 null）
 */
public final class EnzymeFactoryRegistry {
    /** 酶 id -> 数据档案索引表（JSON 顺序，注册方块与查表共用） */
    private static final Map<String, EnzymeFactoryData> ENZYMES = new LinkedHashMap<>();

    /** 按 JSON 顺序排列的酶数据列表（创意标签页展示用） */
    private static final List<EnzymeFactoryData> ORDERED = new ArrayList<>();

    static {
        loadEnzymes();
    }

    private EnzymeFactoryRegistry() {
    }

    /**
     * 解析酶数据表并构建全部酶档案
     * <p>
     * 解析完成后对每条数据执行引擎构建（ReactionDefinition.build 的
     * 全部断言：Km 正值/Keq 正值/可逆产物完整），任何一条失败即整体快速失败，
     * 保证进入游戏的数据全部通过数据防火墙
     */
    private static void loadEnzymes() {
        JsonObject root = readEnzymesJson();
        JsonArray enzymes = root.getAsJsonArray("enzymes");
        for (JsonElement element : enzymes) {
            JsonObject enzyme = element.getAsJsonObject();
            EnzymeFactoryData data = parseEnzyme(enzyme);
            // 数据防火墙：构建引擎模拟器跑一遍全部断言，失败即抛异常
            data.buildSimulator();
            ENZYMES.put(data.id(), data);
            ORDERED.add(data);
        }
        BioCraft.LOGGER.info("Registered {} enzyme factories from enzyme data table", ENZYMES.size());
    }

    /**
     * 解析单条酶记录为数据档案
     * <p>
     * 字段契约（与策划确认的定稿）：
     * <ul>
     *   <li>reaction.reactants/products：{item, count, km} 数组，item 直接是
     *       物品注册名；不可逆反应产物与固定活性物种（H₂O/H⁺）km 填 0</li>
     *   <li>keq 直填（由 ΔG°′ 换算，引擎绝不缩放）；deltaH 未测量为 null</li>
     * </ul>
     *
     * @param enzyme 单条酶 JSON 对象
     * @return 数据档案（纯字段容器）
     */
    private static EnzymeFactoryData parseEnzyme(JsonObject enzyme) {
        String id = enzyme.get("id").getAsString();
        String nameZn = enzyme.get("name_zn").getAsString();
        String nameEn = enzyme.get("name_en").getAsString();
        String abbreviation = enzyme.get("abbreviation").getAsString();
        String category = enzyme.get("category").getAsString();

        JsonObject reaction = enzyme.getAsJsonObject("reaction");
        boolean reversible = reaction.get("reversible").getAsBoolean();
        List<EnzymeFactoryData.SpeciesSpec> reactants = parseSpecies(reaction.getAsJsonArray("reactants"));
        List<EnzymeFactoryData.SpeciesSpec> products = parseSpecies(reaction.getAsJsonArray("products"));

        double keq = enzyme.get("keq").getAsDouble();
        Double deltaH = enzyme.has("deltaH") && !enzyme.get("deltaH").isJsonNull()
                ? enzyme.get("deltaH").getAsDouble() : null;
        double kcat = enzyme.get("kcat").getAsDouble();
        double tempOptimum = enzyme.get("tempOptimum").getAsDouble();

        return new EnzymeFactoryData(id, nameZn, nameEn, abbreviation, category,
                reactants, products, reversible, keq, deltaH, kcat, tempOptimum,
                reactants.size(), products.size());
    }

    /**
     * 解析物种条目数组（{item, count, km}）
     *
     * @param array 物种条目 JSON 数组
     * @return 物种条目列表（保持 JSON 顺序，即槽位顺序）
     */
    private static List<EnzymeFactoryData.SpeciesSpec> parseSpecies(JsonArray array) {
        List<EnzymeFactoryData.SpeciesSpec> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject spec = element.getAsJsonObject();
            result.add(new EnzymeFactoryData.SpeciesSpec(
                    spec.get("item").getAsString(),
                    spec.get("count").getAsInt(),
                    spec.get("km").getAsDouble()));
        }
        return result;
    }

    /**
     * 从 mod jar 的 classpath 读取酶数据表
     *
     * @return 解析后的 JSON 根对象
     */
    private static JsonObject readEnzymesJson() {
        String path = "/data/biocraft/enzyme/enzymes.json";
        InputStream stream = EnzymeFactoryRegistry.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("未找到酶数据表: " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return net.minecraft.util.GsonHelper.parse(reader);
        } catch (Exception e) {
            throw new IllegalStateException("解析酶数据表失败: " + path, e);
        }
    }

    /**
     * 按 id 查找酶数据档案
     *
     * @param id 酶注册名
     * @return 数据档案，不存在时返回 null
     */
    public static EnzymeFactoryData byId(String id) {
        return ENZYMES.get(id);
    }

    /**
     * 获取全部酶数据档案（按 JSON 表顺序）
     *
     * @return 只读映射
     */
    public static Map<String, EnzymeFactoryData> all() {
        return Collections.unmodifiableMap(ENZYMES);
    }

    /**
     * 获取按 JSON 表顺序排列的酶列表（方块循环注册与创意标签页展示用）
     *
     * @return 只读列表
     */
    public static List<EnzymeFactoryData> ordered() {
        return Collections.unmodifiableList(ORDERED);
    }
}
