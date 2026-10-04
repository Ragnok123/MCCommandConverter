package ru.ragnok123.MCCommandConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/**
 * Java Edition backend: lowered IR -> mcfunction text.
 * Multi-statement bodies become generated functions (datapack mode), or, with inline=true, the
 * execute-prefix is repeated on every line (command-block mode, where `function` isn't available).
 */
public final class JavaBackend {
    final String ns;
    final boolean inline;
    final Map<String, List<String>> functions = new LinkedHashMap<>();   // name -> lines
    final Set<String> objectives = new LinkedHashSet<>();
    int counter = 0;

    public JavaBackend(String ns, boolean inline) { this.ns = ns; this.inline = inline; }

    /** @return the commands of the per-tick entry point; generated helpers land in `functions`. */
    public List<String> compile(Program lowered) {
        List<String> tick = new ArrayList<>();
        for (Statement s : lowered.tick()) emit(s, "", tick);
        return tick;
    }

    // ---- names -----------------------------------------------------------------------------
    String tag(EntityType t) { return ns + "_" + t.name(); }
    String obj(IntProp p) {
        String o = ns + "_" + p.owner().name() + "_" + p.name();
        objectives.add(o);
        return o;
    }

    // ---- selectors & conditions --------------------------------------------------------------
    String sel(Target t) {
        if (t instanceof Many m) return "@e[" + args(m.q()) + "]";
        return "@s";
    }

    String args(Query q) {
        List<String> a = new ArrayList<>();
        a.add("tag=" + tag(q.type()));
        List<String> scores = new ArrayList<>();
        for (Condition c : q.filters()) {
            if (c instanceof HasTag h) a.add("tag=" + h.tag());
            else if (c instanceof Not n && n.c() instanceof HasTag h) a.add("tag=!" + h.tag());
            else if (c instanceof ScoreIn s) scores.add(obj(s.prop()) + "=" + range(s.min(), s.max()));
            else throw new IllegalArgumentException("not usable as a selector filter: " + c);
        }
        if (!scores.isEmpty()) a.add("scores={" + String.join(",", scores) + "}");
        if (q.maxDistance() != null) a.add("distance=.." + num(q.maxDistance()));
        if (q.nearestOne()) { a.add("sort=nearest"); a.add("limit=1"); }
        return String.join(",", a);
    }

    static String range(Integer min, Integer max) {
        if (min != null && min.equals(max)) return "" + min;
        return (min == null ? "" : min) + ".." + (max == null ? "" : max);
    }

    /** execute-subcommand fragment for a condition. */
    String cond(Condition c, boolean neg) {
        String w = neg ? "unless" : "if";
        return switch (c) {
            case ScoreIn s -> w + " score @s " + obj(s.prop()) + " matches " + range(s.min(), s.max());
            case HasTag h -> w + " entity @s[tag=" + h.tag() + "]";
            case BlockAt b -> w + " block ~" + b.dx() + " ~" + b.dy() + " ~" + b.dz() + " minecraft:" + b.block();
            case Not n -> cond(n.c(), !neg);
            case And a -> {
                if (neg) throw new IllegalArgumentException("not(and(...)) is not supported");
                yield String.join(" ", a.cs().stream().map(x -> cond(x, false)).toList());
            }
            case Exists e -> {
                String body = w + " entity @e[" + args(e.q()) + "]";
                // shift the search centre, then shift back so later fragments are unaffected
                yield e.dy() == 0 ? body : "positioned ~ ~" + e.dy() + " ~ " + body + " positioned ~ ~" + (-e.dy()) + " ~";
            }
        };
    }

    // ---- statements ------------------------------------------------------------------------
    static String join(String prefix, String frag) { return prefix.isEmpty() ? frag : prefix + " " + frag; }

    void leaf(String cmd, String prefix, List<String> out) {
        out.add(prefix.isEmpty() ? cmd : "execute " + prefix + " run " + cmd);
    }

    void body(List<Statement> stmts, String prefix, List<String> out) {
        if (stmts.size() == 1 || inline) {
            for (Statement s : stmts) emit(s, prefix, out);
        } else {
            String name = "gen/f" + (counter++);
            List<String> lines = new ArrayList<>();
            for (Statement s : stmts) emit(s, "", lines);
            functions.put(name, lines);
            leaf("function " + ns + ":" + name, prefix, out);
        }
    }

    void emit(Statement s, String prefix, List<String> out) {
        switch (s) {
            case ForEach f -> body(f.body(), join(prefix, "as @e[" + args(f.q()) + "] at @s"), out);
            case When w -> body(w.body(), join(prefix, cond(w.c(), false)), out);
            case Positioned p -> body(p.body(), join(prefix, "positioned " + rel(p.dx()) + " " + rel(p.dy()) + " " + rel(p.dz())), out);
            case ScoreOp o -> leaf("scoreboard players " + switch (o.op()) { case SET -> "set"; case ADD -> "add"; case SUB -> "remove"; }
                    + " " + sel(o.t()) + " " + obj(o.p()) + " " + o.v(), prefix, out);
            case TagOp t -> leaf("tag " + sel(t.t()) + (t.add() ? " add " : " remove ") + t.tag(), prefix, out);
            case Kill k -> leaf("kill " + sel(k.t()), prefix, out);
            case Particle p -> leaf("particle " + p.id() + " ~ ~ ~ " + num(p.dx()) + " " + num(p.dy()) + " " + num(p.dz()) + " " + num(p.speed()) + " " + p.count() + " force", prefix, out);
            case Sound so -> leaf("playsound " + so.id() + " master @a[distance=.." + so.radius() + "] ~ ~ ~ " + num(so.volume()) + " " + num(so.pitch()), prefix, out);
            case Summon su -> leaf("summon " + su.type().baseEntity() + " " + rel(su.dx()) + " " + rel(su.dy()) + " " + rel(su.dz())
                    + " {Tags:[\"" + tag(su.type()) + "\"]}", prefix, out);
            case Tp t -> leaf("tp " + sel(t.t()) + " " + rel(t.dx()) + " " + rel(t.dy()) + " " + rel(t.dz()), prefix, out);
            case Rotate r -> leaf("rotate @s ~" + num(r.dyaw()) + " ~", prefix, out);
            case SetBlock b -> leaf("setblock ~" + b.dx() + " ~" + b.dy() + " ~" + b.dz() + " " + b.block(), prefix, out);
            case Fill f -> leaf("fill ~" + f.x1() + " ~" + f.y1() + " ~" + f.z1() + " ~" + f.x2() + " ~" + f.y2() + " ~" + f.z2() + " " + f.block()
                    + (f.replace() == null ? "" : " replace " + f.replace()), prefix, out);
            case Raw r -> leaf(r.command(), prefix, out);
            case Wait w -> throw new IllegalStateException("Wait survived lowering");
            case Sequence q -> throw new IllegalStateException("Sequence survived lowering");
        }
    }

    static String num(double d) {
        BigDecimal b = BigDecimal.valueOf(d).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.signum() == 0 ? "0" : b.toPlainString();
    }
    static String rel(double d) { return d == 0 ? "~" : "~" + num(d); }
}
