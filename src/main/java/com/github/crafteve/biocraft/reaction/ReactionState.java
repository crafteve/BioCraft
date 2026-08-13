package com.github.crafteve.biocraft.reaction;

/**
 * 反应状态容器：每台机器一份，被方块实体与引擎共享
 * <p>
 * 持有三类数据：
 * <ul>
 *   <li>浓度数组（引擎读写）：全部物种的堆叠分数浓度，含固定活性物种。
 *       数组有意暴露内部引用（getConcentrations 直接返回），供方块实体
 *       做槽位桥接（浓度差 → 余量累加 → 满 1 个物品进/出槽）与测试直接
 *       读写，避免逐元素拷贝开销</li>
 *   <li>温度（温度系统写入，引擎只读）：M5 起由群系/邻块温度场事件驱动更新</li>
 *   <li>活性（策略层写入，引擎只读）：KineticBehavior 三类实现算出的
 *       0~1 因子（限速酶无 AMP 折扣、失活归零等），引擎不做任何策略判断</li>
 * </ul>
 * 引擎的纯函数契约：本容器不包含任何 Minecraft 类型，
 * 状态全部为连续实数，可脱离游戏保存与测试
 */
public final class ReactionState {
    /** 全物种浓度（堆叠分数），下标与 ReactionDefinition 物种索引一致 */
    private final double[] concentrations;

    /** 当前温度（K），初值取热力学参考温度 */
    private double temperature = KineticConstants.T0;

    /** 当前活性因子（0~1，策略层权威） */
    private double activity = 1.0;

    /**
     * @param speciesCount 物种总数（由反应网络档案决定）
     */
    public ReactionState(int speciesCount) {
        this.concentrations = new double[speciesCount];
    }

    /**
     * 获取浓度数组的内部引用
     * <p>
     * 有意不返回副本：方块实体桥接槽位与测试都要高频读写，
     * 副本拷贝在每 tick 调用中开销无意义；调用方须自行保证
     * 写入的数值语义正确（引擎内部对非法值有钳制与 NaN 防护）
     *
     * @return 浓度数组内部引用
     */
    public double[] getConcentrations() {
        return concentrations;
    }

    /**
     * 便捷设置单物种浓度（测试与桥接用）
     *
     * @param index 物种下标
     * @param value 浓度值（调用方保证合理范围，引擎计算前会钳制）
     */
    public void setConcentration(int index, double value) {
        concentrations[index] = value;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getActivity() {
        return activity;
    }

    public void setActivity(double activity) {
        this.activity = activity;
    }
}
