package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络数据包注册中心
 * <p>
 * NeoForge 1.21.1 的 payload 注册新风格：在 RegisterPayloadHandlersEvent 中
 * 通过 PayloadRegistrar 声明每个数据包的类型、编解码器与处理器
 * <p>
 * 当前有两个数据包：DNA 序列提交（客户端→服务端）、酶工厂 GUI 运行时数据
 * （服务端→客户端，替代 AUI 容器模型下缺失的 ContainerData 自动同步）
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    /** 网络协议版本号，客户端与服务端不一致时连接失败（快速暴露版本漂移） */
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    /**
     * 注册全部数据包（mod 事件总线的 RegisterPayloadHandlersEvent）
     *
     * @param event 数据包注册事件
     */
    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BioCraft.MODID).versioned(PROTOCOL_VERSION);
        registrar.playToServer(
                ServerboundDnaSequencePacket.TYPE,
                ServerboundDnaSequencePacket.STREAM_CODEC,
                ServerboundDnaSequencePacket::handle);
        registrar.playToClient(
                ClientboundEnzymeGuiPacket.TYPE,
                ClientboundEnzymeGuiPacket.STREAM_CODEC,
                ClientboundEnzymeGuiPacket::handle);
    }
}
