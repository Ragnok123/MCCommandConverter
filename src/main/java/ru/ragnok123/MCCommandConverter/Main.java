package ru.ragnok123.MCCommandConverter;

import java.nio.file.*;
import java.util.*;

import ru.ragnok123.MCCommandConverter.examples.SpaceLaser;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "out");
        Program ir = SpaceLaser.build();
        Program lowered = Lowerer.run(ir);

        // target 1: datapack
        JavaBackend dp = new JavaBackend("laser", false);
        List<String> tick = dp.compile(lowered);
        Emit.datapack(out.resolve("datapack"), "laser", tick, dp);

        // target 2: single-paste command-block installer (no function calls, prefixes repeated)
        JavaBackend cb = new JavaBackend("laser", true);
        List<String> flat = cb.compile(lowered);
        Files.createDirectories(out);
        Files.writeString(out.resolve("installer.txt"), Emit.installer(new ArrayList<>(cb.objectives), flat) + "\n");
        Files.writeString(out.resolve("flat_commands.txt"), String.join("\n", flat) + "\n");

        System.out.println("IR statements (before lowering): " + ir.tick().size());
        System.out.println("IR statements (after lowering):  " + lowered.tick().size());
        System.out.println("datapack: tick=" + tick.size() + " lines + " + dp.functions.size() + " generated functions");
        System.out.println("command-block chain: " + flat.size() + " commands");
    }
}
