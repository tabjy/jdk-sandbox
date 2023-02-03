package jdk.tools.jlink.internal.constprop.values;

import java.lang.constant.ClassDesc;

public final class ClassTypeValue extends ReferenceTypeValue<ClassDesc> {
    @Override
    public int getSize() {
        return 1;
    }
}
