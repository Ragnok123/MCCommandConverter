package ru.ragnok123.MCCommandConverter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Output packaging: datapack folder, or command-block installers (one paste per chunk). */
public final class Emit {
    private Emit() {}

    public static List<String> objectiveCommands(Map<String, String> objectives) {
        List<String> l = new ArrayList<>();
        for (var e : objectives.entrySet()) l.add("scoreboard objectives add " + e.getKey() + " " + e.getValue());
        return l;
    }

    public static void datapack(Path dir, String ns, JavaBackend be, JavaBackend.Result r, TargetVersion v) throws IOException {
        Path fn = dir.resolve("data/" + ns + "/" + v.functionDir());
        Files.createDirectories(fn.resolve("gen"));
        Files.writeString(dir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + v.packFormat() + ",\"description\":\"" + ns + " (compiled for " + v.name() + ")\"}}\n");
        Files.writeString(fn.resolve("tick.mcfunction"), String.join("\n", r.tick()) + "\n");
        List<String> load = new ArrayList<>(objectiveCommands(be.objectives));
        load.addAll(r.load());
        Files.writeString(fn.resolve("load.mcfunction"), String.join("\n", load) + "\n");
        Files.writeString(fn.resolve("uninstall.mcfunction"), String.join("\n", r.uninstall()) + "\n");
        for (var e : be.functions.entrySet()) {
            Path p = fn.resolve(e.getKey() + ".mcfunction");
            Files.createDirectories(p.getParent());
            Files.writeString(p, String.join("\n", e.getValue()) + "\n");
        }
        Path tags = dir.resolve("data/minecraft/tags/" + v.functionDir());
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("tick.json"), "{\"values\":[\"" + ns + ":tick\"]}\n");
        Files.writeString(tags.resolve("load.json"), "{\"values\":[\"" + ns + ":load\"]}\n");
    }

    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ---- command-block layout ------------------------------------------------------------------
    public record Layout(boolean serpentine, int columnLength) {
        public static final Layout STRAIGHT = new Layout(false, 0);
        public static final Layout SNAKE = new Layout(true, 16);
    }
    public record Cell(int x, int z, String facing) {}

    /** Where chain block i goes (relative to the paste point) and which way it points. total = number of blocks. */
    public static Cell cell(Layout l, int i, int total) {
        if (!l.serpentine()) return new Cell(1, 2 + i, "south");
        int len = l.columnLength(), col = i / len, r = i % len;
        boolean down = col % 2 == 0;
        int z = 2 + (down ? r : len - 1 - r);
        String dir = down ? "south" : "north";
        if (r == len - 1 && i < total - 1) dir = "east";      // turn into the next column
        return new Cell(1 + col, z, dir);
    }

    static final int CHUNK_LIMIT = 30000;   // a command block holds 32500 characters

    /**
     * Installer pastes. Every chunk is pasted at the SAME spot, one after another: chunk 1 builds the head and the
     * first blocks, later chunks continue the same chain. Not tested in-game.
     */
    public static List<String> installers(List<String> setup, List<String> chain, Layout layout) {
        int total = chain.size();
        List<List<String>> chunks = new ArrayList<>();
        List<String> cur = new ArrayList<>(setup);
        int curLen = 0;
        for (String c : cur) curLen += c.length() * 2 + 60;
        int zc = 2 + (layout.serpentine() ? layout.columnLength() : total) + 2;
        int chunkNo = 0;
        List<String> pending = cur;
        for (int i = 0; i < total; i++) {
            Cell ce = cell(layout, i, total);
            String block = i == 0 ? "repeating_command_block" : "chain_command_block";
            String cmd = "setblock ~" + ce.x() + " ~ ~" + ce.z() + " " + block + "[facing=" + ce.facing() + "]{auto:true,Command:\"" + esc(chain.get(i)) + "\"}";
            int len = esc(cmd).length() + 60;
            if (curLen + len > CHUNK_LIMIT && !pending.isEmpty()) {
                chunks.add(finish(pending, zc + 3 * chunkNo));
                chunkNo++;
                pending = new ArrayList<>();
                curLen = 0;
            }
            pending.add(cmd);
            curLen += len;
        }
        chunks.add(finish(pending, zc + 3 * chunkNo));
        List<String> out = new ArrayList<>();
        for (List<String> c : chunks) out.add(summon(c));
        return out;
    }

    private static List<String> finish(List<String> cmds, int z) {
        List<String> r = new ArrayList<>(cmds);
        r.add("setblock ~ ~-1 ~" + z + " command_block{Command:\"execute positioned ~ ~2 ~-" + z + " run fill ~ ~-3 ~ ~ ~-1 ~ air\"}");
        r.add("setblock ~ ~-1 ~" + (z + 1) + " command_block{Command:\"execute positioned ~ ~2 ~-" + (z + 1) + " run kill @e[type=command_block_minecart,distance=..3]\"}");
        r.add("fill ~ ~ ~" + z + " ~ ~ ~" + (z + 1) + " redstone_block");
        return r;
    }

    private static String summon(List<String> cmds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:command_block_minecart,Command:\"").append(esc(cmds.get(i))).append("\"}");
        }
        return "/summon falling_block ~ ~1 ~ {BlockState:{Name:redstone_block},Time:9,Passengers:[{id:falling_block,BlockState:{Name:activator_rail},Time:9,Passengers:["
                + sb + "]}]}";
    }

    public static void commandBlocks(Path dir, JavaBackend be, JavaBackend.Result r, Layout layout) throws IOException {
        Files.createDirectories(dir);
        List<String> setup = new ArrayList<>();
        setup.add("gamerule command_block_output false");
        setup.addAll(objectiveCommands(be.objectives));
        setup.addAll(r.load());
        List<String> pastes = installers(setup, r.tick(), layout);
        for (int i = 0; i < pastes.size(); i++)
            Files.writeString(dir.resolve("installer_" + (i + 1) + ".txt"), pastes.get(i) + "\n");
        Files.writeString(dir.resolve("README.txt"),
                "Paste installer_1.txt into a command block and power it once; if there are more files, paste each at the same\n"
                + "spot afterwards, in order (they continue the same chain). Untested in-game.\n"
                + "installer chunks: " + pastes.size() + "\n"
                + "To uninstall: run the commands in uninstall.txt, then break the chain blocks by hand.\n");
        Files.writeString(dir.resolve("flat_commands.txt"), String.join("\n", r.tick()) + "\n");
        Files.writeString(dir.resolve("uninstall.txt"), String.join("\n", r.uninstall()) + "\n");
    }
}
