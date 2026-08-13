package com.github.crafteve.biocraft.machine;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import com.github.crafteve.biocraft.reaction.ReactionState;

/**
 * 限速酶策略：糖酵解通量的"阀门"（HK / PFK / PK）
 * <p>
 * 激活剂门禁（策划 1.3：无 AMP 激活剂则仅 10% 效率）：
 * 当前 activators 数据留空（AMP 物品后置），门禁不触发、恒全速；
 * 门禁代码保留为将来玩法（AMP 物品加入后填 activators 即生效）
 * <p>
 * GUI 的指数进度条形态（红/橙配色）由显示层按 kinetic 分派，
 * 本策略不感知显示
 */
public final class LimitingBehavior implements KineticBehavior {
    /** 单例（策略无状态） */
    public static final LimitingBehavior INSTANCE = new LimitingBehavior();

    /** 无激活剂时的效率折扣（策划 1.3 定死，AMP 玩法启用时使用） */
    private static final double NO_ACTIVATOR_FACTOR = 0.1;

    private LimitingBehavior() {
    }

    /**
     * 限速酶判定：激活剂存在性检查（当前恒通过）
     * <p>
     * 激活剂玩法后置说明：真实糖酵解中 PFK-1 受 AMP 变构激活，
     * 第一版 activators 留空故恒全速；将来启用时需要激活剂槽位
     * （非反应物种，需 BE 额外提供）与存在性判定，此处仅保留规则形状
     *
     * @param data       酶数据档案
     * @param definition 反应网络档案
     * @param state      本机反应状态
     * @return 恒正常运行（activity 1.0），激活剂列表非空时按门禁判定
     */
    @Override
    public Result evaluate(EnzymeFactoryData data, ReactionDefinition definition, ReactionState state) {
        if (!data.activators().isEmpty()) {
            // 激活剂门禁（AMP 玩法后置）：存在性判定，Ki 数值动力学二期
            // 当前无激活剂槽位，此处保守返回折扣，防止数据表填写后静默全速
            return new Result(NO_ACTIVATOR_FACTOR, data.stallMessage());
        }
        return Result.running();
    }
}
