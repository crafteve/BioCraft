package com.github.crafteve.biocraft.machine;

import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import com.github.crafteve.biocraft.reaction.ReactionState;

/**
 * 酶动力学行为策略接口：计算本 tick 的活性因子与停摆原因
 * <p>
 * 三种变体（策划 1.3 硬性规则，与 enzymes.json 的 kinetic 字段一一对应）：
 * <ul>
 *   <li>LIMITING 限速酶：红/橙 GUI、指数进度条，激活剂门禁（AMP 玩法后置，当前留空恒全速）</li>
 *   <li>ISOMERASE 异构酶：中性灰、约 1 秒，要求主底物堆叠 ≥8</li>
 *   <li>OXIDO_LYASE 氧化裂解酶：紫/蓝双阶段，氧化还原辅因子耗尽时停摆报警</li>
 * </ul>
 * 策略层只做"条件判定"：返回 activity（0~1）与停摆原因文案键，
 * 引擎不参与任何策略判断（引擎只接收 activity 值），
 * GUI 的进度条形态差异（指数/双阶段/卡死 50%）由显示层按 kinetic 分派
 * <p>
 * 本包为纯 Java（仅依赖 reaction 包），可脱离游戏单测
 */
public interface KineticBehavior {

    /**
     * 判定结果：活性因子与停摆原因
     *
     * @param activity   活性因子（0~1，乘入引擎速率；0 表示停摆）
     * @param stallReason 停摆原因文案（取自酶数据表 stallMessage），
     *                    null 表示正常运行
     */
    record Result(double activity, String stallReason) {
        /** 正常运行结果（activity 1.0） */
        public static Result running() {
            return new Result(1.0, null);
        }

        /** 停摆结果（activity 0） */
        public static Result stalled(String stallReason) {
            return new Result(0.0, stallReason);
        }
    }

    /**
     * 计算本 tick 的活性与停摆原因
     *
     * @param data       酶数据档案（含 stallMessage 文案与反应结构）
     * @param definition 反应网络档案（物种下标换算）
     * @param state      本机反应状态（浓度读取）
     * @return 判定结果
     */
    Result evaluate(EnzymeFactoryData data, ReactionDefinition definition, ReactionState state);

    /**
     * 按动力学变体名获取策略实现（enzymes.json 的 kinetic 字段）
     *
     * @param kinetic 变体名（LIMITING/ISOMERASE/OXIDO_LYASE）
     * @return 对应策略实现
     */
    static KineticBehavior forKinetic(String kinetic) {
        return switch (kinetic) {
            case "LIMITING" -> LimitingBehavior.INSTANCE;
            case "ISOMERASE" -> IsomeraseBehavior.INSTANCE;
            case "OXIDO_LYASE" -> OxidoLyaseBehavior.INSTANCE;
            default -> throw new IllegalArgumentException("未知动力学变体: " + kinetic);
        };
    }
}
