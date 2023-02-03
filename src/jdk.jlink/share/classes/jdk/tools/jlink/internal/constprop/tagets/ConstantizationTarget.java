package jdk.tools.jlink.internal.constprop.tagets;

public abstract class ConstantizationTarget {
    public enum Scope {CLASS, METHOD}

    /* package-private */
    abstract Scope getScope();
}
