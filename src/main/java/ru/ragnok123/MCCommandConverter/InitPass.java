package ru.ragnok123.MCCommandConverter;

import java.util.*;

import ru.ragnok123.MCCommandConverter.ir.Analysis;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;

/**
 * IR -> IR pass: a scoreboard score that was never set doesn't match `scores={x=0}`. So at the start of every tick each
 * entity of a type that has dummy props gets all of them set to 0 once, and is marked with a `<type>_init` tag.
 * This also covers entities that were summoned by hand with only the type tag. A freshly summoned entity is
 * initialised at the start of the next tick.
 */
public final class InitPass {
    private InitPass() {}

    public static String initTag(EntityType t) { return t.name() + "_init"; }

    public static Program run(Program p) {
        Analysis a = Analysis.of(p);
        Map<EntityType, List<IntProp>> byType = new LinkedHashMap<>();
        for (IntProp pr : a.props)
            if (pr.isDummy()) byType.computeIfAbsent(pr.owner(), k -> new ArrayList<>()).add(pr);

        List<Statement> tick = new ArrayList<>();
        for (var e : byType.entrySet()) {
            EntityType t = e.getKey();
            List<Statement> body = new ArrayList<>();
            for (IntProp pr : e.getValue()) body.add(new ScoreOp(new PropSlot(new Self(), pr), Op.SET, 0));
            body.add(new TagOp(new Self(), initTag(t), true, false));
            tick.add(new ForEach(t.query().where(new Not(new HasTag(initTag(t), false))), body));
        }
        tick.addAll(p.tick());
        return p.withTick(tick);
    }
}
