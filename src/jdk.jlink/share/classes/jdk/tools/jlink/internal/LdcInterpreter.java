package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

import static jdk.internal.org.objectweb.asm.tree.analysis.BasicValue.REFERENCE_VALUE;

public class LdcInterpreter extends BasicInterpreter {

    public LdcInterpreter() {
        super(ASM9);
    }

    @Override
    public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
    {
        if (insn instanceof LdcInsnNode) {
            LdcInsnNode ldc = (LdcInsnNode)insn;
            Object cst = ldc.cst;
            if (cst instanceof String) {
                return new StringValue((String)cst, ldc);
            }
            if (cst instanceof Type) {
                return new TypeValue((Type) cst, ldc);
            }
        }
        return super.newOperation(insn);
    }

    @Override
    public BasicValue merge(BasicValue v1, BasicValue v2) {
        if (v1 instanceof StringValue
                && v2 instanceof StringValue
                && v1.equals(v2)) {
            return new StringValue((StringValue)v1);
        }

        // FIXME: merge different types into super type properly?
        if (v1 instanceof TypeValue
                && v2 instanceof TypeValue
                && v1.equals(v2)) {
            return new TypeValue(((TypeValue)v1).getType());
        }

        return super.merge(degradeValue(v1), degradeValue(v2));
    }

    private BasicValue degradeValue(BasicValue v) {
        if (v instanceof StringValue || v instanceof TypeValue) {
            return REFERENCE_VALUE;
        }
        return v;
    }
}
