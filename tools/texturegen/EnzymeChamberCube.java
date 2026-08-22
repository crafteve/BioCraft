// EnzymeChamberCube.java
// 酶工厂 V4 同风格三贴图生成器（白箱底 + 双灰度主题）
// 中心对称，16×16真像素，tint0 菱形酶窗，tint1 四角灯
import java.io.IOException;
public class EnzymeChamberCube {
    static final int OUTLINE   = 0xFF1E232B;
    static final int LAB_WHITE = 0xFFF0F3F7;
    static final int HIGHLIGHT = 0xFFFFFFFF;
    static final int SHADOW    = 0xFFD4DBE6;
    static final int SHADOW2   = 0xFFB8C0D0;
    static final int GLASS     = 0xFF0B1220;
    static final int GLASS_HI  = 0xFF2A3A52;
    // 灰度主题色（白=纯酶色，808080=暗化，黑=透明）
    static final int G_WHITE   = 0xFFFFFFFF;
    static final int G_MID     = 0xFFC8C8C8;
    static final int G_DARK    = 0xFF808080;
    public static void main(String[] args) throws IOException {
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber.png");
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_side.png");
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_back.png");
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_top.png");
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_bottom.png");
        // 侧面镜像同底（保持旧资源存在，避免缺失）
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_side_mirrored.png");
        // front 同 base（旧 front 也覆盖）
        base().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_front.png");

        themeWindow().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_theme_window.png");
        themeLamp().save("src/main/resources/assets/biocraft/textures/block/enzyme_chamber_theme_lamp.png");

        // 调试预览（合成）
        PixelCanvas preview = base();
        // 把灰度主题按酶色 #00E5A8 叠看效果（仅预览）
        overlay(preview, themeWindow(), 0xFF00E5A8);
        overlay(preview, themeLamp(), 0xFFF2C94C);
        preview.save("tools/texturegen/output/enzyme_chamber_preview.png");
        preview.savePreview("tools/texturegen/output/enzyme_chamber_preview_8x.png",8);
        System.out.println("酶工厂三贴图生成完成：base白箱 + theme_window菱形 + theme_lamp四角");
    }
    // 白箱+黑晶底（无tint）
    static PixelCanvas base() {
        PixelCanvas c = new PixelCanvas(16,16);
        c.fill(OUTLINE);
        c.rect(1,1,14,14,LAB_WHITE);
        c.set(1,1,SHADOW); c.set(14,1,SHADOW); c.set(1,14,SHADOW2); c.set(14,14,SHADOW2);
        c.hline(1,14,1,HIGHLIGHT); c.vline(1,14,1,HIGHLIGHT);
        c.hline(1,14,14,SHADOW); c.vline(1,14,14,SHADOW);
        c.hline(1,14,15,OUTLINE);
        c.outline(4,4,11,11,OUTLINE);
        c.rect(5,5,10,10,GLASS);
        c.hline(5,10,5,GLASS_HI); c.vline(5,10,5,GLASS_HI);
        return c;
    }
    // 灰度酶窗（tint0）— 4×4实心居中，不占四角留给灯，避免透明染黑覆盖
    static PixelCanvas themeWindow() {
        PixelCanvas c = new PixelCanvas(16,16);
        c.rect(6,6,9,9,G_WHITE);
        return c;
    }
    // 灰度四角灯（tint1）
    static PixelCanvas themeLamp() {
        PixelCanvas c = new PixelCanvas(16,16);
        c.set(5,5,G_WHITE); c.set(10,5,G_WHITE); c.set(5,10,G_WHITE); c.set(10,10,G_WHITE);
        return c;
    }
    static void overlay(PixelCanvas base, PixelCanvas theme, int tint) {
        int tr=(tint>>16)&0xFF, tg=(tint>>8)&0xFF, tb=tint&0xFF;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++){
            int g=theme.get(x,y);
            if((g>>24)==0) continue;
            int gr=(g>>16)&0xFF;
            int r= tr*gr/255, gg=tg*gr/255, b=tb*gr/255;
            int a=0xFF000000 | (r<<16) | (gg<<8) | b;
            base.set(x,y,a);
        }
    }
}
