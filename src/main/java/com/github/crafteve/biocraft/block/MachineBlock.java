package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * 酶反应腔方块（enzyme_chamber 专用）
 * <p>
 * 机器两大族之一：化学反应线。方块不持有任何酶数据，酶由方块实体从
 * 0 槽（酶蛋白物品）动态解析，同一方块随插入的酶种不同而呈现不同反应网络
 * <p>
 * 方块类只承载皮层行为（放置/右键交互/破坏掉落/硬度/地图色/tick 调度），
 * 机器的业务逻辑（容器/浓度/引擎/IO 模式）全部在 BlockEntity 层
 * <p>
 * 与 {@link SequenceMachineBlock} 并列，共享 {@link BioCraftMachineBlock} 基类
 */
public class MachineBlock extends BioCraftMachineBlock {

    /**
     * 酶反应腔构造（唯一机器形态，无参数——酶数据来自 0 槽物品）
     * <p>
     * 地图色统一取灰色（地图上不做区分）；贴图为六面机器模型
     * （base 中性贴图 + 贴片元素 tint 分区，主题色随 0 槽酶动态变化）
     *
     * @param properties 方块属性
     */
    public MachineBlock(Properties properties) {
        super(properties);
    }

    /**
     * 无参数构造（DeferredRegister 默认工厂）：硬度/声音/地图色统一
     */
    public MachineBlock() {
        this(Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .sound(SoundType.METAL));
    }

    /**
     * 创建酶反应腔方块实体
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 酶反应腔实体
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnzymeFactoryBlockEntity(pos, state);
    }

    /**
     * 方块实体每 tick 调度器（1.21.1 机制：getTicker 属于 EntityBlock 接口，
     * 不在 BlockEntityType 侧，与 1.20 及更早版本不同）
     * <p>
     * 服务端每 tick 执行引擎桥接流水线；客户端无 tick 逻辑返回 null
     *
     * @param level 所在世界
     * @param state 方块状态
     * @param type  方块实体类型
     * @return 服务端 ticker（酶反应腔）
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) ->
                EnzymeFactoryBlockEntity.serverTick(lvl, pos, st, (EnzymeFactoryBlockEntity) be);
    }
}
