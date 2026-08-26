package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 编辑器草稿保存包：客户端文本变化 → 服务端 BE 存档（跨 GUI 打开保留）
 * <p>
 * 载荷：方块坐标 + 草稿文本（UTF-8）。与提交包（ServerboundSequenceProgramPacket）
 * 的区别：只存档不触发编码流程（不归零/不弹出旧产物）
 */
public record ServerboundProgramDraftPacket(BlockPos pos, String draft) implements CustomPacketPayload {

    public static final Type<ServerboundProgramDraftPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "sequence_program_draft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundProgramDraftPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ServerboundProgramDraftPacket::pos,
                    ByteBufCodecs.STRING_UTF8, ServerboundProgramDraftPacket::draft,
                    ServerboundProgramDraftPacket::new);

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
                be.setProgramDraft(draft);
            }
        });
    }
}

