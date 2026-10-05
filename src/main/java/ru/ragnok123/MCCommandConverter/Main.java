package ru.ragnok123.MCCommandConverter;

import java.nio.file.*;
import java.util.*;

import ru.ragnok123.MCCommandConverter.examples.Showcase;
import ru.ragnok123.MCCommandConverter.examples.SpaceLaser;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.IrPrinter;

/** Usage: Main [outDir] [targetVersion] [--dump-ir] */
public final class Main {
    public static void main(String[] args) throws Exception {
        List<String> a = new ArrayList<>(List.of(args));
        boolean dump = a.remove("--dump-ir");
        Path out = Path.of(a.size() > 0 ? a.get(0) : "out");
        TargetVersion ver = a.size() > 1 ? TargetVersion.byName(a.get(1)) : TargetVersion.LATEST;
        build(out.resolve("laser"), SpaceLaser.build(), ver, dump);
    }

    static void build(Path out, Program ir, TargetVersion ver, boolean dump) throws Exception {
        Program lowered = Compiler.lower(ir);
        if (dump) System.out.println(IrPrinter.dump(lowered));

        JavaBackend dp = new JavaBackend(ir.ns(), false, ver);
        JavaBackend.Result dr = dp.compile(lowered);
        Emit.datapack(out.resolve("datapack"), ir.ns(), dp, dr, ver);

        JavaBackend cb = new JavaBackend(ir.ns(), true, ver);
        JavaBackend.Result cr = cb.compile(lowered);
        Emit.commandBlocks(out.resolve("commandblocks"), cb, cr, Emit.Layout.SNAKE);

        System.out.println("[" + ir.ns() + "] IR statements: " + ir.tick().size() + " -> " + lowered.tick().size() + " after lowering");
        System.out.println("[" + ir.ns() + "] datapack: tick=" + dr.tick().size() + " lines + " + dp.functions.size() + " functions");
        System.out.println("[" + ir.ns() + "] command-block chain: " + cr.tick().size() + " commands");
    }
}
