package jdk.tools.jlink.internal.constprop.values;

import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

public final class ArrayTypeValue<T> extends MutableReferenceTypeValue<T[]> {
    @Override
    public ConstantCollection<T[]> getValues() {
        // TODO
        return null;
    }
}
