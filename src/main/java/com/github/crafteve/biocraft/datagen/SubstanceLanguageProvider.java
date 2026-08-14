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
        addMachineTranslations();
        addEnzymeTranslations();
        addCompatTranslations();
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
     * 添加机器方块、GUI 与合成状态文案翻译
     * <p>
     * 酶工厂方块的显示名不在此处：酶数据表自带中文 name 字段，
     * 由 EnzymeLanguageProvider 按数据驱动生成翻译键
     */
    private void addMachineTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("block.biocraft.dna_encoder",
                zh ? "DNA 编码器" : "DNA Encoder");
        translations.put("item.biocraft.dna_template",
                zh ? "DNA 模板" : "DNA Template");
        translations.put("gui.biocraft.synthesize",
                zh ? "合成" : "Synthesize");
        translations.put("gui.biocraft.button.promoter",
                zh ? "启动子" : "Promoter");
        translations.put("gui.biocraft.button.terminator",
                zh ? "终止子" : "Terminator");
        translations.put("gui.biocraft.sequence_label",
                zh ? "DNA 序列" : "DNA Sequence");
        translations.put("gui.biocraft.status.idle",
                zh ? "输入序列后点击合成" : "Type a sequence and click Synthesize");
        translations.put("gui.biocraft.status.success",
                zh ? "合成成功" : "Synthesis successful");
        translations.put("gui.biocraft.status.empty_sequence",
                zh ? "序列为空" : "Sequence is empty");
        translations.put("gui.biocraft.status.invalid_sequence",
                zh ? "序列含非法字符" : "Sequence contains invalid characters");
        translations.put("gui.biocraft.status.insufficient_base",
                zh ? "碱基不足" : "Insufficient bases");
        translations.put("gui.biocraft.status.output_full",
                zh ? "输出槽已满" : "Output slot is full");
    }

    /**
     * 添加 JEI/EMI 配方显示层文案翻译
     * <p>
     * 配方类别标题、动力学变体、Km/Keq/ΔG°′/kcat 等展示文案；
     * 与酶数据表文案分开维护，避免与数据驱动条目耦合
     */
    private void addCompatTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("jei.biocraft.enzyme_factory",
                zh ? "酶工厂" : "Enzyme Factory");
        translations.put("jei.biocraft.kinetic.limiting",
                zh ? "限速酶" : "Rate-limiting Enzyme");
        translations.put("jei.biocraft.kinetic.isomerase",
                zh ? "异构酶" : "Isomerase");
        translations.put("jei.biocraft.kinetic.oxido_lyase",
                zh ? "氧化裂解酶" : "Oxidoreductase/Lyase");
        translations.put("jei.biocraft.fixed_activity",
                zh ? "固定活性物种（不参与速率计算，仅化学计量结算）"
                        : "Fixed-activity species (not in rate law, stoichiometry only)");
        translations.put("jei.biocraft.km",
                zh ? "Km = %s mM" : "Km = %s mM");
        translations.put("jei.biocraft.keq",
                zh ? "Keq = %s" : "Keq = %s");
        translations.put("jei.biocraft.delta_g",
                zh ? "ΔG°′ = %s kJ/mol" : "ΔG°′ = %s kJ/mol");
        translations.put("jei.biocraft.kcat",
                zh ? "kcat = %s s⁻¹" : "kcat = %s s⁻¹");
        translations.put("jei.biocraft.temp",
                zh ? "T = %s K" : "T = %s K");
        translations.put("jei.biocraft.activator",
                zh ? "激活剂: %s" : "Activators: %s");
        translations.put("jei.biocraft.count",
                zh ? "数量 ×%s" : "Count ×%s");
    }

    /**
     * 添加酶工厂方块翻译（数据驱动：酶数据表自带中文 name）
     * <p>
     * 酶方块显示名是权威数据（策略方提供的显示名），
     * en_us 缺省回退为 id（英文命名后续本地化轮次补充）
     */
    private void addEnzymeTranslations() {
        boolean zh = "zh_cn".equals(language);
        com.github.crafteve.biocraft.init.EnzymeFactoryRegistry.ordered().forEach(data ->
                translations.put("block.biocraft." + data.id(),
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
