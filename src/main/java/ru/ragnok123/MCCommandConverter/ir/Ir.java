package ru.ragnok123.MCCommandConverter.ir;

import java.util.*;

/** The IR: backend-neutral description of what the plugin does. No Minecraft command syntax in here. */
public final class Ir {
    private Ir() {}

    // ---- symbols ---------------------------------------------------------------------------

    public record EntityType(String name, String baseEntity, boolean disposable) {
        /** Players are selected with @a and are never tagged by the pack itself. */
        public static final EntityType PLAYER = new EntityType("player", "player", false);

        public EntityType(String name, String baseEntity) { this(name, baseEntity, false); }
        public boolean isPlayer() { return baseEntity.equals("player"); }
        public EntityType asDisposable() { return new EntityType(name, baseEntity, true); }
        public IntProp prop(String propName) { return new IntProp(this, propName, "dummy"); }
        /** A prop driven by the game through a scoreboard criterion, e.g. "deathCount". */
        public IntProp criterion(String propName, String criterion) { return new IntProp(this, propName, criterion); }
        public Query query() { return new Query(this, List.of(), null, false); }
    }

    /** An integer field on an entity type. Backend decides representation (Java: scoreboard objective). */
    public record IntProp(EntityType owner, String name, String criterion) {
        public boolean isDummy() { return criterion.equals("dummy"); }
        public Condition in(Integer min, Integer max) { return new ScoreIn(this, min, max); }
        public Condition atLeast(int n) { return in(n, null); }
        public Condition atMost(int n) { return in(null, n); }
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

    // ---- value slots & expressions ----------------------------------------------------------
    /** Somewhere an int can live. */
    public sealed interface Slot permits PropSlot, GlobSlot, TmpSlot {}
    public record PropSlot(Target t, IntProp p) implements Slot {}
    /** A program-wide variable. */
    public record GlobSlot(String name) implements Slot {}
    /** Compiler-allocated scratch register (created by ExprLowering). */
    public record TmpSlot(int id) implements Slot {}

    public enum BinOp { ADD, SUB, MUL, DIV, MOD, MIN, MAX }
    public enum CmpOp { EQ, LT, LE, GT, GE }
    /** What `scoreboard players operation` can do. */
    public enum CombineOp { ASSIGN, ADD, SUB, MUL, DIV, MOD, MIN, MAX }

    public sealed interface Expr permits Const, Read, Bin, CountOf {}
    public record Const(int v) implements Expr {}
    public record Read(Slot s) implements Expr {}
    public record Bin(BinOp op, Expr l, Expr r) implements Expr {}
    /** How many entities currently match the query. */
    public record CountOf(Query q) implements Expr {}

    // ---- conditions ------------------------------------------------------------------------
    public sealed interface Condition permits ScoreIn, SlotIn, HasTag, Not, BlockAt, And, Or, Exists, Cmp, SlotCmp, FlagIs {}
    public record ScoreIn(IntProp prop, Integer min, Integer max) implements Condition {}
    public record SlotIn(Slot slot, Integer min, Integer max) implements Condition {}
    /** external = a tag owned by someone else (not prefixed with the pack namespace). */
    public record HasTag(String tag, boolean external) implements Condition {
        public HasTag(String tag) { this(tag, false); }
    }
    public record Not(Condition c) implements Condition {}
    public record BlockAt(int dx, int dy, int dz, String block) implements Condition {}
    public record And(List<Condition> cs) implements Condition {}
    public record Or(List<Condition> cs) implements Condition {}
    /** "an entity matching q exists", with the search centre shifted dy blocks up from @s. */
    public record Exists(Query q, int dy) implements Condition {}
    /** High level comparison; removed by ExprLowering. */
    public record Cmp(Expr l, CmpOp op, Expr r) implements Condition {}
    public record SlotCmp(Slot a, CmpOp op, Slot b) implements Condition {}
    /** Compiler-made boolean (see FlagSet). */
    public record Flag(int id) {}
    public record FlagIs(Flag f) implements Condition {}

    // ---- statements ------------------------------------------------------------------------
    public enum Op { SET, ADD, SUB }

    public sealed interface Statement permits ForEach, When, IfElse, Positioned, ScoreOp, Assign, Combine, StoreCount,
            TagOp, Kill, Particle, Sound, Summon, Tp, Rotate, SetBlock, Fill, DataMerge, StorageSet, Tell,
            FlagSet, FlagClear, Raw, Wait, Sequence {}
    public record ForEach(Query q, List<Statement> body) implements Statement {}
    public record When(Condition c, List<Statement> body) implements Statement {}
    /** Removed by CondLowering. */
    public record IfElse(Condition c, List<Statement> then, List<Statement> otherwise) implements Statement {}
    /** absolute=false: relative to the current position; true: world coordinates. */
    public record Positioned(int dx, int dy, int dz, boolean absolute, List<Statement> body) implements Statement {}
    public record ScoreOp(Slot dst, Op op, int v) implements Statement {}
    /** dst = expr. Removed by ExprLowering. */
    public record Assign(Slot dst, Expr e) implements Statement {}
    public record Combine(Slot dst, CombineOp op, Slot src) implements Statement {}
    public record StoreCount(Slot dst, Query q) implements Statement {}
    public record TagOp(Target t, String tag, boolean add, boolean external) implements Statement {}
    public record Kill(Target t) implements Statement {}
    public record Particle(String id, double dx, double dy, double dz, double speed, int count) implements Statement {}
    public record Sound(String id, int radius, double volume, double pitch) implements Statement {}
    /** Extra tags are namespaced; nbt (may be null) is merged into the summon data. */
    public record Summon(EntityType type, double dx, double dy, double dz, List<String> tags, Nbt.Compound nbt) implements Statement {}
    public record Tp(Target t, double dx, double dy, double dz) implements Statement {}
    public record Rotate(Target t, double dyaw) implements Statement {}
    public record SetBlock(int dx, int dy, int dz, String block) implements Statement {}
    public record Fill(int x1, int y1, int z1, int x2, int y2, int z2, String block, String replace) implements Statement {}
    public record DataMerge(Target t, Nbt.Compound nbt) implements Statement {}
    /** data modify storage <ns>:data <path> set value <nbt> */
    public record StorageSet(String path, Nbt value) implements Statement {}
    /** tellraw with plain text. */
    public record Tell(Target t, String text) implements Statement {}
    public record FlagSet(Flag f) implements Statement {}
    public record FlagClear(Flag f) implements Statement {}
    public record Raw(String command) implements Statement {}

    /**
     * Only legal at the top level of a Sequence body. Removed by Lowerer.
     * Leaves the segment once t >= ticks and (until == null or until holds); the condition is first looked at on the
     * tick after the segment was entered.
     */
    public record Wait(int ticks, Condition until, List<Statement> during) implements Statement {}
    public record SeqOptions(boolean edge, boolean loop) {
        public static final SeqOptions DEFAULT = new SeqOptions(false, false);
    }
    public record Sequence(String name, EntityType type, Condition start, SeqOptions opts, List<Statement> body) implements Statement {}
    public record Program(String ns, List<Statement> tick, List<Statement> load, List<Statement> uninstall) {
        public Program withTick(List<Statement> t) { return new Program(ns, t, load, uninstall); }
    }
}
