// TranslatorFolderCubes.java
// 翻译机（核糖体）与折叠机（分子伴侣）纯几何中心对称 cube_all 贴图
// 同 chassis 白箱+黑晶，H/X 几何，高饱和明亮，16×16真像素，BE未注册先出贴图
import java.io.IOException;
public class TranslatorFolderCubes {
    static final int OUTLINE   = 0xFF1E232B;
    static final int LAB_WHITE = 0xFFF0F3F7;
    static final int HIGHLIGHT = 0xFFFFFFFF;
    static final int SHADOW    = 0xFFD4DBE6;
    static final int SHADOW2   = 0xFFB8C0D0;
    static final int GLASS     = 0xFF0B1220;
    static final int GLASS_HI  = 0xFF2A3A52;
    static final int TRANSLATOR= 0xFFFF3B30; //  ribosome 高饱和红
    static final int FOLDER    = 0xFF3B8FFF; // chaperone 高饱和蓝
    public static void main(String[] args) throws IOException {
        gen("translator", TRANSLATOR, TranslatorFolderCubes::buildTranslator);
        gen("folder", FOLDER, TranslatorFolderCubes::buildFolder);
        System.out.println("翻译/折叠双贴图生成完成");
    }
    interface Builder { PixelCanvas build(int accent); }
    static void gen(String name, int accent, Builder b) throws IOException {
        PixelCanvas c = b.build(accent);
        c.save("tools/texturegen/output/" + name + ".png");
        c.savePreview("tools/texturegen/output/" + name + "_preview_8x.png", 8);
        c.save("src/main/resources/assets/biocraft/textures/block/" + name + ".png");
        System.out.println(name + " -> block/" + name + ".png");
        verify(name, c, accent);
    }
    static PixelCanvas scaffold() {
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
    // 翻译机：H形（核糖体双亚基夹mRNA），中心对称
    static PixelCanvas buildTranslator(int accent) {
        PixelCanvas c = scaffold();
        // H：两竖柱 x6/x9 y6-9 + 横梁 y7-8 x6-9
        c.set(6,6,accent); c.set(9,6,accent);
        c.set(6,7,accent); c.set(7,7,accent); c.set(8,7,accent); c.set(9,7,accent);
        c.set(6,8,accent); c.set(7,8,accent); c.set(8,8,accent); c.set(9,8,accent);
        c.set(6,9,accent); c.set(9,9,accent);
        return c;
    }
    // 折叠机：X形（折叠交叉），中心对称
    static PixelCanvas buildFolder(int accent) {
        PixelCanvas c = scaffold();
        // X：两对角线 4×4
        c.set(6,6,accent); c.set(9,6,accent);
        c.set(7,7,accent); c.set(8,7,accent);
        c.set(7,8,accent); c.set(8,8,accent);
        c.set(6,9,accent); c.set(9,9,accent);
        // 中心1px空隙保持X可读，实际X在16px需断开1px防糊
        return c;
    }
    static void verify(String name, PixelCanvas c, int accent) {
        int mism=0;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) if(c.get(x,y)==accent && c.get(15-x,15-y)!=accent) mism++;
        System.out.println("  中心对称校验 " + name + ": mismatched=" + mism);
    }
}
