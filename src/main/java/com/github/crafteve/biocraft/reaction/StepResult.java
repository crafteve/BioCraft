package com.github.crafteve.biocraft.reaction;

/**
 * 一次引擎步进的成果报告（不可变记录）
 * <p>
 * 三个通量均为活性缩放与边界缩放之后的有效值，直接可供显示：
 * <ul>
 *   <li>fluxForward：正向通量（底物消耗方向）</li>
 *   <li>fluxReverse：逆向通量（产物回推方向，不可逆反应恒 0）</li>
 *   <li>fluxNet：净通量 = 正向 − 逆向，负值表示逆向净流</li>
 * </ul>
 * GUI 速率条（策划 3.4 REACT 页）显示 fluxNet 即可；浓度本身由
 * ReactionState 原地更新，方块实体自行做前后差分桥接槽位，
 * 故本记录不携带浓度快照
 */
public record StepResult(double fluxForward, double fluxReverse, double fluxNet) {
}
