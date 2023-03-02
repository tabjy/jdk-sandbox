package jdk.tools.jlink.internal.constprop.values;

import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public abstract sealed class ConstantizationValue<T> implements Value permits
        ConstantizationValue.MergedConstantizationValue, ReferenceTypeValue, ValueTypeValue {
    public enum Location {
        STACK, LOCAL
    }

    private T directValue;

    protected List<? extends ConstantizationValue<?>> sources;
    protected Function<Optional<?>[], Optional<T>> operator;

    public void setDirectValue(T value) {
        directValue = Objects.requireNonNull(value);
    }

    public boolean isDirectValue() {
        return directValue != null;
    }

    public void setIndirectValue(List<? extends ConstantizationValue<?>> sources,
                                 Function<Optional<?>[], Optional<T>> operator) {
        this.sources = Objects.requireNonNull(sources);
        this.operator = Objects.requireNonNull(operator);
    }

    protected abstract ConstantCollection<T> newConstantCollection();

    public ConstantCollection<T> getValues() {
        ConstantCollection<T> collection = newConstantCollection();
        if (isDirectValue()) {
            collection.add(directValue);
            return collection;
        }

        // TODO
    }

    public boolean isTainted() {
        // TODO: check indirect value is tainted
        return !isDirectValue();
    }

    static final class MergedConstantizationValue<T> extends ConstantizationValue<T> {
        private final ConstantizationValue<T>[] values;

        MergedConstantizationValue(ConstantizationValue<T>[] values) {
            this.values = values;

            if (values.length == 0) {
                throw new IllegalArgumentException("No values");
            }

            Arrays.stream(values).skip(1).filter(v -> v.getSize() != values[0].getSize()).findAny().ifPresent(v -> {
                throw new IllegalArgumentException("Incompatible sizes");
            });
        }

        @Override
        public int getSize() {
            return values[0].getSize();
        }

        @Override
        public ConstantCollection<T> getValues() {
            return Arrays.stream(values).skip(1)
                    .reduce(values[0].getValues(), (acc, cur) -> acc.merge(cur.getValues()), ConstantCollection::merge);
        }
    }
}
