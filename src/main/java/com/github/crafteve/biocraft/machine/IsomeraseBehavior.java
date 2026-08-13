package com.github.crafteve.biocraft.machine;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import com.github.crafteve.biocraft.reaction.ReactionState;

/**
 * 异构酶策略：可逆快平衡反应（PGI / TPI / PGM / ENO / ALDO / PGK）
 * <p>
 * 停摆规则（策划 1.3）：主底物堆叠数必须 ≥ 8，否则停摆并提示
 * 底物浓度不足——防止玩家塞 1~2 个底物就期待反应启动
 * （异构酶是快平衡反应，底物极少时热力学上无意义地空转）
 * <p>
 * 主底物 = 反应式第一个反应物（酶数据表 reactants 首项，
 * 与引擎的速率项无关，纯策略层判定）
 */
public final class IsomeraseBehavior implements KineticBehavior {
    /** 单例（策略无状态） */
    public static final IsomeraseBehavior INSTANCE = new IsomeraseBehavior();

    /** 主底物最低堆叠数（策划 1.3 定死：8 个） */
    private static final double MIN_SUBSTRATE_STACK = 8.0;

    private IsomeraseBehavior() {
    }

    /**
     * 异构酶判定：主底物堆叠 < 8 时停摆
     *
     * @param data       酶数据档案（首反应物为主底物）
     * @param definition 反应网络档案（物品名 → 浓度下标）
     * @param state      本机反应状态
     * @return 底物充足则正常运行，否则停摆（stallMessage 提示）
     */
    @Override
    public Result evaluate(EnzymeFactoryData data, ReactionDefinition definition, ReactionState state) {
        String mainSubstrate = data.reactants().get(0).item();
        int index = definition.getSpeciesIndex(mainSubstrate);
        if (index < 0) {
            throw new IllegalStateException("主底物不在反应网络中: " + mainSubstrate);
        }
        double concentration = state.getConcentrations()[index];
        if (concentration * 64.0 < MIN_SUBSTRATE_STACK) {
            return Result.stalled(data.stallMessage());
        }
        return Result.running();
    }
}
