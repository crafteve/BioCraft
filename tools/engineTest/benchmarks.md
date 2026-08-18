# 引擎工作效率基准（wall-time）

引擎专属脚本（EngineSelfTest）内置基准段，积分器改造前后对比存档。
运行方式：`gradlew build` → `javac -encoding UTF-8 -cp build/classes/java/main -d tools/engineTest/out tools/engineTest/*.java` → `java -cp "build/classes/java/main;tools/engineTest/out" engineTest.EngineSelfTest`

## 场景定义

| 场景 | 数据 | 活性 | 初始浓度 | 工况 |
|---|---|---|---|---|
| PGI [E]=1 | TestEnzymes.pgi()（kcat 79） | 1.0 | G6P=1.0 | 普通非刚性 |
| TPI kcat=9000 [E]=3 | 手构造真实数据（同 test26） | 3.0 | DHAP=1.0 | 高 kcat 刚性 |
| ALDO [E]=64 | TestEnzymes.aldo()（kcat 10.7） | 64.0 | F16P=2.0 | 重刚性 + 平衡区驻留 |

计时口径：预热 100 tick 后正式计时 3 次取中位数（System.nanoTime），10000 tick 墙钟毫秒与每秒 tick 速率。
机器：本机（用户开发机，未标注型号）

## 基线（RK4 显式 + 四判据自适应细分）

日期：2026-08-16（tag: engine-baseline-rk4，commit 048ec97 起）

| 场景 | 用时 ms / 10000 tick | tick/s |
|---|---|---|
| PGI [E]=1 普通非刚性 | 16.28 | 614202 |
| TPI kcat=9000 [E]=3 高刚性 | 512.56 | 19510 |
| ALDO [E]=64 重刚性平衡区 | 620.55 | 16115 |

## Rosenbrock（半隐式 L 稳定，改造后）

日期：2026-08-16（tag: engine-rosenbrock；KPP ROS-4 系数，4 阶 L-stable，死区后退欧拉起步 + 单一矛盾守卫细分）

| 场景 | 用时 ms / 10000 tick | tick/s |
|---|---|---|
| PGI [E]=1 普通非刚性 | 27.71 | 360832 |
| TPI kcat=9000 [E]=3 高刚性 | 25.36 | 394277 |
| ALDO [E]=64 重刚性平衡区 | 36.37 | 274984 |

## 结论（RK4 vs Rosenbrock）

| 场景 | RK4 基线 | Rosenbrock | 加速比 |
|---|---|---|---|
| PGI 普通非刚性 | 16.28 ms | 27.71 ms | 0.59×（慢 1.7×，阶段数多） |
| TPI 高刚性 | 512.56 ms | 25.36 ms | **20.2×** |
| ALDO 重刚性平衡区 | 620.55 ms | 36.37 ms | **17.1×** |

刚性场景每 tick 从 51~62μs 降到 2.5~3.6μs（机器 CPU 压力大减）；普通场景绝对量 2.8μs/tick 微不足道（游戏 tick 50ms 的 0.006%）。基准数字有 ±20% 跨进程噪音（JIT/CPU 波动）。

注：Rosenbrock 初版实现（各阶段误用独立 γ 构建矩阵）曾退化为低阶且普通场景 123ms——按 KPP 实现修正（全部阶段共用 γ₀ 矩阵）后恢复 4 阶（收敛阶验证 test28 守护），轨迹与 RK4 近乎重合（PGI 快照 G6P 差 6.6e-8）。