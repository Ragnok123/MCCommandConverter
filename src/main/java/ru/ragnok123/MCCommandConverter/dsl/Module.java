package ru.ragnok123.MCCommandConverter.dsl;

import java.util.List;
import java.util.function.Consumer;

import ru.ragnok123.MCCommandConverter.ir.Ir.Condition;
import ru.ragnok123.MCCommandConverter.ir.Ir.EntityType;
import ru.ragnok123.MCCommandConverter.ir.Ir.Program;
import ru.ragnok123.MCCommandConverter.ir.Ir.Sequence;

public final class Module {
    final String ns;
    final Context root = new Context();
    public Module(String ns) { this.ns = ns; }

    public EntityType entityType(String name, String baseEntity) { return new EntityType(name, baseEntity); }
    public void everyTick(Consumer<Context> body) { body.accept(root); }
    /** A per-entity coroutine: starts when `start` holds, then runs its body with wait()s. */
    public void sequence(String name, EntityType t, Condition start, Consumer<Context> body) {
        Context c = new Context(); body.accept(c);
        root.out.add(new Sequence(name, t, start, c.out));
    }
    public Program build() { return new Program(ns, List.copyOf(root.out)); }
}