package ru.ragnok123.MCCommandConverter.ir;

import java.util.*;

/** Backend-neutral NBT-ish value tree. The Java backend renders it as SNBT. */
public sealed interface Nbt {
    record Str(String v) implements Nbt {}
    record Int(int v) implements Nbt {}
    record Dbl(double v) implements Nbt {}
    record Flt(float v) implements Nbt {}
    record Bool(boolean v) implements Nbt {}
    record NList(List<Nbt> items) implements Nbt {}
    /** A text component from plain text; rendering differs between versions (JSON string vs SNBT component). */
    record Text(String plain) implements Nbt {}
    /** Escape hatch: already-rendered SNBT. */
    record Raw(String snbt) implements Nbt {}

    final class Compound implements Nbt {
        public final LinkedHashMap<String, Nbt> map = new LinkedHashMap<>();
        public Compound put(String k, Nbt v) { map.put(k, v); return this; }
        public Compound put(String k, String v) { return put(k, new Str(v)); }
        public Compound put(String k, int v) { return put(k, new Int(v)); }
        public Compound put(String k, double v) { return put(k, new Dbl(v)); }
        public Compound put(String k, boolean v) { return put(k, new Bool(v)); }
        public Compound putFloats(String k, float... v) {
            List<Nbt> l = new ArrayList<>();
            for (float f : v) l.add(new Flt(f));
            return put(k, new NList(l));
        }
        @Override public boolean equals(Object o) { return o instanceof Compound c && c.map.equals(map); }
        @Override public int hashCode() { return map.hashCode(); }
        @Override public String toString() { return "Compound" + map; }
    }

    static Compound obj() { return new Compound(); }
    static NList list(Nbt... items) { return new NList(List.of(items)); }
}
