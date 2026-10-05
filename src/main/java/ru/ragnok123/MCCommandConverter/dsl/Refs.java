package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Ir.*;
import ru.ragnok123.MCCommandConverter.ir.Nbt;

/** Every entity matching a query, handled in bulk. Position-relative operations run per target. */
public final class Refs {
    final Context ctx; final Query q;
    Refs(Context c, Query q) { ctx = c; this.q = q; }
    private Target t() { return new Many(q); }
    public MultiVar get(IntProp p) { return new MultiVar(ctx, new PropSlot(t(), p)); }
    public void addTag(String tag) { ctx.add(new TagOp(t(), tag, true, false)); }
    public void removeTag(String tag) { ctx.add(new TagOp(t(), tag, false, false)); }
    public void kill() { ctx.add(new Kill(t())); }
    public void teleportBy(double dx, double dy, double dz) { ctx.add(new Tp(t(), dx, dy, dz)); }
    public void rotateBy(double yaw) { ctx.add(new Rotate(t(), yaw)); }
    public void dataMerge(Nbt.Compound nbt) { ctx.add(new DataMerge(t(), nbt)); }
    public void tell(String text) { ctx.add(new Tell(t(), text)); }
}
