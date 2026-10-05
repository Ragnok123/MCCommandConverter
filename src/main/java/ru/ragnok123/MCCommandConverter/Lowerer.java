package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * IR -> IR pass: turns Sequence/Wait into plain per-tick state-machine statements.
 *
 * Per sequence these IntProps are added to the entity: <name>_state (0 = idle) and <name>_t (ticks in state),
 * plus <name>_blk when the sequence is edge-triggered.
 * Segment i is entered when state == i+1 && t == 0; its `during` block runs on every tick spent in it
 * (including the entering tick), so wait(W) separates segment entries by exactly W ticks.
 * waitUntil(c): the segment is left on the first tick (after the entering one) where c holds.
 */
public final class Lowerer {
    private Lowerer() {}

    record Segment(List<Statement> enter, int ticks, Condition until, List<Statement> during) {}

    public static Program run(Program p) {
        List<Statement> out = new ArrayList<>();
        for (Statement s : p.tick()) {
            if (s instanceof Sequence q) out.addAll(lower(q)); else out.add(s);
        }
        return p.withTick(out);
    }

    public static IntProp stateProp(EntityType t, String name) { return t.prop(name + "_state"); }
    public static IntProp timeProp(EntityType t, String name) { return t.prop(name + "_t"); }
    public static IntProp blockProp(EntityType t, String name) { return t.prop(name + "_blk"); }

    static Slot self(IntProp p) { return new PropSlot(new Self(), p); }

    static List<Statement> lower(Sequence q) {
        IntProp state = stateProp(q.type(), q.name());
        IntProp t = timeProp(q.type(), q.name());
        IntProp blk = blockProp(q.type(), q.name());

        // split the body at top-level waits
        List<Segment> segs = new ArrayList<>();
        List<Statement> cur = new ArrayList<>();
        for (Statement s : q.body()) {
            if (s instanceof Wait w) {
                if (w.until() == null && w.ticks() < 1) throw new IllegalArgumentException("wait must be >= 1 tick");
                segs.add(new Segment(cur, w.ticks(), w.until(), w.during()));
                cur = new ArrayList<>();
            } else {
                rejectNestedWait(s);
                cur.add(s);
            }
        }
        segs.add(new Segment(cur, -1, null, List.of()));      // final segment: runs once, then idle (or loop)
        int n = segs.size();

        List<Statement> r = new ArrayList<>();
        Query all = q.type().query();

        // 1. start: idle entities whose trigger holds enter state 1
        if (q.start() != null) {
            if (q.opts().edge()) {
                // re-arm once the trigger is false again
                r.add(new ForEach(all.where(blk.atLeast(1)), List.of(
                        new When(new Not(q.start()), List.of(new ScoreOp(self(blk), Op.SET, 0))))));
            }
            List<Condition> guard = new ArrayList<>();
            guard.add(new Not(new ScoreIn(state, 1, null)));
            if (q.opts().edge()) guard.add(new Not(new ScoreIn(blk, 1, null)));
            guard.add(q.start());
            List<Statement> startBody = new ArrayList<>(List.of(
                    new ScoreOp(self(state), Op.SET, 1), new ScoreOp(self(t), Op.SET, 0)));
            if (q.opts().edge()) startBody.add(new ScoreOp(self(blk), Op.SET, 1));
            r.add(new ForEach(all, List.of(new When(new And(guard), startBody))));
        }

        // 2. transitions (descending so nothing advances twice in one tick)
        for (int i = n - 2; i >= 0; i--) {
            Segment sg = segs.get(i);
            List<Statement> advance = List.of(
                    new ScoreOp(self(state), Op.SET, i + 2), new ScoreOp(self(t), Op.SET, 0));
            Query at = all.where(state.is(i + 1), t.atLeast(Math.max(sg.ticks(), 0)));
            if (sg.until() == null) r.add(new ForEach(at, advance));
            else r.add(new ForEach(at, List.of(new When(sg.until(), advance))));
        }

        // 3. segment bodies (ascending)
        for (int i = 0; i < n; i++) {
            Segment sg = segs.get(i);
            List<Statement> enter = new ArrayList<>(sg.enter());
            if (i == n - 1) {
                if (q.opts().loop()) {
                    enter.add(new ScoreOp(self(state), Op.SET, 1));
                    enter.add(new ScoreOp(self(t), Op.SET, 0));
                } else {
                    enter.add(new ScoreOp(self(state), Op.SET, 0));
                }
            }
            if (!enter.isEmpty()) r.add(new ForEach(all.where(state.is(i + 1), t.is(0)), enter));
            if (!sg.during().isEmpty()) r.add(new ForEach(all.where(state.is(i + 1)), sg.during()));
            if (i < n - 1) r.add(new ScoreOp(new PropSlot(new Many(all.where(state.is(i + 1))), t), Op.ADD, 1));
        }
        for (Statement s : r) Sites.copy(q, s);
        return r;
    }

    static void rejectNestedWait(Statement s) {
        List<Statement> kids = switch (s) {
            case ForEach f -> f.body();
            case When w -> w.body();
            case IfElse i -> { var l = new ArrayList<>(i.then()); l.addAll(i.otherwise()); yield l; }
            case Positioned p -> p.body();
            default -> List.of();
        };
        for (Statement k : kids) {
            if (k instanceof Wait) throw new IllegalArgumentException("wait() is only allowed at the top level of a sequence");
            rejectNestedWait(k);
        }
    }
}
