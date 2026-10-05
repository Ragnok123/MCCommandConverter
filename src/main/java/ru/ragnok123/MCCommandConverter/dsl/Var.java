package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** A readable score: the property of @s, or a global. */
public final class Var {
    final Context ctx; final Slot slot;
    Var(Context c, Slot s) { ctx = c; slot = s; }
    public void set(int v) { ctx.add(new ScoreOp(slot, Op.SET, v)); }
    public void add(int v) { ctx.add(new ScoreOp(slot, Op.ADD, v)); }
    public void sub(int v) { ctx.add(new ScoreOp(slot, Op.SUB, v)); }
    public void set(Val v) { ctx.add(new Assign(slot, v.e)); }
    public Val get() { return new Val(new Read(slot)); }
    public Condition in(Integer min, Integer max) { return new SlotIn(slot, min, max); }
    public Condition is(int n) { return in(n, n); }
    public Condition atLeast(int n) { return in(n, null); }
    public Condition atMost(int n) { return in(null, n); }
}
