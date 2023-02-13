package jdk.tools.jlink.internal.constprop.values;

import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

public non-sealed class ReferenceTypeValue<T /*extends Object*/> extends ConstantizationValue<T> {
    public boolean isImmutable() {
        return false;
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public void setDirectValue(T value) {
        // TODO
    }

    @Override
    public ConstantCollection<T> getValues() {
        // TODO
        return null;
    }
}
