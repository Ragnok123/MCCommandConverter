package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.IntProp;
import ru.ragnok123.MCCommandConverter.ir.Ir.Kill;
import ru.ragnok123.MCCommandConverter.ir.Ir.Rotate;
import ru.ragnok123.MCCommandConverter.ir.Ir.TagOp;
import ru.ragnok123.MCCommandConverter.ir.Ir.Target;
import ru.ragnok123.MCCommandConverter.ir.Ir.Tp;

public  final class Ref {
    final Context ctx; final Target t;
    Ref(Context c, Target t) { this.ctx = c; this.t = t; }
    public Var get(IntProp p) { return new Var(this, p); }
    public void addTag(String tag) { ctx.out.add(new TagOp(t, tag, true)); }
    public void removeTag(String tag) { ctx.out.add(new TagOp(t, tag, false)); }
    public void kill() { ctx.out.add(new Kill(t)); }
    public void teleportBy(double dx, double dy, double dz) { ctx.out.add(new Tp(t, dx, dy, dz)); }
    public void rotateBy(double yaw) { ctx.out.add(new Rotate(yaw)); }   // acts on @s only
}