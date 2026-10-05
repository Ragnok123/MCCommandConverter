package ru.ragnok123.MCCommandConverter;

import ru.ragnok123.MCCommandConverter.ir.Ir.Program;

/** The pass pipeline: validate -> sequences -> or/else -> expressions -> entity init. */
public final class Compiler {
    private Compiler() {}

    public static Program lower(Program p) {
        Validator.run(p);
        p = Lowerer.run(p);
        p = CondLowering.run(p);
        p = ExprLowering.run(p);
        p = InitPass.run(p);
        return p;
    }
}
