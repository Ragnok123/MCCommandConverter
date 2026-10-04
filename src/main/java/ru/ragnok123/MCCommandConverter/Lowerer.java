package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/**
 * IR -> IR pass: turns Sequence/Wait into plain per-tick state-machine statements.
 *
 * Per sequence two IntProps are added to the entity: <name>_state (0 = idle) and <name>_t (ticks in state).
 * Segment i is entered when state == i+1 && t == 0; its `during` block runs on every tick spent in it
 * (including the entering tick), so wait(W) separates segment entries by exactly W ticks.
 */
public final class Lowerer {
    private Lowerer() {}

    record Segment(List<Statement> enter, int ticks, List<Statement> during) {}

    public static Program run(Program p) {
        List<Statement> out = new ArrayList<>();
        for (Statement s : p.tick()) {
            if (s instanceof Sequence q) out.addAll(lower(q)); else out.add(s);
        }
        return new Program(p.ns(), out);
    }

    static List<Statement> lower(Sequence q) {
        IntProp state = q.type().prop(q.name() + "_state");
        IntProp t = q.type().prop(q.name() + "_t");

        // split the body at top-level waits
        List<Segment> segs = new ArrayList<>();
        List<Statement> cur = new ArrayList<>();
        for (Statement s : q.body()) {
            if (s instanceof Wait w) {
                if (w.ticks() < 1) throw new IllegalArgumentException("wait must be >= 1 tick");
                segs.add(new Segment(cur, w.ticks(), w.during()));
                cur = new ArrayList<>();
            } else {
                rejectNestedWait(s);
                cur.add(s);
            }
        }
        segs.add(new Segment(cur, -1, List.of()));      // final segment: runs once, then back to idle
        int n = segs.size();

        List<Statement> r = new ArrayList<>();
        Query all = q.type().query();

        // 1. start: idle entities whose trigger holds enter state 1
        r.add(new ForEach(all, List.of(new When(new And(List.of(new Not(new ScoreIn(state, 1, null)), q.start())), List.of(
                new ScoreOp(new Self(), state, Op.SET, 1), new ScoreOp(new Self(), t, Op.SET, 0))))));

        // 2. transitions (descending so nothing advances twice in one tick)
        for (int i = n - 2; i >= 0; i--) {
            r.add(new ForEach(all.where(state.is(i + 1), t.atLeast(segs.get(i).ticks())), List.of(
                    new ScoreOp(new Self(), state, Op.SET, i + 2), new ScoreOp(new Self(), t, Op.SET, 0))));
        }

        // 3. segment bodies (ascending)
        for (int i = 0; i < n; i++) {
            Segment sg = segs.get(i);
            List<Statement> enter = new ArrayList<>(sg.enter());
            if (i == n - 1) enter.add(new ScoreOp(new Self(), state, Op.SET, 0));
            if (!enter.isEmpty()) r.add(new ForEach(all.where(state.is(i + 1), t.is(0)), enter));
            if (!sg.during().isEmpty()) r.add(new ForEach(all.where(state.is(i + 1)), sg.during()));
            if (i < n - 1) r.add(new ScoreOp(new Many(all.where(state.is(i + 1))), t, Op.ADD, 1));
        }
        return r;
    }

    static void rejectNestedWait(Statement s) {
        List<Statement> kids = switch (s) {
            case ForEach f -> f.body();
            case When w -> w.body();
            case Positioned p -> p.body();
            default -> List.of();
        };
        for (Statement k : kids) {
            if (k instanceof Wait) throw new IllegalArgumentException("wait() is only allowed at the top level of a sequence");
            rejectNestedWait(k);
        }
    }
}
