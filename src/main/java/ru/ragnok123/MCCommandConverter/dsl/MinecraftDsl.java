package ru.ragnok123.MCCommandConverter.dsl;

import java.util.*;
import java.util.function.Consumer;

import ru.ragnok123.MCCommandConverter.ir.Ir;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** The "Bukkit-ish" authoring layer. Everything here just builds IR. */
public final class MinecraftDsl {
    private MinecraftDsl() {}

    public static Condition hasTag(String t) { return new HasTag(t); }
    public static Condition not(Condition c) { return new Not(c); }
    public static Condition all(Condition... cs) { return new And(List.of(cs)); }
    public static Condition blockAt(int dx, int dy, int dz, String b) { return new BlockAt(dx, dy, dz, b); }
    public static Condition exists(Query q, int dy) { return new Exists(q, dy); }
}
