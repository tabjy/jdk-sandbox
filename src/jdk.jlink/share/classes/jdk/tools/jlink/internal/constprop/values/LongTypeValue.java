package jdk.tools.jlink.internal.constprop.values;

public final class LongTypeValue extends ValueTypeValue<Long> {
    @Override
    public int getSize() {
        return 2;
    }
}
