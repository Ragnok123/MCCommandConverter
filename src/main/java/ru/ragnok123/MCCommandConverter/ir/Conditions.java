package ru.ragnok123.MCCommandConverter.ir;

import java.util.List;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** Helpers for condition trees. */
public final class Conditions {
    private Conditions() {}

    /** Can this condition be written inside a target selector (@e[...])? */
    public static boolean selectable(Condition c) {
        return c instanceof ScoreIn || c instanceof HasTag || (c instanceof Not n && n.c() instanceof HasTag);
    }

    public static Condition and(List<Condition> cs) { return cs.size() == 1 ? cs.get(0) : new And(List.copyOf(cs)); }

    /** Logical negation with De Morgan so that Not only ever wraps a leaf. */
    public static Condition negate(Condition c) {
        return switch (c) {
            case Not n -> nnf(n.c());
            case And a -> new Or(a.cs().stream().map(Conditions::negate).toList());
            case Or o -> new And(o.cs().stream().map(Conditions::negate).toList());
            case Cmp x when x.op() != CmpOp.EQ -> new Cmp(x.l(), complement(x.op()), x.r());
            case SlotCmp x when x.op() != CmpOp.EQ -> new SlotCmp(x.a(), complement(x.op()), x.b());
            default -> new Not(c);
        };
    }

    /** Negation normal form: Not only wraps leaves. */
    public static Condition nnf(Condition c) {
        return switch (c) {
            case Not n -> negate(n.c());
            case And a -> new And(a.cs().stream().map(Conditions::nnf).toList());
            case Or o -> new Or(o.cs().stream().map(Conditions::nnf).toList());
            default -> c;
        };
    }

    private static CmpOp complement(CmpOp op) {
        return switch (op) {
            case EQ -> throw new IllegalArgumentException("no complement for EQ");
            case LT -> CmpOp.GE;
            case LE -> CmpOp.GT;
            case GT -> CmpOp.LE;
            case GE -> CmpOp.LT;
        };
    }
}
