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

    /**
     * 主题液体色缓存（ARGB，客户端 BlockColor 0 号 tint 查询用，O(1) 免每帧查酶）
     * <p>
     * 无酶 = 空机暗灰（液体槽位呈现"空窗/空管"）；有酶 = 酶数据表主题色，
     * 由 rebuildFromEnzymeSlot 随换酶同步更新（客户端渲染线程只读本字段，
     * 与引擎数据无耦合，服务端/客户端各自维护）
     */
    private int themeLiquidArgb = EMPTY_LIQUID_ARGB;

    /**
     * 状态灯色缓存（ARGB，客户端 BlockColor 1 号 tint 查询用）
     * <p>
     * 状态灯三态（用户 2026-08-16 重定语义，替代原主题灯）：
     * 无酶 = 灭灯暗色；有酶且全部浓度 ≈0 = 黄灯（等料）；
     * 有酶且反应速度 v < 1e-6 = 红灯（停摆/平衡待干预）；
     * 浓度正常且速度正常 = 绿灯（平稳运行）
     */
    private int themeLampArgb = EMPTY_LAMP_ARGB;

    /** 空机液体暗灰（无酶时所有液体槽位的 tint 色） */
    public static final int EMPTY_LIQUID_ARGB = 0xFF2A2F38;

    /** 灭灯暗色（无酶时所有指示灯的 tint 色） */
    public static final int EMPTY_LAMP_ARGB = 0xFF1D2129;

    /** 红灯（反应速度 v < 1e-6：停摆/平衡，需要玩家干预） */
    public static final int LAMP_RED_ARGB = 0xFFE53935;

    /** 黄灯（全部物种浓度 ≈0：等料/空闲） */
    public static final int LAMP_YELLOW_ARGB = 0xFFF2C94C;

    /** 绿灯（浓度正常且速度正常：平稳运行） */
    public static final int LAMP_GREEN_ARGB = 0xFF43A047;

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
    /**
     * 反应速率缓存（×1000 定点）：主产物槽位投影增量 / 主产物系数
     * （反应次数/tick，1 tick 实时）——替代引擎瞬时净通量（瞬时值在
     * 平衡区/抽取工况无法反映实际吞吐，实测 TPI"显示 0.0 但管道飞快"）
     */
    private int cachedFluxX1000;
    private int cachedTempX100;
    private int cachedProgressX1000;

    /** 工业 IO 适配器（懒加载单例：管道查询 capability 时复用同一实例，避免每 tick 分配） */
    private EnzymeFactoryItemHandler itemHandler;

    /**
     * INPUT 区域（反应物槽）IO 模式，默认仅输入（只允许物品进入）
     * <p>
     * 由 GUI 底部按钮点击切换（网络包写入），经 ContainerData 同步回客户端；
     * 存档持久化；换酶不重置（机器级设置，反应物/产物侧语义随酶保持）
     */
    private IoMode inputIoMode = IoMode.INPUT_ONLY;

    /** OUTPUT 区域（产物槽）IO 模式，默认仅输出（只允许物品抽出） */
    private IoMode outputIoMode = IoMode.OUTPUT_ONLY;

    /** v-t 通量历史环形缓冲（200 tick = 10 秒，打开 GUI 时一次性下发，不存档） */
    private static final int HISTORY_LENGTH = 200;
    private final int[] fluxHistory = new int[HISTORY_LENGTH];
    private int historyIndex;

    /** 酶槽当前酶 id 快照（换酶检测：对比本值与 0 槽解析结果） */
    private String enzymeSnapshot = "";

    /**
     * 主产物引擎物种下标（-1 = 无）与化学计量系数
     * <p>
     * 主产物 = 产物列表中第一个"非 fe 且非固定活性"物种（避开 fe/水/H⁺）；
     * GUI 反应速率 = 主产物槽位投影增量 / 主产物系数（反应进度变化率，
     * 反应次数/tick，1 tick 实时）——玩家视角的"反应速度"，替代引擎
     * 瞬时净通量（瞬时值在平衡区/抽取工况下无法反映实际吞吐）
     */
    private int mainProductSpeciesIndex = -1;

    /** 主产物化学计量系数（反应进度换算：速率 = 增量/系数） */
    private int mainProductStoich = 1;

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
     * <p>
     * 无论新旧酶，一律彻底重置：旧引擎实例丢弃（新 simulator 全新浓度
     * 数组 = 0）、余量清零（否则换酶后残留旧浓度尾数，回写会把浓度
     * 抬回非零）、观测缓存/能量快照复位（GUI 不残留旧酶读数）
     */
    private void rebuildFromEnzymeSlot() {
        EnzymeFactoryData data = resolveEnzyme();
        this.enzymeData = data;
        this.energyStorage = null;
        this.energyStored = 0;
        this.energyStoredSnapshot = -1;
        this.cachedEnergyRate = 0;
        this.cachedFluxX1000 = 0;
        this.cachedTempX100 = (int) Math.round(KineticConstants.T0 * 100.0);
        this.cachedProgressX1000 = 0;
        java.util.Arrays.fill(slotToSpeciesIndex, -1);
        java.util.Arrays.fill(remainder, 0.0);
        if (data == null) {
            this.simulator = null;
            this.speciesIds = new String[0];
            this.feSpeciesIndex = -1;
            this.themeLiquidArgb = EMPTY_LIQUID_ARGB;
            this.themeLampArgb = EMPTY_LAMP_ARGB;
            return;
        }
        this.themeLiquidArgb = data.color();
        // 新引擎浓度全 0 = 等料，状态灯初始黄灯（首个 tick 的 updateStatusLamp 修正）
        this.themeLampArgb = LAMP_YELLOW_ARGB;
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
        // 主产物定位：产物列表中第一个非 fe 非固定活性物种（速率显示口径）
        this.mainProductSpeciesIndex = -1;
        this.mainProductStoich = 1;
        for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
            if (EnergyKinetics.isEnergySpecies(spec.item())
                    || KineticConstants.FIXED_ACTIVITY_SPECIES.contains(spec.item())) {
                continue;
            }
            for (int i = 0; i < speciesIds.length; i++) {
                if (speciesIds[i].equals(spec.item())) {
                    mainProductSpeciesIndex = i;
                    break;
                }
            }
            mainProductStoich = spec.count();
            break;
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
        // 主题色已随换酶更新（rebuildFromEnzymeSlot 内）：通知客户端重渲染
        // （sendBlockUpdated → BlockUpdatePacket + 本 BE 的 update packet，
        // 客户端 handleUpdateTag 读回新颜色后 LevelRenderer 重烘焙该方块，
        // BlockColor 按新主题色重新查询——不加此通知则客户端恒显示旧色）
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
     * 获取主题液体色（ARGB，客户端方块染色 0 号 tint 数据源）
     *
     * @return 酶主题色（无酶 = 空机暗灰）
     */
    public int getThemeLiquidArgb() {
        return themeLiquidArgb;
    }

    /**
     * 获取主题灯色（ARGB，客户端方块染色 1 号 tint 数据源）
     *
     * @return 提亮酶主题色（无酶 = 灭灯暗色）
     */
    public int getThemeLampArgb() {
        return themeLampArgb;
    }

    /**
     * 更新包 tag：主题色随 BE 数据同步到客户端
     * <p>
     * 客户端 BE 无引擎/无 tick，酶变化只发生在服务端——主题色必须走
     * BE 数据同步通道（区块加载 update tag + 换酶时 update packet），
     * 否则客户端 BlockColor 恒取构造时的空机暗灰（实测 bug 根因）
     *
     * @param registries 注册表查找器
     * @return 含主题色的 NBT
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("themeLiquidArgb", themeLiquidArgb);
        tag.putInt("themeLampArgb", themeLampArgb);
        return tag;
    }

    /**
     * 客户端接收更新 tag：读回主题色
     * <p>
     * 空 tag（vanilla 默认 update tag 为空）或旧版本存档缺字段时
     * 保留构造默认值（空机暗灰），防误读为 0
     *
     * @param tag        服务端下发的 NBT
     * @param registries 注册表查找器
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.contains("themeLiquidArgb")) {
            this.themeLiquidArgb = tag.getInt("themeLiquidArgb");
        }
        if (tag.contains("themeLampArgb")) {
            this.themeLampArgb = tag.getInt("themeLampArgb");
        }
        // 客户端收到 BE 数据包后必须主动触发本地重渲染：随包下发的
        // BlockUpdatePacket 的 state 与当前相同，Level.setBlock 因状态不变
        // 直接 no-op（源码实证），不会重烘焙该方块——不主动刷新则 BlockColor
        // 不会重新查询（实测"热插拔不变色、读档才刷新"根因）；区块加载时
        // 冗余触发一次无害
        requestThemeRenderUpdate();
    }

    /**
     * 换酶时的增量数据包：带主题色的更新包（SignBlockEntity 同款标准姿势）
     * <p>
     * 服务端 handleEnzymeSlotChanged 里 sendBlockUpdated 触发本包发送，
     * 客户端 handleUpdateTag 读回颜色后由 LevelRenderer 重渲染该方块
     *
     * @return BE 数据包
     */
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
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
     * 获取 INPUT 区域（反应物侧）IO 模式
     *
     * @return 当前模式
     */
    public IoMode getInputIoMode() {
        return inputIoMode;
    }

    /**
     * 获取 OUTPUT 区域（产物侧）IO 模式
     *
     * @return 当前模式
     */
    public IoMode getOutputIoMode() {
        return outputIoMode;
    }

    /**
     * 设置指定区域的 IO 模式（服务端网络包入口）
     * <p>
     * 区域编码：0 = INPUT（反应物侧）、1 = OUTPUT（产物侧）；
     * 变更触发 setChanged（Menu 数据刷新 + 存档脏标记）
     *
     * @param area 区域编码（0/1）
     * @param mode 新模式
     */
    public void setIoMode(int area, IoMode mode) {
        if (area == 0) {
            inputIoMode = mode;
        } else {
            outputIoMode = mode;
        }
        setChanged();
    }

    /**
     * 槽位所属 IO 区域：0 = INPUT（反应物侧）、1 = OUTPUT（产物侧）、-1 = 酶槽/未映射
     * <p>
     * 依据当前酶的酶数据表判定（反应物列表 = INPUT、产物列表 = OUTPUT），
     * 与 GUI 滚动区布局同源；无酶时返回 -1（无门控）
     *
     * @param slot 容器槽位下标（0 = 酶槽）
     * @return 区域编码（-1 = 无门控区域）
     */
    public int ioAreaOfSlot(int slot) {
        if (slot == ENZYME_SLOT || enzymeData == null) {
            return -1;
        }
        String speciesId = getSpeciesId(slot);
        if (speciesId == null) {
            return -1;
        }
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.reactants()) {
            if (spec.item().equals(speciesId)) {
                return 0;
            }
        }
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.products()) {
            if (spec.item().equals(speciesId)) {
                return 1;
            }
        }
        return -1;
    }

    /**
     * 槽位是否允许物品插入（IO 门控：仅输入/双向允许，仅输出禁止）
     * <p>
     * 插入路径统一入口：GUI Slot.mayPlace、管道 isItemValid、
     * 漏斗 canPlaceItem 三路都经本方法（酶槽/未映射槽恒允许）
     *
     * @param slot 容器槽位下标
     * @return true 表示允许插入
     */
    public boolean canInsertIntoSlot(int slot) {
        int area = ioAreaOfSlot(slot);
        if (area < 0) {
            return true;
        }
        return (area == 0 ? inputIoMode : outputIoMode).allowsInsert();
    }

    /**
     * 槽位是否允许物品抽出（IO 门控：仅输出/双向允许，仅输入禁止）
     * <p>
     * 抽出路径统一入口：GUI Slot.mayPickup、管道 extractItem、
     * 漏斗 removeItem 三路都经本方法（酶槽/未映射槽恒允许）
     *
     * @param slot 容器槽位下标
     * @return true 表示允许抽出
     */
    public boolean canExtractFromSlot(int slot) {
        int area = ioAreaOfSlot(slot);
        if (area < 0) {
            return true;
        }
        return (area == 0 ? inputIoMode : outputIoMode).allowsExtract();
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
     * 容器插入许可钩子（原版漏斗 canPlaceItem 路径）：IO 模式门控
     * <p>
     * 漏斗塞入前询问 canPlaceItem——模式禁止插入时直接拒绝，
     * 与 GUI（mayPlace）/管道（isItemValid）同规则，避免非法物品
     * 进槽后再由防呆弹出
     *
     * @param slot  目标槽位
     * @param stack 待插入物品
     * @return true 表示允许
     */
    @Override
    protected boolean canPlaceItemInternal(int slot, ItemStack stack) {
        return canInsertIntoSlot(slot);
    }

    /**
     * 容器抽取许可钩子（原版漏斗 removeItem 路径）：IO 模式门控
     * <p>
     * 漏斗抽出走容器 removeItem 无任何权限询问——基类容器经本钩子
     * 拦截，模式禁止抽出时返回空堆（物品原地不动）
     *
     * @param slot 源槽位
     * @return true 表示允许
     */
    @Override
    protected boolean canTakeItemInternal(int slot) {
        return canExtractFromSlot(slot);
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
        if (level != null && level.isClientSide) {
            // 客户端酶槽变化（GUI 操作经菜单槽位同步触发）：即时刷新主题色与
            // 重渲染，不依赖服务端 data packet 的到达时序（双通道之一）
            refreshThemeFromEnzymeSlot();
        }
    }

    /**
     * 客户端从 0 槽即时解析液体主题色（GUI 操作路径，setChanged 触发）
     * <p>
     * 颜色变化才更新并请求重渲染（物种槽变动也会触发 setChanged，
     * 但酶没换时颜色不变，零重渲染开销）；与服务端 data packet 通道
     * 互为双保险——GUI 路径即时变色，管道/漏斗路径靠 data packet；
     * 灯色是状态灯（黄/红/绿），只由服务端 updateStatusLamp 判定，
     * 本方法不触碰灯色（避免覆盖状态）
     *
     * @see #handleUpdateTag(CompoundTag, HolderLookup.Provider)
     */
    private void refreshThemeFromEnzymeSlot() {
        EnzymeFactoryData data = resolveEnzyme();
        int liquid = data == null ? EMPTY_LIQUID_ARGB : data.color();
        if (liquid != themeLiquidArgb) {
            themeLiquidArgb = liquid;
            requestThemeRenderUpdate();
        }
    }

    /**
     * 请求客户端重渲染本方块（主题色变化的渲染刷新）
     * <p>
     * 客户端 sendBlockUpdated → LevelRenderer.blockChanged → setBlockDirty
     * → section 重烘焙 → BlockColor 重新查询；无递归（只标记 dirty 不触
     * 发 setChanged/数据包）
     */
    private void requestThemeRenderUpdate() {
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 状态灯更新（每 tick 判定，服务端专用，用户 2026-08-16 重定语义）
     * <p>
     * 三态判定顺序：
     * <ol>
     *   <li>全部物种浓度 ≈0 → 黄灯（等料/空闲）</li>
     *   <li>反应速度 v < 1e-6 → 红灯（停摆/平衡，需要玩家干预）</li>
     *   <li>其余 → 绿灯（浓度正常且速度正常，平稳运行）</li>
     * </ol>
     * v = cachedFluxX1000 / 1000（反应次数/tick 定点 ×1000，与 GUI 速率
     * 读数同口径）；cachedFluxX1000 为 int 定点，v < 1e-6 等价于
     * 定点值 < 1e-3，即整数 0 或负（无正向产出，含平衡/逆向吞料）；状态翻转时把灯色缓存切换并走主题色更新包通道通知
     * 客户端（getUpdatePacket 携带 themeLampArgb，客户端 handleUpdateTag
     * + requestThemeRenderUpdate 完成重渲染闭环，零客户端改动）；
     * 以 themeLampArgb 为"已同步状态"做变化检测，避免每 tick 广播
     *
     * @param allZero 本次判定是否全部物种浓度 ≈0（tick 流水线的 asleep 判定）
     */
    private void updateStatusLamp(boolean allZero) {
        if (enzymeData == null || simulator == null) {
            return; // 无酶保持灭灯（EMPTY_LAMP_ARGB）
        }
        int newLamp = allZero ? LAMP_YELLOW_ARGB
                : (cachedFluxX1000 < 1e-3 ? LAMP_RED_ARGB : LAMP_GREEN_ARGB);
        if (newLamp != themeLampArgb) {
            themeLampArgb = newLamp;
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
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
     * 槽位合法性防呆 → 事件回写 → 引擎 step → 浓度投影回槽位 → 观测数据缓存
     * <p>
     * 酶槽变动由 setChanged 事件驱动立即初始化（handleEnzymeSlotChanged，
     * GUI/漏斗/管道全部经容器 setItem → setChanged 捕获，无需 tick 兜底）；
     * 无酶时引擎睡眠：本 tick 只做防呆（非法物品弹出）后直接返回，
     * 不执行任何引擎/缓存工作——放入酶的事件会立即唤醒初始化
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
        if (simulator == null) {
            // 无酶睡眠：引擎不步进，变化由 setChanged 事件唤醒；
            // 防呆已在上面执行，直接返回（省去缓存/历史写入）
            return;
        }
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
            settleEnergy(result.fluxNet());
            projectToSlots();
        }
        // 状态灯判定（在 updateCachedData 之后执行，v 用最新值）：
        // 浓度全 0 = 黄灯（等料）；v < 1e-6 = 红灯（停摆/平衡）；正常 = 绿灯
        updateCachedData();
        updateStatusLamp(asleep);
        // 能量存档脏标记：settleEnergy 只更新存量，标记放在投影之后——
        // 此时槽位浓度与引擎一致，setChanged → syncFromSlots 幂等
        if (energyStored != energyStoredSnapshot) {
            energyStoredSnapshot = energyStored;
            setChanged();
            notifyEnergyCapabilityChange();
        }
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
     * <p>
     * 每 tick 顺带统计反应速率：主产物槽位增量 / 主产物化学计量系数
     * （反应进度变化率，反应次数/tick）——引擎投影是"实际落到槽位的
     * 变化"（外部抽取不干扰，抽取发生在投影之后），1 tick 实时，
     * 替代引擎瞬时净通量（瞬时值在平衡区/抽取工况无法反映实际吞吐）
     */
    private void projectToSlots() {
        if (simulator == null) {
            return;
        }
        projecting = true;
        try {
            double[] x = simulator.getState().getConcentrations();
            int slotLimit = slotStackLimit();
            double rateDelta = 0.0;
            for (int slot = SPECIES_SLOT_BASE; slot < slotToSpeciesIndex.length; slot++) {
                int speciesIndex = slotToSpeciesIndex[slot];
                if (speciesIndex < 0) {
                    continue;
                }
                double total = x[speciesIndex] * 64.0;
                int count = Math.min((int) Math.floor(total), slotLimit);
                remainder[slot] = total - count;
                ItemStack stack = inventory.getItem(slot);
                int oldCount = stack.getCount();
                if (oldCount == count) {
                    continue;
                }
                if (count <= 0) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                } else if (stack.isEmpty()) {
                    inventory.setItem(slot, new ItemStack(ModItems.byId(speciesIds[speciesIndex]).get(), count));
                } else {
                    stack.setCount(count);
                }
                if (speciesIndex == mainProductSpeciesIndex) {
                    rateDelta = (count - oldCount) / (double) mainProductStoich;
                }
            }
            // 1 tick 实时反应速率（反应次数/tick ×1000 定点）；
            // 睡眠态（浓度全 0）由 tickServer 提前置 0，此处恒有引擎步进
            cachedFluxX1000 = (int) Math.round(rateDelta * 1000.0);
            logBeSlotState();
        } finally {
            projecting = false;
        }
    }

    /**
     * 临时测试点：BE 容器投影后槽位 count（服务端权威基准）
     * <p>
     * 定位"平衡 TPI 取走 G3P 后 GUI 反应物与生成物飞速消失"用——
     * 与 [MENU-SRV]（服务端 Menu 视角）对比，验证 menu↔BE 是否同源，
     * 定位后删除
     */
    private void logBeSlotState() {
        if (level == null || level.isClientSide) {
            return;
        }
        StringBuilder sb = new StringBuilder("[BE-CONC] t=").append(level.getGameTime());
        for (int s = SPECIES_SLOT_BASE; s < slotToSpeciesIndex.length; s++) {
            sb.append(" s").append(s).append("=").append(inventory.getItem(s).getCount());
        }
        BioCraft.LOGGER.info(sb.toString());
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
        tag.putByte("inputIoMode", (byte) inputIoMode.id());
        tag.putByte("outputIoMode", (byte) outputIoMode.id());
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
        // IO 模式恢复（旧存档无字段 → byId(0) 落回默认：INPUT 仅输入/OUTPUT 仅输出）
        inputIoMode = IoMode.byId(tag.getByte("inputIoMode"));
        outputIoMode = IoMode.byId(tag.getByte("outputIoMode"));
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
