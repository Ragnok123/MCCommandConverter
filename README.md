##MCCommandConverter
Note: this project is in work and may not be finished

---
Hypixel SMP is funny thing. It is free, but it does not support custom plugins, mods or datapacks. 

But it doesn't forbid command blocks!!

My friend enraged me by killing my dog and griefing my base. So i decided to drop a nuke on him. But how to do that? With command blocks!!!

But because i am so lazy to place 100 command blocks and manually fill them, i decided to write (with AI assist, because MC is not my priority rn) interpreter that will convert java code to command blocks.

---

# How it works

The basic idea is similar to how a compiler works.

You write your Minecraft logic using a small Java-based DSL. MCCommandConverter turns that code into an intermediate representation, transforms it into something Minecraft can actually execute, and finally generates the required commands.

```text
Java DSL
   ↓
Intermediate Representation (IR)
   ↓
Lowering
   ↓
Minecraft commands
   ↓
Command blocks / datapack
```

### 1. Java DSL

Instead of writing hundreds of Minecraft commands manually, you describe what should happen using Java:

```java
plugin.everyTick(ctx -> {
    ctx.forEach(ENTITY, entity -> {
        entity.when(hasTag("charged"), e -> {
            e.particle("minecraft:flame");
            e.sound("minecraft:entity.generic.explode");
        });
    });
});
```

The Java code isn't arbitrary Java that gets magically converted into Minecraft commands. It uses the MCCommandConverter API, which provides Minecraft-specific operations such as entities, selectors, conditions, scoreboards, particles, sounds, sequences and delays.

This gives you the convenience of writing Minecraft logic in Java while keeping the resulting program compatible with vanilla Minecraft commands.

### 2. Intermediate Representation

The DSL is converted into an **Intermediate Representation (IR)**.

The IR is a structured description of the Minecraft program rather than a collection of command strings.

For example:

```text
When
 ├── HasTag("charged")
 └── Particle("minecraft:flame")
```

This separation is important because the compiler can manipulate the program before deciding exactly which Minecraft commands need to be generated.

### 3. Lowering

Some things available in the DSL don't have a direct Minecraft command equivalent.

For example:

```java
sequence("charge", s -> {
    s.doSomething();
    s.wait(20);
    s.doSomethingElse();
});
```

Minecraft doesn't have a normal `wait 20` command.

MCCommandConverter therefore **lowers** this high-level operation into a state machine using Minecraft scoreboards.

Conceptually:

```text
DO A
WAIT 20 TICKS
DO B
```

becomes something similar to:

```text
state = 1

state 1 → DO A → state = 2

state 2 → increment timer
          if timer >= 20:
              state = 3

state 3 → DO B
```

This allows higher-level programming concepts to be implemented using only vanilla Minecraft mechanics.

### 4. Command generation

After lowering, the resulting program is passed to the Minecraft Java Edition backend.

The backend converts the IR into commands such as:

```mcfunction
execute as @e[tag=charged] at @s run particle minecraft:flame ~ ~ ~
```

The backend is responsible for Minecraft command syntax, selectors, `execute` contexts, scoreboards and other Minecraft-specific details.

### 5. Output

The generated commands can then be packaged into a Minecraft datapack or converted into a command-block installation.

The goal is that you write the logic once:

```text
Java
```

and the compiler handles the tedious part:

```text
Java DSL
   ↓
IR
   ↓
lowering
   ↓
Minecraft commands
   ↓
command blocks
```

This makes complicated command-block contraptions much easier to create, modify and maintain than manually building large chains of command blocks.
