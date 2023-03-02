package jdk.tools.jlink.internal.constprop.values.collections;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A collection of values that are known to be in a range. This is a closed set on both ends.
 *
 * @param <T> The type of the values in the range.
 */
public class RangeCollection<T extends Comparable<T>> extends ConstantCollection<T> {
    private T min; // inclusive
    private T max; // inclusive

    public RangeCollection(T value) {
        Objects.requireNonNull(value);

        this.min = value;
        this.max = value;
    }

    public RangeCollection(T v1, T v2) {
        update(v1, v2);
    }

    RangeCollection() {
    }

    public T getMax() {
        return max;
    }

    public T getMin() {
        return min;
    }

    public boolean update(T v1, T v2) {
        T min = v1.compareTo(v2) < 0 ? v1 : v2;
        T max = v1.compareTo(v2) > 0 ? v1 : v2;

        if (min.compareTo(this.min) < 0 || max.compareTo(this.max) > 0) {
            this.min = min;
            this.max = max;
            return true;
        }

        return false;
    }

    public boolean project(Function<T, T> projection) {
        return update(projection.apply(min), projection.apply(max));
    }

    @Override
    protected void doDegrade() {
        min = null;
        max = null;
    }

    @Override
    protected ConstantCollection<T> doCopy() {
        RangeCollection<T> copy = new RangeCollection<>();
        copy.min = min;
        copy.max = max;

        return copy;
    }

    @Override
    public int size() {
        return min == max ? 1 : 2;
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
        }

        if (this.isDegraded() || other.isDegraded()) {
            RangeCollection<T> copy = (RangeCollection<T>) this.copy();
            copy.degrade();
            return copy;
        }

        return new RangeCollection<>(min.compareTo(that.min) < 0 ? min : that.min,
                max.compareTo(that.max) > 0 ? max : that.max);
    }

    @Override
    public Iterator<T> iterator() {
        return Stream.of(min, max).filter(Objects::nonNull).distinct().iterator();
    }
}
