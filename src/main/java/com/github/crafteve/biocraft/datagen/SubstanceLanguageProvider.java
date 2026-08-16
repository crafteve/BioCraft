package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.data.SubstanceData;
import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 语言文件生成器（datagen）
 * <p>
 * 读取 substances.json 的 name_zn/name_en，为指定语言生成 lang JSON；
 * en_us 与 zh_cn 各实例化一次
 * <p>
 * 除物品显示名外，还输出创意标签页标题、机器与酶显示名、工具提示翻译
 */
public class SubstanceLanguageProvider implements DataProvider {
    private final PackOutput packOutput;
    private final String language;
    private final Map<String, String> translations = new LinkedHashMap<>();

    /**
     * @param packOutput datagen 输出目录包装
     * @param language   Minecraft 语言代码（en_us / zh_cn）
     */
    public SubstanceLanguageProvider(PackOutput packOutput, String language) {
        this.packOutput = packOutput;
        this.language = language;
    }

    /**
     * 收集全部翻译条目并写出语言文件
     * <p>
     * 条目顺序：物品显示名（按物质表顺序）→ 标签页标题 → 机器与酶显示名
     *
     * @param cachedOutput 输出写入器
     * @return 完成信号
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        addTabTranslations();
        addEnzymeTranslations();
        addCompatTranslations();
        addEnzymeTooltipTranslations();
        addItemTranslations();

        JsonObject lang = new JsonObject();
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            lang.addProperty(entry.getKey(), entry.getValue());
        }

        Path path = packOutput.getOutputFolder()
                .resolve("assets/biocraft/lang/" + language + ".json");
        byte[] bytes = lang.toString().getBytes(StandardCharsets.UTF_8);
        try {
            cachedOutput.writeIfNeeded(path, bytes, HashCode.fromBytes(bytes));
        } catch (java.io.IOException e) {
            throw new RuntimeException("写出语言文件失败: " + path, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 添加创意标签页标题与通用 tooltip 翻译
     */
    private void addTabTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("itemGroup.biocraft.molecules",
                zh ? "生物工艺 · 分子" : "BioCraft: Molecules");
        translations.put("itemGroup.biocraft.machines",
                zh ? "生物工艺 · 机器" : "BioCraft: Machines");
        translations.put("itemGroup.biocraft.enzymes",
                zh ? "生物工艺 · 酶" : "BioCraft: Enzymes");
        translations.put("block.biocraft.enzyme_chamber",
                zh ? "酶反应腔" : "Enzyme Chamber");
        translations.put("gui.biocraft.enzyme_chamber.no_enzyme",
                zh ? "放入酶蛋白以启动反应腔" : "Insert enzyme to start the chamber");
        translations.put("tooltip.biocraft.molar_mass",
                zh ? "摩尔质量 %s g/mol" : "Molar Mass %s g/mol");
        translations.put("tooltip.biocraft.smiles_error",
                zh ? "§8结构数据解析失败" : "§8Structure data parse failed");
        translations.put("tooltip.biocraft.show_structure",
                zh ? "按住 Shift 查看结构式" : "Hold Shift to view structure");
        translations.put("tooltip.biocraft.sequence",
                zh ? "序列: %s" : "Sequence: %s");
    }

    /**
     * 添加 JEI/EMI 配方显示层文案翻译
     * <p>
     * 配方类别标题、Km/Keq/ΔG°′/kcat 等展示文案；
     * 与酶数据表文案分开维护，避免与数据驱动条目耦合
     */
    private void addCompatTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("jei.biocraft.fixed_activity",
                zh ? "固定活性物种（不参与速率计算，仅化学计量结算）"
                        : "Fixed-activity species (not in rate law, stoichiometry only)");
        translations.put("jei.biocraft.enzyme_catalyst",
                zh ? "酶催化剂：插入酶反应腔 0 槽，堆叠数 = [E]"
                        : "Enzyme catalyst: insert into chamber slot 0, stack = [E]");
        translations.put("jei.biocraft.keq",
                zh ? "平衡常数 %s" : "Equilibrium Constant %s");
        translations.put("jei.biocraft.delta_g",
                zh ? "ΔG°′ = %s kJ/mol" : "ΔG°′ = %s kJ/mol");
        translations.put("jei.biocraft.kcat",
                zh ? "kcat = %s s⁻¹" : "kcat = %s s⁻¹");
        translations.put("jei.biocraft.temp",
                zh ? "T = %s K" : "T = %s K");
        translations.put("jei.biocraft.count",
                zh ? "数量 ×%s" : "Count ×%s");
        translations.put("jei.biocraft.vmax_f",
                zh ? "正向速率最大值 %s/tick" : "Fwd Max Rate %s/tick");
        translations.put("jei.biocraft.vmax_b",
                zh ? "逆向速率最大值 %s/tick" : "Rev Max Rate %s/tick");
        translations.put("jei.biocraft.energy",
                zh ? "能量 %s kFE/分子 · 容量 %s kFE" : "Energy %s kFE/mol · Capacity %s kFE");
    }

    /**
     * 添加酶物品 tooltip 翻译
     * <p>
     * tooltip.biocraft.enzyme.* 供 EnzymeItem 数据摘要使用；
     * 主题色已数据表化（enzymes.json color 字段），不再需要类别名条目
     */
    private void addEnzymeTooltipTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("tooltip.biocraft.enzyme.reversible",
                zh ? "可逆" : "Reversible");
        translations.put("tooltip.biocraft.enzyme.irreversible",
                zh ? "不可逆" : "Irreversible");
        translations.put("tooltip.biocraft.enzyme.keq",
                zh ? "平衡常数 %s" : "Equilibrium Constant %s");
        translations.put("tooltip.biocraft.enzyme.vmax_f",
                zh ? "正向速率最大值 %s/tick" : "Fwd Max Rate %s/tick");
        translations.put("tooltip.biocraft.enzyme.vmax_b",
                zh ? "逆向速率最大值 %s/tick" : "Rev Max Rate %s/tick");
        translations.put("tooltip.biocraft.enzyme.temp",
                zh ? "最适温度 %s K" : "Optimum Temp %s K");
        translations.put("tooltip.biocraft.enzyme.energy",
                zh ? "能量 %s kFE/分子 · 容量 %s kFE" : "Energy %s kFE/mol · Capacity %s kFE");
    }

    /**
     * 添加酶翻译（数据驱动：酶数据表自带中文 name）
     * <p>
     * 酶显示名是权威数据（策略方提供的显示名），
     * en_us 缺省回退为 id（英文命名后续本地化轮次补充）；
     * 方块条目已随酶工厂方块移除，只保留酶蛋白物品条目
     */
    private void addEnzymeTranslations() {
        boolean zh = "zh_cn".equals(language);
        com.github.crafteve.biocraft.init.EnzymeFactoryRegistry.ordered().forEach(data ->
                translations.put("item.biocraft.enzyme_" + data.id(),
                        zh ? data.nameZn() : data.nameEn()));
    }

    /**
     * 添加全部物品显示名与类别名翻译
     * <p>
     * zh_cn 取 name_zn 字段，en_us 取 name_en 字段；
     * 类别 key 为 category.biocraft.&lt;id&gt;，供 tooltip 类别徽章使用
     */
    private void addItemTranslations() {
        JsonObject root = SubstanceData.loadRoot();
        String field = "zh_cn".equals(language) ? "name_zn" : "name_en";
        for (var element : root.getAsJsonArray("substances")) {
            JsonObject substance = element.getAsJsonObject();
            translations.put("item.biocraft." + substance.get("id").getAsString(),
                    substance.get(field).getAsString());
        }
        for (var element : root.getAsJsonArray("categories")) {
            JsonObject category = element.getAsJsonObject();
            translations.put("category.biocraft." + category.get("id").getAsString(),
                    category.get(field).getAsString());
        }
    }

    /**
     * 返回 Provider 名称，用于 datagen 日志与缓存键
     *
     * @return 名称字符串
     */
    @Override
    public String getName() {
        return "BioCraft 语言生成: " + language;
    }
}
