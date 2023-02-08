package jdk.tools.jlink.internal.constprop.values;

import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

public abstract class ConstantizationValue<T> implements Value {
    public enum Location {
        STACK, LOCAL
    }

    abstract public void setDirectValue(T value);

    abstract public T getDirectValue();

    public abstract ConstantCollection<T> getValues();

    public boolean isDirectValue() {
        return getDirectValue() != null;
    }

    public boolean isTainted() {
        // TODO: check indirect value is tainted
        return !isDirectValue();
    }
}
