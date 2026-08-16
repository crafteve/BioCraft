package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.data.SubstanceData;
import com.google.gson.JsonObject;

/**
 * 分子类别数据档案（不可变数据容器，由 substances.json 的 categories 段驱动）
 * <p>
 * 取代原 MoleculeCategory 枚举：类别 id/主题色/结构式可用性全部数据表化——
 * 新增分子类别只改 JSON 即可（与酶体系的 EnzymeFactoryData 同构对称），
 * 代码侧只保留"字段名"契约（id/color/structure 的解析语义）
 *
 * @param id        类别注册 id（substances.json 的 category 字段值，如 amino_acid）
 * @param color     类别主题色（ARGB，tooltip 类别徽章色）
 * @param structure 该类别分子是否展示键线式结构图（离子/原子/无机物为 false）
 */
public record MoleculeCategoryData(String id, int color, boolean structure) {

    /**
     * 解析单个类别 JSON 条目（缺失字段直接抛异常快速失败，
     * 与数据防火墙同风格：类别数据错误应在注册期暴露）
     *
     * @param category 类别 JSON 对象（id/color/structure 三字段）
     * @return 类别数据档案
     */
    public static MoleculeCategoryData parse(JsonObject category) {
        return new MoleculeCategoryData(
                category.get("id").getAsString(),
                SubstanceData.parseColor(category.get("color").getAsString()),
                category.get("structure").getAsBoolean());
    }
}
