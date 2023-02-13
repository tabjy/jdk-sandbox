package jdk.tools.jlink.internal.constprop.values;

public final class IntegerTypeValue extends ValueTypeValue<Integer> {
    @Override
    public int getSize() {
        return 1;
    }
}
