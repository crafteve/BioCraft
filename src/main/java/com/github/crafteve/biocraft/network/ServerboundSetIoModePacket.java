package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.enzyme.EnzymeMachineBlockEntity.IoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 IO 模式切换包：GUI 底部按钮点击 → 客户端 → 服务端
 * <p>
 * 载荷：方块坐标 + 区域编码（0 = INPUT、1 = OUTPUT）+ 目标模式编码
 * （IoMode 的 0/1/2）。服务端校验后写入对应酶反应腔方块实体，
 * 经 ContainerData 广播回客户端刷新按钮显示
 * <p>
 * 客户端发送：PacketDistributor.sendToServer(new ServerboundSetIoModePacket(...))
 */
public record ServerboundSetIoModePacket(BlockPos pos, int area, int mode) implements CustomPacketPayload {

    /** 包类型 id（服务端注册到 playToServer 通道） */
    public static final Type<ServerboundSetIoModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "set_io_mode"));

    /** 流编解码：方块坐标 + 两个 VAR_INT（区域、模式） */
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSetIoModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ServerboundSetIoModePacket::pos,
                    ByteBufCodecs.VAR_INT, ServerboundSetIoModePacket::area,
                    ByteBufCodecs.VAR_INT, ServerboundSetIoModePacket::mode,
                    ServerboundSetIoModePacket::new);

    /**
     * 包类型标识（CustomPacketPayload 契约）
     *
     * @return 本包的类型
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：校验后写入目标方块实体的 IO 模式
     * <p>
     * 校验项：服务端侧（客户端直接丢弃）、区域 0/1、模式 0~2、
     * 目标确为酶反应腔且玩家在 8 格内（与菜单 stillValid 同口径）
     *
     * @param ctx 网络上下文（enqueueWork 保证主线程执行）
     */
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null || player.level().isClientSide) {
                return;
            }
            if (area < 0 || area > 1 || mode < 0 || mode > 2) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof EnzymeMachineBlockEntity be
                    && player.blockPosition().distSqr(pos) <= 64) {
                be.setIoMode(area, IoMode.byId(mode));
            }
        });
    }
}

