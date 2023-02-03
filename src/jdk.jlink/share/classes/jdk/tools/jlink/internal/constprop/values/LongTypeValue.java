package jdk.tools.jlink.internal.constprop.values;

public final class LongTypeValue extends ValueTypeValue<Long> {
    @Override
    public int getSize() {
        return 2;
    }

    @Override
    public void setDirectValue(Long value) {
        // TODO
    }

    @Override
    public Long getDirectValue() {
        // TODO
        return null;
    }
}
