package com.github.crafteve.biocraft.reaction;

/**
 * Rosenbrock-W 刚性积分器（半隐式，L-stable，4 阶）
 * <p>
 * 替代旧显式 RK4 + 四判据自适应细分：Rosenbrock 是半隐式方法，
 * 稳定域覆盖整个左半平面（L-stable），大步长（单游戏 tick 0.05s）
 * 直接稳定积分高 kcat 刚性系统（TPI kcat=9000、ALDO [E]=64 的
 * Vmax_b≈19200），不再需要"自抵消/单调大消耗/平衡区驻留/方向矛盾"
 * 四判据与 64 子步细分（RK4 最坏每 tick 256 次速率求值）
 * <p>
 * 方法选型：KPP（Kinetic PreProcessor）大气化学包的 ROS-4，
 * 系数来自 Sandu et al. 1997（Benchmarking stiff ODE solvers for
 * atmospheric chemistry problems），4 阶段 4(3) 阶 L-stable，
 * 经大气化学学界多年验证。系数表见 {@link #GAMMA}/{@link #A}/
 * {@link #C}/{@link #M}（与 KPP 文档逐值核对）
 * <p>
 * 本系统的关键结构红利：单反应网络下全部物种导数 = 化学计量向量 ×
 * 同一标量速率 f(x) = s·v(x)，雅可比矩阵 J = ∂f/∂x = s·∇vᵀ 是
 * 秩 1 外积。Rosenbrock 每阶段需解 (I/(hγᵢ) − J)·kᵢ = bᵢ，
 * 秩 1 结构用 Sherman-Morrison 化为纯标量运算：
 * <pre>
 *   (I/(hγ) − J)⁻¹ = hγ·(I − hγ·s·gᵀ)⁻¹
 *   (I − hγ·s·gᵀ)⁻¹·b = b + [hγ/(1 − hγ·gᵀs)]·(gᵀb)·s
 * </pre>
 * 其中 g = ∇v（引擎每 tick 数值差分一次，O(n) 次速率求值），
 * gᵀs 与 gᵀb 均为标量——"解线性方程组"退化为除法
 * <p>
 * 每 tick 成本：数值梯度 n+1 次 + 4 阶段各 1 次速率求值，
 * 最坏 n≤6 时 11 次求值 vs RK4 刚性最坏 256 次（性能基准见
 * tools/engineTest/benchmarks.md）；普通非刚性酶略慢于单子步 RK4
 * （阶段数多），但绝对量级微不可查
 * <p>
 * 保留的物流语义：终值全局边界缩放（boundaryScale，槽满停转/
 * 满能量回压）与浓度钳制在步外执行，与旧积分器行为一致
 */
public final class RosenbrockIntegrator {

    /** 阶段数（KPP ROS-4：4 阶段 4(3) 阶） */
    private static final int STAGES = 4;

    /**
     * 线性系统对角系数 γ₀（KPP ROS-4 唯一使用值）
     * <p>
     * KPP 实现要点（与 rosenbrock.c 逐行核对）：所有阶段共用同一个
     * 矩阵 A = I/(H·γ₀) − J（ros_PrepareMatrix 在 stage 循环外以
     * ros_Gamma[0] 调用一次）；其余 γ₂₋₄ 仅用于非自治系统的时间偏导
     * 项（K[istage] += H·γᵢ·dFdT，自治系统跳过）——本项目引擎是
     * 自治的（f 不依赖时间），故只有 γ₀ 参与。先前实现误用每阶段
     * 各自的 γ 构建矩阵，导致方法退化为低阶（收敛阶验证 test28 实测
     * 一阶），此为修复
     */
    private static final double GAMMA0 = 0.5728200000000000;

    /**
     * 线性组合系数 a（下三角，Yᵢ = y + Σ aᵢⱼ·kⱼ）
     * <p>
     * 精确值取自 KPP rosenbrock.c Ros4()（来源 Hairer & Wanner,
     * Solving ODEs II, Springer 1990，L-stable 4 阶 4 阶段）
     */
    private static final double[][] A = {
            {0.0, 0.0, 0.0, 0.0},
            {2.0, 0.0, 0.0, 0.0},
            {1.867943637803922, 0.2344449711399156, 0.0, 0.0},
            {1.867943637803922, 0.2344449711399156, 0.0, 0.0}
    };

    /** 刚性系数 c（右端 (1/h)·Σ cᵢⱼ·kⱼ 项，同上精确值） */
    private static final double[][] C = {
            {0.0, 0.0, 0.0, 0.0},
            {-7.137615036412310, 0.0, 0.0, 0.0},
            {2.580708087951457, 0.6515950076447975, 0.0, 0.0},
            {-2.137148994382534, -0.3214669691237626, -0.6949742501781779, 0.0}
    };

    /** 输出权重 m（y₁ = y + Σ mᵢ·kᵢ，同上精确值） */
    private static final double[] M = {2.255570073418735, 0.2870493262186792, 0.4353179431840180, 1.093502252409163};

    /**
     * 数值梯度差分步长基准（前向差分，相对物种浓度自适应放大）
     * <p>
     * f 对 x 的偏导用前向差分 (v(x+ε) − v(x))/ε 近似；ε 太小会有
     * 消减误差、太大则梯度失真——取 1e-6 × max(1, |x|) 是双精度
     * 机器精度 (≈2.2e-16) 的三次方根量级，前向差分的误差平衡点
     */
    private static final double GRAD_EPS = 1e-6;

    /** 不可变反应网络档案（提供 stoich 向量与速率项物种下标） */
    private final ReactionDefinition definition;

    /** 速率项物种下标集合（进入速率方程的物种，梯度只对这些求） */
    private final int[] rateSpeciesIndices;

    /**
     * 化学计量向量缓存（构造期一次构建，step 高频使用）
     * <p>
     * 引擎物种表在构造后不可变（ReactionDefinition 全程只读），
     * 每 tick 拷贝属无谓开销（基准实测：普通非刚性场景相对 RK4
     * 慢 7.6 倍中数组分配占比显著），缓存后纯引用访问
     */
    private final double[] stoich;

    /**
     * 梯度差分探测数组（构造期分配，step 内复用）
     * <p>
     * 单线程每 tick 串行调用（服务端 tick 循环），无并发别名风险；
     * 避免数值梯度每次 clone 全浓度数组的分配开销
     */
    private final double[] gradProbe;

    /**
     * @param definition 反应网络档案（引擎构造期已通过断言防火墙）
     */
    public RosenbrockIntegrator(ReactionDefinition definition) {
        this.definition = definition;
        java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateReactants()) {
            if (!indices.contains(entry.index())) {
                indices.add(entry.index());
            }
        }
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateProducts()) {
            if (!indices.contains(entry.index())) {
                indices.add(entry.index());
            }
        }
        this.rateSpeciesIndices = indices.stream().mapToInt(Integer::intValue).toArray();
        int n = definition.getSpeciesCount();
        this.stoich = new double[n];
        for (int i = 0; i < n; i++) {
            stoich[i] = definition.getStoich(i);
        }
        this.gradProbe = new double[n];
    }

    /**
     * 执行一步 Rosenbrock-W 积分（原地更新浓度，返回边界缩放因子）
     * <p>
     * 流水线：死区检测（速率项产物全部 ≈0 且净速率为正：雅可比切线
     * 信息在乘积项边界退化，Rosenbrock 修正项会给出错误幅度——冷启动
     * 起步阶段）→ 显式欧拉一步（函数值方法无需导数，方向天然正确）；
     * 非死区 → 数值梯度（y 处一次）→ 标量 gᵀs → 4 阶段 Sherman-Morrison
     * 求解 → 加权组合 → 边界缩放 + 钳制
     * <p>
     * dt ≤ 0 时跳过积分直接返回（调用方取 t=0 瞬时通量的接口兼容）
     *
     * @param y        浓度数组（原地更新）
     * @param dt       步长（秒），游戏 tick 为 KineticConstants.TICK_SECONDS
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子（酶堆叠数，1 个 = 1 倍速）
     * @return 本步边界缩放因子（0~1，1 表示未截断）
     */
    public double step(double[] y, double dt, double vmaxB, double activity) {
        if (dt <= 0.0) {
            return 1.0;
        }
        int n = y.length;
        if (isZeroProductDeadZone(y, vmaxB, activity) && netRate(y, vmaxB, activity) > 0.0) {
            // 死区起步：后退欧拉（隐式欧拉）一步——L-stable、不依赖雅可比
            // 切线、大步长直接逼近平衡点（不会过冲穿越）。显式欧拉在此不可
            // 用：大步长会把产物踢得大幅越过平衡（TPI kcat=9000 实测 Δ=0.94，
            // Q 冲到 8 倍于 Keq），随后逆向巨流全量程反弹回死区，形成
            // 2-tick 极限环。后退欧拉把"单步看着产物方向推进"变成"解产出
            // 平衡附近"，死区一步即离开，下一 tick 梯度恢复自动切回 Rosenbrock
            double u = solveImplicitEuler(y, stoich, dt, vmaxB, activity);
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                next[i] = y[i] + stoich[i] * u * dt;
            }
            double scale = KineticsCalculator.boundaryScale(y, next);
            for (int i = 0; i < n; i++) {
                y[i] = KineticsCalculator.clampConcentration(y[i] + (next[i] - y[i]) * scale);
            }
            return scale;
        }

        // 引擎净速率（含活性缩放，梯度与阶段求值共用同一函数口径）
        // activity 乘在速率标量上：Vmax = kcat×[E] 严格线性，平衡位置不受影响
        double[] g = gradV(y, vmaxB, activity);

        // 标量 gᵀs：Sherman-Morrison 分母的公共因子，每 tick 只算一次
        double gDotS = 0.0;
        for (int i = 0; i < n; i++) {
            gDotS += g[i] * stoich[i];
        }

        double[][] k = new double[STAGES][n];
        double[] yCur = new double[n];
        double[] b = new double[n];

        for (int stage = 0; stage < STAGES; stage++) {
            // 阶段采样点 Yᵢ = y + Σ aᵢⱼ·kⱼ
            for (int i = 0; i < n; i++) {
                yCur[i] = y[i];
            }
            for (int j = 0; j < stage; j++) {
                double aij = A[stage][j];
                if (aij == 0.0) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    yCur[i] += aij * k[j][i];
                }
            }
            // 右端 bᵢ = f(Yᵢ) + (1/h)·Σ cᵢⱼ·kⱼ
            double vCur = netRate(yCur, vmaxB, activity);
            for (int i = 0; i < n; i++) {
                b[i] = stoich[i] * vCur;
            }
            for (int j = 0; j < stage; j++) {
                double cij = C[stage][j];
                if (cij == 0.0) {
                    continue;
                }
                double coef = cij / dt;
                for (int i = 0; i < n; i++) {
                    b[i] += coef * k[j][i];
                }
            }
            // Sherman-Morrison：kᵢ = hγ₀·(I − hγ₀·s·gᵀ)⁻¹·bᵢ（所有阶段共用 γ₀）
            double hg = dt * GAMMA0;
            double gDotB = 0.0;
            for (int i = 0; i < n; i++) {
                gDotB += g[i] * b[i];
            }
            double denom = 1.0 - hg * gDotS;
            double lambda = hg / denom;
            double[] ki = k[stage];
            for (int i = 0; i < n; i++) {
                ki[i] = hg * (b[i] + lambda * gDotB * stoich[i]);
            }
        }

        // 加权组合 y₁ = y + Σ mᵢ·kᵢ，然后全局边界缩放 + 钳制
        double[] next = new double[n];
        for (int i = 0; i < n; i++) {
            double delta = 0.0;
            for (int stage = 0; stage < STAGES; stage++) {
                delta += M[stage] * k[stage][i];
            }
            next[i] = y[i] + delta;
        }
        double scale = KineticsCalculator.boundaryScale(y, next);
        for (int i = 0; i < n; i++) {
            y[i] = KineticsCalculator.clampConcentration(y[i] + (next[i] - y[i]) * scale);
        }
        return scale;
    }

    /**
     * 数值梯度：速率 v 对全部速率项物种浓度的偏导（前向差分）
     * <p>
     * 只在 y 处求一次（Rosenbrock 单雅可比特性：所有阶段共用步起点
     * 的雅可比）；非速率项物种（固定活性 H₂O/H⁺/fe）偏导恒 0——
     * 它们不进入速率方程，雅可比对应列自然为零
     *
     * @param y        步起点浓度
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return 梯度向量（长度 = 物种数，速率项物种为差分值、其余 0）
     */
    private double[] gradV(double[] y, double vmaxB, double activity) {
        double[] g = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            gradProbe[i] = y[i];
        }
        for (int idx : rateSpeciesIndices) {
            double eps = GRAD_EPS * Math.max(1.0, Math.abs(y[idx]));
            // 中心差分（误差 O(ε²)），数值试验
            gradProbe[idx] = y[idx] + eps;
            double vPlus = netRate(gradProbe, vmaxB, activity);
            gradProbe[idx] = y[idx] - eps;
            double vMinus = netRate(gradProbe, vmaxB, activity);
            g[idx] = (vPlus - vMinus) / (2.0 * eps);
            gradProbe[idx] = y[idx];
        }
        return g;
    }

    /**
     * 后退欧拉（隐式欧拉）的标量求解：单反应网络下的闭式结构红利
     * <p>
     * 隐式欧拉：y₁ = y + h·s·u，其中 u = v(y₁)（净速率标量）。代入
     * 得标量方程 G(u) = u − v(y + h·s·u) = 0。G 在 [0, v(y)] 上
     * 物理单调（u 增大 → 产物增/反应物减 → v 单调下降），二分收敛稳定
     * <p>
     * 只用于死区起步（可逆反应产物全 0、雅可比切线退化的瞬时阶段），
     * 一阶精度的一次性误差由随后的平衡吸引子吸收（平衡收敛测试守护）
     *
     * @param y        死区起步浓度
     * @param stoich   化学计量向量
     * @param dt       步长（秒）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return 净速率标量解 u（含活性缩放，Δy = h·s·u）
     */
    private double solveImplicitEuler(double[] y, double[] stoich, double dt,
                                      double vmaxB, double activity) {
        double upper = netRate(y, vmaxB, activity); // = v(y) > 0（调用方已保证）
        double lower = 0.0;
        double[] probe = y.clone();
        for (int iter = 0; iter < 100; iter++) {
            double mid = 0.5 * (lower + upper);
            for (int i = 0; i < probe.length; i++) {
                probe[i] = y[i] + stoich[i] * mid * dt;
            }
            double g = mid - netRate(probe, vmaxB, activity);
            if (g > 0.0) {
                upper = mid;
            } else {
                lower = mid;
            }
        }
        return 0.5 * (lower + upper);
    }

    /**
     * 死区检测：速率项产物（可逆反应的逆项分母）浓度全部 ≈ 0
     * <p>
     * 产物乘积为零的点上，速率对每个产物单独方向的偏导恒为 0
     * （乘积求导的退化），雅可比切线信息缺失——Rosenbrock 的刚性
     * 修正项（c 系数）在此处给出错误幅度，实测 ALDO F16P 满堆 +
     * 双产物 0 起步时净通量被压成 0（"死区冻结"，与旧 RK4 的方向
     * 失真同族问题）。此时必须退回函数值方法（显式欧拉）起步，
     * 产物推进到非零后梯度信息恢复，下一 tick 自动切回 Rosenbrock
     * <p>
     * 不可逆反应无速率项产物（逆项不存在），不存在此退化，恒 false
     *
     * @param y        步起点浓度
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return true = 处于产物死区（应改用显式欧拉起步）
     */
    private boolean isZeroProductDeadZone(double[] y, double vmaxB, double activity) {
        if (definition.getRateProducts().isEmpty()) {
            return false;
        }
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateProducts()) {
            if (y[entry.index()] > 1e-9) {
                return false;
            }
        }
        return true;
    }

    /**
     * 引擎净速率（含活性缩放），梯度与阶段求值共用
     *
     * @param y        采样点浓度（允许越界，速率函数内部钳制）
     * @param vmaxB    当前温度逆向 Vmax
     * @param activity 活性因子
     * @return 净速率（堆叠分数/s，含供料门与活性缩放）
     */
    private double netRate(double[] y, double vmaxB, double activity) {
        return KineticsCalculator.netRate(definition, y, vmaxB) * activity;
    }
}