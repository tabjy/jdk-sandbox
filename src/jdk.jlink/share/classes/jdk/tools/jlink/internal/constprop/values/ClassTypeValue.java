package jdk.tools.jlink.internal.constprop.values;

import java.lang.constant.ClassDesc;

public final class ClassTypeValue extends ImmutableReferenceTypeValue<ClassDesc> {
    @Override
    public int getSize() {
        return 1;
    }
}
