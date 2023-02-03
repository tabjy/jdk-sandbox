package jdk.tools.jlink.internal.constprop.values;

public final class IntegerTypeValue extends ValueTypeValue<Integer> {
    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public void setDirectValue(Integer value) {
        // TODO
    }

    @Override
    public Integer getDirectValue() {
        // TODO
        return null;
    }
}
