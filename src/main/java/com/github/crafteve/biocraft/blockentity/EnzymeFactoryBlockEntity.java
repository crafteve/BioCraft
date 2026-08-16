package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.EnzymeItem;
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
 * 酶反应腔方块实体（唯一 BE，酶由 0 槽物品动态解析）
 * <p>
 * 酶工厂方块时代结束后的统一机器形态：
 * <ul>
 *   <li>容器固定容量 = 1（酶槽）+ 最大非 fe 物种数（注册期统计）：
 *       0 槽放酶蛋白物品（EnzymeItem，催化剂不参与浓度投影），
 *       1..n 槽为当前酶的物种槽（动态映射，未用槽位禁用）</li>
 *   <li>酶槽变化（放入/取走/更换）自动解析：无酶 → 停摆态（引擎不步进、
 *       GUI 显示告示）；有酶 → 构建引擎模拟器 + 槽位映射，完整运转</li>
 *   <li>酶种变更（含取空）→ 物种槽全部内容弹出世界 + 引擎归零重建；
 *       同种酶数量增减 → 只改 [E] 活性（activity = 堆叠数），不清空</li>
 *   <li>浓度权威：引擎积分的连续浓度是权威，槽位是整数投影；
 *       玩家/漏斗改动槽位触发 setChanged → 事件回写浓度</li>
 *   <li>睡眠机制：无酶或全部物种浓度≈0 时跳过引擎 step</li>
 *   <li>存档：只存浓度数组 + 容器内容（含 0 槽酶物品），读档时从酶槽重建</li>
 * </ul>
 */
public class EnzymeFactoryBlockEntity extends MachineBlockEntity {
    /** 酶槽容器下标（0 槽，放酶蛋白物品） */
    public static final int ENZYME_SLOT = 0;

    /** 物种槽起始下标（1 起，与 Menu/Screen 的槽位协议一致） */
    public static final int SPECIES_SLOT_BASE = 1;

    /** 本机酶数据档案（从 0 槽解析，无酶为 null） */
    private EnzymeFactoryData enzymeData;

    /** 引擎模拟器实例（有酶时构建，无酶为 null） */
    private EnzymeSimulator simulator;

    /** 物种注册名数组（引擎权威顺序，有酶时有效） */
    private String[] speciesIds = new String[0];

    /**
     * 槽位 → 物种下标映射（长度 = 最大非 fe 物种数，槽位 i → 引擎浓度下标；
     * 无酶或未用槽位为 -1）。fe 无物品槽不占位
     */
    private final int[] slotToSpeciesIndex;

    /** fe 能量物种的引擎浓度下标（无 fe 酶或未插入酶为 -1） */
    private int feSpeciesIndex = -1;

    /** 能量存量（FE，仅含 fe 酶使用；容量 = EnergyKinetics.capacity(count)） */
    private int energyStored;

    /** 每 tick 能量结算缓存（FE/tick，GUI 产率读数数据源，定点 ×10 同步） */
    private double cachedEnergyRate;

    /** 能量 IO 适配器（懒加载单例，capability 查询复用；换酶时置空重建） */
    private net.neoforged.neoforge.energy.IEnergyStorage energyStorage;

    /** 能量存档脏标记（避免每 tick 触发 setChanged 存档写盘） */
    private int energyStoredSnapshot = -1;

    /** 每物种余量（浓度小数部分 = 下一个物品的积累进度，GUI 进度条数据源） */
    private final double[] remainder;

    /** 投影递归守卫：投影修改槽位会触发 setChanged → 回写，需拦截避免互相覆盖 */
    private boolean projecting;

    /** 观测数据缓存（Menu ContainerData 读取，每 tick 更新） */
    private int cachedFluxX1000;
    private int cachedTempX100;
    private int cachedProgressX1000;

    /** 工业 IO 适配器（懒加载单例：管道查询 capability 时复用同一实例，避免每 tick 分配） */
    private EnzymeFactoryItemHandler itemHandler;

    /** v-t 通量历史环形缓冲（200 tick = 10 秒，打开 GUI 时一次性下发，不存档） */
    private static final int HISTORY_LENGTH = 200;
    private final int[] fluxHistory = new int[HISTORY_LENGTH];
    private int historyIndex;

    /** 酶槽当前酶 id 快照（换酶检测：对比本值与 0 槽解析结果） */
    private String enzymeSnapshot = "";

    /**
     * @param pos   方块位置
     * @param state 方块状态
     */
    public EnzymeFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ENZYME_CHAMBER_BE.get(), pos, state, 1 + EnzymeFactoryRegistry.maxNonFeSpeciesCount());
        this.slotToSpeciesIndex = new int[EnzymeFactoryRegistry.maxNonFeSpeciesCount()];
        java.util.Arrays.fill(slotToSpeciesIndex, -1);
        this.remainder = new double[EnzymeFactoryRegistry.maxNonFeSpeciesCount()];
        rebuildFromEnzymeSlot();
    }

    /**
     * 从 0 槽解析酶数据档案（防御查表：物品存在但注册表查无 → 停摆态）
     * <p>
     * 未来"酶插件"NBT 修饰变体（耐温/增效位点）的解析预留点：
     * 此处可读 enzymeStack 的 NBT 修饰字段并映射为引擎参数，
     * 当前酶物品无修饰组件（v1 未启用）
     *
     * @return 酶数据档案，无酶/非法酶为 null
     */
    private EnzymeFactoryData resolveEnzyme() {
        ItemStack stack = inventory.getItem(ENZYME_SLOT);
        if (stack.isEmpty() || !(stack.getItem() instanceof EnzymeItem enzymeItem)) {
            return null;
        }
        return EnzymeFactoryRegistry.byId(enzymeItem.getEnzymeData().id());
    }

    /**
     * 重建：按 0 槽当前酶构建引擎模拟器与槽位映射（换酶/读档共用）
     * <p>
     * 无酶 → 全部停摆（simulator=null、映射全 -1、能量归零）；
     * 有酶 → 构建模拟器（注册期防火墙保证数据必然通过断言）、
     * 槽位映射（1..n 按引擎物种表顺序）、fe 下标定位
     */
    private void rebuildFromEnzymeSlot() {
        EnzymeFactoryData data = resolveEnzyme();
        this.enzymeData = data;
        this.energyStorage = null;
        this.energyStored = 0;
        this.cachedEnergyRate = 0;
        java.util.Arrays.fill(slotToSpeciesIndex, -1);
        if (data == null) {
            this.simulator = null;
            this.speciesIds = new String[0];
            this.feSpeciesIndex = -1;
            return;
        }
        this.simulator = data.buildSimulator();
        this.speciesIds = simulator.getDefinition().getSpeciesIds();
        this.feSpeciesIndex = -1;
        int slot = SPECIES_SLOT_BASE;
        for (int i = 0; i < speciesIds.length; i++) {
            if (EnergyKinetics.isEnergySpecies(speciesIds[i])) {
                feSpeciesIndex = i;
            } else {
                if (slot < slotToSpeciesIndex.length) {
                    slotToSpeciesIndex[slot] = i;
                }
                slot++;
            }
        }
    }

    /**
     * 酶槽变动事件处理（事件驱动初始化）：解析 0 槽 → 快照对比 →
     * 变化则立即执行初始化/回收，不等下一 tick
     * <p>
     * 由 setChanged（容器内容变化回调，拖入/取走/换酶瞬间触发）调用，
     * tick 流水线保留同款兜底（漏斗等外部路径保险）：
     * <ul>
     *   <li>解析：读 0 槽酶物品 → registry 查酶档案（防呆：非法物品由
     *       ejectIllegalItems 弹出）；此处预留未来酶插件 NBT 修饰解析点</li>
     *   <li>有酶（新酶 id ≠ 快照）→ 构建引擎模拟器 + 槽位映射 + fe 定位，
     *       能量适配器缓存置空（懒加载按新酶重建）</li>
     *   <li>无酶（取空）→ 物种槽全部弹出世界 + 引擎删除（结束生命周期）→
     *       回到初始状态（enzymeData/simulator = null，GUI 显示 [unknown]）</li>
     *   <li>同种酶数量增减（[E] 缩放）不触发（快照只记 id）</li>
     * </ul>
     * 弹出物种槽时容器 setItem 会再次触发 setChanged——快照已更新使
     * 本方法幂等返回，无递归
     */
    private void handleEnzymeSlotChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        EnzymeFactoryData newData = resolveEnzyme();
        String newId = newData == null ? "" : newData.id();
        if (newId.equals(enzymeSnapshot)) {
            return;
        }
        enzymeSnapshot = newId;
        if (newData == null) {
            // 酶被取走/换空：弹出物种槽内容（掉落物形式）后回收引擎
            ejectSpeciesContents();
        }
        rebuildFromEnzymeSlot();
        notifyEnergyCapabilityChange();
    }

    /**
     * 弹出全部物种槽内容为世界掉落实体（换酶清空用）
     */
    private void ejectSpeciesContents() {
        for (int slot = SPECIES_SLOT_BASE; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 获取本机酶数据档案（GUI/网络/策略层共用；无酶为 null）
     *
     * @return 酶数据档案或 null
     */
    public EnzymeFactoryData getEnzymeData() {
        return enzymeData;
    }

    /**
     * 获取工业 IO 适配器（懒加载单例）
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
     * 无 fe 物种的酶/未插入酶返回 null（ModCapabilities 不注册该面能力）；
     * 方向由 fe 净化学计量自动判定：产物侧（stoich>0）发电机只可抽出，
     * 反应物侧（stoich<0）合成器只可充入；换酶后缓存已置空，此处按新酶重建
     *
     * @return 本实体的 IEnergyStorage，无能量能力时 null
     */
    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage() {
        if (feSpeciesIndex < 0 || simulator == null) {
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
     *
     * @return 容量（FE）
     */
    public int getEnergyCapacity() {
        if (feSpeciesIndex < 0 || simulator == null) {
            return 0;
        }
        return EnergyKinetics.capacity((int) Math.abs(simulator.getDefinition().getStoich(feSpeciesIndex)));
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
     *
     * @param amount 充入量（FE，已由调用方校验 ≤ 剩余容量）
     */
    public void addEnergy(int amount) {
        energyStored = Math.min(getEnergyCapacity(), energyStored + amount);
        setChanged();
        notifyEnergyCapabilityChange();
    }

    /**
     * 外部抽取（能量管道 extractEnergy 执行路径）
     *
     * @param amount 抽出量（FE，已由调用方校验 ≤ 存量）
     */
    public void consumeEnergy(int amount) {
        energyStored = Math.max(0, energyStored - amount);
        setChanged();
        notifyEnergyCapabilityChange();
    }

    /**
     * 能量变化/换酶 → capability 缓存失效通知（NeoForge 标准实践）
     * <p>
     * 管道模组用 BlockCapabilityCache 缓存 capability 查询结果，
     * 换酶后能量面可能挂载/卸载，必须通知缓存刷新
     */
    private void notifyEnergyCapabilityChange() {
        if (level != null && !level.isClientSide) {
            invalidateCapabilities();
        }
    }

    /**
     * 获取引擎模拟器实例（无酶为 null）
     *
     * @return 引擎模拟器或 null
     */
    public EnzymeSimulator getSimulator() {
        return simulator;
    }

    /**
     * 获取槽位对应物种注册名（槽位 → 物种映射，排除 fe；无酶/未用槽位返回 null）
     *
     * @param slot 槽位下标（0 = 酶槽）
     * @return 物种物品注册名或 null
     */
    public String getSpeciesId(int slot) {
        if (slot == ENZYME_SLOT || slot < 0 || slot >= slotToSpeciesIndex.length) {
            return null;
        }
        int speciesIndex = slotToSpeciesIndex[slot];
        return speciesIndex >= 0 ? speciesIds[speciesIndex] : null;
    }

    /**
     * 获取槽位余量（GUI 进度条数据源；无酶/未用槽位恒 0）
     *
     * @param slot 槽位下标（0 = 酶槽）
     * @return 0~1 的余量（下一个物品的积累进度）
     */
    public double getRemainder(int slot) {
        if (slot == ENZYME_SLOT || slot < 0 || slot >= remainder.length) {
            return 0.0;
        }
        return remainder[slot];
    }

    /**
     * 槽位堆叠上限：物种槽按槽位组数放大（n 组 = n×64 个物品），
     * 酶槽保持 64（堆叠数 = [E]，1 个 = 1 倍速、64 个 = 64 倍速）
     *
     * @return 单槽最大堆叠数（默认 2 组 = 128）
     */
    @Override
    protected int slotStackLimit() {
        return 64 * KineticConstants.SLOT_GROUPS;
    }

    /**
     * 容器内容变化回调：槽位 IO 事件 → 浓度回写 + 酶槽变动事件驱动
     * <p>
     * 玩家/漏斗/管道改动槽位后（如取走 3 个物品），把引擎浓度同步为
     * (新槽位数量 + 保留余量)/64——余量不变，只损失取走的整数物品，
     * 引擎与槽位在连续值上永不分叉；0 槽（酶）不参与浓度投影，
     * 但每次变动都触发酶槽检查——放入酶立即初始化、取走酶立即回收
     */
    @Override
    public void setChanged() {
        super.setChanged();
        syncFromSlots();
        handleEnzymeSlotChanged();
    }

    /**
     * 槽位 → 浓度事件回写（setChanged 触发）
     * <p>
     * 每个物种：浓度 = (槽位数量 + 余量)/64，钳制 [0, MAX_CONCENTRATION]；
     * 投影自身修改槽位时由 projecting 守卫跳过，避免递归覆盖
     */
    private void syncFromSlots() {
        if (projecting || level == null || level.isClientSide || simulator == null) {
            return;
        }
        double[] x = simulator.getState().getConcentrations();
        for (int slot = SPECIES_SLOT_BASE; slot < slotToSpeciesIndex.length; slot++) {
            int speciesIndex = slotToSpeciesIndex[slot];
            if (speciesIndex < 0) {
                continue;
            }
            int count = inventory.getItem(slot).getCount();
            x[speciesIndex] = KineticsCalculator.clampConcentration((count + remainder[slot]) / 64.0);
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
     * 槽位合法性防呆 → 事件回写兜底 → 酶槽检查兜底 → 引擎 step →
     * 浓度投影回槽位 → 观测数据缓存
     * <p>
     * 酶槽变动主要由 setChanged 事件驱动立即初始化（handleEnzymeSlotChanged），
     * 本处保留同款检查作为兜底（任何绕过 setChanged 的路径保险）
     * <p>
     * 活性 = 酶堆叠数 [E]（0 = 无酶停摆，64 = 64 倍速），由引擎
     * 活动通道实现 Vmax = kcat × [E] 严格线性，平衡位置不受影响
     */
    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        ejectIllegalItems();
        syncFromSlots();
        handleEnzymeSlotChanged();
        if (simulator == null) {
            cachedFluxX1000 = 0;
        } else {
            double[] x = simulator.getState().getConcentrations();

            // fe 能量镜像：step 前把 fe 浓度写为"存量镜像"（满能量 = 满浓度），
            // 引擎结算的 fe 导数被下一 tick 的镜像覆盖丢弃（fe 固定活性不进速率方程）
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
                ItemStack enzymeStack = inventory.getItem(ENZYME_SLOT);
                simulator.getState().setActivity(enzymeStack.isEmpty() ? 0.0 : enzymeStack.getCount());
                var result = simulator.step(KineticConstants.TICK_SECONDS);
                cachedFluxX1000 = (int) Math.round(result.fluxNet() * 1000.0);
                settleEnergy(result.fluxNet());
                projectToSlots();
            }
        }
        // 能量存档脏标记：settleEnergy 只更新存量，标记放在投影之后——
        // 此时槽位浓度与引擎一致，setChanged → syncFromSlots 幂等
        if (energyStored != energyStoredSnapshot) {
            energyStoredSnapshot = energyStored;
            setChanged();
            notifyEnergyCapabilityChange();
        }
        updateCachedData();
        fluxHistory[historyIndex] = cachedFluxX1000;
        historyIndex = (historyIndex + 1) % HISTORY_LENGTH;

        if (level.getGameTime() % 20 == 0) {
            BioCraft.LOGGER.debug("enzyme chamber [{}] slots: {}, concentrations: {}, fluxX1000: {}, FE: {}/{}",
                    enzymeData == null ? "none" : enzymeData.id(), slotSummary(), concentrationSummary(), cachedFluxX1000,
                    energyStored, getEnergyCapacity());
        }
    }

    /**
     * 能量结算：FE 流量 = 引擎有效净通量 × fe 净化学计量 × 64 × 0.05 × 1000
     * <p>
     * 正 = 充能（fe 产物侧，发电机）、负 = 消耗（fe 反应物侧，合成器）；
     * 存量钳制 [0, 容量]——满存量时通量已被引擎边界缩放压到 0（镜像回压）
     */
    private void settleEnergy(double fluxNet) {
        if (feSpeciesIndex < 0 || simulator == null) {
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
     * 余量 = 浓度×64 − 数量（0~1 个物品的积累进度）
     */
    private void projectToSlots() {
        projecting = true;
        try {
            double[] x = simulator.getState().getConcentrations();
            int slotLimit = slotStackLimit();
            for (int slot = SPECIES_SLOT_BASE; slot < slotToSpeciesIndex.length; slot++) {
                int speciesIndex = slotToSpeciesIndex[slot];
                if (speciesIndex < 0) {
                    continue;
                }
                double total = x[speciesIndex] * 64.0;
                int count = Math.min((int) Math.floor(total), slotLimit);
                remainder[slot] = total - count;
                ItemStack stack = inventory.getItem(slot);
                if (stack.getCount() == count) {
                    continue;
                }
                if (count <= 0) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else if (stack.isEmpty()) {
                    inventory.setItem(slot, new ItemStack(ModItems.byId(speciesIds[speciesIndex]).get(), count));
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
     * 0 槽只接受酶蛋白物品（registry 内）；物种槽只接受对应物种
     */
    private void ejectIllegalItems() {
        ItemStack enzymeStack = inventory.getItem(ENZYME_SLOT);
        if (!enzymeStack.isEmpty()
                && !(enzymeStack.getItem() instanceof EnzymeItem)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, enzymeStack);
            inventory.setItem(ENZYME_SLOT, ItemStack.EMPTY);
        }
        for (int slot = SPECIES_SLOT_BASE; slot < slotToSpeciesIndex.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int speciesIndex = slotToSpeciesIndex[slot];
            if (speciesIndex >= 0 && stack.is(ModItems.byId(speciesIds[speciesIndex]).get())) {
                continue;
            }
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, stack);
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 更新观测数据缓存（Menu ContainerData 数据源）
     */
    private void updateCachedData() {
        cachedTempX100 = simulator == null
                ? (int) Math.round(KineticConstants.T0 * 100.0)
                : (int) Math.round(simulator.getState().getTemperature() * 100.0);
        cachedProgressX1000 = 0;
        if (simulator != null && enzymeData != null && !enzymeData.products().isEmpty()) {
            String mainProduct = enzymeData.products().get(enzymeData.products().size() - 1).item();
            int productIndex = simulator.getDefinition().getSpeciesIndex(mainProduct);
            double[] x = simulator.getState().getConcentrations();
            cachedProgressX1000 = (int) Math.round(x[Math.max(productIndex, 0)] * 1000.0);
        }
    }

    private String slotSummary() {
        StringBuilder sb = new StringBuilder("[");
        for (int slot = SPECIES_SLOT_BASE; slot < slotToSpeciesIndex.length; slot++) {
            int speciesIndex = slotToSpeciesIndex[slot];
            if (speciesIndex < 0) {
                continue;
            }
            sb.append(speciesIds[speciesIndex]).append('=')
                    .append(inventory.getItem(slot).getCount()).append(' ');
        }
        return sb.append(']').toString();
    }

    private String concentrationSummary() {
        if (simulator == null) {
            return "[]";
        }
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
     * 每槽物品拆成 {slot, id, count} 三个独立字段存 NBT，count 用
     * 原生 int 不受 CODEC 校验；0 槽酶物品同样走本格式
     *
     * @param registries 注册表查找器
     * @return 容器内容 NBT 列表
     */
    @Override
    protected net.minecraft.nbt.Tag saveContainerData(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("slot", slot);
            entry.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.putInt("count", stack.getCount());
            list.add(entry);
        }
        return list;
    }

    /**
     * 容器反序列化钩子（覆写基类）：与 saveContainerData 对称
     * <p>
     * 按槽位写回物品堆；读档后由 loadAdditional 从 0 槽重建引擎
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
            if (slot < 0 || slot >= inventory.getContainerSize()) {
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
     * NBT 无 double 数组 API，采用 int 定点缩放（×1e6）
     *
     * @param tag        待写入的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (simulator != null) {
            double[] x = simulator.getState().getConcentrations();
            int[] fixed = new int[x.length];
            for (int i = 0; i < x.length; i++) {
                fixed[i] = (int) Math.round(x[i] * 1_000_000.0);
            }
            tag.putIntArray("concentrations", fixed);
        }
        if (feSpeciesIndex >= 0) {
            tag.putInt("energyStored", energyStored);
        }
    }

    /**
     * 读档：恢复容器（含 0 槽酶物品）→ 从酶槽重建引擎 → 恢复浓度并投影
     * <p>
     * 浓度数组按当前酶长度截断恢复：旧存档酶种不同则长度不同，
     * 截断后剩余物种浓度归零（换酶语义：反应态清空）
     *
     * @param tag        已读取的 NBT 标签
     * @param registries 注册表查找器
     */
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rebuildFromEnzymeSlot();
        if (simulator != null && tag.contains("concentrations")) {
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
        enzymeSnapshot = enzymeData == null ? "" : enzymeData.id();
        projectToSlots();
    }

    /**
     * 获取方块显示名（GUI 标题与玩家反馈共用；有酶 = 酶名，无酶 = 反应腔名）
     *
     * @return 方块翻译组件
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        if (enzymeData != null) {
            return net.minecraft.network.chat.Component.literal(enzymeData.nameZn());
        }
        return net.minecraft.network.chat.Component.translatable("block.biocraft.enzyme_chamber");
    }

    /**
     * 创建菜单：酶反应腔菜单（服务端）
     *
     * @param containerId     菜单容器编号
     * @param playerInventory 玩家物品栏
     * @param player          打开菜单的玩家
     * @return 酶反应腔菜单实例
     */
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.github.crafteve.biocraft.gui.MachineMenu(containerId, playerInventory, this, historySnapshot());
    }

    /**
     * 打开菜单时向客户端写入自定义数据（NeoForge 扩展点）
     * <p>
     * 写入内容：酶 id（空字符串 = 无酶，校验用）+ v-t 历史数组
     *
     * @param menu   刚创建的服务端菜单
     * @param buffer 打开数据包缓冲
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(enzymeData == null ? "" : enzymeData.id());
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
