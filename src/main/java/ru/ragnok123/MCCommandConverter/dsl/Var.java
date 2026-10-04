package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.IntProp;
import ru.ragnok123.MCCommandConverter.ir.Ir.Op;
import ru.ragnok123.MCCommandConverter.ir.Ir.ScoreOp;

public final class Var {
    final Ref r; final IntProp p;
    Var(Ref r, IntProp p) { this.r = r; this.p = p; }
    public void set(int v) { r.ctx.out.add(new ScoreOp(r.t, p, Op.SET, v)); }
    public void add(int v) { r.ctx.out.add(new ScoreOp(r.t, p, Op.ADD, v)); }
    public void sub(int v) { r.ctx.out.add(new ScoreOp(r.t, p, Op.SUB, v)); }
}