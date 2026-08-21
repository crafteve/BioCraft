package com.github.crafteve.biocraft;

import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.client.EmissiveLampBakedModel;
import com.github.crafteve.biocraft.gui.EncoderScreen;
import com.github.crafteve.biocraft.gui.HelicaseScreen;
import com.github.crafteve.biocraft.gui.LoaderScreen;
import com.github.crafteve.biocraft.gui.MachineScreen;
import com.github.crafteve.biocraft.gui.SequenceMachineScreen;
import com.github.crafteve.biocraft.gui.TranscriberScreen;
import com.github.crafteve.biocraft.init.ModBlocks;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.Map;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = BioCraft.MODID, dist = Dist.CLIENT)
// 客户端装配事件统一挂在 mod 总线上（菜单屏幕注册/染色注册事件均在此总线派发）
@EventBusSubscriber(modid = BioCraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class BioCraftClient {

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        // 将机器菜单类型与对应的屏幕类绑定，打开 GUI 时客户端按 MenuType 实例化屏幕
        // NeoForge 1.21.1 的 MenuScreens.register 为私有方法，必须经本事件注册
        event.register(ModBlocks.ENZYME_CHAMBER_MENU.get(), MachineScreen::new);
        event.register(ModBlocks.DNA_ENCODER_MENU.get(), EncoderScreen::new);
        event.register(ModBlocks.TRANSCRIBER_MENU.get(), TranscriberScreen::new);
        event.register(ModBlocks.HELICASE_MENU.get(), HelicaseScreen::new);
        event.register(ModBlocks.LOADER_MENU.get(), LoaderScreen::new);
    }

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 自发光指示灯：把酶反应腔全部朝向 variant 的烘焙模型包装为
        // EmissiveLampBakedModel（灯贴片 quad 光照强制全亮，夜晚真实发光）——
        // vanilla 方块模型无自发光属性，烘焙后包装是标准做法；物品模型
        // key 路径为 item/ 前缀不命中，物品栏/手持不受影响
        // key 格式实证（BlockModelShaper.stateToModelLocation）：id =
        // 方块注册名（biocraft:enzyme_chamber，无 block/ 前缀），variant =
        // "facing=north" 等——路径匹配必须用注册名而非模型路径
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            ModelResourceLocation key = entry.getKey();
            if (key.id().getNamespace().equals(BioCraft.MODID)
                    && key.id().getPath().equals("enzyme_chamber")) {
                entry.setValue(new EmissiveLampBakedModel(entry.getValue()));
            }
        }
    }

    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        // 酶反应腔方块主题色染色：贴片元素（tintindex 0=液体、1=灯）按方块实体
        // 缓存的酶主题色上色——无酶时返回空机暗灰（窗/管"空的"、灯熄灭），
        // 有酶时液体=酶数据表色、灯=提亮酶色；同一模型零 blockstate 表达状态
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null
                    && level.getBlockEntity(pos) instanceof EnzymeFactoryBlockEntity be) {
                return tintIndex == 1 ? be.getThemeLampArgb() : be.getThemeLiquidArgb();
            }
            return tintIndex == 1
                    ? EnzymeFactoryBlockEntity.EMPTY_LAMP_ARGB
                    : EnzymeFactoryBlockEntity.EMPTY_LIQUID_ARGB;
        }, ModBlocks.ENZYME_CHAMBER.get());
    }

    @SubscribeEvent
    static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // 酶反应腔方块物品（无 BE，无法知道具体酶色）：固定呈现"空机"暗灰
        // （物品 = 缩小版未装酶机器，与用户确认的默认 3D 方块模型渲染一致）
        event.register((stack, tintIndex) -> tintIndex == 1
                ? EnzymeFactoryBlockEntity.EMPTY_LAMP_ARGB
                : EnzymeFactoryBlockEntity.EMPTY_LIQUID_ARGB,
                ModBlocks.ENZYME_CHAMBER_ITEM.get());
    }
}
