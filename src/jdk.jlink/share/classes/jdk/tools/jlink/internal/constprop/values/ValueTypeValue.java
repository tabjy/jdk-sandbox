package jdk.tools.jlink.internal.constprop.values;

public abstract sealed class ValueTypeValue<T> extends ConstantizationValue<T> permits
        IntegerTypeValue,
        LongTypeValue,
        FloatTypeValue,
        DoubleTypeValue {
}
