package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;

public class TypeValue extends BasicValue {

    private Type type;
    private LdcInsnNode node;
    public TypeValue(Type type, LdcInsnNode node) {
        super(Type.getObjectType("java/lang/Class"));
        this.type = type;
        this.node = node;
    }

    public Type getType() {
        return type;
    }

    public LdcInsnNode getLdcNode() { return node; }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (o instanceof TypeValue) {
            return type.equals(((TypeValue) o).type);
        }
        return false;
    }
}
