// TextureScript.java
// 示例贴图脚本：生成纪元一 DNA 编码器的 16x16 占位贴图与 8x 放大预览
// 运行方式：javac -encoding UTF-8 -d tools/texturegen/out tools/texturegen/*.java
//           java -cp tools/texturegen/out TextureScript [输出目录]
// 约定：输出目录默认为 tools/texturegen/output（已 gitignore），正式贴图确定后手动拷入 src/main/resources

import java.io.IOException;

public class TextureScript {

    /**
     * 绘制 DNA 编码器占位贴图
     * 构图：金属灰机身（左上高光/右下暗部）+ 中央观察窗内黄绿双螺旋 + 右下角状态灯
     * 双螺旋用正弦波偏移模拟两条缠绕主链，横档代表碱基对
     *
     * @return 16x16 画布
     */
    public static PixelCanvas dnaEncoder() {
        PixelCanvas tex = new PixelCanvas(16, 16);

        // 调色板：机身三档灰（受光/基色/背光）+ 观察窗深底 + 螺旋绿两档 + 碱基黄 + 状态灯红
        tex.color("body", "#4C4C58");
        tex.color("bodyLight", "#63636F");
        tex.color("bodyDark", "#33333C");
        tex.color("outline", "#17171D");
        tex.color("window", "#141A24");
        tex.color("helixA", "#39D353");
        tex.color("helixB", "#1F9E45");
        tex.color("rung", "#E8C33C");
        tex.color("led", "#E5484D");

        // 机身：整体涂灰后描深色外框
        tex.fill("body");
        tex.outline(0, 0, 15, 15, "outline");

        // 模拟左上光源：顶边与左边一像素提亮，底边与右边一像素压暗
        tex.hline(1, 14, 1, "bodyLight");
        tex.vline(1, 14, 1, "bodyLight");
        tex.hline(1, 14, 14, "bodyDark");
        tex.vline(1, 14, 14, "bodyDark");

        // 中央观察窗：x 3..12，y 2..12
        tex.rect(3, 2, 12, 12, "window");

        // 双螺旋：两条竖直波浪链，相位相差半周期，先画横档再画链保证链色不被横档覆盖
        for (int y = 2; y <= 12; y++) {
            // 正弦偏移范围约 -2..2，两条链分别落在窗口左右半区
            int offset = (int) Math.round(1.5 * Math.sin((y - 2) * Math.PI / 3));
            int x1 = 5 + offset;
            int x2 = 10 - offset;
            if (y % 2 == 1) {
                tex.hline(x1, x2, y, "rung");
            }
            tex.set(x1, y, "helixA");
            tex.set(x2, y, "helixB");
        }

        // 右下角状态灯 2x2
        tex.rect(13, 13, 14, 14, "led");

        return tex;
    }

    /**
     * 程序入口：绘制示例贴图并导出原始 PNG 与放大预览
     *
     * @param args 可选输出目录，缺省为 tools/texturegen/output
     */
    public static void main(String[] args) throws IOException {
        String outDir = args.length > 0 ? args[0] : "tools/texturegen/output";
        PixelCanvas tex = dnaEncoder();
        String assetPath = outDir + "/dna_encoder.png";
        String previewPath = outDir + "/dna_encoder_preview_8x.png";
        tex.save(assetPath);
        tex.savePreview(previewPath, 8);
        System.out.println("已生成 " + assetPath);
        System.out.println("已生成 " + previewPath);
    }
}
