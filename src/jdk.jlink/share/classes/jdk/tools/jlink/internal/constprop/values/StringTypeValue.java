package jdk.tools.jlink.internal.constprop.values;

public final class StringTypeValue extends ImmutableReferenceTypeValue<String> {
    @Override
    public int getSize() {
        return 1;
    }
}
