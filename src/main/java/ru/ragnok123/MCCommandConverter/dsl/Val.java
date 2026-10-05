package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** A game-time integer expression. (Plain Java ints are build-time constants.) */
public final class Val {
    final Expr e;
    Val(Expr e) { this.e = e; }
    public Expr expr() { return e; }

    public static Val of(int v) { return new Val(new Const(v)); }
    public static Val count(Query q) { return new Val(new CountOf(q)); }

    private Val bin(BinOp op, Val o) { return new Val(new Bin(op, e, o.e)); }
    public Val plus(Val o) { return bin(BinOp.ADD, o); }
    public Val plus(int v) { return plus(of(v)); }
    public Val minus(Val o) { return bin(BinOp.SUB, o); }
    public Val minus(int v) { return minus(of(v)); }
    public Val times(Val o) { return bin(BinOp.MUL, o); }
    public Val times(int v) { return times(of(v)); }
    /** floor division, like scoreboard players operation /= */
    public Val div(Val o) { return bin(BinOp.DIV, o); }
    public Val div(int v) { return div(of(v)); }
    public Val mod(Val o) { return bin(BinOp.MOD, o); }
    public Val mod(int v) { return mod(of(v)); }
    public Val min(Val o) { return bin(BinOp.MIN, o); }
    public Val max(Val o) { return bin(BinOp.MAX, o); }

    private Condition cmp(CmpOp op, Val o) { return new Cmp(e, op, o.e); }
    public Condition eq(Val o) { return cmp(CmpOp.EQ, o); }
    public Condition eq(int v) { return eq(of(v)); }
    public Condition lt(Val o) { return cmp(CmpOp.LT, o); }
    public Condition lt(int v) { return lt(of(v)); }
    public Condition le(Val o) { return cmp(CmpOp.LE, o); }
    public Condition le(int v) { return le(of(v)); }
    public Condition gt(Val o) { return cmp(CmpOp.GT, o); }
    public Condition gt(int v) { return gt(of(v)); }
    public Condition ge(Val o) { return cmp(CmpOp.GE, o); }
    public Condition ge(int v) { return ge(of(v)); }
}
