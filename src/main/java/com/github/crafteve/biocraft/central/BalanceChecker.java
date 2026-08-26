package com.github.crafteve.biocraft.central;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 化学守恒对账工具（原 program/ChemBalanceChecker + SpeciesComposition 合并）
 * <p>
 * 纯逻辑判定：∑反应物原子 == ∑产物原子（C/H/O/N/P 全元素），
 * 原子组成由 MC 侧 data/EnzymeProgramChecker 用 CDK 从 substances.json 算好传入，
 * 本核零 MC 依赖
 */
public final class BalanceChecker {

    /** 物种原子组成（元素 → 计数，如 C6H12O6 → {C:6,H:12,O:6}） */
    public record SpeciesComposition(Map<String, Integer> atoms) {

        /** 空组成（未知物种/解析失败兜底） */
        public static SpeciesComposition empty() {
            return new SpeciesComposition(Collections.emptyMap());
        }

        /** 是否无任何原子（解析失败标记） */
        public boolean isEmpty() {
            return atoms.isEmpty();
        }
    }

    /** 反应项：物种组成 × 系数 */
    public record ReactionTerm(SpeciesComposition composition, int coefficient) {
    }

    private BalanceChecker() {
    }

    /**
     * 判定完整反应式原子守恒
     *
     * @param reactants 反应物反应项
     * @param products  产物反应项
     * @return true = 守恒
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
