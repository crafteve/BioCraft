import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.Inflater;

/** 诊断工具：解析 .mca 存档，打印 biocraft:sequence_machine BE 的 inventory 槽位（不入库） */
public class McaDump {
    static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3, TAG_LONG = 4,
            TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7, TAG_STRING = 8, TAG_LIST = 9,
            TAG_COMPOUND = 10, TAG_INT_ARRAY = 11, TAG_LONG_ARRAY = 12;

    static class R {
        byte[] d; int p;
        R(byte[] d) { this.d = d; }
        int b() { return d[p++] & 0xFF; }
        int s() { int v = ((d[p]&0xFF)<<8)|(d[p+1]&0xFF); p += 2; return v; }
        int i() { int v = ((d[p]&0xFF)<<24)|((d[p+1]&0xFF)<<16)|((d[p+2]&0xFF)<<8)|(d[p+3]&0xFF); p += 4; return v; }
        String str() { int n = s(); String v = new String(d, p, n, StandardCharsets.UTF_8); p += n; return v; }
    }

    /** 解析 NBT 为 Map/List/String/Number 树 */
    static Object parse(R r, int type) {
        switch (type) {
            case TAG_END: return null;
            case TAG_BYTE: return r.b();
            case TAG_SHORT: return r.s();
            case TAG_INT: return r.i();
            case TAG_LONG: return ((long) r.i() << 32) | (r.i() & 0xFFFFFFFFL);
            case TAG_FLOAT: { float v = Float.intBitsToFloat(r.i()); return v; }
            case TAG_DOUBLE: { long bits = ((long) r.i() << 32) | (r.i() & 0xFFFFFFFFL); return Double.longBitsToDouble(bits); }
            case TAG_BYTE_ARRAY: { int n = r.i(); r.p += n; return null; }
            case TAG_STRING: return r.str();
            case TAG_LIST: {
                int et = r.b(); int n = r.i(); List<Object> l = new ArrayList<>();
                for (int k = 0; k < n; k++) l.add(parse(r, et));
                return l;
            }
            case TAG_COMPOUND: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int t = r.b(); if (t == TAG_END) break;
                    String name = r.str();
                    m.put(name, parse(r, t));
                }
                return m;
            }
            case TAG_INT_ARRAY: { int n = r.i(); r.p += n * 4; return null; }
            case TAG_LONG_ARRAY: { int n = r.i(); r.p += n * 8; return null; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static void walkCompound(Object o, String path, List<Object> out) {
        if (o instanceof Map<?, ?> m) {
            if (m.containsKey("id") && m.get("id") instanceof String s && s.equals("biocraft:sequence_machine")) {
                out.add(m);
            }
            for (Object v : m.values()) walk(v, path, out);
        }
    }

    static void collectIds(Object o, Set<String> ids) {
        if (o instanceof Map<?, ?> m) {
            if (m.containsKey("id") && m.get("id") instanceof String s) {
                ids.add(s);
            }
            for (Object v : m.values()) collectIds(v, ids);
        } else if (o instanceof List<?> l) {
            for (Object v : l) collectIds(v, ids);
        }
    }

    static void walk(Object o, String path, List<Object> out) {
        if (o instanceof Map<?, ?> m) {
            if (m.containsKey("id") && "biocraft:sequence_machine".equals(m.get("id"))) {
                out.add(m);
            }
            for (Object v : m.values()) walk(v, path, out);
        } else if (o instanceof List<?> l) {
            for (Object v : l) walk(v, path, out);
        }
    }

    static String itemName(Object entity, String id) {
        if (entity instanceof Map<?, ?> m && m.containsKey(id)) {
            Object it = m.get(id);
            if (it instanceof String s) return s;
            if (it instanceof Map<?, ?> im) {
                Object inner = im.get("id");
                if (inner instanceof String s) return s;
                if (inner instanceof Map<?, ?> im2) {
                    Object v = im2.get("value");
                    if (v instanceof String s2) return s2;
                    if (v instanceof List<?> ll && !ll.isEmpty() && ll.get(0) instanceof String s3) return s3;
                }
            }
        }
        return null;
    }

    static void dumpEntity(Object entity, String region, int chunkX, int chunkZ) {
        Map<?, ?> m = (Map<?, ?>) entity;
        System.out.println("--- " + region + " chunk(" + chunkX + "," + chunkZ + ") ---");
        for (Object key : m.keySet()) {
            String k = String.valueOf(key);
            if (k.equals("id") || k.equals("x") || k.equals("y") || k.equals("z") || k.equals("keepPacked") || k.equals("isMovable")) {
                System.out.println("  " + k + " = " + m.get(key));
            }
        }
        Object inv = m.get("inventory");
        if (inv instanceof List<?> slots) {
            System.out.println("  inventory (" + slots.size() + " items):");
            for (Object slot : slots) {
                if (slot instanceof Map<?, ?> sm) {
                    Object slotIdx = sm.get("Slot");
                    String name = itemName(slot, "id");
                    Object count = sm.get("count");
                    System.out.println("    Slot=" + slotIdx + " item=" + name + " count=" + count);
                }
            }
        } else {
            System.out.println("  inventory: MISSING");
        }
        Object seq = m.get("seqState");
        if (seq instanceof Map<?, ?> sm) {
            System.out.println("  seqState: " + sm);
        }
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "run/saves/New World/region";
        int found = 0;
        int chunkCount = 0;
        int parsedOk = 0;
        for (Path p : Files.list(Paths.get(dir)).filter(x -> x.toString().endsWith(".mca")).sorted().toList()) {
            byte[] mca = Files.readAllBytes(p);
            for (int i = 0; i < 1024; i++) {
                int b0 = mca[i * 4] & 0xFF, b1 = mca[i * 4 + 1] & 0xFF,
                        b2 = mca[i * 4 + 2] & 0xFF, b3 = mca[i * 4 + 3] & 0xFF;
                int sector = (b0 << 16) | (b1 << 8) | b2;
                if (sector == 0 && b3 == 0) continue; // 空条目（偏移高字节常为 0，不能只看首字节）
                int dataStart = sector * 4096;
                if (dataStart + 5 > mca.length) continue;
                int len = ((mca[dataStart] & 0xFF) << 24) | ((mca[dataStart + 1] & 0xFF) << 16)
                        | ((mca[dataStart + 2] & 0xFF) << 8) | (mca[dataStart + 3] & 0xFF);
                int compression = mca[dataStart + 4] & 0xFF;
                if (compression != 2) continue; // 2 = zlib
                chunkCount++;
                byte[] compressed = Arrays.copyOfRange(mca, dataStart + 5, dataStart + 5 + len);
                byte[] raw = inflate(compressed);
                if (raw == null) continue;
                parsedOk++;
                R r = new R(raw);
                int rootType = r.b();
                String rootName = r.str(); // NBT 根还有名字段（通常为空串）
                Object root = parse(r, rootType);
                if (chunkCount <= 3) {
                    System.out.println(p.getFileName() + " chunk(" + (i % 32) + "," + (i / 32)
                            + ") raw=" + raw.length + " rootType=" + rootType
                            + " parsedPos=" + r.p + " root=" + (root == null ? "null" : root.getClass().getSimpleName()));
                }
                if (root instanceof Map<?, ?> rm) {
                    if (chunkCount <= 3) {
                        System.out.println(p.getFileName() + " chunk(" + (i % 32) + "," + (i / 32)
                                + ") keys: " + rm.keySet());
                    }
                    if (rm.containsKey("block_entities")) {
                        Set<String> ids = new TreeSet<>();
                        collectIds(root, ids);
                        ids.removeIf(x -> !x.contains("biocraft"));
                        if (!ids.isEmpty()) {
                            System.out.println(p.getFileName() + " chunk(" + (i % 32) + "," + (i / 32)
                                    + ") biocraft ids: " + ids);
                        }
                    }
                }
                List<Object> entities = new ArrayList<>();
                walk(root, "", entities);
                for (Object e : entities) {
                    found++;
                    dumpEntity(e, p.getFileName().toString(), i % 32, i / 32);
                }
            }
        }
        System.out.println("=== chunks=" + chunkCount + " parsed=" + parsedOk + " sequence_machine BEs: " + found + " ===");
        System.out.println("=== total sequence_machine BEs: " + found + " ===");
    }

    static byte[] inflate(byte[] data) {
        try {
            Inflater inf = new Inflater();
            inf.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) break;
                out.write(buf, 0, n);
            }
            inf.end();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
