package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.data.SubstanceData;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.github.crafteve.biocraft.item.MoleculeCategory;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分子物品注册中心，由物质数据表 substances.json 驱动
 * <p>
 * 在类加载时解析 data/biocraft/molecule/substances.json（打包于 mod jar 内），
 * 为表中每条物质记录动态调用 DeferredRegister 注册一个 MoleculeItem，
 * 并保持 JSON 中的顺序用于创意标签页展示
 * <p>
 * 新增物质只需编辑 JSON 表并重新运行 runData 生成模型资源，
 * 本类代码无需任何改动（数据驱动设计）
 */
public final class ModItems {
    /** 分子物品注册表 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BioCraft.MODID);

    /** 物质 id -> 物品引用的索引表，供后续反应引擎等模块按名称查找物品 */
    private static final Map<String, DeferredItem<MoleculeItem>> MOLECULES = new LinkedHashMap<>();

    /** 按 JSON 顺序排列的物品列表，供创意标签页展示 */
    private static final List<DeferredItem<MoleculeItem>> ORDERED = new ArrayList<>();

    /** 酶 id -> 酶蛋白物品引用（新架构：酶 = 物品，数据驱动注册） */
    private static final Map<String, DeferredItem<EnzymeItem>> ENZYMES = new LinkedHashMap<>();

    /** 按酶数据表顺序排列的酶物品列表，供创意标签页展示 */
    private static final List<DeferredItem<EnzymeItem>> ENZYME_ORDERED = new ArrayList<>();

    static {
        loadSubstances();
        loadEnzymes();
    }

    private ModItems() {
    }

    /**
     * 解析物质表并注册全部分子物品
     * <p>
     * 从 classpath 读取 substances.json（运行与 datagen 环境均在 classpath 内，
     * 无文件系统路径依赖），解析失败直接抛异常快速失败，
     * 避免因数据错误导致静默的空物品注册表
     */
    private static void loadSubstances() {
        JsonObject root = SubstanceData.loadRoot();
        JsonArray substances = root.getAsJsonArray("substances");
        for (JsonElement element : substances) {
            JsonObject substance = element.getAsJsonObject();
            String id = substance.get("id").getAsString();
            String smiles = substance.get("smiles").getAsString();
            String abbreviation = substance.get("abbreviation").getAsString();
            int color = substance.get("color").getAsInt();
            MoleculeCategory category = MoleculeCategory.byId(substance.get("category").getAsString());
            DeferredItem<MoleculeItem> item = ITEMS.register(id, () -> new MoleculeItem(
                    new Item.Properties(), smiles, abbreviation, color, category));
            MOLECULES.put(id, item);
            ORDERED.add(item);
        }
        BioCraft.LOGGER.info("Registered {} molecule items from substance data table", MOLECULES.size());
    }

    /**
     * 按 id 查找物质物品
     *
     * @param id 物质注册名
     * @return 物品引用，不存在时返回 null
     */
    public static DeferredItem<MoleculeItem> byId(String id) {
        return MOLECULES.get(id);
    }

    /**
     * 解析酶数据表并注册全部酶蛋白物品
     * <p>
     * 注册名 = enzyme_&lt;酶id&gt;（前缀避免与过渡期仍存在的酶工厂方块物品
     * id 冲突；未来方块删除后注册名保持稳定，存档物品 id 不迁移）。
     * 堆叠数 = 酶浓度 [E]（stacksTo 64，速率线性倍率由反应腔引擎
     * 活动通道实现，本类只承载物品形态）
     */
    private static void loadEnzymes() {
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            DeferredItem<EnzymeItem> item = ITEMS.register(
                    "enzyme_" + data.id(),
                    () -> new EnzymeItem(new Item.Properties(), data));
            ENZYMES.put(data.id(), item);
            ENZYME_ORDERED.add(item);
        }
        BioCraft.LOGGER.info("Registered {} enzyme items from enzyme data table", ENZYMES.size());
    }

    /**
     * 按酶 id 查找酶蛋白物品
     *
     * @param enzymeId 酶注册名（enzymes.json 的 id）
     * @return 物品引用，不存在时返回 null
     */
    public static DeferredItem<EnzymeItem> enzymeById(String enzymeId) {
        return ENZYMES.get(enzymeId);
    }

    /**
     * 获取全部酶蛋白物品引用（按酶数据表顺序）
     *
     * @return 只读引用表
     */
    public static Map<String, DeferredItem<EnzymeItem>> enzymeAll() {
        return Collections.unmodifiableMap(ENZYMES);
    }

    /**
     * 获取按酶数据表顺序排列的酶物品列表（创意标签页展示用）
     *
     * @return 只读列表
     */
    public static List<DeferredItem<EnzymeItem>> enzymeOrdered() {
        return Collections.unmodifiableList(ENZYME_ORDERED);
    }

    /**
     * 获取全部物质物品引用（按 JSON 表顺序）
     *
     * @return 只读引用表
     */
    public static Map<String, DeferredItem<MoleculeItem>> all() {
        return Collections.unmodifiableMap(MOLECULES);
    }

    /**
     * 获取按 JSON 表顺序排列的物品列表（创意标签页展示用）
     *
     * @return 只读列表
     */
    public static List<DeferredItem<MoleculeItem>> ordered() {
        return Collections.unmodifiableList(ORDERED);
    }
}
