package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** A score on several entities at once. Write-only: there is no single value to read. */
public final class MultiVar {
    final Context ctx; final Slot slot;
    MultiVar(Context c, Slot s) { ctx = c; slot = s; }
    public void set(int v) { ctx.add(new ScoreOp(slot, Op.SET, v)); }
    public void add(int v) { ctx.add(new ScoreOp(slot, Op.ADD, v)); }
    public void sub(int v) { ctx.add(new ScoreOp(slot, Op.SUB, v)); }
    public void set(Val v) { ctx.add(new Assign(slot, v.e)); }
}
