package ru.ragnok123.MCCommandConverter.ir;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** Collects every entity type and int prop a program mentions. */
public final class Analysis {
    public final Set<IntProp> props = new LinkedHashSet<>();
    public final Set<EntityType> types = new LinkedHashSet<>();

    public static Analysis of(Program p) {
        Analysis a = new Analysis();
        a.stmts(p.tick());
        a.stmts(p.load());
        a.stmts(p.uninstall());
        return a;
    }

    void stmts(List<Statement> l) { for (Statement s : l) stmt(s); }

    void stmt(Statement s) {
        switch (s) {
            case ForEach f -> { query(f.q()); stmts(f.body()); }
            case When w -> { cond(w.c()); stmts(w.body()); }
            case IfElse i -> { cond(i.c()); stmts(i.then()); stmts(i.otherwise()); }
            case Positioned p -> stmts(p.body());
            case ScoreOp o -> slot(o.dst());
            case Assign a -> { slot(a.dst()); expr(a.e()); }
            case Combine c -> { slot(c.dst()); slot(c.src()); }
            case StoreCount c -> { slot(c.dst()); query(c.q()); }
            case TagOp t -> target(t.t());
            case Kill k -> target(k.t());
            case Summon su -> types.add(su.type());
            case Tp t -> target(t.t());
            case Rotate r -> target(r.t());
            case DataMerge d -> target(d.t());
            case Tell t -> target(t.t());
            case Wait w -> { if (w.until() != null) cond(w.until()); stmts(w.during()); }
            case Sequence q -> { types.add(q.type()); if (q.start() != null) cond(q.start()); stmts(q.body()); }
            case Particle x -> {}
            case Sound x -> {}
            case SetBlock x -> {}
            case Fill x -> {}
            case StorageSet x -> {}
            case FlagSet x -> {}
            case FlagClear x -> {}
            case Raw x -> {}
        }
    }

    void cond(Condition c) {
        switch (c) {
            case ScoreIn s -> prop(s.prop());
            case SlotIn s -> slot(s.slot());
            case Not n -> cond(n.c());
            case And a -> a.cs().forEach(this::cond);
            case Or o -> o.cs().forEach(this::cond);
            case Exists e -> query(e.q());
            case Cmp x -> { expr(x.l()); expr(x.r()); }
            case SlotCmp x -> { slot(x.a()); slot(x.b()); }
            case HasTag x -> {}
            case BlockAt x -> {}
            case FlagIs x -> {}
        }
    }

    void expr(Expr e) {
        switch (e) {
            case Read r -> slot(r.s());
            case Bin b -> { expr(b.l()); expr(b.r()); }
            case CountOf c -> query(c.q());
            case Const c -> {}
        }
    }

    void slot(Slot s) {
        if (s instanceof PropSlot p) { prop(p.p()); target(p.t()); }
    }

    void target(Target t) { if (t instanceof Many m) query(m.q()); }

    void query(Query q) {
        types.add(q.type());
        for (Condition c : q.filters()) cond(c);
    }

    void prop(IntProp p) { props.add(p); types.add(p.owner()); }
}
