package com.github.crafteve.biocraft.datagen;

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
 * 除物品显示名外，还输出创意标签页标题与配置界面翻译
 * （原模板语言文件中的配置 key 由此处接管，模板 en_us.json 移除）
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
     * 条目顺序：物品显示名（按物质表顺序）→ 标签页标题 → 配置界面文案
     *
     * @param cachedOutput 输出写入器
     * @return 完成信号
     */
    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        addConfigTranslations();
        addTabTranslations();
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
        translations.put("tooltip.biocraft.molar_mass",
                zh ? "▸ 摩尔质量 %s g/mol" : "▸ Molar Mass %s g/mol");
    }

    /**
     * 添加配置界面翻译（沿用原模板文案，防止配置界面 key 丢失）
     */
    private void addConfigTranslations() {
        boolean zh = "zh_cn".equals(language);
        translations.put("biocraft.configuration.title", zh ? "生物工艺配置" : "BioCraft Configs");
        translations.put("biocraft.configuration.section.biocraft.common.toml",
                zh ? "生物工艺配置" : "BioCraft Configs");
        translations.put("biocraft.configuration.section.biocraft.common.toml.title",
                zh ? "生物工艺配置" : "BioCraft Configs");
        translations.put("biocraft.configuration.items", zh ? "物品列表" : "Item List");
        translations.put("biocraft.configuration.logDirtBlock", zh ? "记录泥土方块" : "Log Dirt Block");
        translations.put("biocraft.configuration.magicNumberIntroduction", zh ? "魔法数字文案" : "Magic Number Text");
        translations.put("biocraft.configuration.magicNumber", zh ? "魔法数字" : "Magic Number");
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
