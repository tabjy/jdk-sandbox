package jdk.tools.jlink.internal.constprop.tagets;

class StaticFieldTarget extends ConstantizationTarget {
    public final String owner;
    public final String name;

    StaticFieldTarget(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    @Override
    Scope getScope() {
        return Scope.CLASS;
    }

    @Override
    public String toString() {
        return String.format("StaticFieldTarget[owner=%s, name=%s]", owner, name);
    }
}
