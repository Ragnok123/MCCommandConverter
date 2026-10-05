package ru.ragnok123.MCCommandConverter.ir;

import java.util.List;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/** Indented IR dump for debugging. */
public final class IrPrinter {
    private IrPrinter() {}

    public static String dump(Program p) {
        StringBuilder sb = new StringBuilder();
        sb.append("program ").append(p.ns()).append('\n');
        sec(sb, "load", p.load()); sec(sb, "tick", p.tick()); sec(sb, "uninstall", p.uninstall());
        return sb.toString();
    }
    static void sec(StringBuilder sb, String n, List<Statement> l) {
        if (l.isEmpty()) return;
        sb.append(n).append(":\n");
        list(sb, l, 1);
    }
    static void list(StringBuilder sb, List<Statement> l, int d) { for (Statement s : l) stmt(sb, s, d); }

    static void stmt(StringBuilder sb, Statement s, int d) {
        String ind = "  ".repeat(d);
        String site = Sites.of(s);
        List<Statement> kids = switch (s) {
            case ForEach f -> f.body();
            case When w -> w.body();
            case Positioned p -> p.body();
            case Sequence q -> q.body();
            case Wait w -> w.during();
            default -> List.of();
        };
        String head = switch (s) {
            case ForEach f -> "forEach " + f.q();
            case When w -> "when " + w.c();
            case IfElse i -> "ifElse " + i.c();
            default -> s.toString();
        };
        sb.append(ind).append(head.length() > 160 && !kids.isEmpty() ? head.substring(0, 160) + "…" : head);
        if (site != null) sb.append("   // ").append(site);
        sb.append('\n');
        list(sb, kids, d + 1);
        if (s instanceof IfElse i) { list(sb, i.then(), d + 1); sb.append(ind).append(" else\n"); list(sb, i.otherwise(), d + 1); }
    }
}
