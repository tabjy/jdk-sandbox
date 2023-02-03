package jdk.tools.jlink.internal.constprop.tagets;

class StackTarget extends ConstantizationTarget {
    public final int instruction;
    public final int index;

    StackTarget(int instruction, int index) {
        this.instruction = instruction;
        this.index = index;
    }

    @Override
    Scope getScope() {
        return Scope.METHOD;
    }

    @Override
    public String toString() {
        return String.format("StackValueTarget[instruction=%d, index=%d]", instruction, index);
    }
}
