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

日期：（待填写）（tag: engine-rosenbrock）

| 场景 | 用时 ms / 10000 tick | tick/s |
|---|---|---|
| PGI [E]=1 普通非刚性 | （待实测填写） | |
| TPI kcat=9000 [E]=3 高刚性 | （待实测填写） | |
| ALDO [E]=64 重刚性平衡区 | （待实测填写） | |