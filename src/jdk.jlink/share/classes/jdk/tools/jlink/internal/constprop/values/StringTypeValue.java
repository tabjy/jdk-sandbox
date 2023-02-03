package jdk.tools.jlink.internal.constprop.values;

public final class StringTypeValue extends ReferenceTypeValue<String> {

    @Override
    public boolean isImmutable() {
        return true;
    }

    @Override
    public int getSize() {
        return 1;
    }
}
