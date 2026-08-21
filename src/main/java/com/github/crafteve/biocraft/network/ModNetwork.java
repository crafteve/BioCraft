package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络载荷注册中心：mod 总线事件注册全部自定义包
 * <p>
 * NeoForge 21.1 的 payload 机制（playToServer/playToClient 通道），
 * 注册后客户端/服务端按包类型自动分发（客户端发送
 * PacketDistributor.sendToServer 直达对应 handle）
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {

    private ModNetwork() {
    }

    /**
     * 注册全部自定义载荷（RegisterPayloadHandlersEvent，mod 装载期触发）
     *
     * @param event 载荷注册事件
     */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BioCraft.MODID);
        registrar.playToServer(ServerboundSetIoModePacket.TYPE, ServerboundSetIoModePacket.STREAM_CODEC,
                ServerboundSetIoModePacket::handle);
        registrar.playToServer(ServerboundSequenceProgramPacket.TYPE, ServerboundSequenceProgramPacket.STREAM_CODEC,
                ServerboundSequenceProgramPacket::handle);
        registrar.playToServer(ServerboundProgramDraftPacket.TYPE, ServerboundProgramDraftPacket.STREAM_CODEC,
                ServerboundProgramDraftPacket::handle);
        registrar.playToServer(ServerboundTranscribePacket.TYPE, ServerboundTranscribePacket.STREAM_CODEC,
                ServerboundTranscribePacket::handle);
    }
}
