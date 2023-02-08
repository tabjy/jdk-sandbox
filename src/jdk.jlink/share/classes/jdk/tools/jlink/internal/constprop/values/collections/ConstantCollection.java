package jdk.tools.jlink.internal.constprop.values.collections;

public abstract class ConstantCollection<T> {
    public abstract boolean add(T value);

    public abstract ConstantCollection<T> merge(ConstantCollection<T> other);

    public abstract ConstantCollection<T> copy();
}
