package ru.ragnok123.MCCommandConverter.ir;

import java.util.*;

/** The IR: backend-neutral description of what the plugin does. No Minecraft syntax in here. */
public final class Ir {
    private Ir() {}

    // ---- symbols ---------------------------------------------------------------------------
    /** A kind of entity. Backend decides representation (Java: a tag + a base entity id). */
    public record EntityType(String name, String baseEntity) {
        public IntProp prop(String propName) { return new IntProp(this, propName); }
        public Query query() { return new Query(this, List.of(), null, false); }
    }

    /** An integer field on an entity type. Backend decides representation (Java: scoreboard objective). */
    public record IntProp(EntityType owner, String name) {
        public Condition in(Integer min, Integer max) { return new ScoreIn(this, min, max); }
        public Condition atLeast(int n) { return in(n, null); }
        public Condition is(int n) { return in(n, n); }
    }

    public record Query(EntityType type, List<Condition> filters, Double maxDistance, boolean nearestOne) {
        public Query where(Condition... cs) {
            var l = new ArrayList<>(filters); l.addAll(List.of(cs));
            return new Query(type, List.copyOf(l), maxDistance, nearestOne);
        }
        public Query within(double d) { return new Query(type, filters, d, nearestOne); }
        public Query nearest() { return new Query(type, filters, maxDistance, true); }
    }

    // ---- targets ---------------------------------------------------------------------------
    public sealed interface Target permits Self, Many {}
    public record Self() implements Target {}
    public record Many(Query q) implements Target {}

    public sealed interface Condition permits ScoreIn, HasTag, Not, BlockAt, And, Exists {}
    public record ScoreIn(IntProp prop, Integer min, Integer max) implements Condition {}
    public record HasTag(String tag) implements Condition {}
    public record Not(Condition c) implements Condition {}
    public record BlockAt(int dx, int dy, int dz, String block) implements Condition {}
    public record And(List<Condition> cs) implements Condition {}
    /** "an entity matching q exists", with the search centre shifted dy blocks up from @s. */
    public record Exists(Query q, int dy) implements Condition {}

    // ---- statements ------------------------------------------------------------------------
    public enum Op { SET, ADD, SUB }

    public sealed interface Statement permits ForEach, When, Positioned, ScoreOp, TagOp, Kill, Particle, Sound,
            Summon, Tp, Rotate, SetBlock, Fill, Wait, Sequence, Raw {}
    public record ForEach(Query q, List<Statement> body) implements Statement {}
    public record When(Condition c, List<Statement> body) implements Statement {}
    public record Positioned(int dx, int dy, int dz, List<Statement> body) implements Statement {}
    public record ScoreOp(Target t, IntProp p, Op op, int v) implements Statement {}
    public record TagOp(Target t, String tag, boolean add) implements Statement {}
    public record Kill(Target t) implements Statement {}
    public record Particle(String id, double dx, double dy, double dz, double speed, int count) implements Statement {}
    public record Sound(String id, int radius, double volume, double pitch) implements Statement {}
    public record Summon(EntityType type, double dx, double dy, double dz) implements Statement {}
    public record Tp(Target t, double dx, double dy, double dz) implements Statement {}
    public record Rotate(double dyaw) implements Statement {}
    public record SetBlock(int dx, int dy, int dz, String block) implements Statement {}
    public record Fill(int x1, int y1, int z1, int x2, int y2, int z2, String block, String replace) implements Statement {}
    public record Raw(String command) implements Statement {}

    /** Only legal at the top level of a Sequence body. Removed by Lowering. */
    public record Wait(int ticks, List<Statement> during) implements Statement {}
    /** Per-entity coroutine. Removed by Lowering. */
    public record Sequence(String name, EntityType type, Condition start, List<Statement> body) implements Statement {}

    public record Program(String ns, List<Statement> tick) {}
}
