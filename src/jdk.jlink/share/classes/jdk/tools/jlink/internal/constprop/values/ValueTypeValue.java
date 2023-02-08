package jdk.tools.jlink.internal.constprop.values;

/**
 * for primitive, non-reference, types
 *
 * @param <T>
 */
public abstract sealed class ValueTypeValue<T> extends ConstantizationValue<T> permits
        IntegerTypeValue,
        LongTypeValue,
        FloatTypeValue,
        DoubleTypeValue {
}
