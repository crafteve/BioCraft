package com.github.crafteve.biocraft.data;

import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.item.MoleculeDataCalculator;
import com.github.crafteve.biocraft.central.BalanceChecker;
import com.github.crafteve.biocraft.central.BalanceChecker.ReactionTerm;
import com.github.crafteve.biocraft.central.DslParser;
import com.github.crafteve.biocraft.central.DslParser.ParseResult;
import com.github.crafteve.biocraft.central.DslField;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酶设计单完整校验器（MC 侧装配层）
 * <p>
 * 在 program/ 纯核心解析（语法/格式/数量）之上，补齐需要外部数据的校验：
 * <ul>
 *   <li><b>id 解析</b>：基酶必须是已注册酶（enzymes.json）</li>
 *   <li><b>物种存在性</b>：input/output 物种必须是 substances.json 已注册分子/离子</li>
 *   <li><b>化学守恒</b>（方案甲）：完整反应式 = 模板酶反应式 + input/output 新增项
 *       （各 ≤2，系数 1），CDK 算原子组成 → BalanceChecker 判定平衡</li>
 * </ul>
 * 供编码器编辑器预览与未来折叠机/翻译机共用
 */
public final class EnzymeProgramChecker {

    /** 物种 id → SMILES（substances.json，懒加载） */
    private static Map<String, String> speciesSmiles;

    private EnzymeProgramChecker() {
    }

    /**
     * 完整校验：语法 + id 解析 + 物种存在性 + 化学守恒
     *
     * @param parsed 解析器输出（含语法错误）
     * @return 全部错误（语法错误透传 + 装配层错误）
     */
    public static List<DslParser.ProgramError> check(ParseResult parsed) {
        List<DslParser.ProgramError> errors = new ArrayList<>(parsed.errors());
        if (errors.stream().anyMatch(e -> e.code() == DslParser.ProgramErrorCode.MISSING_ID)) {
            return errors; // 缺 id：致命，不继续装配层校验
        }
        // id 解析：基酶必须存在
        String idValue = parsed.program().value(DslField.ID);
        EnzymeFactoryData template = idValue == null ? null : findEnzyme(idValue);
        if (template == null) {
            if (idValue != null) {
                errors.add(new DslParser.ProgramError(DslParser.ProgramErrorCode.ID_NOT_FOUND,
                        parsed.program().lineNumbers().getOrDefault(DslField.ID, 0), idValue));
            }
            return errors;
        }
        // 物种存在性（input/output 所有项）
        for (String species : parsed.program().inputList()) {
            if (!speciesSmiles().containsKey(species)) {
                errors.add(new DslParser.ProgramError(DslParser.ProgramErrorCode.UNKNOWN_SPECIES, 0, species));
            }
        }
        for (String species : parsed.program().outputList()) {
            if (!speciesSmiles().containsKey(species)) {
                errors.add(new DslParser.ProgramError(DslParser.ProgramErrorCode.UNKNOWN_SPECIES, 0, species));
            }
        }
        // 化学守恒（方案甲：完整反应式 = 模板 + 新增项，系数 1）
        List<ReactionTerm> reactants = new ArrayList<>();
        List<ReactionTerm> products = new ArrayList<>();
        for (EnzymeFactoryData.SpeciesSpec spec : template.reactants()) {
            BalanceChecker.SpeciesComposition comp = compositionOf(spec.item());
            if (!comp.isEmpty()) {
                reactants.add(new ReactionTerm(comp, spec.count()));
            }
        }
        for (EnzymeFactoryData.SpeciesSpec spec : template.products()) {
            BalanceChecker.SpeciesComposition comp = compositionOf(spec.item());
            if (!comp.isEmpty()) {
                products.add(new ReactionTerm(comp, spec.count()));
            }
        }
        for (String species : parsed.program().inputList()) {
            BalanceChecker.SpeciesComposition comp = compositionOf(species);
            if (!comp.isEmpty()) {
                reactants.add(new ReactionTerm(comp, 1));
            }
        }
        for (String species : parsed.program().outputList()) {
            BalanceChecker.SpeciesComposition comp = compositionOf(species);
            if (!comp.isEmpty()) {
                products.add(new ReactionTerm(comp, 1));
            }
        }
        if (!BalanceChecker.isBalanced(reactants, products)) {
            errors.add(new DslParser.ProgramError(DslParser.ProgramErrorCode.CHEM_UNBALANCED, 0, ""));
        }
        return errors;
    }

    /** 物种原子组成（SMILES → CDK 计数）；未知/解析失败返回空组成 */
    private static BalanceChecker.SpeciesComposition compositionOf(String speciesId) {
        String smiles = speciesSmiles().get(speciesId);
        if (smiles == null) {
            return BalanceChecker.SpeciesComposition.empty();
        }
        return new BalanceChecker.SpeciesComposition(MoleculeDataCalculator.atomCounts(smiles));
    }

    /**
     * 酶 id 解析：先按正式 id 精确匹配（hexokinase），
     * 失败回退按缩写匹配（HK → hexokinase，玩家更熟悉缩写，大小写不敏感）
     */
    private static EnzymeFactoryData findEnzyme(String id) {
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            if (data.id().equals(id)) {
                return data;
            }
        }
        for (EnzymeFactoryData data : EnzymeFactoryRegistry.ordered()) {
            String abbr = data.abbreviation();
            if (abbr != null && abbr.equalsIgnoreCase(id)) {
                return data;
            }
        }
        return null;
    }

    /** 物种 id → SMILES 映射（substances.json 懒加载缓存） */
    private static synchronized Map<String, String> speciesSmiles() {
        if (speciesSmiles == null) {
            Map<String, String> map = new HashMap<>();
            JsonObject root = SubstanceData.loadRoot();
            JsonArray substances = root.getAsJsonArray("substances");
            for (JsonElement element : substances) {
                JsonObject substance = element.getAsJsonObject();
                String id = substance.get("id").getAsString();
                if (substance.has("smiles") && !substance.get("smiles").isJsonNull()) {
                    map.put(id, substance.get("smiles").getAsString());
                }
            }
            speciesSmiles = map;
        }
        return speciesSmiles;
    }
}
