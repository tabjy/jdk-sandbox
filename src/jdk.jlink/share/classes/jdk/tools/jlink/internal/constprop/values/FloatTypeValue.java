package jdk.tools.jlink.internal.constprop.values;

public final class FloatTypeValue extends ValueTypeValue<Float> {
    @Override
    public int getSize() {
        return 1;
    }
}
