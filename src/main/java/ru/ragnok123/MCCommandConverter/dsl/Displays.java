package ru.ragnok123.MCCommandConverter.dsl;

import ru.ragnok123.MCCommandConverter.ir.Nbt;
import ru.ragnok123.MCCommandConverter.ir.Nbt.Compound;

/** Builders for display-entity NBT. Pass the result to summon(...) or dataMerge(...). */
public final class Displays {
    private Displays() {}

    public static Compound text(String plain) { return new Compound().put("text", new Nbt.Text(plain)); }
    public static Compound block(String block) {
        return new Compound().put("block_state", new Compound().put("Name", "minecraft:" + block));
    }
    public static Compound transformation(float sx, float sy, float sz, float tx, float ty, float tz) {
        return new Compound().put("transformation", new Compound()
                .putFloats("left_rotation", 0, 0, 0, 1).putFloats("right_rotation", 0, 0, 0, 1)
                .putFloats("translation", tx, ty, tz).putFloats("scale", sx, sy, sz));
    }
    /** Smooth property changes: start after `delay` ticks, take `duration` ticks (1.20.2+). */
    public static Compound interpolation(int delay, int duration) {
        return new Compound().put("start_interpolation", delay).put("interpolation_duration", duration);
    }
    /** Smooth teleports over `ticks` ticks. NBT name from memory; verify for your target version. */
    public static Compound teleportDuration(int ticks) { return new Compound().put("teleport_duration", ticks); }

    public static Compound merge(Compound... cs) {
        Compound r = new Compound();
        for (Compound c : cs) r.map.putAll(c.map);
        return r;
    }
}
