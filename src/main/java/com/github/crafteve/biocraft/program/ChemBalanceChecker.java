package com.github.crafteve.biocraft.program;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 化学守恒纯逻辑判定器
 * <p>
 * 输入：反应式两侧的反应项（物种原子组成 × 化学计量系数），
 * 判定 ∑反应物原子 = ∑产物原子（C/H/O/N/P 全元素平衡）。
 * 原子组成由 MC 侧装配层（CDK + substances.json）提供，本核心零 MC 依赖。
 * <p>
 * 零 MC 依赖（只 import java.*），同 reaction/seq 门禁
 */
public final class ChemBalanceChecker {

    /** 反应项：物种组成 × 系数 */
    public record ReactionTerm(SpeciesComposition composition, int coefficient) {
    }

    private ChemBalanceChecker() {
    }

    /**
     * 判定完整反应式原子守恒
     *
     * @param reactants 反应物反应项
     * @param products  产物反应项
     * @return true = 守恒（元素种类与数量两侧完全一致）
     */
    public static boolean isBalanced(List<ReactionTerm> reactants, List<ReactionTerm> products) {
        Map<String, Integer> left = new HashMap<>();
        Map<String, Integer> right = new HashMap<>();
        for (ReactionTerm term : reactants) {
            accumulate(left, term);
        }
        for (ReactionTerm term : products) {
            accumulate(right, term);
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            Integer other = right.get(entry.getKey());
            if (other == null || !other.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void accumulate(Map<String, Integer> acc, ReactionTerm term) {
        int coefficient = term.coefficient();
        for (Map.Entry<String, Integer> entry : term.composition().atoms().entrySet()) {
            acc.merge(entry.getKey(), entry.getValue() * coefficient, Integer::sum);
        }
    }
}
