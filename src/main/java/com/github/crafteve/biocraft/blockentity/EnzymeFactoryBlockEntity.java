package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.EnzymeSimulator;
import com.github.crafteve.biocraft.reaction.EnergyKinetics;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.KineticsCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 酶工厂方块实体（共享一个 BlockEntityType，全酶实例共用）
 * <p>
 * 设计（M3 桥接层，已与策划确认的方案）：
 * <ul>
 *   <li>槽位布局：每物种一槽（槽位数 = 物种数 = 反应物+产物数），
 *       槽位顺序与物种索引完全一致（reactants 先 products 后）；
 *       可逆反应不区分输入/输出方向，Δx 符号自动决定扣/放</li>
 *   <li>浓度权威：引擎积分的连续浓度是权威，槽位是整数投影；
 *       玩家/漏斗改动槽位触发 setChanged → 事件回写浓度
 *       （保留余量小数，取走整数物品不影响余量）</li>
 *   <li>进度条：余量（浓度小数部分）即为 GUI 进度条数据源，
 *       槽位 count = floor(浓度×64)，进度条 = 浓度×64 − count</li>
 *   <li>serverTick 流水线：槽位合法性防呆（非法物品弹出）→ 事件回写兜底
 *       → 策略层活性判定 → 引擎 step → 浓度投影回槽位 → 观测数据缓存</li>
 *   <li>睡眠机制：全部物种浓度≈0 时跳过引擎（无反应物无产物，
 *       step 恒返回零通量，省一次浮点计算）</li>
 *   <li>存档：只存浓度数组（余量可从浓度与槽位重算）</li>
 * </ul>
 */
public class EnzymeFactoryBlockEntity extends MachineBlockEntity {
    /** 本机酶数据档案（从方块取回，构造时不可空） */
    private final EnzymeFactoryData enzymeData;

    /** 引擎模拟器实例（注册期构建，含数据防火墙断言） */
    private final EnzymeSimulator simulator;

    /** 物种注册名数组（槽位下标 = 物种下标；含 fe 能量物种，引擎权威） */
    private final String[] speciesIds;

    /**
     * 槽位 → 物种下标映射（排除 fe 能量物种）
     * <p>
     * fe 无物品槽：引擎物种表含 fe（化学计量结算），但容器/GUI/管道
     * 只操作非 fe 物种——本映射是"槽位序号 ↔ 引擎浓度下标"的唯一桥梁，
     * 投影/回写/存档/物品校验全部经它换算
     */
    private final int[] slotToSpeciesIndex;

    /** fe 能量物种的引擎浓度下标（无能量酶为 -1） */
    private final int feSpeciesIndex;

    /** 能量存量（FE，仅含 fe 酶使用；容量 = EnergyKinetics.capacity(count)） */
    private int energyStored;

    /** 每 tick 能量结算缓存（FE/tick，GUI 产率读数数据源，定点 ×10 同步） */
    private double cachedEnergyRate;

    /** 能量 IO 适配器（懒加载单例，capability 查询复用） */
    private net.neoforged.neoforge.energy.IEnergyStorage energyStorage;

    /** 能量存档脏标记（避免每 tick 触发 setChanged 存档写盘） */
    private int energyStoredSnapshot = -1;

    /** 每物种余量（浓度小数部分 = 下一个物品的积累进度，GUI 进度条数据源） */
    private final double[] remainder;

    /** 投影递归守卫：投影修改槽位会触发 setChanged → 回写，需拦截避免互相覆盖 */
    private boolean projecting;

    /** 观测数据缓存（M4 的 Menu ContainerData 读取，每 tick 更新） */
    private int cachedFluxX1000;
    private int cachedTempX100;
    private int cachedProgressX1000;

    /** 工业 IO 适配器（懒加载单例：管道查询 capability 时复用同一实例，避免每 tick 分配） */
    private EnzymeFactoryItemHandler itemHandler;

    /** v-t 通量历史环形缓冲（200 tick = 10 秒，打开 GUI 时一次性下发，不存档） */
    private static final int HISTORY_LENGTH = 200;
    private final int[] fluxHistory = new int[HISTORY_LENGTH];
    private int historyIndex;

    /**
     * @param pos   方块位置
     * @param state 方块状态（其方块必须是酶工厂 MachineBlock）
     */
    public EnzymeFactoryBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, enzymeDataFromState(state));
    }

    /**
     * 回退构造：菜单打开竞态（方块已被破坏）时，用数据表档案 + AIR 状态
     * 构造占位实体，避免菜单崩溃；仅由 MachineMenu 的防御降级路径调用
     *
     * @param pos  方块位置
     * @param data 酶数据档案
     */
    public EnzymeFactoryBlockEntity(BlockPos pos, EnzymeFactoryData data) {
        this(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), data);
    }

    /**
     * 统一私有构造：槽位数从酶数据推导（排除 fe 能量物种），
     * 不再依赖方块状态强转
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @param data  酶数据档案
     */
    private EnzymeFactoryBlockEntity(BlockPos pos, BlockState state, EnzymeFactoryData data) {
        super(ModBlocks.ENZYME_FACTORY_BE.get(), pos, state, slotCount(data));
        this.enzymeData = data;
        this.simulator = enzymeData.buildSimulator();
        this.speciesIds = simulator.getDefinition().getSpeciesIds();
        this.slotToSpeciesIndex = buildSlotMapping(speciesIds);
        this.feSpeciesIndex = findFeIndex(speciesIds);
        this.remainder = new double[getContainer().getContainerSize()];
    }

    /**
     * 槽位数：物种总数 − fe 能量物种数（fe 无物品槽）
     *
     * @param data 酶数据档案
     * @return 容器槽位数
     */
    private static int slotCount(EnzymeFactoryData data) {
        int total = data.reactants().size() + data.products().size();
        int energy = 0;
        for (EnzymeFactoryData.SpeciesSpec spec : data.reactants()) {
            if (EnergyKinetics.isEnergySpecies(spec.item())) {
                energy++;
            }
        }
        for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
            if (EnergyKinetics.isEnergySpecies(spec.item())) {
                energy++;
            }
        }
        return total - energy;
    }

    /**
     * 建立槽位 → 物种下标映射（物种表顺序 = 反应物先产物后，与槽位一致）
     * <p>
     * 映射长度 = 非 fe 物种数（槽位数），fe 能量物种无槽位不占位；
     * 必须先数非 fe 数再建数组（直接用物种数会因 fe 空位导致长度不符）
     *
     * @param speciesIds 全物种注册名（引擎权威顺序）
     * @return 映射表：槽位 i → 物种下标（长度 = 非 fe 物种数）
     */
    private static int[] buildSlotMapping(String[] speciesIds) {
        int nonEnergy = 0;
        for (String id : speciesIds) {
            if (!EnergyKinetics.isEnergySpecies(id)) {
                nonEnergy++;
            }
        }
        int[] mapping = new int[nonEnergy];
        int slot = 0;
        for (int i = 0; i < speciesIds.length; i++) {
            if (EnergyKinetics.isEnergySpecies(speciesIds[i])) {
                continue;
            }
            mapping[slot++] = i;
        }
        if (slot != nonEnergy) {
            throw new IllegalStateException("槽位映射长度不符: 期望 " + nonEnergy + " 实际 " + slot);
        }
        return mapping;
    }

    /**
     * 查找 fe 能量物种的引擎浓度下标（无能量酶返回 -1）
     *
     * @param speciesIds 全物种注册名
     * @return fe 下标或 -1
     */
    private static int findFeIndex(String[] speciesIds) {
        for (int i = 0; i < speciesIds.length; i++) {
            if (EnergyKinetics.isEnergySpecies(speciesIds[i])) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从方块状态安全提取酶数据档案
     * <p>
     * 非 MachineBlock 方块（如菜单回退路径的 AIR）不直接强转，抛异常
     * 快速暴露错误而非静默产生空实体
     *
     * @param state 方块状态
     * @return 酶数据档案
     */
    private static EnzymeFactoryData enzymeDataFromState(BlockState state) {
        if (state.getBlock() instanceof MachineBlock block && block.getEnzymeFactoryData() != null) {
            return block.getEnzymeFactoryData();
        }
        throw new IllegalArgumentException("酶工厂方块实体挂到了非酶工厂方块上: " + state);
    }

    /**
     * 获取本机酶数据档案（GUI/网络/策略层共用）
     *
     * @return 酶数据档案
     */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }

    /**
     * 获取工业 IO 适配器（懒加载单例）
     * <p>
     * 供 ModCapabilities 的 capability 查询返回；管道每 tick 查询多次，
     * 复用同一实例避免对象分配。适配器直接操作本实体容器，
     * 物种过滤/浓度回写全部内聚在适配器内
     *
     * @return 本实体的 IItemHandler 适配器
     */
    public EnzymeFactoryItemHandler getItemHandler() {
        if (itemHandler == null) {
            itemHandler = new EnzymeFactoryItemHandler(this);
        }
        return itemHandler;
    }

    /**
     * 获取能量 IO 适配器（懒加载单例）
     * <p>
     * 无 fe 物种的酶返回 null（ModCapabilities 不注册该面能力）；
     * 方向由 fe 净化学计量自动判定：产物侧（stoich>0）发电机只可抽出，
     * 反应物侧（stoich<0）合成器只可充入
     *
     * @return 本实体的 IEnergyStorage，无能量物种时 null
     */
    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage() {
        if (feSpeciesIndex < 0) {
            return null;
        }
        if (energyStorage == null) {
            double stoich = simulator.getDefinition().getStoich(feSpeciesIndex);
            energyStorage = new MachineEnergyStorage(this, getEnergyCapacity(), stoich > 0.0);
        }
        return energyStorage;
    }

    /**
     * 能量容量（FE）：count × 1000 × 64 × MAX_CONCENTRATION（kFE 约定）
     * <p>
     * 满存量 = 满浓度镜像 → 引擎边界缩放停转（回压），
     * 容量公式只由引擎 EnergyKinetics 给出，显示层不得复制
     *
     * @return 容量（FE）
     */
    public int getEnergyCapacity() {
        if (feSpeciesIndex < 0) {
            return 0;
        }
        return EnergyKinetics.capacity(simulator.getDefinition().getStoich(feSpeciesIndex) > 0
                ? (int) simulator.getDefinition().getStoich(feSpeciesIndex)
                : (int) -simulator.getDefinition().getStoich(feSpeciesIndex));
    }

    /**
     * 当前能量存量（FE）
     *
     * @return 存量
     */
    public int getEnergyStored() {
        return energyStored;
    }

    /**
     * 当前能量产率缓存（FE/tick，GUI 读数数据源）
     *
     * @return FE/tick（正 = 充能、负 = 消耗）
     */
    public double getCachedEnergyRate() {
        return cachedEnergyRate;
    }

    /**
     * 外部充能（能量管道 receiveEnergy 执行路径）
     * <p>
     * 钳制到容量；变更经 setChanged 标记存档（槽位不变时
     * syncFromSlots 幂等，安全）
     *
     * @param amount 充入量（FE，已由调用方校验 ≤ 剩余容量）
     */
    public void addEnergy(int amount) {
        energyStored = Math.min(getEnergyCapacity(), energyStored + amount);
        setChanged();
    }

    /**
     * 外部抽取（能量管道 extractEnergy 执行路径）
     * <p>
     * 抽取后存量下降 → 引擎镜像浓度下降 → 满能量停转解除，
     * 反应恢复（回压释放）；变更经 setChanged 标记存档
     *
     * @param amount 抽出量（FE，已由调用方校验 ≤ 存量）
     */
    public void consumeEnergy(int amount) {
        energyStored = Math.max(0, energyStored - amount);
        setChanged();
    }

    /**
     * 获取引擎模拟器实例
     *
     * @return 引擎模拟器
     */
    public EnzymeSimulator getSimulator() {
        return simulator;
    }

    /**
     * 获取槽位对应物种注册名（槽位 → 物种映射，排除 fe）
     *
     * @param slot 槽位下标
     * @return 物种物品注册名（非 fe）
     */
    public String getSpeciesId(int slot) {
        return speciesIds[slotToSpeciesIndex[slot]];
    }

    /**
     * 获取槽位余量（GUI 进度条数据源）
     *
     * @param slot 槽位下标
     * @return 0~1 的余量（下一个物品的积累进度）
     */
    public double getRemainder(int slot) {
        return remainder[slot];
    }

    /**
     * 槽位堆叠上限：按槽位组数放大（n 组 = n×64 个物品）
     * <p>
     * 容量参数化（KineticConstants.SLOT_GROUPS）：每槽可容纳多组物品，
     * 配合浓度钳制上限放宽（MAX_CONCENTRATION），让强偏向反应物
     * （Keq 极小）的酶在满堆下平衡产物突破 1 个物品粒度可被抽出
     *
     * @return 单槽最大堆叠数（默认 2 组 = 128）
     */
    @Override
    protected int slotStackLimit() {
        return 64 * KineticConstants.SLOT_GROUPS;
    }

    /**
     * 容器内容变化回调：槽位 IO 事件 → 浓度回写
     * <p>
     * 玩家/漏斗改动槽位后（如取走 3 个物品），把引擎浓度同步为
     * (新槽位数量 + 保留余量)/64——余量不变，只损失取走的整数物品，
     * 引擎与槽位在连续值上永不分叉
     */
    @Override
    public void setChanged() {
        super.setChanged();
        syncFromSlots();
    }

    /**
     * 槽位 → 浓度事件回写（setChanged 触发）
     * <p>
     * 每个物种：浓度 = (槽位数量 + 余量)/64，钳制 [0, MAX_CONCENTRATION]；
     * 上限随槽位容量参数化（n 组 + 余量 <1 个物品），"槽满仍攒余量"
     * 的状态合法存在——此前钳制 1.0 会把投入物品吞掉
     * （余量 0.23 + 投入后 (64+0.23)/64 = 1.0036 被钳回 1.0 的 bug）；
     * 投影自身修改槽位时由 projecting 守卫跳过，避免递归覆盖
     */
    private void syncFromSlots() {
        if (projecting || level == null || level.isClientSide) {
            return;
        }
        double[] x = simulator.getState().getConcentrations();
        for (int i = 0; i < slotToSpeciesIndex.length; i++) {
            int count = inventory.getItem(i).getCount();
            x[slotToSpeciesIndex[i]] = KineticsCalculator.clampConcentration((count + remainder[i]) / 64.0);
        }
    }

    /**
     * 服务端每 tick 调度（BlockEntityType 匿名 getTicker 绑定）
     *
     * @param level 所在世界
     * @param pos   方块位置
     * @param state 方块状态
     * @param be    本实体
     */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos,
                                  BlockState state, EnzymeFactoryBlockEntity be) {
        be.tickServer();
    }

    /**
     * 单 tick 完整流水线（仅服务端）：
     * 槽位合法性防呆 → 事件回写兜底 → 引擎 step →
     * 浓度投影回槽位 → 观测数据缓存
     * <p>
     * 活性恒为 1.0（默认）：机器是否运转完全由化学引擎计算决定
     * （底物耗尽、产物回压、平衡状态等），不存在任何外部停机判定——
     * 玩家通过观察浓度变化即可判断机器状态，无需提示
     * <p>
     * 防呆：漏斗可绕过 Slot.mayPlace 直接向容器塞任意物品，
     * 每 tick 检查槽位物品类型，非法物品弹出世界（防呆而非拒绝）
     */
    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        ejectIllegalItems();
        syncFromSlots();

        double[] x = simulator.getState().getConcentrations();

        // fe 能量镜像：step 前把 fe 浓度写为"存量镜像"（满能量 = 满浓度），
        // 引擎结算的 fe 导数被下一 tick 的镜像覆盖丢弃，不影响其他物种
        // （fe 固定活性不进速率方程）；满能量时镜像 = 上限，RK4 越界触发
        // boundaryScale 全局停转——"满能量停转、抽走恢复"的回压
        if (feSpeciesIndex >= 0) {
            x[feSpeciesIndex] = EnergyKinetics.mirrorConcentration(energyStored, getEnergyCapacity());
        }

        boolean asleep = true;
        for (double value : x) {
            if (value > 1e-9) {
                asleep = false;
                break;
            }
        }
        if (asleep) {
            cachedFluxX1000 = 0;
        } else {
            var result = simulator.step(KineticConstants.TICK_SECONDS);
            cachedFluxX1000 = (int) Math.round(result.fluxNet() * 1000.0);
            settleEnergy(result.fluxNet());
            projectToSlots();
        }
        // 能量存档脏标记：settleEnergy 只更新存量，标记放在投影之后——
        // 此时槽位浓度与引擎一致，setChanged → syncFromSlots 幂等，
        // 不会抹掉 step 结果（若放在 step 后投影前调用会覆盖浓度）
        if (energyStored != energyStoredSnapshot) {
            energyStoredSnapshot = energyStored;
            setChanged();
        }
        updateCachedData();
        fluxHistory[historyIndex] = cachedFluxX1000;
        historyIndex = (historyIndex + 1) % HISTORY_LENGTH;

        if (level.getGameTime() % 20 == 0) {
            BioCraft.LOGGER.debug("enzyme factory [{}] slots: {}, concentrations: {}, fluxX1000: {}, FE: {}/{}",
                    enzymeData.id(), slotSummary(), concentrationSummary(), cachedFluxX1000,
                    energyStored, getEnergyCapacity());
        }
    }

    /**
     * 能量结算：FE 流量 = 引擎有效净通量 × fe 净化学计量 × 64 × 0.05 × 1000
     * <p>
     * 正 = 充能（fe 产物侧，发电机）、负 = 消耗（fe 反应物侧，合成器）；
     * 存量钳制 [0, 容量]——满存量时通量已被引擎边界缩放压到 0（镜像回压），
     * 钳制是最后防线；存量不足时引擎 hasSupply 门已冻结反应（不欠账）。
     * 本方法只更新存量与产率缓存，存档脏标记由 tickServer 在投影后统一处理
     */
    private void settleEnergy(double fluxNet) {
        if (feSpeciesIndex < 0) {
            return;
        }
        double delta = EnergyKinetics.fePerTick(fluxNet, simulator.getDefinition().getStoich(feSpeciesIndex));
        cachedEnergyRate = delta;
        int capacity = getEnergyCapacity();
        energyStored = (int) Math.max(0, Math.min(capacity, energyStored + delta));
    }

    /**
     * 浓度 → 槽位整数投影（每 tick 引擎 step 后）
     * <p>
     * 槽位数量 = floor(浓度×64)，钳制到槽位物理容量（64×n 组），
     * 余量 = 浓度×64 − 数量（0~1 个物品的积累进度）；
     * 槽位满（n 组）时浓度钳制到 MAX_CONCENTRATION 与引擎边界缩放
     * 同步，产物满堆反应自然停转，取走产物即恢复；
     * 余量不会被钳制吞掉（此前 (64+0.23)/64 被 clamp01 钳回 1.0 的 bug）
     */
    private void projectToSlots() {
        projecting = true;
        try {
            double[] x = simulator.getState().getConcentrations();
            int slotLimit = slotStackLimit();
            for (int i = 0; i < slotToSpeciesIndex.length; i++) {
                int speciesIndex = slotToSpeciesIndex[i];
                double total = x[speciesIndex] * 64.0;
                int count = Math.min((int) Math.floor(total), slotLimit);
                remainder[i] = total - count;
                ItemStack stack = inventory.getItem(i);
                if (stack.getCount() == count) {
                    continue;
                }
                if (count <= 0) {
                    inventory.setItem(i, ItemStack.EMPTY);
                } else if (stack.isEmpty()) {
                    inventory.setItem(i, new ItemStack(ModItems.byId(speciesIds[speciesIndex]).get(), count));
                } else {
                    stack.setCount(count);
                }
            }
        } finally {
            projecting = false;
        }
    }

    /**
     * 槽位合法性检查：非法物品弹出世界（防呆，漏斗绕过 Slot 限制）
     * <p>
     * 玩家 GUI 放置由 Menu 的 Slot.mayPlace 拦截（M4 实现），
     * 漏斗直接操作容器 API，只能在此处防御
     */
    private void ejectIllegalItems() {
        for (int i = 0; i < slotToSpeciesIndex.length; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.byId(speciesIds[slotToSpeciesIndex[i]]).get())) {
                continue;
            }
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
            inventory.setItem(i, ItemStack.EMPTY);
        }
    }

    /**
     * 更新观测数据缓存（Menu ContainerData 数据源）
     * <p>
     * 缓存项（策划 3.6 同步通道）：
     * <ul>
     *   <li>温度×100（M5 温度机制接入前恒为参考温度）</li>
     *   <li>净通量×1000（GUI 速率条，在 tick 流水线中由 step 结果更新）</li>
     *   <li>主产物浓度×1000（GUI 平衡条进度）</li>
     * </ul>
     */
    private void updateCachedData() {
        cachedTempX100 = (int) Math.round(simulator.getState().getTemperature() * 100.0);
        double[] x = simulator.getState().getConcentrations();
        cachedProgressX1000 = 0;
        if (!enzymeData.products().isEmpty()) {
            String mainProduct = enzymeData.products().get(enzymeData.products().size() - 1).item();
            int productIndex = simulator.getDefinition().getSpeciesIndex(mainProduct);
            cachedProgressX1000 = (int) Math.round(x[Math.max(productIndex, 0)] * 1000.0);
        }
    }

    private String slotSummary() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slotToSpeciesIndex.length; i++) {
            sb.append(speciesIds[slotToSpeciesIndex[i]]).append('=')
                    .append(inventory.getItem(i).getCount()).append(' ');
        }
        return sb.append(']').toString();
    }

    private String concentrationSummary() {
        StringBuilder sb = new StringBuilder("[");
        double[] x = simulator.getState().getConcentrations();
        for (int i = 0; i < x.length; i++) {
            sb.append(String.format("%.3f ", x[i]));
        }
        return sb.append(']').toString();
    }

    /**
     * 容器序列化钩子（覆写基类）：自定义格式绕过 vanilla count 上限
     * <p>
     * vanilla 的 ItemStack.CODEC 对 count 硬编码校验 [1,99]
     * （ItemStack.java:107），槽位容量放大到 128 后 createTag 存档
     * 直接崩溃（实测"破坏正在工作的酶工厂崩溃"根因）。本方法把
     * 每槽物品拆成 {slot, id, count} 三个独立字段存 NBT，count 用
     * 原生 int 不受 CODEC 校验；分子物品无组件（SMILES 等由注册表
     * 驱动），id+count 即可完整还原
     *
     * @param registries 注册表查找器
     * @return 容器内容 NBT 列表
     */
    @Override
    protected net.minecraft.nbt.Tag saveContainerData(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < slotToSpeciesIndex.length; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("slot", i);
            entry.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.putInt("count", stack.getCount());
            list.add(entry);
        }
        return list;
    }

    /**
     * 容器反序列化钩子（覆写基类）：与 saveContainerData 对称
     * <p>
     * 按槽位写回物品堆；count 直接用存档值（可超 64，引擎浓度
     * 投影在 loadAdditional 末尾执行，槽位与浓度保持一致）
     *
     * @param list       容器内容 NBT 列表
     * @param registries 注册表查找器
     */
    @Override
    protected void loadContainerData(net.minecraft.nbt.ListTag list, net.minecraft.core.HolderLookup.Provider registries) {
        inventory.clearContent();
        for (net.minecraft.nbt.Tag element : list) {
            net.minecraft.nbt.CompoundTag entry = (net.minecraft.nbt.CompoundTag) element;
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= slotToSpeciesIndex.length) {
                continue;
            }
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.resources.ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) {
                continue;
            }
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            inventory.setItem(slot, new ItemStack(item, entry.getInt("count")));
        }
    }

    /**
     * 存档：引擎浓度数组（余量可由浓度与槽位重算，无需单独存档）
     * <p>
     * NBT 无 double 数组 API，采用 int 定点缩放（×1e6，精度 1e-6 浓度
     * ≈ 万分之一个物品，远超存档需要的精度）
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        double[] x = simulator.getState().getConcentrations();
        int[] fixed = new int[x.length];
        for (int i = 0; i < x.length; i++) {
            fixed[i] = (int) Math.round(x[i] * 1_000_000.0);
        }
        tag.putIntArray("concentrations", fixed);
        if (feSpeciesIndex >= 0) {
            tag.putInt("energyStored", energyStored);
        }
    }

    /**
     * 读档：恢复浓度并重新投影到槽位（以浓度为准，保证一致性）
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("concentrations")) {
            int[] saved = tag.getIntArray("concentrations");
            double[] x = simulator.getState().getConcentrations();
            for (int i = 0; i < Math.min(saved.length, x.length); i++) {
                x[i] = saved[i] / 1_000_000.0;
            }
        }
        if (feSpeciesIndex >= 0) {
            energyStored = Math.min(tag.getInt("energyStored"), getEnergyCapacity());
            energyStoredSnapshot = energyStored;
        }
        projectToSlots();
    }

    /**
     * 获取方块显示名（GUI 标题与玩家反馈共用，酶数据表中文名）
     *
     * @return 方块翻译组件
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.literal(enzymeData.nameZn());
    }

    /**
     * 创建菜单：酶工厂菜单（服务端）
     * <p>
     * experiment/gui-remake 分支重建第一版：菜单仅含玩家背包槽位，
     * 物种槽与仪表盘待逐项追加
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param player          打开菜单的玩家
     * @return 酶工厂菜单实例
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.github.crafteve.biocraft.gui.MachineMenu(containerId, playerInventory, this, historySnapshot());
    }

    /**
     * 打开菜单时向客户端写入自定义数据（NeoForge 扩展点）
     * <p>
     * 写入内容：酶 id（校验用）+ v-t 历史数组（按时间展开为旧→新顺序）
     *
     * @param menu   刚创建的服务端菜单
     * @param buffer 打开数据包缓冲
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(enzymeData.id());
        int[] snapshot = historySnapshot();
        buffer.writeVarInt(snapshot.length);
        for (int value : snapshot) {
            buffer.writeVarInt(value);
        }
    }

    /**
     * 按时间顺序展开历史环形缓冲（最旧 → 最新），客户端 v-t 图直接按序绘制
     *
     * @return 历史快照数组
     */
    private int[] historySnapshot() {
        int[] snapshot = new int[HISTORY_LENGTH];
        for (int i = 0; i < HISTORY_LENGTH; i++) {
            snapshot[i] = fluxHistory[(historyIndex + i) % HISTORY_LENGTH];
        }
        return snapshot;
    }

    /** 观测数据缓存读取（Menu ContainerData 数据源） */
    public int getCachedTempX100() {
        return cachedTempX100;
    }

    public int getCachedFluxX1000() {
        return cachedFluxX1000;
    }

    public int getCachedProgressX1000() {
        return cachedProgressX1000;
    }
}
