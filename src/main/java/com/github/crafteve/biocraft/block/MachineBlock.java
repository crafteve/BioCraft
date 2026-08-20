package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 唯一的机器方块类（AGENTS.md 1.4 硬性规则）
 * <p>
 * 酶工厂方块时代结束：机器收敛为统一的"酶反应腔"（enzyme_chamber）——
 * 方块不持有任何酶数据，酶由方块实体从 0 槽（酶物品）动态解析，
 * 同一方块随插入的酶种不同而呈现不同反应网络
 * <p>
 * 方块类只承载方块行为（放置/右键交互/破坏掉落/硬度/地图色），
 * 机器的业务逻辑（容器/浓度/引擎）全部在 BlockEntity 层
 */
public class MachineBlock extends Block implements EntityBlock {

    /**
     * 水平朝向属性：正面（大观察窗）放置时面向玩家（熔炉同款）
     * <p>
     * 方块贴图是有方向的六面设计（正面观察窗/侧面管道/背面法兰），
     * 必须让玩家放置时正面朝向自己；旧存档方块无此属性自动取默认朝北
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

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
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
     * 声明方块状态属性（FACING 水平朝向）
     *
     * @param builder 状态定义构造器
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * 放置时朝向：正面面向放置玩家（熔炉同款 getOpposite）
     *
     * @param context 放置上下文
     * @return 含朝向的方块状态
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * 旋转支持（活塞/结构方块等），朝向随旋转角更新
     *
     * @param state    原方块状态
     * @param rotation 旋转角
     * @return 旋转后的方块状态
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * 镜像支持，镜像转为等效旋转
     *
     * @param state  原方块状态
     * @param mirror 镜像方向
     * @return 镜像后的方块状态
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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

    /**
     * 右键交互：打开机器 GUI
     * <p>
     * BlockEntity 实现 MenuProvider，玩家 openMenu 时写入方块位置，
     * 客户端据此重建容器（Menu 构造从数据包读取 BlockPos）
     *
     * @param state     方块状态
     * @param level     所在世界
     * @param pos       方块位置
     * @param player    交互玩家
     * @param hitResult 命中信息
     * @return 服务端/客户端均返回成功，保证客户端渲染"交互成功"动画
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            // NeoForge 打开数据：BlockPos + 子类钩子（编码器追加编辑器草稿）
            player.openMenu(machine, buf -> {
                buf.writeBlockPos(pos);
                machine.writeMenuOpeningData(buf);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 方块被破坏时把容器内容全部掉落为实体物品
     * <p>
     * 掉落逻辑必须放在 onRemove 而非 BlockEntity.setRemoved：
     * 世界卸载/区块卸载同样触发 setRemoved，会误爆库存（实测 bug）
     *
     * @param state         原方块状态
     * @param level         所在世界
     * @param pos           方块位置
     * @param newState      新方块状态
     * @param movedByPiston 是否为活塞推动
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
                dropContainerContents(level, pos, machine);
                machine.dropExtraContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 容器内容掉落：按 64 拆堆生成掉落物实体
     * <p>
     * 槽位容量放大到 128 后，Containers.dropContents 会直接生成
     * count=128 的 ItemEntity——其存档同样走 ItemStack.CODEC 的
     * [1,99] count 校验，进出存档即崩溃（与 BE 存档同根因）。
     * 此处按物品最大堆叠拆成多堆掉落，每堆 ≤64 存档安全
     *
     * @param level   所在世界
     * @param pos     方块位置
     * @param machine 机器方块实体
     */
    private static void dropContainerContents(Level level, BlockPos pos, MachineBlockEntity machine) {
        net.minecraft.world.SimpleContainer container = machine.getContainer();
        for (int i = 0; i < container.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = container.getItem(i);
            while (!stack.isEmpty()) {
                int drop = Math.min(stack.getCount(), 64);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        stack.split(drop));
            }
        }
    }
}
