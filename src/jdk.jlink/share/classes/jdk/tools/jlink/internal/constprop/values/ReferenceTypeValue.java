package jdk.tools.jlink.internal.constprop.values;

import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

public sealed abstract class ReferenceTypeValue<T /*extends Object*/> extends ConstantizationValue<T> permits
        ImmutableReferenceTypeValue, MutableReferenceTypeValue {
    @Override
    public int getSize() {
        return 1;
    }
}
