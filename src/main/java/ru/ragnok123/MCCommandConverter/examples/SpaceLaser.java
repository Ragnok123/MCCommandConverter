package ru.ragnok123.MCCommandConverter.examples;

import ru.ragnok123.MCCommandConverter.dsl.*;
import ru.ragnok123.MCCommandConverter.dsl.Module;
import ru.ragnok123.MCCommandConverter.ir.Ir.*;

import static ru.ragnok123.MCCommandConverter.dsl.MinecraftDsl.*;

/** Stress test: the charge + fire timeline of IJAMinecraft's Space Laser, written against the DSL. */
public final class SpaceLaser {
    enum Side {
        NORTH(0, -1), WEST(-1, 0), EAST(1, 0), SOUTH(0, 1);
        final int dx, dz; Side(int dx, int dz) { this.dx = dx; this.dz = dz; }
        String tag() { return name().toLowerCase(); }
    }

    public static Program build() {
        Module p = new Module("laser");

        EntityType CANNON = p.entityType("cannon", "text_display");
        EntityType FIN    = p.entityType("fin", "block_display");
        EntityType TARGET = p.entityType("target", "text_display");
        EntityType BOLT   = p.disposableType("bolt", "text_display");

        IntProp charge = FIN.prop("charge");
        IntProp age = BOLT.prop("age");

        p.everyTick(t -> {
            for (Side s : Side.values()) {                                // build-time loop -> 4 game-time commands
                t.forEach(CANNON.query(), cannon ->
                    cannon.when(blockAt(s.dx, 0, s.dz, "redstone_block"), c ->
                        c.at(0, 3, 0, in ->
                            in.each(FIN.query().where(hasTag(s.tag()), not(hasTag("locked"))).within(3)).get(charge).add(2))));
            }
            t.forEach(FIN.query().where(charge.is(1)), fin -> fin.self().rotateBy(-180));   // snap back as it hits 0
            t.each(FIN.query().where(charge.atLeast(1))).get(charge).sub(1);                 // decay
            t.each(FIN.query().where(charge.atLeast(43))).get(charge).set(43);               // cap
            t.forEach(FIN.query().where(charge.in(1, 40)), fin -> fin.self().rotateBy(1.125));
            // colour swap at charge 40/41
            t.forEach(FIN.query().where(charge.is(41)), fin -> fin.self().dataMerge(Displays.block("red_concrete")));
            t.forEach(FIN.query().where(charge.is(0), hasTag("hot")), fin -> {
                fin.self().removeTag("hot");
                fin.self().dataMerge(Displays.block("iron_block"));
            });

            // ---- beam bolts rise 3 blocks/tick and expire ------------------------------------
            t.each(BOLT.query()).get(age).add(1);
            t.forEach(BOLT.query(), b -> b.self().teleportBy(0, 3, 0));
            t.each(BOLT.query().where(age.atLeast(90))).kill();
        });

        Query finsFull = FIN.query().where(charge.is(43)).within(2);
        Condition fullyCharged = all(
            exists(finsFull.where(hasTag("north")), 3), exists(finsFull.where(hasTag("west")), 3),
            exists(finsFull.where(hasTag("east")), 3),  exists(finsFull.where(hasTag("south")), 3));

        p.sequence("fire", CANNON, fullyCharged, SeqOptions.DEFAULT, s -> {
            s.fill(-1, 0, 0, 1, 0, 0, "bedrock", "redstone_block");
            s.fill(0, 0, -1, 0, 0, 1, "bedrock", "redstone_block");
            s.waitDuring(60,  d -> d.particle("large_smoke", 0.1, 0.1, 0.1, 0.1, 9));
            s.waitDuring(110, d -> { d.particle("large_smoke", 0.1, 0.1, 0.1, 0.1, 9); d.sound("item.totem.use", 50, 0.1, 0.5); });
            s.sound("entity.ghast.shoot", 50, 1, 0.5);
            s.particle("large_smoke", 1.3, 1, 1.3, 0.01, 450);
            s.waitDuring(130, d -> { d.particle("large_smoke", 0.1, 0.1, 0.1, 0.1, 9); d.summon(BOLT, 0, 2, 0); });
            s.wait(10);
            s.forEach(TARGET.query().nearest(), tgt -> { tgt.self().kill(); });
            s.wait(60);
            s.fill(-1, 0, 0, 1, 0, 0, "air", "bedrock");
            s.fill(0, 0, -1, 0, 0, 1, "air", "bedrock");
        });
        return p.build();
    }
}
