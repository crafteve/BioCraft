package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SynthesisStatus;
import com.github.crafteve.biocraft.gui.DNAEncoderMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * DNA 序列提交数据包（客户端→服务端）
 * <p>
 * DNA 编码器的唯一自定义网络包：玩家点击合成按钮后，客户端把序列文本
 * 提交给服务端，服务端在方块实体上执行权威合成
 * <p>
 * 为什么需要自定义包：序列文本只能来自玩家输入（GUI 文本框），
 * 没有对应的槽位/容器数据可以承载，必须显式传输
 *
 * @param sequence 玩家输入的 DNA 序列（仅含 A/T/C/G）
 */
public record ServerboundDnaSequencePacket(String sequence) implements CustomPacketPayload {
    /** 数据包类型标识（全局唯一资源路径） */
    public static final Type<ServerboundDnaSequencePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "dna_sequence"));

    /** 流编解码器：UTF-8 字符串直接映射 */
    public static final StreamCodec<ByteBuf, ServerboundDnaSequencePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ServerboundDnaSequencePacket::sequence,
                    ServerboundDnaSequencePacket::new);

    /**
     * 数据包类型访问器（CustomPacketPayload 协议方法）
     *
     * @return 类型标识
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：执行合成并反馈结果
     * <p>
     * 从玩家当前打开的菜单取回方块实体（玩家正对着机器 GUI 才能触发合成），
     * 合成结果通过菜单 ContainerData 自动同步回客户端 GUI 状态文本
     *
     * @param context 网络上下文（enqueueWork 保证在服务端主线程执行）
     */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isServerbound()) {
                return;
            }
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            // 仅当玩家菜单是 DNA 编码器时执行合成（防止伪造数据包远程操作其他方块）
            if (serverPlayer.containerMenu instanceof DNAEncoderMenu menu) {
                SynthesisStatus result = menu.getBlockEntity().synthesize(this.sequence);
                if (result != SynthesisStatus.SUCCESS) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("gui.biocraft.status." + result.name().toLowerCase()),
                            true);
                }
            }
        });
    }
}
