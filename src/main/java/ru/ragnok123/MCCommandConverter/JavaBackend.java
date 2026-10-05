package ru.ragnok123.MCCommandConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Analysis;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Nbt;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * Java Edition backend: lowered IR -> mcfunction text.
 *
 * datapack mode: multi-statement bodies become generated functions (named by content hash, deduplicated).
 * inline mode (command blocks, no `function`): the execute prefix is repeated per line. That is only correct if
 * earlier lines can't change what the prefix matches, so when they can, the match is frozen first with a temporary
 * tag (entity context) or a fake-player flag (no entity), and removed afterwards.
 */
public final class JavaBackend {
    final String ns;
    final boolean inline;
    final TargetVersion ver;
    final Map<String, List<String>> functions = new LinkedHashMap<>();
    final Map<String, String> objectives = new LinkedHashMap<>();   // name -> criterion
    final Set<String> tagsSeen = new LinkedHashSet<>();
    private final Map<String, String> byContent = new HashMap<>();
    private final Set<EntityType> disposables = new LinkedHashSet<>();
    private int snap = 0;

    public record Result(List<String> tick, List<String> load, List<String> uninstall) {}

    public JavaBackend(String ns, boolean inline) { this(ns, inline, TargetVersion.LATEST); }
    public JavaBackend(String ns, boolean inline, TargetVersion ver) { this.ns = ns; this.inline = inline; this.ver = ver; }

    public Result compile(Program p) {
        Analysis a = Analysis.of(p);
        for (EntityType t : a.types) if (t.disposable() && !t.isPlayer()) disposables.add(t);
        List<String> tick = new ArrayList<>(), load = new ArrayList<>(), un = new ArrayList<>();
        for (Statement s : p.tick()) emit(s, Prefix.ROOT, tick);
        for (Statement s : p.load()) emit(s, Prefix.ROOT, load);
        for (Statement s : p.uninstall()) emit(s, Prefix.ROOT, un);
        // automatic cleanup
        for (EntityType t : disposables) un.add("kill @e[type=" + t.baseEntity() + ",tag=" + tag(t) + "]");
        for (EntityType t : a.types) tagsSeen.add(tag(t));
        for (String t : new ArrayList<>(tagsSeen)) un.add("tag @e[tag=" + t + "] remove " + t);
        for (String o : objectives.keySet()) un.add("scoreboard objectives remove " + o);
        return new Result(tick, load, un);
    }

    // ---- prefix handling ---------------------------------------------------------------------
    /** kind: E entity context (as..at @s), C condition, P position. */
    record Frag(char kind, String text, String selPrefix, Set<String> reads) {}

    static final class Prefix {
        static final Prefix ROOT = new Prefix(List.of(), false);
        final List<Frag> frags; final boolean entity;
        Prefix(List<Frag> f, boolean e) { frags = f; entity = e; }
        Prefix with(Frag f) {
            List<Frag> l = new ArrayList<>(frags); l.add(f);
            return new Prefix(l, entity || f.kind() == 'E');
        }
        boolean isEmpty() { return frags.isEmpty(); }
        String text() {
            StringBuilder sb = new StringBuilder();
            for (Frag f : frags) { if (sb.length() > 0) sb.append(' '); sb.append(f.text()); }
            return sb.toString();
        }
        Prefix fnRoot() { return new Prefix(List.of(), entity); }
    }

    static Frag pos(String t) { return new Frag('P', t, null, Set.of()); }

    // ---- names -----------------------------------------------------------------------------
    String tag(EntityType t) { return ns + "_" + t.name(); }
    String userTag(String tag, boolean external) {
        String r = external ? tag : ns + "_" + tag;
        tagsSeen.add(external ? null : r);
        tagsSeen.remove(null);
        return r;
    }
    String obj(IntProp p) {
        String o = ns + "_" + p.owner().name() + "_" + p.name();
        objectives.putIfAbsent(o, p.criterion());
        return o;
    }
    String globalObj() { String o = ns + "_global"; objectives.putIfAbsent(o, "dummy"); return o; }
    String tmpObj() { String o = ns + "_tmp"; objectives.putIfAbsent(o, "dummy"); return o; }
    String tmpEntityObj(int id) { String o = ns + "_t" + id; objectives.putIfAbsent(o, "dummy"); return o; }

    record Holder(String who, String obj) {}

    Holder holder(Slot s, Prefix p) {
        return switch (s) {
            case PropSlot ps -> new Holder(sel(ps.t()), obj(ps.p()));
            case GlobSlot g -> new Holder("#" + g.name(), globalObj());
            case TmpSlot t -> p.entity ? new Holder("@s", tmpEntityObj(t.id())) : new Holder("#t" + t.id(), tmpObj());
        };
    }

    // ---- selectors ---------------------------------------------------------------------------
    String sel(Target t) { return t instanceof Many m ? selector(m.q()) : "@s"; }

    String selector(Query q) {
        List<String> a = new ArrayList<>();
        boolean player = q.type().isPlayer();
        if (!player) a.add("type=" + q.type().baseEntity());
        a.add("tag=" + tag(q.type()));
        if (player) a.remove(a.size() - 1);                      // players are never tagged by the pack
        Map<String, long[]> scores = new LinkedHashMap<>();
        for (Condition c : q.filters()) {
            if (c instanceof HasTag h) a.add("tag=" + userTag(h.tag(), h.external()));
            else if (c instanceof Not n && n.c() instanceof HasTag h) a.add("tag=!" + userTag(h.tag(), h.external()));
            else if (c instanceof ScoreIn s) {
                long[] r = scores.computeIfAbsent(obj(s.prop()), k -> new long[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
                if (s.min() != null) r[0] = Math.max(r[0], s.min());
                if (s.max() != null) r[1] = Math.min(r[1], s.max());
            } else throw new IllegalArgumentException("not usable as a selector filter: " + c);
        }
        if (!scores.isEmpty()) {
            List<String> sc = new ArrayList<>();
            for (var e : scores.entrySet()) {
                long[] r = e.getValue();
                if (r[0] > r[1]) throw new IllegalArgumentException("contradicting score filters on " + e.getKey() + " in query for " + q.type().name());
                sc.add(e.getKey() + "=" + range(r[0] == Integer.MIN_VALUE ? null : (int) r[0], r[1] == Integer.MAX_VALUE ? null : (int) r[1]));
            }
            a.add("scores={" + String.join(",", sc) + "}");
        }
        if (q.maxDistance() != null) a.add("distance=.." + num(q.maxDistance()));
        if (q.nearestOne()) { a.add("sort=nearest"); a.add("limit=1"); }
        if (player) return a.isEmpty() ? "@a" : "@a[" + String.join(",", a) + "]";
        return "@e[" + String.join(",", a) + "]";
    }

    static String range(Integer min, Integer max) {
        if (min != null && min.equals(max)) return "" + min;
        return (min == null ? "" : min) + ".." + (max == null ? "" : max);
    }

    String selPrefix(Query q) {
        return q.type().isPlayer() ? "@a[" : "@e[type=" + q.type().baseEntity() + ",";
    }

    // ---- conditions --------------------------------------------------------------------------
    String cond(Condition c, boolean neg, Prefix p) {
        String w = neg ? "unless" : "if";
        return switch (c) {
            case ScoreIn s -> w + " score @s " + obj(s.prop()) + " matches " + range(s.min(), s.max());
            case SlotIn s -> { Holder h = holder(s.slot(), p); yield w + " score " + h.who() + " " + h.obj() + " matches " + range(s.min(), s.max()); }
            case SlotCmp s -> {
                Holder a = holder(s.a(), p), b = holder(s.b(), p);
                yield w + " score " + a.who() + " " + a.obj() + " " + cmpSym(s.op()) + " " + b.who() + " " + b.obj();
            }
            case HasTag h -> w + " entity @s[tag=" + userTag(h.tag(), h.external()) + "]";
            case BlockAt b -> w + " block ~" + b.dx() + " ~" + b.dy() + " ~" + b.dz() + " minecraft:" + b.block();
            case Not n -> cond(n.c(), !neg, p);
            case And a -> {
                if (neg) throw new IllegalArgumentException("not(and(...)) reached the backend; run CondLowering first");
                yield String.join(" ", a.cs().stream().map(x -> cond(x, false, p)).toList());
            }
            case Exists e -> {
                String body = w + " entity " + selector(e.q());
                yield e.dy() == 0 ? body : "positioned ~ ~" + e.dy() + " ~ " + body + " positioned ~ ~" + (-e.dy()) + " ~";
            }
            case FlagIs f -> p.entity ? w + " entity @s[tag=" + flagTag(f.f()) + "]"
                                      : w + " score #f" + f.f().id() + " " + tmpObj() + " matches 1";
            case Or o -> throw new IllegalStateException("Or survived CondLowering");
            case Cmp x -> throw new IllegalStateException("Cmp survived ExprLowering");
        };
    }

    String flagTag(Flag f) { String t = ns + "_f" + f.id(); tagsSeen.add(t); return t; }
    static String cmpSym(CmpOp o) { return switch (o) { case EQ -> "="; case LT -> "<"; case LE -> "<="; case GT -> ">"; case GE -> ">="; }; }

    // ---- read/write footprints (for deciding whether a prefix needs freezing) -------------------
    void readsOf(Query q, Set<String> r) {
        r.add("e:" + q.type().name());
        if (q.maxDistance() != null || q.nearestOne()) r.add("p");
        for (Condition c : q.filters()) readsOf(c, r);
    }
    void readsOf(Condition c, Set<String> r) {
        switch (c) {
            case ScoreIn s -> r.add("s:" + s.prop().name() + "@" + s.prop().owner().name());
            case SlotIn s -> r.add(slotKey(s.slot()));
            case SlotCmp s -> { r.add(slotKey(s.a())); r.add(slotKey(s.b())); }
            case HasTag h -> r.add("t:" + h.tag());
            case BlockAt b -> r.add("b");
            case Not n -> readsOf(n.c(), r);
            case And a -> a.cs().forEach(x -> readsOf(x, r));
            case Or o -> o.cs().forEach(x -> readsOf(x, r));
            case Exists e -> { readsOf(e.q(), r); r.add("p"); }
            case FlagIs f -> r.add("f:" + f.f().id());
            case Cmp x -> r.add("*");
        }
    }
    static String slotKey(Slot s) {
        return switch (s) {
            case PropSlot p -> "s:" + p.p().name() + "@" + p.p().owner().name();
            case GlobSlot g -> "g:" + g.name();
            case TmpSlot t -> "tmp:" + t.id();
        };
    }
    void writesOf(List<Statement> l, Set<String> w) { for (Statement s : l) writesOf(s, w); }
    void writesOf(Statement s, Set<String> w) {
        switch (s) {
            case ForEach f -> writesOf(f.body(), w);
            case When x -> writesOf(x.body(), w);
            case IfElse x -> { writesOf(x.then(), w); writesOf(x.otherwise(), w); }
            case Positioned x -> writesOf(x.body(), w);
            case ScoreOp o -> w.add(slotKey(o.dst()));
            case Combine c -> w.add(slotKey(c.dst()));
            case StoreCount c -> w.add(slotKey(c.dst()));
            case TagOp t -> w.add("t:" + t.tag());
            case Kill k -> w.add(k.t() instanceof Many m ? "e:" + m.q().type().name() : "e:*");
            case Summon su -> { w.add("e:" + su.type().name()); for (String t : su.tags()) w.add("t:" + t); }
            case Tp t -> w.add("p");
            case SetBlock b -> w.add("b");
            case Fill f -> w.add("b");
            case FlagSet f -> w.add("f:" + f.f().id());
            case FlagClear f -> w.add("f:" + f.f().id());
            case Raw r -> w.add("*");
            default -> {}
        }
    }
    static boolean conflict(Set<String> reads, Set<String> writes) {
        if (writes.contains("*") || reads.contains("*")) return true;
        for (String wr : writes) {
            if (wr.equals("e:*")) { for (String r : reads) if (r.startsWith("e:")) return true; }
            else if (reads.contains(wr)) return true;
        }
        return false;
    }

    // ---- bodies ----------------------------------------------------------------------------
    void body(List<Statement> stmts, Prefix p, List<String> out) {
        if (stmts.isEmpty()) return;
        if (stmts.size() == 1) { emit(stmts.get(0), p, out); return; }
        if (!inline) {
            List<String> lines = new ArrayList<>();
            for (Statement s : stmts) emit(s, p.fnRoot(), lines);
            String name = register(lines);
            leaf("function " + ns + ":" + name, p, out);
            return;
        }
        // inline: repeat the prefix, freezing the match first if the body can invalidate it
        checkNestedTp(stmts);
        Prefix q = p;
        List<String> cleanup = new ArrayList<>();
        if (!p.isEmpty()) {
            Set<String> reads = new HashSet<>(), writes = new HashSet<>();
            for (Frag f : p.frags) reads.addAll(f.reads());
            writesOf(stmts.subList(0, stmts.size() - 1), writes);
            if (conflict(reads, writes)) q = freeze(p, out, cleanup);
        }
        double dx = 0, dy = 0, dz = 0;
        for (Statement s : stmts) {
            Prefix pp = q;
            if (dx != 0 || dy != 0 || dz != 0) pp = q.with(pos("positioned " + rel(-dx) + " " + rel(-dy) + " " + rel(-dz)));
            emit(s, pp, out);
            if (s instanceof Tp t && t.t() instanceof Self) { dx += t.dx(); dy += t.dy(); dz += t.dz(); }
        }
        out.addAll(cleanup);
    }

    /** tp(self) moves the executor; later lines would re-run `at @s` at the new place, unlike a function. */
    void checkNestedTp(List<Statement> stmts) {
        for (int i = 0; i < stmts.size() - 1; i++) {
            Statement s = stmts.get(i);
            if (!(s instanceof Tp) && containsSelfTp(s))
                throw new IllegalArgumentException("inline mode: a conditional tp(self) followed by more statements in the same body would shift later lines ("
                        + Sites.of(s) + "); put the tp last or use the datapack target");
        }
    }
    boolean containsSelfTp(Statement s) {
        List<Statement> kids = switch (s) {
            case When w -> w.body();
            case Positioned x -> x.body();
            case IfElse x -> { var l = new ArrayList<>(x.then()); l.addAll(x.otherwise()); yield l; }
            case Tp t -> { yield t.t() instanceof Self ? List.of(s) : List.<Statement>of(); }
            default -> List.<Statement>of();
        };
        for (Statement k : kids) if (k instanceof Tp t ? t.t() instanceof Self : containsSelfTp(k)) return true;
        return false;
    }

    Prefix freeze(Prefix p, List<String> out, List<String> cleanup) {
        int id = snap++;
        int lastE = -1;
        for (int i = 0; i < p.frags.size(); i++) if (p.frags.get(i).kind() == 'E') lastE = i;
        if (lastE >= 0) {
            String tg = ns + "_s" + id;
            String selPre = p.frags.get(lastE).selPrefix();
            String snapSel = selPre + "tag=" + tg + "]";
            leaf("tag @s add " + tg, p, out);
            cleanup.add("tag " + snapSel + " remove " + tg);
            List<Frag> nf = new ArrayList<>();
            nf.add(new Frag('E', "as " + snapSel + " at @s", selPre, Set.of("t:" + tg)));
            for (int i = lastE + 1; i < p.frags.size(); i++) if (p.frags.get(i).kind() == 'P') nf.add(p.frags.get(i));
            return new Prefix(nf, true);
        }
        String h = "#s" + id;
        leaf("scoreboard players set " + h + " " + tmpObj() + " 1", p, out);
        cleanup.add("scoreboard players reset " + h + " " + tmpObj());
        List<Frag> nf = new ArrayList<>();
        for (Frag f : p.frags) if (f.kind() == 'P') nf.add(f);
        nf.add(new Frag('C', "if score " + h + " " + tmpObj() + " matches 1", null, Set.of("snap" + id)));
        return new Prefix(nf, p.entity);
    }

    String register(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) if (!l.startsWith("#")) sb.append(l).append('\n');
        String key = sb.toString();
        String ex = byContent.get(key);
        if (ex != null) return ex;
        String name = "gen/" + hash(key);
        while (functions.containsKey(name)) name += "x";
        functions.put(name, lines);
        byContent.put(key, name);
        return name;
    }
    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    // ---- statements ------------------------------------------------------------------------
    void leaf(String cmd, Prefix p, List<String> out) {
        out.add(p.isEmpty() ? cmd : "execute " + p.text() + " run " + cmd);
    }
    void exec(String frag, Prefix p, List<String> out) {
        out.add("execute " + (p.isEmpty() ? "" : p.text() + " ") + frag);
    }

    Prefix withEntity(Prefix p, Query q) {
        Set<String> r = new HashSet<>();
        readsOf(q, r);
        return p.with(new Frag('E', "as " + selector(q) + " at @s", selPrefix(q), r));
    }
    Prefix withCond(Prefix p, Condition c, boolean neg) {
        Set<String> r = new HashSet<>();
        readsOf(c, r);
        return p.with(new Frag('C', cond(c, neg, p), null, r));
    }

    /** Run `cmd` once per target with @s = target and the position at the target. */
    void perTarget(Target t, Prefix p, List<String> out, String cmdOnSelf, boolean needsAt) {
        if (t instanceof Many m) {
            Prefix q = withEntity(p, m.q());
            leaf(cmdOnSelf, q, out);
        } else if (needsAt && (!p.entity || p.frags.stream().anyMatch(f -> f.kind() == 'P'))) {
            exec("at @s run " + cmdOnSelf, p, out);
        } else leaf(cmdOnSelf, p, out);
    }

    void emit(Statement s, Prefix p, List<String> out) {
        if (!inline) {
            String site = Sites.of(s);
            if (site != null && !("# " + site).equals(out.isEmpty() ? "" : out.get(out.size() - 1))) out.add("# " + site);
        }
        switch (s) {
            case ForEach f -> body(f.body(), withEntity(p, f.q()), out);
            case When w -> body(w.body(), withCond(p, w.c(), false), out);
            case Positioned x -> body(x.body(), p.with(pos("positioned " + (x.absolute() ? x.dx() + " " + x.dy() + " " + x.dz()
                    : rel(x.dx()) + " " + rel(x.dy()) + " " + rel(x.dz())))), out);
            case ScoreOp o -> {
                Holder h = holder(o.dst(), p);
                int v = o.v();
                String verb; 
                switch (o.op()) {
                    case SET -> verb = "set";
                    case ADD -> { verb = v >= 0 ? "add" : "remove"; v = Math.abs(v); }
                    default -> { verb = v >= 0 ? "remove" : "add"; v = Math.abs(v); }
                }
                leaf("scoreboard players " + verb + " " + h.who() + " " + h.obj() + " " + v, p, out);
            }
            case Combine c -> {
                Holder d = holder(c.dst(), p), r = holder(c.src(), p);
                String op = switch (c.op()) { case ASSIGN -> "="; case ADD -> "+="; case SUB -> "-="; case MUL -> "*="; case DIV -> "/="; case MOD -> "%="; case MIN -> "<"; case MAX -> ">"; };
                leaf("scoreboard players operation " + d.who() + " " + d.obj() + " " + op + " " + r.who() + " " + r.obj(), p, out);
            }
            case StoreCount c -> {
                Holder d = holder(c.dst(), p);
                exec("store result score " + d.who() + " " + d.obj() + " if entity " + selector(c.q()), p, out);
            }
            case TagOp t -> leaf("tag " + sel(t.t()) + (t.add() ? " add " : " remove ") + userTag(t.tag(), t.external()), p, out);
            case Kill k -> leaf("kill " + sel(k.t()), p, out);
            case Particle x -> leaf("particle " + x.id() + " ~ ~ ~ " + num(x.dx()) + " " + num(x.dy()) + " " + num(x.dz()) + " " + num(x.speed()) + " " + x.count() + " force", p, out);
            case Sound so -> leaf("playsound " + so.id() + " master @a[distance=.." + so.radius() + "] ~ ~ ~ " + num(so.volume()) + " " + num(so.pitch()), p, out);
            case Summon su -> {
                Nbt.Compound n = new Nbt.Compound();
                if (su.nbt() != null) {
                    if (su.nbt().map.containsKey("Tags")) throw new IllegalArgumentException("Tags is managed by the compiler; use the tags list");
                    n.map.putAll(su.nbt().map);
                }
                List<Nbt> tags = new ArrayList<>();
                tags.add(new Nbt.Str(tag(su.type())));
                tagsSeen.add(tag(su.type()));
                for (String t : su.tags()) tags.add(new Nbt.Str(userTag(t, false)));
                n.map.put("Tags", new Nbt.NList(tags));
                leaf("summon " + su.type().baseEntity() + " " + rel(su.dx()) + " " + rel(su.dy()) + " " + rel(su.dz()) + " " + snbt(n), p, out);
            }
            case Tp t -> {
                String c = "tp @s " + rel(t.dx()) + " " + rel(t.dy()) + " " + rel(t.dz());
                perTarget(t.t(), p, out, c, true);
            }
            case Rotate r -> {
                String c = ver.hasRotate() ? "rotate @s ~" + num(r.dyaw()) + " ~" : "tp @s ~ ~ ~ ~" + num(r.dyaw()) + " ~";
                perTarget(r.t(), p, out, c, !ver.hasRotate());
            }
            case SetBlock b -> leaf("setblock ~" + b.dx() + " ~" + b.dy() + " ~" + b.dz() + " " + b.block(), p, out);
            case Fill f -> leaf("fill ~" + f.x1() + " ~" + f.y1() + " ~" + f.z1() + " ~" + f.x2() + " ~" + f.y2() + " ~" + f.z2() + " " + f.block()
                    + (f.replace() == null ? "" : " replace " + f.replace()), p, out);
            case DataMerge d -> perTarget(d.t(), p, out, "data merge entity @s " + snbt(d.nbt()), false);
            case StorageSet d -> leaf("data modify storage " + ns + ":data " + d.path() + " set value " + snbt(d.value()), p, out);
            case Tell t -> leaf("tellraw " + sel(t.t()) + " " + json(t.text()), p, out);
            case FlagSet f -> {
                if (p.entity) leaf("tag @s add " + flagTag(f.f()), p, out);
                else leaf("scoreboard players set #f" + f.f().id() + " " + tmpObj() + " 1", p, out);
            }
            case FlagClear f -> {
                if (p.entity) leaf("tag @s remove " + flagTag(f.f()), p, out);
                else leaf("scoreboard players reset #f" + f.f().id() + " " + tmpObj(), p, out);
            }
            case Raw r -> leaf(r.command(), p, out);
            case IfElse x -> throw new IllegalStateException("IfElse survived CondLowering");
            case Assign x -> throw new IllegalStateException("Assign survived ExprLowering");
            case Wait w -> throw new IllegalStateException("Wait survived lowering");
            case Sequence q -> throw new IllegalStateException("Sequence survived lowering");
        }
    }

    // ---- value rendering ---------------------------------------------------------------------
    static String json(String plain) { return "{\"text\":\"" + plain.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"; }

    String snbt(Nbt n) {
        return switch (n) {
            case Nbt.Str s -> quote(s.v());
            case Nbt.Int i -> "" + i.v();
            case Nbt.Dbl d -> num(d.v()) + "d";
            case Nbt.Flt f -> num(f.v()) + "f";
            case Nbt.Bool b -> b.v() ? "1b" : "0b";
            case Nbt.NList l -> "[" + String.join(",", l.items().stream().map(this::snbt).toList()) + "]";
            case Nbt.Text t -> ver.snbtComponents() ? "{text:" + quote(t.plain()) + "}"
                    : "'" + json(t.plain()).replace("\\", "\\\\").replace("'", "\\'") + "'";
            case Nbt.Raw r -> r.snbt();
            case Nbt.Compound c -> {
                StringBuilder sb = new StringBuilder("{");
                boolean first = true;
                for (var e : c.map.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    String k = e.getKey();
                    sb.append(k.matches("[A-Za-z0-9_.+-]+") ? k : quote(k)).append(':').append(snbt(e.getValue()));
                }
                yield sb.append('}').toString();
            }
        };
    }
    static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static String num(double d) {
        BigDecimal b = BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.signum() == 0 ? "0" : b.toPlainString();
    }
    static String rel(double d) { return d == 0 ? "~" : "~" + num(d); }
}
