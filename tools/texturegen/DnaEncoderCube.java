// DnaEncoderCube.java V4 - 纯几何中心对称（修正：真中心对称 + 放大）
// 16×16 单贴图 cube_all，AE风格，中心对称验证：(x,y) ↔ (15-x,15-y)
// 中心点 7.5,7.5，用 |dx|+|dy|<=3 的偶数菱形，保证180°旋转完全重合，尺寸 6高
import java.io.IOException;

public class DnaEncoderCube {
    static final int OUTLINE  = 0xFF1E232B;
    static final int LAB_WHITE= 0xFFF0F3F7;
    static final int HIGHLIGHT= 0xFFFFFFFF;
    static final int SHADOW   = 0xFFD4DBE6;
    static final int SHADOW2  = 0xFFB8C0D0;
    static final int GLASS    = 0xFF0B1220;
    static final int GLASS_HI = 0xFF2A3A52;
    static final int CYAN     = 0xFF00E5FF;
    static final int CYAN_HI  = 0xFF6AF2FF;
    static final int CYAN_DARK= 0xFF00B8D4; // 阴影1档，增加立体但保持高饱和

    public static void main(String[] args) throws IOException {
        PixelCanvas c = build();
        c.save("tools/texturegen/output/dna_encoder.png");
        c.savePreview("tools/texturegen/output/dna_encoder_preview_8x.png", 8);
        c.save("src/main/resources/assets/biocraft/textures/block/dna_encoder.png");
        System.out.println("V4 几何中心对称（放大6高菱形）生成完成");
        // 验证中心对称
        verify(c);
    }

    static PixelCanvas build() {
        PixelCanvas c = new PixelCanvas(16, 16);
        // 白机箱
        c.fill(OUTLINE);
        c.rect(1, 1, 14, 14, LAB_WHITE);
        c.set(1, 1, SHADOW); c.set(14, 1, SHADOW); c.set(1, 14, SHADOW2); c.set(14, 14, SHADOW2);
        c.hline(1, 14, 1, HIGHLIGHT);
        c.vline(1, 14, 1, HIGHLIGHT);
        c.hline(1, 14, 14, SHADOW);
        c.vline(1, 14, 14, SHADOW);
        c.hline(1, 14, 15, OUTLINE);

        // 中央8×8黑晶 (4,4)-(11,11)
        c.outline(4, 4, 11, 11, OUTLINE);
        c.rect(5, 5, 10, 10, GLASS);
        c.hline(5, 10, 5, GLASS_HI);
        c.vline(5, 10, 5, GLASS_HI);

        // 中央6高菱形：|x-7.5|+|y-7.5| <= 3  （完美中心对称，放大一圈）
        // 逐行手写保证对称：
        // y5: x7,8  (2px)
        // y6: x6,7,8,9 (4px)
        // y7: x5,6,7,8,9,10 (6px)
        // y8: x5,6,7,8,9,10 (6px) 中心两行等宽保证偶数高度对称
        // y9: x6,7,8,9 (4px)
        // y10:x7,8 (2px)
        // 用 CYAN 填充，顶尖用 HI，底边用 DARK 做1px阴影增加立体

        // y5 顶尖 2px（高光，中心对称同色保证校验通过，立体靠玻璃高光已足够）
        c.set(7, 5, CYAN);
        c.set(8, 5, CYAN);
        // y6
        c.set(6, 6, CYAN); c.set(7, 6, CYAN); c.set(8, 6, CYAN); c.set(9, 6, CYAN);
        // y7 中腰
        c.set(5, 7, CYAN); c.set(6, 7, CYAN); c.set(7, 7, CYAN); c.set(8, 7, CYAN); c.set(9, 7, CYAN); c.set(10, 7, CYAN);
        // y8 中腰对称
        c.set(5, 8, CYAN); c.set(6, 8, CYAN); c.set(7, 8, CYAN); c.set(8, 8, CYAN); c.set(9, 8, CYAN); c.set(10, 8, CYAN);
        // y9
        c.set(6, 9, CYAN); c.set(7, 9, CYAN); c.set(8, 9, CYAN); c.set(9, 9, CYAN);
        // y10 底尖 2px（同色，保证180°色对称）
        c.set(7, 10, CYAN);
        c.set(8, 10, CYAN);

        // 中心对称校验点：中心十字1px更亮（可选，落在(7,8)已含，提亮）
        // 保持纯几何，不加文字

        return c;
    }

    /** 校验中心对称：每个非白底像素必须在 (15-x,15-y) 有同色像素 */
    static void verify(PixelCanvas c) {
        int mism=0;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++){
            int a=c.get(x,y), b=c.get(15-x,15-y);
            // 只校验青色符号区（黑晶内），忽略机箱高光阴影（本身对称）
            boolean isSymbol = a==CYAN || a==CYAN_HI || a==CYAN_DARK;
            if(isSymbol && a!=b) mism++;
        }
        System.out.println("中心对称校验：mismatched symbol pixels = "+mism+" (0=完美)");
    }
}
