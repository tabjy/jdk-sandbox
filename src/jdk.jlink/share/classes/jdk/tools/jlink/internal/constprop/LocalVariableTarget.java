package jdk.tools.jlink.internal.constprop;

class LocalVariableTarget extends ConstantizationTarget {
    public final int instruction;
    public final int index;
    public final int line;
    public final String name;

    LocalVariableTarget(int instruction, int index, String name, int line) {
        this.instruction = instruction;
        this.index = index;
        this.name = name;
        this.line = line;
    }

    LocalVariableTarget(int instruction, int index) {
        this.instruction = instruction;
        this.index = index;
        this.name = null;
        this.line = -1;
    }

    @Override
    Scope getScope() {
        return Scope.METHOD;
    }

    @Override
    public String toString() {
        return String.format("LocalVariableTarget[instruction=%d, index=%d, name=%s, line=%d]",
                instruction, index, name, line);
    }
}