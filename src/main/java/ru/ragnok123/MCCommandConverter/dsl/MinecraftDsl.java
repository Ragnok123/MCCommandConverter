package ru.ragnok123.MCCommandConverter.dsl;

import java.util.List;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** Static helpers for conditions and values. */
public final class MinecraftDsl {
    private MinecraftDsl() {}

    public static Condition hasTag(String t) { return new HasTag(t, false); }
    /** A tag owned by another pack/plugin (not namespaced). */
    public static Condition hasExternalTag(String t) { return new HasTag(t, true); }
    public static Condition not(Condition c) { return new Not(c); }
    public static Condition all(Condition... cs) { return new And(List.of(cs)); }
    public static Condition any(Condition... cs) { return new Or(List.of(cs)); }
    public static Condition blockAt(int dx, int dy, int dz, String b) { return new BlockAt(dx, dy, dz, b); }
    public static Condition exists(Query q, int dy) { return new Exists(q, dy); }
    public static Condition exists(Query q) { return new Exists(q, 0); }
    public static Val val(int v) { return Val.of(v); }
    public static Val count(Query q) { return Val.count(q); }
    /** The value of an int prop of @s, as an expression. */
    public static Val read(IntProp p) { return new Val(new Read(new PropSlot(new Self(), p))); }
}
