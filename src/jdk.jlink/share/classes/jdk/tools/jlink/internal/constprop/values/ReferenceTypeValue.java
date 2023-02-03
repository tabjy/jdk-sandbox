package jdk.tools.jlink.internal.constprop.values;

public class ReferenceTypeValue<T extends Object> extends ConstantizationValue<T> {
    public boolean isImmutable() {
        return false;
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public void setDirectValue(T value) {
        // TODO
    }

    @Override
    public T getDirectValue() {
        return null;
    }
}
