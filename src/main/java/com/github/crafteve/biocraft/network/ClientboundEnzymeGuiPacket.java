package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.client.aui.EnzymeGuiContext;
import com.github.crafteve.biocraft.client.aui.EnzymeGuiUpdater;
import com.github.crafteve.biocraft.init.EnzymeFactoryRegistry;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 酶工厂 GUI 运行时数据包（服务端→客户端）
 * <p>
 * 替代原 vanilla {@code ContainerData} 自动同步：AUI 的容器模型没有 ContainerData，
 * 酶引擎的连续状态（温度/通量/浓度/历史）无法用物品槽承载，必须走显式网络包。
 * 服务端在玩家打开酶工厂 GUI 期间每 tick 发送（值变化阈值优化留待调参阶段）
 * <p>
 * 载荷字段（全部定点缩放，避免浮点编解码）：
 * <ul>
 *   <li>enzymeId：酶注册名（客户端查表取静态档案）</li>
 *   <li>tempX100：温度×100</li>
 *   <li>fluxX1000：净通量×1000（堆叠分数/s，可为负表示逆向）</li>
 *   <li>concentrations：每物种浓度×1000（下标 = 物种下标）</li>
 *   <li>history：v-t 通量历史快照×1000（最旧→最新，100 tick = 5 秒）</li>
 * </ul>
 */
public record ClientboundEnzymeGuiPacket(
        String enzymeId,
        int tempX100,
        int fluxX1000,
        int[] concentrations,
        int[] history) implements CustomPacketPayload {

    /** 数据包类型标识（全局唯一资源路径） */
    public static final Type<ClientboundEnzymeGuiPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "enzyme_gui"));

    /** int 数组编解码器（长度前缀 + 逐元素 VAR_INT） */
    private static final StreamCodec<ByteBuf, int[]> INT_ARRAY = new StreamCodec<>() {
        @Override
        public int[] decode(ByteBuf buffer) {
            int length = ByteBufCodecs.VAR_INT.decode(buffer);
            int[] values = new int[length];
            for (int i = 0; i < length; i++) {
                values[i] = ByteBufCodecs.VAR_INT.decode(buffer);
            }
            return values;
        }

        @Override
        public void encode(ByteBuf buffer, int[] values) {
            ByteBufCodecs.VAR_INT.encode(buffer, values.length);
            for (int value : values) {
                ByteBufCodecs.VAR_INT.encode(buffer, value);
            }
        }
    };

    /** 流编解码器 */
    public static final StreamCodec<ByteBuf, ClientboundEnzymeGuiPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ClientboundEnzymeGuiPacket::enzymeId,
            ByteBufCodecs.VAR_INT, ClientboundEnzymeGuiPacket::tempX100,
            ByteBufCodecs.VAR_INT, ClientboundEnzymeGuiPacket::fluxX1000,
            INT_ARRAY, ClientboundEnzymeGuiPacket::concentrations,
            INT_ARRAY, ClientboundEnzymeGuiPacket::history,
            ClientboundEnzymeGuiPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：写入持有器并向打开的酶工厂文档推送
     * <p>
     * 酶数据由注册表同源查表（两端一致），静态 DOM 首次构建、动态文本持续刷新
     *
     * @param context 网络上下文（enqueueWork 保证客户端主线程执行）
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isClientbound()) {
                return;
            }
            EnzymeFactoryData data = EnzymeFactoryRegistry.byId(this.enzymeId);
            if (data == null) {
                return;
            }
            EnzymeGuiContext.update(data, this.tempX100, this.fluxX1000, this.concentrations, this.history);
            EnzymeGuiUpdater.pushAll();
        });
    }
}
