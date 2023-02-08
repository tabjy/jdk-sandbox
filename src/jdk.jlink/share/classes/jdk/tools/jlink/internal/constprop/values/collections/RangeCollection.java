package jdk.tools.jlink.internal.constprop.values.collections;

import java.util.function.Function;

/**
 * A collection of values that are known to be in a range. This is a closed set on both ends.
 *
 * @param <T> The type of the values in the range.
 */
public class RangeCollection<T extends Comparable<T>> extends ConstantCollection<T> {
    private T min; // inclusive
    private T max; // inclusive

    public RangeCollection(T value) {
        this.min = value;
        this.max = value;
    }

    public RangeCollection(T min, T max) {
        this.min = min;
        this.max = max;
    }

    public T getMax() {
        return max;
    }

    public T getMin() {
        return min;
    }

    public boolean project(Function<T, T> projection) {
        T v1 = projection.apply(min);
        T v2 = projection.apply(max);

        T newMin = v1.compareTo(v2) < 0 ? v1 : v2;
        T newMax = v1.compareTo(v2) > 0 ? v1 : v2;

        if (newMin.compareTo(min) < 0 || newMax.compareTo(max) > 0) {
            min = newMin;
            max = newMax;
            return true;
        }

        return false;
    }

    @Override
    public boolean add(T value) {
        if (value.compareTo(min) < 0) {
            min = value;
            return true;
        } else if (value.compareTo(max) > 0) {
            max = value;
            return true;
        }

        return false;
    }

    @Override
    public ConstantCollection<T> merge(ConstantCollection<T> other) {
        if (!(other instanceof RangeCollection<T> that)) {
            throw new IllegalArgumentException("Incompatible collection type");
        } else {
            return new RangeCollection<>(
                    min.compareTo(that.min) < 0 ? min : that.min,
                    max.compareTo(that.max) > 0 ? max : that.max);
        }
    }

    @Override
    public ConstantCollection<T> copy() {
        return new RangeCollection<>(min, max);
    }
}
