package jdk.tools.jlink.internal.constprop.values.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ArrayCollection<T> extends ConstantCollection<T[]> {
    private final int length; // -1: unknown length
    private final Supplier<ConstantCollection<T>> elementCollectionFactory;

    private final Map<Integer, ConstantCollection<T>> values = new HashMap<>(); // sparse array with hash map

    public ArrayCollection() {
        this(-1, UnionSetCollection::new);
    }

    public ArrayCollection(int length) {
        this(length, UnionSetCollection::new);
    }

    public ArrayCollection(Supplier<ConstantCollection<T>> elementCollectionFactory) {
        this(-1, elementCollectionFactory);
    }

    public ArrayCollection(int length, Supplier<ConstantCollection<T>> elementCollectionFactory) {
        this.length = length;
        this.elementCollectionFactory = elementCollectionFactory;
    }

    public int getLength() {
        return length;
    }

    @Override
    public boolean add(T[] value) {
        return IntStream.range(0, value.length)
                .mapToObj(i -> add(i, value[i]))
                .allMatch(added -> added);
    }

    public boolean add(int index, T value) {
        if (length != -1 && index >= length) {
            return false;
        }

        ConstantCollection<T> element = values.computeIfAbsent(index, i -> elementCollectionFactory.get());
        return element.add(value);
    }

    @Override
    public ConstantCollection<T[]> merge(ConstantCollection<T[]> other) {
        if (other instanceof ArrayCollection<T> that) {
            ArrayCollection<T> result = new ArrayCollection<>(Math.max(length, that.length), elementCollectionFactory);
            Stream.concat(this.values.keySet().stream(), that.values.keySet().stream())
                    .distinct()
                    .forEach(i -> result.values.put(i, this.values.get(i).merge(that.values.get(i))));
            return result;
        }

        throw new IllegalArgumentException("Incompatible collection type");
    }

    @Override
    public ConstantCollection<T[]> copy() {
        ArrayCollection<T> copy = new ArrayCollection<>(length, elementCollectionFactory);
        values.forEach((i, collection) -> copy.values.put(i, collection.copy()));
        return copy;
    }
}
