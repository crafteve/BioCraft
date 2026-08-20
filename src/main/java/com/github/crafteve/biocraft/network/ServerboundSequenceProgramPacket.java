package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端程序文本提交包：编码器文本编辑器 → 客户端 → 服务端 BE
 * <p>
 * 载荷：方块坐标 + 程序文本（UTF-8）。服务端校验后调 BE.submitProgram
 * （换文本 = 换模板语义：归零 + 旧产物弹出）
 */
public record ServerboundSequenceProgramPacket(BlockPos pos, String program) implements CustomPacketPayload {

    public static final Type<ServerboundSequenceProgramPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "sequence_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundSequenceProgramPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ServerboundSequenceProgramPacket::pos,
                    ByteBufCodecs.STRING_UTF8, ServerboundSequenceProgramPacket::program,
                    ServerboundSequenceProgramPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null || player.level().isClientSide) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof SequenceMachineBlockEntity be
                    && player.blockPosition().distSqr(pos) <= 64) {
                be.submitProgram(program);
            }
        });
    }
}
