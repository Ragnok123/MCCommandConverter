package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * IR -> IR pass: removes Assign and Cmp. Expressions are evaluated into scratch registers (TmpSlot), comparisons are
 * hoisted in front of the statement that uses them and become plain SlotIn/SlotCmp conditions.
 * Registers are never reused, so two expressions can't clobber each other.
 */
public final class ExprLowering {
    private int next = 0;

    public static Program run(Program p) {
        ExprLowering x = new ExprLowering();
        return new Program(p.ns(), x.list(p.tick()), x.list(p.load()), x.list(p.uninstall()));
    }

    List<Statement> list(List<Statement> in) {
        List<Statement> out = new ArrayList<>();
        for (Statement s : in) stmt(s, out);
        return out;
    }

    void stmt(Statement s, List<Statement> out) {
        switch (s) {
            case Assign a -> assign(a, out);
            case When w -> {
                List<Statement> pre = new ArrayList<>();
                Condition c = hoist(w.c(), pre, w);
                out.addAll(pre);
                out.add(Sites.copy(w, new When(c, list(w.body()))));
            }
            case IfElse i -> {
                List<Statement> pre = new ArrayList<>();
                Condition c = hoist(i.c(), pre, i);
                out.addAll(pre);
                out.add(Sites.copy(i, new IfElse(c, list(i.then()), list(i.otherwise()))));
            }
            case ForEach f -> out.add(Sites.copy(f, new ForEach(f.q(), list(f.body()))));
            case Positioned p -> out.add(Sites.copy(p, new Positioned(p.dx(), p.dy(), p.dz(), p.absolute(), list(p.body()))));
            default -> out.add(s);
        }
    }

    TmpSlot tmp() { return new TmpSlot(next++); }

    <T extends Statement> void add(List<Statement> out, Object site, T s) { out.add(Sites.copy(site, s)); }

    // ---- assignments -------------------------------------------------------------------------
    void assign(Assign a, List<Statement> out) {
        Slot dst = a.dst();
        Expr e = fold(a.e());
        // x = x (+|-) k  and  x = x op y
        if (e instanceof Bin b && b.l() instanceof Read r && r.s().equals(dst)) {
            if (b.r() instanceof Const k && (b.op() == BinOp.ADD || b.op() == BinOp.SUB)) {
                add(out, a, new ScoreOp(dst, b.op() == BinOp.ADD ? Op.ADD : Op.SUB, k.v()));
                return;
            }
            TmpSlot t = tmp();
            into(b.r(), t, out, a);
            add(out, a, new Combine(dst, combine(b.op()), t));
            return;
        }
        switch (e) {
            case Const k -> add(out, a, new ScoreOp(dst, Op.SET, k.v()));
            case Read r -> add(out, a, new Combine(dst, CombineOp.ASSIGN, r.s()));
            case CountOf c -> add(out, a, new StoreCount(dst, c.q()));
            case Bin b -> {
                TmpSlot t = tmp();
                into(b, t, out, a);
                add(out, a, new Combine(dst, CombineOp.ASSIGN, t));
            }
        }
    }

    /** Evaluate e into the fresh register dst. */
    void into(Expr e0, TmpSlot dst, List<Statement> out, Object site) {
        Expr e = fold(e0);
        switch (e) {
            case Const k -> add(out, site, new ScoreOp(dst, Op.SET, k.v()));
            case Read r -> add(out, site, new Combine(dst, CombineOp.ASSIGN, r.s()));
            case CountOf c -> add(out, site, new StoreCount(dst, c.q()));
            case Bin b -> {
                into(b.l(), dst, out, site);
                if (b.r() instanceof Const k && (b.op() == BinOp.ADD || b.op() == BinOp.SUB)) {
                    add(out, site, new ScoreOp(dst, b.op() == BinOp.ADD ? Op.ADD : Op.SUB, k.v()));
                } else {
                    TmpSlot t = tmp();
                    into(b.r(), t, out, site);
                    add(out, site, new Combine(dst, combine(b.op()), t));
                }
            }
        }
    }

    static CombineOp combine(BinOp op) {
        return switch (op) {
            case ADD -> CombineOp.ADD; case SUB -> CombineOp.SUB; case MUL -> CombineOp.MUL;
            case DIV -> CombineOp.DIV; case MOD -> CombineOp.MOD; case MIN -> CombineOp.MIN; case MAX -> CombineOp.MAX;
        };
    }

    /** Constant folding with Minecraft's semantics (floor division / floor modulo). */
    static Expr fold(Expr e) {
        if (!(e instanceof Bin b)) return e;
        Expr l = fold(b.l()), r = fold(b.r());
        if (l instanceof Const x && r instanceof Const y) {
            int a = x.v(), c = y.v();
            switch (b.op()) {
                case ADD: return new Const(a + c);
                case SUB: return new Const(a - c);
                case MUL: return new Const(a * c);
                case DIV: if (c != 0) return new Const(Math.floorDiv(a, c)); break;
                case MOD: if (c != 0) return new Const(Math.floorMod(a, c)); break;
                case MIN: return new Const(Math.min(a, c));
                case MAX: return new Const(Math.max(a, c));
            }
        }
        return new Bin(b.op(), l, r);
    }

    // ---- comparisons -------------------------------------------------------------------------
    Condition hoist(Condition c, List<Statement> pre, Object site) {
        return switch (c) {
            case Cmp x -> cmp(x, pre, site);
            case Not n -> new Not(hoist(n.c(), pre, site));
            case And a -> new And(a.cs().stream().map(k -> hoist(k, pre, site)).toList());
            case Or o -> new Or(o.cs().stream().map(k -> hoist(k, pre, site)).toList());
            default -> c;
        };
    }

    Condition cmp(Cmp x, List<Statement> pre, Object site) {
        Expr l = fold(x.l()), r = fold(x.r());
        CmpOp op = x.op();
        if (l instanceof Const && !(r instanceof Const)) {    // put the constant on the right
            Expr t = l; l = r; r = t;
            op = switch (op) { case LT -> CmpOp.GT; case GT -> CmpOp.LT; case LE -> CmpOp.GE; case GE -> CmpOp.LE; default -> op; };
        }
        Slot ls = asSlot(l, pre, site);
        if (r instanceof Const k) return range(ls, op, k.v());
        return new SlotCmp(ls, op, asSlot(r, pre, site));
    }

    Slot asSlot(Expr e, List<Statement> pre, Object site) {
        if (e instanceof Read r) return r.s();
        TmpSlot t = tmp();
        into(e, t, pre, site);
        return t;
    }

    static Condition range(Slot s, CmpOp op, int v) {
        return switch (op) {
            case EQ -> new SlotIn(s, v, v);
            case LT -> new SlotIn(s, null, v - 1);
            case LE -> new SlotIn(s, null, v);
            case GT -> new SlotIn(s, v + 1, null);
            case GE -> new SlotIn(s, v, null);
        };
    }
}
