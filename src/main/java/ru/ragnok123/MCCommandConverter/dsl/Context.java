package ru.ragnok123.MCCommandConverter.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Nbt;
import ru.ragnok123.MCCommandConverter.ir.Sites;

/**
 * A block of game-time statements. Everything you call here appends IR; plain Java around it (if/for) runs at
 * build time. when()/ifElse() are the game-time `if`.
 */
public final class Context {
    final List<Statement> out = new ArrayList<>();

    <T extends Statement> T add(T s) { out.add(Sites.mark(s)); return s; }
    private List<Statement> sub(Consumer<Context> body) { Context c = new Context(); body.accept(c); return c.out; }

    public Ref self() { return new Ref(this); }
    public Refs each(Query q) { return new Refs(this, q); }
    public Var global(Global g) { return new Var(this, g.slot()); }

    public void forEach(Query q, Consumer<Context> body) { add(new ForEach(q, sub(body))); }
    public void when(Condition c, Consumer<Context> body) { add(new When(c, sub(body))); }
    public void ifElse(Condition c, Consumer<Context> then, Consumer<Context> otherwise) { add(new IfElse(c, sub(then), sub(otherwise))); }
    /** Relative to the current position (needs a forEach around it). */
    public void at(int dx, int dy, int dz, Consumer<Context> body) { add(new Positioned(dx, dy, dz, false, sub(body))); }
    /** World coordinates; usable anywhere. */
    public void atAbs(int x, int y, int z, Consumer<Context> body) { add(new Positioned(x, y, z, true, sub(body))); }

    // sequences only
    public void wait(int ticks) { add(new Wait(ticks, null, List.of())); }
    public void waitDuring(int ticks, Consumer<Context> perTick) { add(new Wait(ticks, null, sub(perTick))); }
    /** Leave this step on the first tick after entering it where the condition holds. */
    public void waitUntil(Condition c) { add(new Wait(0, c, List.of())); }
    public void waitUntil(Condition c, Consumer<Context> perTick) { add(new Wait(0, c, sub(perTick))); }
    /** At least `ticks` ticks, and the condition must hold. */
    public void waitAtLeastUntil(int ticks, Condition c) { add(new Wait(ticks, c, List.of())); }

    public void particle(String id, double dx, double dy, double dz, double speed, int count) { add(new Particle(id, dx, dy, dz, speed, count)); }
    public void sound(String id, int radius, double vol, double pitch) { add(new Sound(id, radius, vol, pitch)); }
    public void summon(EntityType t, double dx, double dy, double dz) { add(new Summon(t, dx, dy, dz, List.of(), null)); }
    public void summon(EntityType t, double dx, double dy, double dz, List<String> tags, Nbt.Compound nbt) { add(new Summon(t, dx, dy, dz, List.copyOf(tags), nbt)); }
    public void setBlock(int dx, int dy, int dz, String b) { add(new SetBlock(dx, dy, dz, b)); }
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b, String replace) { add(new Fill(x1, y1, z1, x2, y2, z2, b, replace)); }
    public void storage(String path, Nbt value) { add(new StorageSet(path, value)); }
    public void tellAll(String text) { add(new Tell(new Many(EntityType.PLAYER.query()), text)); }
    public void raw(String cmd) { add(new Raw(cmd)); }
}
