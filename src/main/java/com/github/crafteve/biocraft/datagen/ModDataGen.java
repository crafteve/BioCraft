package com.github.crafteve.biocraft.datagen;

import com.github.crafteve.biocraft.BioCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * datagen 装配入口
 * <p>
 * 通过 GatherDataEvent 挂载各资源生成器：
 * 分子物品模型（两层贴图）与中英文语言文件
 * <p>
 * 运行方式：gradlew runData，产物输出到 src/generated/resources（build.gradle 已配置）
 */
@EventBusSubscriber(modid = BioCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModDataGen {

    /**
     * 装配全部数据生成器
     *
     * @param event 数据生成事件
     */
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(true, new SubstanceModelProvider(packOutput, existingFileHelper));
        generator.addProvider(true, new SubstanceLanguageProvider(packOutput, "en_us"));
        generator.addProvider(true, new SubstanceLanguageProvider(packOutput, "zh_cn"));
        generator.addProvider(true, new MachineModelProvider(packOutput));
        // 方块 tag 查找器传"已完成空 future"（vanilla 构造要求非 null，本 provider 不使用方块 tag）
        generator.addProvider(true, new EnzymeTagProvider(packOutput, event.getLookupProvider(),
                java.util.concurrent.CompletableFuture.completedFuture(null)));
    }
}
