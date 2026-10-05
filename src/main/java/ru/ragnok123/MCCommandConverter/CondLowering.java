package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Conditions;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * IR -> IR pass: removes Or and IfElse, and moves filters a selector can't express out of ForEach queries.
 *
 * Or(a, b) becomes a flag that each branch sets; IfElse evaluates its condition once into a flag.
 * Conditions are first put in negation normal form so Not only wraps leaves (Not(And) works).
 */
public final class CondLowering {
    private int flags = 0;

    public static Program run(Program p) {
        CondLowering x = new CondLowering();
        return new Program(p.ns(), x.list(p.tick()), x.list(p.load()), x.list(p.uninstall()));
    }

    List<Statement> list(List<Statement> in) {
        List<Statement> out = new ArrayList<>();
        for (Statement s : in) stmt(s, out);
        return out;
    }

    void stmt(Statement s, List<Statement> out) {
        switch (s) {
            case ForEach f -> forEach(f, out);
            case When w -> {
                List<Statement> pre = new ArrayList<>(), post = new ArrayList<>();
                Condition c = hoistOr(Conditions.nnf(w.c()), pre, post, w);
                out.addAll(pre);
                out.add(Sites.copy(w, new When(c, list(w.body()))));
                out.addAll(post);
            }
            case IfElse i -> {
                List<Statement> pre = new ArrayList<>(), post = new ArrayList<>();
                Condition c = hoistOr(Conditions.nnf(i.c()), pre, post, i);
                Flag f = new Flag(flags++);
                out.addAll(pre);
                out.add(Sites.copy(i, new When(c, List.of(Sites.copy(i, new FlagSet(f))))));
                out.add(Sites.copy(i, new When(new FlagIs(f), list(i.then()))));
                if (!i.otherwise().isEmpty())
                    out.add(Sites.copy(i, new When(new Not(new FlagIs(f)), list(i.otherwise()))));
                out.add(Sites.copy(i, new FlagClear(f)));
                out.addAll(post);
            }
            case Positioned p -> out.add(Sites.copy(p, new Positioned(p.dx(), p.dy(), p.dz(), p.absolute(), list(p.body()))));
            default -> out.add(s);
        }
    }

    void forEach(ForEach f, List<Statement> out) {
        List<Condition> sel = new ArrayList<>(), rest = new ArrayList<>();
        for (Condition c : f.q().filters()) (Conditions.selectable(c) ? sel : rest).add(c);
        if (rest.isEmpty()) {
            out.add(Sites.copy(f, new ForEach(f.q(), list(f.body()))));
            return;
        }
        if (f.q().nearestOne())
            throw new IllegalArgumentException("nearest() can't be combined with a filter a selector can't express ("
                    + rest.get(0).getClass().getSimpleName() + "): the result would depend on evaluation order");
        Query q2 = new Query(f.q().type(), List.copyOf(sel), f.q().maxDistance(), false);
        When inner = Sites.copy(f, new When(Conditions.and(rest), f.body()));
        out.add(Sites.copy(f, new ForEach(q2, list(List.of(inner)))));
    }

    /** Replaces every Or in c by a flag; the statements that compute the flag go to pre, its cleanup to post. */
    Condition hoistOr(Condition c, List<Statement> pre, List<Statement> post, Object site) {
        return switch (c) {
            case Or o -> {
                Flag f = new Flag(flags++);
                for (Condition b : o.cs()) {
                    Condition b2 = hoistOr(b, pre, post, site);
                    pre.add(Sites.copy(site, new When(b2, List.of(Sites.copy(site, new FlagSet(f))))));
                }
                post.add(Sites.copy(site, new FlagClear(f)));
                yield new FlagIs(f);
            }
            case And a -> new And(a.cs().stream().map(k -> hoistOr(k, pre, post, site)).toList());
            default -> c;
        };
    }
}
