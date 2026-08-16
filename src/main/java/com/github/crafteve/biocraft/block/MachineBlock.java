package com.github.crafteve.biocraft.block;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineBlockEntity;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 唯一的机器方块类（AGENTS.md 1.4 硬性规则）
 * <p>
 * DNA 编码器移除后，机器只剩酶工厂（数据驱动注册）：方块持有酶数据档案，
 * 放置时由共享的酶工厂方块实体按方块取回数据；BlockEntity 统一为
 * 酶工厂实体，每 tick 由本类 getTicker 调度引擎流水线
 * <p>
 * 方块类只承载方块行为（放置/右键交互/破坏掉落/硬度/地图色），
 * 机器的业务逻辑（容器/浓度/引擎）全部在 BlockEntity 层
 */
public class MachineBlock extends Block implements EntityBlock {
    /** 酶数据档案（数据驱动注册时绑定，方块实体放置时取回） */
    private final EnzymeFactoryData enzymeFactoryData;

    /**
     * 酶工厂构造（数据驱动注册）
     * <p>
     * 地图色统一取灰色（地图上不做区分，视觉区分由方块 tint 承担）；
     * 方块持有数据档案的引用，方块实体放置时从方块取回（共享 BE 类型无需每实例注册）
     *
     * @param enzymeFactoryData 酶数据档案
     */
    public MachineBlock(EnzymeFactoryData enzymeFactoryData) {
        super(Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F)
                .sound(SoundType.METAL));
        this.enzymeFactoryData = enzymeFactoryData;
    }

    /**
     * 创建酶工厂方块实体（本类方块全部为酶工厂）
     *
     * @param pos   方块位置
     * @param state 方块状态
     * @return 酶工厂实体
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnzymeFactoryBlockEntity(pos, state);
    }

    /**
     * 方块实体每 tick 调度器（1.21.1 机制：getTicker 属于 EntityBlock 接口，
     * 不在 BlockEntityType 侧，与 1.20 及更早版本不同）
     * <p>
     * 酶工厂服务端每 tick 执行引擎桥接流水线；客户端无 tick 逻辑返回 null
     *
     * @param level 所在世界
     * @param state 方块状态
     * @param type  方块实体类型
     * @return 服务端 ticker（酶工厂）
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
            player.openMenu(machine, pos);
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

    /**
     * 获取酶数据档案（方块实体放置时取回数据，客户端染色同源）
     *
     * @return 酶数据档案
     */
    public EnzymeFactoryData getEnzymeFactoryData() {
        return enzymeFactoryData;
    }
}
