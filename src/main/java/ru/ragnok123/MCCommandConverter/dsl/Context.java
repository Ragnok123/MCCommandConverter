package ru.ragnok123.MCCommandConverter.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import ru.ragnok123.MCCommandConverter.ir.Ir.Condition;
import ru.ragnok123.MCCommandConverter.ir.Ir.EntityType;
import ru.ragnok123.MCCommandConverter.ir.Ir.Fill;
import ru.ragnok123.MCCommandConverter.ir.Ir.ForEach;
import ru.ragnok123.MCCommandConverter.ir.Ir.Many;
import ru.ragnok123.MCCommandConverter.ir.Ir.Particle;
import ru.ragnok123.MCCommandConverter.ir.Ir.Positioned;
import ru.ragnok123.MCCommandConverter.ir.Ir.Query;
import ru.ragnok123.MCCommandConverter.ir.Ir.Raw;
import ru.ragnok123.MCCommandConverter.ir.Ir.Self;
import ru.ragnok123.MCCommandConverter.ir.Ir.SetBlock;
import ru.ragnok123.MCCommandConverter.ir.Ir.Sound;
import ru.ragnok123.MCCommandConverter.ir.Ir.Statement;
import ru.ragnok123.MCCommandConverter.ir.Ir.Summon;
import ru.ragnok123.MCCommandConverter.ir.Ir.Wait;
import ru.ragnok123.MCCommandConverter.ir.Ir.When;

public final class Context {
    final List<Statement> out = new ArrayList<>();
    private Context sub(Consumer<Context> body) { Context c = new Context(); body.accept(c); return c; }

    public Ref self() { return new Ref(this, new Self()); }
    public Ref each(Query q) { return new Ref(this, new Many(q)); }   // bulk, no iteration

    public void forEach(Query q, Consumer<Context> body) { out.add(new ForEach(q, sub(body).out)); }
    public void when(Condition c, Consumer<Context> body) { out.add(new When(c, sub(body).out)); }
    public void at(int dx, int dy, int dz, Consumer<Context> body) { out.add(new Positioned(dx, dy, dz, sub(body).out)); }

    public void wait(int ticks) { out.add(new Wait(ticks, List.of())); }
    public void waitDuring(int ticks, Consumer<Context> perTick) { out.add(new Wait(ticks, sub(perTick).out)); }

    public void particle(String id, double dx, double dy, double dz, double speed, int count) { out.add(new Particle(id, dx, dy, dz, speed, count)); }
    public void sound(String id, int radius, double vol, double pitch) { out.add(new Sound(id, radius, vol, pitch)); }
    public void summon(EntityType t, double dx, double dy, double dz) { out.add(new Summon(t, dx, dy, dz)); }
    public void setBlock(int dx, int dy, int dz, String b) { out.add(new SetBlock(dx, dy, dz, b)); }
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b, String replace) { out.add(new Fill(x1, y1, z1, x2, y2, z2, b, replace)); }
    public void raw(String cmd) { out.add(new Raw(cmd)); }
}