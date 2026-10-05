package ru.ragnok123.MCCommandConverter.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Sites;

public final class Module {
    final String ns;
    final Context root = new Context();
    final Context load = new Context();
    final Context uninstall = new Context();
    private int eventIds = 0;

    public Module(String ns) { this.ns = ns; }

    public EntityType entityType(String name, String baseEntity) { return new EntityType(name, baseEntity); }
    /** Killed (not just untagged) by the generated uninstall function. */
    public EntityType disposableType(String name, String baseEntity) { return new EntityType(name, baseEntity, true); }

    public void everyTick(Consumer<Context> body) { body.accept(root); }
    public void onLoad(Consumer<Context> body) { body.accept(load); }
    public void onUninstall(Consumer<Context> body) { body.accept(uninstall); }

    // ---- sequences -------------------------------------------------------------------------
    /** Handle for starting/cancelling a sequence by hand from inside a forEach on its entity type. */
    public record SeqHandle(String name, EntityType type) {
        public IntProp state() { return ru.ragnok123.MCCommandConverter.Lowerer.stateProp(type, name); }
        public IntProp time() { return ru.ragnok123.MCCommandConverter.Lowerer.timeProp(type, name); }
        public void start(Context c) { var r = c.self(); r.get(state()).set(1); r.get(time()).set(0); }
        public void cancel(Context c) { var r = c.self(); r.get(state()).set(0); r.get(time()).set(0); }
        public Condition running() { return state().atLeast(1); }
    }

    /** A per-entity coroutine: starts when `start` holds (null = manual start only), then runs its body with waits. */
    public SeqHandle sequence(String name, EntityType t, Condition start, Consumer<Context> body) {
        return sequence(name, t, start, SeqOptions.DEFAULT, body);
    }
    /** edge: start only when `start` turns true; loop: restart after the last step. */
    public SeqHandle sequence(String name, EntityType t, Condition start, SeqOptions opts, Consumer<Context> body) {
        Context c = new Context(); body.accept(c);
        root.out.add(Sites.mark(new Sequence(name, t, start, opts, c.out)));
        return new SeqHandle(name, t);
    }

    // ---- globals and timers ------------------------------------------------------------------
    /** A program-wide int, set to `initial` on load only if it has no value yet. */
    public Global global(String name, int initial) {
        Global g = new Global(name);
        load.out.add(Sites.mark(new When(new Not(new SlotIn(g.slot(), Integer.MIN_VALUE, null)),
                List.of(new ScoreOp(g.slot(), Op.SET, initial)))));
        return g;
    }

    /** Runs `body` every `period` ticks (at the top level, no entity). */
    public void every(String name, int period, Consumer<Context> body) {
        if (period < 1) throw new IllegalArgumentException("period must be >= 1");
        Global g = new Global("every_" + name);
        Context c = new Context(); body.accept(c);
        List<Statement> inner = new ArrayList<>();
        inner.add(new ScoreOp(g.slot(), Op.SET, 0));
        inner.addAll(c.out);
        root.out.add(Sites.mark(new ScoreOp(g.slot(), Op.ADD, 1)));
        root.out.add(Sites.mark(new When(g.atLeast(period), inner)));
    }

    /** One-shot timer: start(ctx) arms it, `body` runs `delay` ticks later. */
    public record Timer(Global g, int delay) {
        public void start(Context c) { c.global(g).set(delay); }
        public void cancel(Context c) { c.global(g).set(0); }
    }
    public Timer later(String name, int delay, Consumer<Context> body) {
        if (delay < 1) throw new IllegalArgumentException("delay must be >= 1");
        Global g = new Global("later_" + name);
        Context c = new Context(); body.accept(c);
        List<Statement> inner = new ArrayList<>();
        inner.add(new ScoreOp(g.slot(), Op.SUB, 1));
        inner.add(new When(g.is(0), c.out));
        root.out.add(Sites.mark(new When(g.atLeast(1), inner)));
        return new Timer(g, delay);
    }

    // ---- events (vanilla only polls; these wrap the scoreboard criteria) -------------------------
    /** Body runs as the player (self() = the player) on the tick the criterion's score went up. */
    public void onStat(String name, String criterion, Consumer<Context> body) {
        IntProp p = EntityType.PLAYER.criterion("ev_" + name, criterion);
        fireOnScore(p, body);
    }
    private void fireOnScore(IntProp p, Consumer<Context> body) {
        Context c = new Context(); body.accept(c);
        c.add(new ScoreOp(new PropSlot(new Self(), p), Op.SET, 0));
        root.out.add(Sites.mark(new ForEach(EntityType.PLAYER.query().where(p.atLeast(1)), c.out)));
    }
    public void onUse(String item, Consumer<Context> body) { onStat("use_" + item, "minecraft.used:minecraft." + item, body); }
    public void onDeath(Consumer<Context> body) { onStat("death", "deathCount", body); }
    public void onJump(Consumer<Context> body) { onStat("jump", "minecraft.custom:minecraft.jump", body); }
    public void onKillEntity(String entityId, Consumer<Context> body) { onStat("kill_" + entityId, "minecraft.killed:minecraft." + entityId, body); }

    /** Fires the first time a player is seen, and again every time they come back after leaving. */
    public void onJoin(Consumer<Context> body) {
        int id = eventIds++;
        IntProp left = EntityType.PLAYER.criterion("ev_left" + id, "minecraft.custom:minecraft.leave_game");
        IntProp join = EntityType.PLAYER.prop("ev_join" + id);
        String seen = "joined" + id;
        Context c = new Context(); body.accept(c);
        c.add(new ScoreOp(new PropSlot(new Self(), join), Op.SET, 0));
        Statement s1 = Sites.mark(new ForEach(EntityType.PLAYER.query().where(new Not(new HasTag(seen, false))), List.of(
                new TagOp(new Self(), seen, true, false), new ScoreOp(new PropSlot(new Self(), join), Op.SET, 1))));
        Statement s2 = Sites.mark(new ForEach(EntityType.PLAYER.query().where(left.atLeast(1)), List.of(
                new ScoreOp(new PropSlot(new Self(), left), Op.SET, 0), new ScoreOp(new PropSlot(new Self(), join), Op.SET, 1))));
        Statement s3 = Sites.mark(new ForEach(EntityType.PLAYER.query().where(join.atLeast(1)), c.out));
        root.out.add(s1); root.out.add(s2); root.out.add(s3);
    }

    public Program build() { return new Program(ns, List.copyOf(root.out), List.copyOf(load.out), List.copyOf(uninstall.out)); }
}
