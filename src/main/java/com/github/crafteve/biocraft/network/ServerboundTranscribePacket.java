package com.github.crafteve.biocraft.network;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineBlockEntity;
import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 序列机启动工序包：客户端点击"编码/转录/翻译"按钮 → 服务端手动触发开工
 * <p>
 * 泛化自原转录包（原只支持 CONSOLE_TRANSCRIBER）：服务端按 BE kind 校验，
 * 编码器/转录仪/翻译机共用同一通道——三机均为"点按钮才开工"语义（禁自动），
 * 触发时执行 canStart+init；转录仪/翻译机额外记录模板指纹
 * （编码器链源是提交的程序文本，由 submitProgram 归零语义覆盖，无指纹）
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
                    && (be.kind() == SequenceMachineKind.DNA_ENCODER
                        || be.kind() == SequenceMachineKind.TRANSCRIBER
                        || be.kind() == SequenceMachineKind.TRANSLATOR)
                    && player.blockPosition().distSqr(pos) <= 64) {
                if (be.stepState().stage() == com.github.crafteve.biocraft.blockentity.sequence.SeqStepState.Stage.IDLE
                        && be.operation().canStart(be.getContainer(), be.stepState())
                        && be.operation().init(be.getContainer(), be.stepState())) {
                    // 记录模板指纹：转录仪 = ssDNA 模板槽，翻译机 = mRNA 槽；
                    // 编码器链源是程序文本（submitProgram 换文本即归零），无指纹
                    // （指纹用于 BE tick 检测模板被拿走/换链，见 SequenceMachineBlockEntity）
                    int templateSlot = switch (be.kind()) {
                        case TRANSCRIBER -> com.github.crafteve.biocraft.blockentity.sequence.operation.TranscriptionOperation.SLOT_TEMPLATE;
                        case TRANSLATOR -> com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation.SLOT_MRNA;
                        default -> -1;
                    };
                    if (templateSlot >= 0) {
                        var tmpl = be.getContainer().getItem(templateSlot);
                        var d = tmpl.get(com.github.crafteve.biocraft.init.ModDataComponents.SEQUENCE.get());
                        be.setLastTemplateSeq(d != null ? d.seq() : "");
                    }
                    be.setChanged();
                }
            }
        });
    }
}

