package jdk.tools.jlink.internal.constprop.values;

public final class DoubleTypeValue extends ValueTypeValue<Double> {
    @Override
    public int getSize() {
        return 2;
    }
}
