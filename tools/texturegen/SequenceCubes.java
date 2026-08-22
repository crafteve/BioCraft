// SequenceCubes.java
// 转录/解旋/装载 三机纯几何中心对称 cube_all 生成器（V4 同款白箱+黑晶）
// 单贴图六面复用，中心对称 180°，高饱和明亮，AE几何风
// 编译：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/PixelCanvas.java tools/texturegen/SequenceCubes.java
// 运行：java -cp tools/texturegen/out SequenceCubes

import java.io.IOException;

public class SequenceCubes {
    // 共用机箱色
    static final int OUTLINE   = 0xFF1E232B;
    static final int LAB_WHITE = 0xFFF0F3F7;
    static final int HIGHLIGHT = 0xFFFFFFFF;
    static final int SHADOW    = 0xFFD4DBE6;
    static final int SHADOW2   = 0xFFB8C0D0;
    static final int GLASS     = 0xFF0B1220;
    static final int GLASS_HI  = 0xFF2A3A52;
    // 各机点缀色（高饱和明亮，互区分）
    static final int CYAN      = 0xFF00E5FF; // 编码器用（已存在）
    static final int AMBER     = 0xFFFFB700; // 转录仪 琥珀金
    static final int VIOLET    = 0xFF9B6BFF; // 解旋酶 紫
    static final int LIME      = 0xFF33FF77; // 装载机 荧光绿

    public static void main(String[] args) throws IOException {
        gen("transcriber", AMBER, SequenceCubes::buildTranscriber);
        gen("helicase", VIOLET, SequenceCubes::buildHelicase);
        gen("loader", LIME, SequenceCubes::buildLoader);
        System.out.println("三机几何贴图全部生成完成");
    }

    interface Builder { PixelCanvas build(int accent); }

    static void gen(String name, int accent, Builder b) throws IOException {
        PixelCanvas c = b.build(accent);
        c.save("tools/texturegen/output/" + name + ".png");
        c.savePreview("tools/texturegen/output/" + name + "_preview_8x.png", 8);
        c.save("src/main/resources/assets/biocraft/textures/block/" + name + ".png");
        System.out.println(name + " -> 16x16 + 8x + block/" + name + ".png");
        verify(name, c, accent);
    }

    // 机箱+黑晶模板（白箱 16×16 ，中央8×8黑晶 4,4-11,11）
    static PixelCanvas scaffold() {
        PixelCanvas c = new PixelCanvas(16, 16);
        c.fill(OUTLINE);
        c.rect(1, 1, 14, 14, LAB_WHITE);
        c.set(1, 1, SHADOW); c.set(14, 1, SHADOW); c.set(1, 14, SHADOW2); c.set(14, 14, SHADOW2);
        c.hline(1, 14, 1, HIGHLIGHT);
        c.vline(1, 14, 1, HIGHLIGHT);
        c.hline(1, 14, 14, SHADOW);
        c.vline(1, 14, 14, SHADOW);
        c.hline(1, 14, 15, OUTLINE);
        c.outline(4, 4, 11, 11, OUTLINE);
        c.rect(5, 5, 10, 10, GLASS);
        c.hline(5, 10, 5, GLASS_HI);
        c.vline(5, 10, 5, GLASS_HI);
        return c;
    }

    // 转录仪：空心菱形环 + 中央横杠（DNA→RNA 的“转录杠”），中心对称
    static PixelCanvas buildTranscriber(int accent) {
        PixelCanvas c = scaffold();
        // 空心菱形环（6高，环宽1px，中空 2×2）
        // 外菱形同编码器满菱形，内挖空 4点
        // y5: 7,8
        // y6: 6,9
        // y7: 5,10
        // y8: 5,10
        // y9: 6,9
        // y10:7,8
        // 这样形成环
        c.set(7, 5, accent); c.set(8, 5, accent);
        c.set(6, 6, accent); c.set(9, 6, accent);
        c.set(5, 7, accent); c.set(10, 7, accent);
        c.set(5, 8, accent); c.set(10, 8, accent);
        c.set(6, 9, accent); c.set(9, 9, accent);
        c.set(7,10, accent); c.set(8,10, accent);
        // 中央横杠 4px（转录方向）
        c.set(6, 7, accent); c.set(7, 7, accent); c.set(8, 7, accent); c.set(9, 7, accent);
        c.set(6, 8, accent); c.set(7, 8, accent); c.set(8, 8, accent); c.set(9, 8, accent);
        // 横杠上下留1px黑缝，保证环与杠分离可读（挖掉中缝? 实际已覆盖，保持实心杠更醒目）
        return c;
    }

    // 解旋酶：双三角蝴蝶结（双链分叉），左右双三角外指，完美180°对称
    static PixelCanvas buildHelicase(int accent) {
        PixelCanvas c = scaffold();
        // 左三角（尖向西 5,7-8）
        // y6: 6
        // y7: 5,6
        // y8: 5,6
        // y9: 6
        c.set(6, 6, accent);
        c.set(5, 7, accent); c.set(6, 7, accent);
        c.set(5, 8, accent); c.set(6, 8, accent);
        c.set(6, 9, accent);
        // 右三角（尖向东 10,7-8），与左三角180°镜像
        c.set(9, 6, accent);
        c.set(9, 7, accent); c.set(10, 7, accent);
        c.set(9, 8, accent); c.set(10, 8, accent);
        c.set(9, 9, accent);
        // 中心连接菱点
        c.set(7, 7, accent); c.set(8, 7, accent);
        c.set(7, 8, accent); c.set(8, 8, accent);
        return c;
    }

    // 装载机：十字+菱心（tRNA三叶抽象为中心十字），中心对称
    static PixelCanvas buildLoader(int accent) {
        PixelCanvas c = scaffold();
        // 6高菱形满填充作底（同编码器，同色系但绿），稍小一圈留1px黑缝，作“tRNA体”
        c.set(7, 5, accent); c.set(8, 5, accent);
        c.set(6, 6, accent); c.set(7, 6, accent); c.set(8, 6, accent); c.set(9, 6, accent);
        c.set(5, 7, accent); c.set(6, 7, accent); c.set(7, 7, accent); c.set(8, 7, accent); c.set(9, 7, accent); c.set(10, 7, accent);
        c.set(5, 8, accent); c.set(6, 8, accent); c.set(7, 8, accent); c.set(8, 8, accent); c.set(9, 8, accent); c.set(10, 8, accent);
        c.set(6, 9, accent); c.set(7, 9, accent); c.set(8, 9, accent); c.set(9, 9, accent);
        c.set(7,10, accent); c.set(8,10, accent);
        // 中心挖十字黑缝（1px），形成“十字”负形，代表氨基酸+接合点
        c.set(7, 7, GLASS); c.set(8, 7, GLASS);
        c.set(7, 8, GLASS); c.set(8, 8, GLASS);
        // 中心1px会全黑，需回补1px accent 保持可读？改为十字：竖黑2px，横黑4px
        // 已挖2×2黑块，保留菱形外环可读
        return c;
    }

    static void verify(String name, PixelCanvas c, int accent) {
        int mism=0;
        for(int y=0;y<16;y++) for(int x=0;x<16;x++){
            int a=c.get(x,y), b=c.get(15-x,15-y);
            boolean isAccent = a==accent;
            if(isAccent && a!=b) mism++;
        }
        System.out.println("  中心对称校验 " + name + ": mismatched=" + mism + " (0完美,仅符号区)");
    }
}
