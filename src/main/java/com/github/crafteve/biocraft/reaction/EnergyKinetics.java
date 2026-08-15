package com.github.crafteve.biocraft.reaction;

/**
 * 能量（FE）物种的纯数学换算：容量/镜像/结算，全部零 MC 依赖
 * <p>
 * 设计背景（与策划确认的方案）：FE 与 H₂O/H⁺ 一样是固定活性物种
 * （FIXED_ACTIVITY_SPECIES），直接写在 enzymes.json 的反应物/产物里，
 * 化学计量系数即"每分子 FE 数"（ATP 水解酶产物 fe count=100 →
 * 每分子发电 100 kFE）。方块实体注册时以 isEnergySpecies 拦截该 id，
 * 不建物品槽、改建能量存储；GUI/JEI/工具提示的能量数值一律调用
 * 本类方法，显示层禁止复制换算公式（AGENTS.md 2.6 欠账 23 同款约定）
 * <p>
 * 单位约定：
 * <ul>
 *   <li>容量与存量：FE（1 kFE = 1000 FE，KFE_SCALE 为容量缩放旋钮）</li>
 *   <li>引擎浓度镜像：存量/容量 × MAX_CONCENTRATION——满能量 = 满浓度，
 *       引擎边界缩放（boundaryScale）在存量满时把反应通量缩到 0，
 *       实现"满能量停转、抽走恢复"的回压，零额外代码</li>
 *   <li>每 tick 结算：fluxNet × stoich × 64 × TICK_SECONDS × KFE_SCALE，
 *       正 = 充能（产物侧）、负 = 消耗（反应物侧）</li>
 * </ul>
 */
public final class EnergyKinetics {
    /** 能量物种在酶数据表中的注册名（enzymes.json 直接书写） */
    public static final String FE_SPECIES_ID = "fe";

    /**
     * kFE 缩放：容量公式的 1000 倍因子
     * <p>
     * 容量 = count × KFE_SCALE × 64 × MAX_CONCENTRATION，count=100 时
     * 约 1290 万 FE；"每分子发电 count kFE"（1 分子 ATP → 100 kFE）
     */
    public static final double KFE_SCALE = 1000.0;

    private EnergyKinetics() {
    }

    /**
     * 判断物种是否为能量物种（fe）
     * <p>
     * 方块实体/菜单/GUI/JEI/方程式统一用本方法拦截 fe：
     * 不建物品槽、不查 ModItems.byId（fe 无 MoleculeItem，直接查会 NPE）
     *
     * @param speciesId 物种注册名（enzymes.json 的 item 字段）
     * @return true 表示能量物种
     */
    public static boolean isEnergySpecies(String speciesId) {
        return FE_SPECIES_ID.equals(speciesId);
    }

    /**
     * 能量存储容量（FE）
     * <p>
     * 容量 = count × 1000 × 64 × MAX_CONCENTRATION：
     * "满存量 = 满浓度"的等价物——满存量时引擎浓度镜像 = MAX_CONCENTRATION，
     * boundaryScale 停转，因此容量公式必须与镜像公式同源，改任何一处
     * 都会破坏回压语义（勿在显示层复制）
     *
     * @param count 化学计量系数（每分子 FE 数）
     * @return 容量（FE，恒正）
     */
    public static int capacity(int count) {
        return (int) Math.round(count * KFE_SCALE * 64.0 * KineticConstants.MAX_CONCENTRATION);
    }

    /**
     * 存量 → 引擎浓度镜像
     * <p>
     * 镜像 = 存量/容量 × MAX_CONCENTRATION：空能量 → 0、满能量 → 满浓度。
     * 方块实体每 tick 在引擎 step 前把能量物种浓度写为镜像（引擎结算的
     * fe 导数被覆盖丢弃，不影响其他物种——fe 不进速率方程），
     * 满存量时镜像 = 上限，RK4 终值越界触发全局缩放停转
     *
     * @param storedFE 当前能量存量（FE）
     * @param capacityFE 容量（FE，由 {@link #capacity(int)} 给出）
     * @return 引擎浓度镜像（[0, MAX_CONCENTRATION]）
     */
    public static double mirrorConcentration(int storedFE, int capacityFE) {
        if (capacityFE <= 0) {
            return 0.0;
        }
        double ratio = (double) storedFE / capacityFE;
        return Math.max(0.0, Math.min(ratio * KineticConstants.MAX_CONCENTRATION,
                KineticConstants.MAX_CONCENTRATION));
    }

    /**
     * 每 tick 能量结算（FE/tick）
     * <p>
     * FE 流量 = 净通量（浓度/s）× 净化学计量系数（含符号，产物 +count /
     * 反应物 −count）× 64（物品/浓度）× KFE_SCALE（每分子 FE）×
     * TICK_SECONDS（秒/tick）
     * 正 = 充能（fe 在产物侧）、负 = 消耗（fe 在反应物侧）；
     * 通量已含引擎边界缩放（StepResult 的有效通量），满能量时自动为 0
     *
     * @param fluxNet 引擎净通量（堆叠分数/s，StepResult.fluxNet）
     * @param stoich  fe 物种的净化学计量系数（ReactionDefinition.getStoich）
     * @return FE/tick（可正可负）
     */
    public static double fePerTick(double fluxNet, double stoich) {
        return fluxNet * stoich * 64.0 * KineticConstants.TICK_SECONDS * KFE_SCALE;
    }
}
