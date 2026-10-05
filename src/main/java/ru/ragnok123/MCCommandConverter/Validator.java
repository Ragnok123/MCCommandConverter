package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Conditions;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * Checks a high-level program before any lowering: execution context (is there an @s? is there a position?),
 * where Wait may appear, and which queries may be used where. Reports every problem with its DSL call site.
 */
public final class Validator {
    public static final class ValidationException extends RuntimeException {
        public final List<String> errors;
        ValidationException(List<String> errors) {
            super(errors.size() + " problem(s):\n  - " + String.join("\n  - ", errors));
            this.errors = errors;
        }
    }

    /** entity: @s is an entity. pos: a meaningful execution position exists (inside ForEach or absolute positioned). */
    record Ctx(boolean entity, boolean pos) {}
    static final Ctx TOP = new Ctx(false, false);
    static final Ctx ENTITY = new Ctx(true, true);

    private final List<String> errors = new ArrayList<>();
    private final Set<String> sequenceNames = new HashSet<>();

    public static void run(Program p) {
        Validator v = new Validator();
        v.list(p.tick(), TOP, true);
        v.list(p.load(), TOP, false);
        v.list(p.uninstall(), TOP, false);
        if (!v.errors.isEmpty()) throw new ValidationException(v.errors);
    }

    void err(Object node, String msg) {
        String site = Sites.of(node);
        errors.add((site == null ? "" : site + ": ") + msg);
    }

    void list(List<Statement> l, Ctx c, boolean topLevelOfTick) {
        for (Statement s : l) stmt(s, c, topLevelOfTick);
    }

    void stmt(Statement s, Ctx c, boolean top) {
        switch (s) {
            case ForEach f -> { query(f, f.q(), false); list(f.body(), ENTITY, false); }
            case When w -> { cond(w, w.c(), c); list(w.body(), c, false); }
            case IfElse i -> { cond(i, i.c(), c); list(i.then(), c, false); list(i.otherwise(), c, false); }
            case Positioned p -> {
                if (!p.absolute() && !c.pos())
                    err(s, "relative at() outside a forEach: there is no position to be relative to (use atAbs or wrap it in forEach)");
                list(p.body(), new Ctx(c.entity(), true), false);
            }
            case ScoreOp o -> slot(s, o.dst(), c);
            case Assign a -> { slot(s, a.dst(), c); expr(s, a.e(), c); }
            case Combine x -> {
                slot(s, x.dst(), c); slot(s, x.src(), c);
                if (x.src() instanceof PropSlot p && p.t() instanceof Many) err(s, "operation source must be a single holder");
            }
            case StoreCount x -> { slot(s, x.dst(), c); query(s, x.q(), true); }
            case TagOp t -> target(s, t.t(), c);
            case Kill k -> target(s, k.t(), c);
            case Tp t -> target(s, t.t(), c);
            case Rotate r -> target(s, r.t(), c);
            case DataMerge d -> target(s, d.t(), c);
            case Tell t -> target(s, t.t(), c);
            case Particle x -> needPos(s, c, "particle");
            case Sound x -> needPos(s, c, "sound");
            case Summon x -> {
                if (x.type().isPlayer()) err(s, "can't summon a player");
                needPos(s, c, "summon");
            }
            case SetBlock x -> needPos(s, c, "setBlock");
            case Fill x -> needPos(s, c, "fill");
            case StorageSet x -> {}
            case FlagSet x -> {}
            case FlagClear x -> {}
            case Raw x -> {}
            case Wait w -> err(s, "wait() is only allowed at the top level of a sequence");
            case Sequence q -> sequence(q, top);
        }
    }

    void sequence(Sequence q, boolean top) {
        if (!top) err(q, "sequence '" + q.name() + "' must be declared at the top level of the program");
        if (!sequenceNames.add(q.type().name() + "." + q.name()))
            err(q, "duplicate sequence '" + q.name() + "' on entity type '" + q.type().name() + "'");
        if (q.start() != null) cond(q, q.start(), ENTITY);
        for (Statement s : q.body()) {
            if (s instanceof Wait w) {
                if (w.until() == null && w.ticks() < 1) err(s, "wait must be >= 1 tick");
                if (w.until() != null) cond(s, w.until(), ENTITY);
                list(w.during(), ENTITY, false);
            } else {
                stmt(s, ENTITY, false);
            }
        }
    }

    void needPos(Statement s, Ctx c, String what) {
        if (!c.pos()) err(s, what + " outside a forEach/atAbs: it would run at the function origin, not where you expect");
    }

    void target(Statement s, Target t, Ctx c) {
        if (t instanceof Self && !c.entity()) err(s, "self() used outside a forEach/sequence: there is no entity");
        if (t instanceof Many m) query(s, m.q(), true);
    }

    void slot(Statement s, Slot sl, Ctx c) {
        if (sl instanceof PropSlot p) target(s, p.t(), c);
    }

    /** selectorOnly: the query ends up in a selector, so every filter must be expressible there. */
    void query(Object node, Query q, boolean selectorOnly) {
        if (!selectorOnly) return;   // ForEach splits unsupported filters into a nested when()
        for (Condition f : q.filters())
            if (!Conditions.selectable(f))
                err(node, "query filter " + f.getClass().getSimpleName() + " can't be used in a selector here; use forEach(...) + when(...) instead");
    }

    void cond(Object node, Condition cd, Ctx c) {
        switch (cd) {
            case ScoreIn s -> needEntity(node, c, "score check");
            case HasTag t -> needEntity(node, c, "tag check");
            case SlotIn s -> { if (s.slot() instanceof PropSlot p) target(stmtOf(node), p.t(), c); }
            case Not n -> cond(node, n.c(), c);
            case And a -> a.cs().forEach(x -> cond(node, x, c));
            case Or o -> o.cs().forEach(x -> cond(node, x, c));
            case BlockAt b -> { if (!c.pos()) err(node, "blockAt outside a forEach/atAbs: no position"); }
            case Exists e -> { if (!c.pos()) err(node, "exists outside a forEach/atAbs: no position"); query(node, e.q(), true); }
            case Cmp x -> { expr(node, x.l(), c); expr(node, x.r(), c); }
            case SlotCmp x -> { slotCond(node, x.a(), c); slotCond(node, x.b(), c); }
            case FlagIs f -> {}
        }
    }

    static Statement stmtOf(Object o) { return o instanceof Statement s ? s : null; }

    void slotCond(Object node, Slot s, Ctx c) {
        if (s instanceof PropSlot p) {
            if (p.t() instanceof Self && !c.entity()) err(node, "score of self() read outside a forEach/sequence");
            if (p.t() instanceof Many) err(node, "can't read a score from several entities at once");
        }
    }

    void needEntity(Object node, Ctx c, String what) {
        if (!c.entity()) err(node, what + " on self() outside a forEach/sequence: there is no entity");
    }

    void expr(Object node, Expr e, Ctx c) {
        switch (e) {
            case Const k -> {}
            case Read r -> slotCond(node, r.s(), c);
            case Bin b -> { expr(node, b.l(), c); expr(node, b.r(), c); }
            case CountOf k -> query(node, k.q(), true);
        }
    }
}
