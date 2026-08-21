package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 转录按钮包：客户端点击转录区右下角“转录”按钮 → 服务端
 * 仿 dnaEncoder 的编码按钮，手动检测条件并启动转录（自动 tick 仍保留，按钮为显式触发）
 */
public record ServerboundTranscribePacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ServerboundTranscribePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "transcribe"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ServerboundTranscribePacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ServerboundTranscribePacket::pos,
                    ServerboundTranscribePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null || player.level().isClientSide) return;
            if (player.level().getBlockEntity(pos) instanceof SequenceMachineBlockEntity be
                    && be.kind() == SequenceMachineKind.TRANSCRIBER
                    && player.blockPosition().distSqr(pos) <= 64) {
                if (be.stepState().stage() == com.github.crafteve.biocraft.blockentity.SeqStepState.Stage.IDLE
                        && be.operation().canStart(be.getContainer(), be.stepState())
                        && be.operation().init(be.getContainer(), be.stepState())) {
                    var tmpl = be.getContainer().getItem(com.github.crafteve.biocraft.blockentity.TranscriptionOperation.SLOT_TEMPLATE);
                    var d = tmpl.get(com.github.crafteve.biocraft.init.ModDataComponents.SEQUENCE.get());
                    be.setLastTemplateSeq(d != null ? d.seq() : "");
                    be.setChanged();
                }
            }
        });
    }
}
