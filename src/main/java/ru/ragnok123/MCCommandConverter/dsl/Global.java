package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** A program-wide variable (fake player on the global objective). Use ctx.global(g) to write it. */
public record Global(String name) {
    Slot slot() { return new GlobSlot(name); }
    public Val get() { return new Val(new Read(slot())); }
    public Condition in(Integer min, Integer max) { return new SlotIn(slot(), min, max); }
    public Condition is(int n) { return in(n, n); }
    public Condition atLeast(int n) { return in(n, null); }
    public Condition atMost(int n) { return in(null, n); }
}
