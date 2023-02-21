package jdk.tools.jlink.internal.constprop.values.collections;

import java.util.Iterator;

public abstract class ConstantCollection<T> implements Iterable<T> {
    public abstract int size();

    public abstract boolean add(T value);

    public abstract ConstantCollection<T> merge(ConstantCollection<T> other);

    public abstract ConstantCollection<T> copy();

    public abstract Iterator<T> iterator();
}
