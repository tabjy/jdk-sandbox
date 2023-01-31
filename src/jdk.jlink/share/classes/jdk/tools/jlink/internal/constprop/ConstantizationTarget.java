package jdk.tools.jlink.internal.constprop;

public abstract class ConstantizationTarget {
    public enum Scope {CLASS, METHOD}

    /* package-private */
    abstract Scope getScope();
}
