package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.block.MachineBlock;
import com.github.crafteve.biocraft.init.ModBlocks;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.EnzymeSimulator;
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

    /** 物种注册名数组（槽位下标 = 物种下标） */
    private final String[] speciesIds;

    /** 每物种余量（浓度小数部分 = 下一个物品的积累进度，GUI 进度条数据源） */
    private final double[] remainder;

    /** 投影递归守卫：投影修改槽位会触发 setChanged → 回写，需拦截避免互相覆盖 */
    private boolean projecting;

    /** 观测数据缓存（M4 的 Menu ContainerData 读取，每 tick 更新） */
    private int cachedFluxX1000;
    private int cachedTempX100;
    private int cachedProgressX1000;

    /** v-t 通量历史环形缓冲（100 tick = 5 秒，打开 GUI 时一次性下发，不存档） */
    private static final int HISTORY_LENGTH = 100;
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
     * 统一私有构造：槽位数从酶数据推导，不再依赖方块状态强转
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @param data  酶数据档案
     */
    private EnzymeFactoryBlockEntity(BlockPos pos, BlockState state, EnzymeFactoryData data) {
        super(ModBlocks.ENZYME_FACTORY_BE.get(), pos, state,
                data.reactants().size() + data.products().size());
        this.enzymeData = data;
        this.simulator = enzymeData.buildSimulator();
        this.speciesIds = simulator.getDefinition().getSpeciesIds();
        this.remainder = new double[speciesIds.length];
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
     * 获取引擎模拟器实例
     *
     * @return 引擎模拟器
     */
    public EnzymeSimulator getSimulator() {
        return simulator;
    }

    /**
     * 获取物种注册名（GUI 槽位映射与调试用）
     *
     * @param slot 槽位下标
     * @return 物种物品注册名
     */
    public String getSpeciesId(int slot) {
        return speciesIds[slot];
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
     * 每个物种：浓度 = (槽位数量 + 余量)/64，钳制 [0,1]；
     * 投影自身修改槽位时由 projecting 守卫跳过，避免递归覆盖
     */
    private void syncFromSlots() {
        if (projecting || level == null || level.isClientSide) {
            return;
        }
        double[] x = simulator.getState().getConcentrations();
        for (int i = 0; i < speciesIds.length; i++) {
            int count = inventory.getItem(i).getCount();
            x[i] = KineticsCalculator.clamp01((count + remainder[i]) / 64.0);
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
            projectToSlots();
        }
        updateCachedData();
        fluxHistory[historyIndex] = cachedFluxX1000;
        historyIndex = (historyIndex + 1) % HISTORY_LENGTH;

        if (level.getGameTime() % 20 == 0) {
            BioCraft.LOGGER.debug("酶工厂 [{}] 槽位: {}, 浓度: {}, 通量×1000: {}",
                    enzymeData.id(), slotSummary(), concentrationSummary(), cachedFluxX1000);
        }
    }

    /**
     * 浓度 → 槽位整数投影（每 tick 引擎 step 后）
     * <p>
     * 槽位数量 = floor(浓度×64)，余量 = 浓度×64 − 数量；
     * 槽位满（64）时浓度钳制 1.0 与引擎边界截断同步，
     * 产物满堆反应自然停转，取走产物即恢复
     */
    private void projectToSlots() {
        projecting = true;
        try {
            double[] x = simulator.getState().getConcentrations();
            for (int i = 0; i < speciesIds.length; i++) {
                double total = x[i] * 64.0;
                int count = (int) Math.floor(total);
                remainder[i] = total - count;
                ItemStack stack = inventory.getItem(i);
                if (stack.getCount() == count) {
                    continue;
                }
                if (count <= 0) {
                    inventory.setItem(i, ItemStack.EMPTY);
                } else if (stack.isEmpty()) {
                    inventory.setItem(i, new ItemStack(ModItems.byId(speciesIds[i]).get(), count));
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
        for (int i = 0; i < speciesIds.length; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.byId(speciesIds[i]).get())) {
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
        for (int i = 0; i < speciesIds.length; i++) {
            sb.append(speciesIds[i]).append('=').append(inventory.getItem(i).getCount()).append(' ');
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
     * 创建菜单：酶工厂菜单（服务端，历史快照传入供客户端初始化 v-t 图）
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
