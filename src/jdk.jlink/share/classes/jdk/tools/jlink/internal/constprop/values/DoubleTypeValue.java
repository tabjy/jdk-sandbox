package jdk.tools.jlink.internal.constprop.values;

public final class DoubleTypeValue extends ValueTypeValue<Double> {
    @Override
    public int getSize() {
        return 2;
    }

    @Override
    public void setDirectValue(Double value) {
        // TODO
    }

    @Override
    public Double getDirectValue() {
        // TODO
        return null;
    }
}
