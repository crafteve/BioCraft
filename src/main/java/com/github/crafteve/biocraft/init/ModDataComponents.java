package com.github.crafteve.biocraft.init;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.item.SequenceData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据组件注册中心（重建：曾随 DNA 编码器移除，现为序列物品重新引入）
 * <p>
 * 序列载荷组件 = 序列物品的唯一事实源（type/strand/kind/seq/complete）。
 * 持久化走 NBT（Codec），网络同步走 StreamCodec（物品堆同步/容器同步都需要）
 */
public final class ModDataComponents {

    /** 数据组件注册表 */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, BioCraft.MODID);

    /** 序列载荷组件 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SequenceData>> SEQUENCE =
            COMPONENTS.register("sequence", () -> DataComponentType.<SequenceData>builder()
                    .persistent(SequenceDataCodecs.CODEC)
                    .networkSynchronized(SequenceDataCodecs.STREAM)
                    .build());

    /** 模板链标记（helicase 产物的模板/非模板区分，tooltip 方向与 nbt 区分） */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> IS_TEMPLATE =
            COMPONENTS.register("is_template", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * SequenceData 的持久化/网络编解码（MC 侧——seq/ 包保持零依赖门禁）
     * <p>
     * strand 对非 DNA 为 null，编解码时统一归一为 DS（存储态恒非空，
     * 往返一致；DNA 的 DS/SS 由物品 id 区分，本字段仅内容标注）
     */
    private static final class SequenceDataCodecs {
        static final Codec<SequenceData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                enumCodec(SequenceData.SeqType.class).fieldOf("type").forGetter(SequenceData::type),
                enumCodec(SequenceData.Strand.class)
                        .optionalFieldOf("strand", SequenceData.Strand.DS)
                        .forGetter(d -> d.strand() == null ? SequenceData.Strand.DS : d.strand()),
                enumCodec(SequenceData.Kind.class).fieldOf("kind").forGetter(SequenceData::kind),
                Codec.STRING.fieldOf("seq").forGetter(SequenceData::seq),
                Codec.BOOL.optionalFieldOf("complete", false).forGetter(SequenceData::complete)
        ).apply(inst, SequenceData::new));

        static final StreamCodec<ByteBuf, SequenceData> STREAM = ByteBufCodecs.fromCodec(CODEC);

        /** 枚举 ↔ 名称字符串编解码（1.21.1 ExtraCodecs 无 enumCodec，手写等价实现） */
        private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> clazz) {
            return Codec.STRING.xmap(s -> Enum.valueOf(clazz, s), Enum::name);
        }
    }

    private ModDataComponents() {
    }
}
