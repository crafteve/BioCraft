package com.github.crafteve.biocraft.machine;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import com.github.crafteve.biocraft.reaction.ReactionState;

/**
 * 氧化裂解酶策略：氧化还原步（GAPDH 等，NAD⁺ 耦合）
 * <p>
 * 停摆规则（策划 1.3）：氧化还原辅因子（NAD⁺ 类）耗尽时停摆报警
 * （GUI 显示"卡死 50% 并报警"是显示层行为，本策略只判定停摆原因）。
 * NAD⁺ 耗尽时反应在数学上天然停转（多底物乘积 v∝[NAD⁺]），
 * 本策略的意义在于给出明确的停摆原因文案（而非玩家看到静默停转）
 * <p>
 * 氧化还原辅因子集合：{nad_plus, nadh}——若任一反应物命中集合且
 * 其浓度耗尽（≤0），判定停摆。集合可随 NADP 玩法扩展
 */
public final class OxidoLyaseBehavior implements KineticBehavior {
    /** 单例（策略无状态） */
    public static final OxidoLyaseBehavior INSTANCE = new OxidoLyaseBehavior();

    /** 氧化还原辅因子集合（作为反应物出现时受耗尽判定） */
    private static final String[] REDOX_COFACTORS = {"nad_plus", "nadh"};

    private OxidoLyaseBehavior() {
    }

    /**
     * 氧化裂解判定：氧化还原辅因子耗尽时停摆
     *
     * @param data       酶数据档案
     * @param definition 反应网络档案
     * @param state      本机反应状态
     * @return 辅因子充足则正常运行，否则停摆（stallMessage 提示）
     */
    @Override
    public Result evaluate(EnzymeFactoryData data, ReactionDefinition definition, ReactionState state) {
        for (EnzymeFactoryData.SpeciesSpec reactant : data.reactants()) {
            for (String cofactor : REDOX_COFACTORS) {
                if (reactant.item().equals(cofactor)) {
                    int index = definition.getSpeciesIndex(cofactor);
                    if (index >= 0 && state.getConcentrations()[index] <= 0.0) {
                        return Result.stalled(data.stallMessage());
                    }
                }
            }
        }
        return Result.running();
    }
}
