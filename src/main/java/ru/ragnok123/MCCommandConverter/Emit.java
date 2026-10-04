package ru.ragnok123.MCCommandConverter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Output packaging: datapack folder, or a single one-paste command-block installer. */
public final class Emit {
    private Emit() {}

    public static void datapack(Path dir, String ns, List<String> tick, JavaBackend be) throws IOException {
        Path fn = dir.resolve("data/" + ns + "/function");
        Files.createDirectories(fn.resolve("gen"));
        Files.writeString(dir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":71,\"description\":\"" + ns + " (compiled)\"}}\n");   // 71 = 1.21.5, verify
        Files.writeString(fn.resolve("tick.mcfunction"), String.join("\n", tick) + "\n");
        List<String> load = new ArrayList<>();
        for (String o : be.objectives) load.add("scoreboard objectives add " + o + " dummy");
        Files.writeString(fn.resolve("load.mcfunction"), String.join("\n", load) + "\n");
        for (var e : be.functions.entrySet()) {
            Path p = fn.resolve(e.getKey() + ".mcfunction");
            Files.writeString(p, String.join("\n", e.getValue()) + "\n");
        }
        Path tags = dir.resolve("data/minecraft/tags/function");
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("tick.json"), "{\"values\":[\"" + ns + ":tick\"]}\n");
        Files.writeString(tags.resolve("load.json"), "{\"values\":[\"" + ns + ":load\"]}\n");
    }

    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    /**
     * One command to paste into a command block. Same trick as the original: a falling redstone block + activator
     * rail carrying command-block minecarts; each minecart runs one setup command, then they clean themselves up.
     * Layout is a straight chain along +z (the original used a serpentine to stay compact).
     * Untested in-game: offsets mirror the original's cleanup convention.
     */
    public static String installer(List<String> objectives, List<String> tickCommands) {
        List<String> cmds = new ArrayList<>();
        cmds.add("gamerule command_block_output false");
        for (String o : objectives) cmds.add("scoreboard objectives add " + o + " dummy");
        int n = tickCommands.size();
        for (int i = 0; i < n; i++) {
            String block = i == 0 ? "repeating_command_block[facing=south]{auto:true," : "chain_command_block[facing=south]{auto:true,";
            cmds.add("setblock ~1 ~ ~" + (2 + i) + " " + block + "Command:\"" + esc(tickCommands.get(i)) + "\"}");
        }
        int z = 2 + n;
        cmds.add("setblock ~ ~-1 ~" + z + " command_block{Command:\"execute positioned ~ ~2 ~-" + z + " run fill ~ ~-3 ~ ~ ~-1 ~ air\"}");
        cmds.add("setblock ~ ~-1 ~" + (z + 1) + " command_block{Command:\"execute positioned ~ ~2 ~-" + (z + 1) + " run kill @e[type=command_block_minecart,distance=..3]\"}");
        cmds.add("fill ~ ~ ~" + z + " ~ ~ ~" + (z + 1) + " redstone_block");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{id:command_block_minecart,Command:\"").append(esc(cmds.get(i))).append("\"}");
        }
        return "/summon falling_block ~ ~1 ~ {BlockState:{Name:redstone_block},Time:9,Passengers:[{id:falling_block,BlockState:{Name:activator_rail},Time:9,Passengers:["
                + sb + "]}]}";
    }
}
