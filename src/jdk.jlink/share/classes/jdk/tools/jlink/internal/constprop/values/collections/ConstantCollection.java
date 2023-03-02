package jdk.tools.jlink.internal.constprop.values.collections;

import java.util.Iterator;

public abstract class ConstantCollection<T> implements Iterable<T> {

    private boolean isDegraded = false;

    public final void degrade() {
        doDegrade();
        isDegraded = true;
    }

    public final boolean isDegraded() {
        return isDegraded;
    }

    public final ConstantCollection<T> copy() {
        ConstantCollection<T> copy = doCopy();
        copy.isDegraded = isDegraded;

        return copy;
    }

    protected abstract void doDegrade();

    protected abstract ConstantCollection<T> doCopy();

    public abstract int size();

    public abstract boolean add(T value);

    public abstract ConstantCollection<T> merge(ConstantCollection<T> other);

    public abstract Iterator<T> iterator();
}
