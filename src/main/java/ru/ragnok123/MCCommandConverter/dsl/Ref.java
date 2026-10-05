package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Nbt;

/** Exactly one entity: @s. */
public final class Ref {
    final Context ctx;
    Ref(Context c) { ctx = c; }
    public Var get(IntProp p) { return new Var(ctx, new PropSlot(new Self(), p)); }
    public void addTag(String tag) { ctx.add(new TagOp(new Self(), tag, true, false)); }
    public void removeTag(String tag) { ctx.add(new TagOp(new Self(), tag, false, false)); }
    /** A tag owned by someone else (not namespaced). */
    public void addExternalTag(String tag) { ctx.add(new TagOp(new Self(), tag, true, true)); }
    public void kill() { ctx.add(new Kill(new Self())); }
    /** Relative to the entity's position at the start of the enclosing body. */
    public void teleportBy(double dx, double dy, double dz) { ctx.add(new Tp(new Self(), dx, dy, dz)); }
    public void rotateBy(double yaw) { ctx.add(new Rotate(new Self(), yaw)); }
    public void dataMerge(Nbt.Compound nbt) { ctx.add(new DataMerge(new Self(), nbt)); }
    public void tell(String text) { ctx.add(new Tell(new Self(), text)); }
}
