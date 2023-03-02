package jdk.tools.jlink.internal.constprop.values.collections;

import jdk.tools.jlink.internal.constprop.utils.SparseArray;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ArrayCollection<T> extends ConstantCollection<SparseArray<T>> {
    private final Supplier<ConstantCollection<T>> elementCollectionFactory;
    private final SparseArray<ConstantCollection<T>> values;

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
        this.values = new SparseArray<>(length);
        this.elementCollectionFactory = elementCollectionFactory;
    }

    public ArrayCollection(T[] array, Supplier<ConstantCollection<T>> elementCollectionFactory) {
        this(array.length, elementCollectionFactory);

        for (int i = 0; i < array.length; i++) {
            this.set(i, array[i]);
        }
    }

    @Override
    protected void doDegrade() {
        values.clear();
    }

    @Override
    public int size() {
        return StreamSupport.stream(values.spliterator(), false).mapToInt(ConstantCollection::size).max()
                .orElse(0);
    }

    public int length() {
        return values.length();
    }

    public boolean add(int index, T value) {
        return this.get(index).map(element -> element.add(value)).orElse(false);
    }

    public boolean set(int index, T value) {
        ConstantCollection<T> element = elementCollectionFactory.get();
        element.add(value);

        try {
            values.set(index, element);
            return true;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }

    public Optional<ConstantCollection<T>> get(int index) {
        try {
            ConstantCollection<T> element = values.get(index);
            if (element == null) {
                element = elementCollectionFactory.get();
                values.set(index, element);
            }
            return Optional.ofNullable(element);
        } catch (ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean add(SparseArray<T> value) {
        int length = Math.max(values.length(), value.length());
        if (values.length() == -1 || value.length() == -1) {
            length = -1;
        }

        values.resize(length);
        return Arrays.stream(value.indices()).mapToObj(i -> add(i, value.get(i))).allMatch(added -> added);
    }

    @Override
    public ConstantCollection<SparseArray<T>> merge(ConstantCollection<SparseArray<T>> other) {
        if (other instanceof ArrayCollection<T> that) {
            ArrayCollection<T> result = new ArrayCollection<>(Math.max(this.length(), that.length()),
                    elementCollectionFactory);
            Stream.concat(Arrays.stream(this.values.indices()).boxed(), Arrays.stream(that.values.indices()).boxed())
                    .distinct()
                    .forEach(i -> {
                        ConstantCollection<T> thisElement = this.values.get(i);
                        ConstantCollection<T> thatElement = that.values.get(i);

                        if (thisElement != null && thatElement != null) {
                            result.values.set(i, thisElement.merge(thatElement));
                        } else if (thisElement != null) {
                            result.values.set(i, thisElement);
                        } else if (thatElement != null) {
                            result.values.set(i, thatElement);
                        }
                    });

            return result;
        }

        throw new IllegalArgumentException("Incompatible collection type");
    }

    @Override
    public ConstantCollection<SparseArray<T>> doCopy() {
        ArrayCollection<T> copy = new ArrayCollection<>(length(), elementCollectionFactory);
        Arrays.stream(values.indices()).forEach(i -> copy.values.set(i, values.get(i).copy()));
        return copy;
    }

    @Override
    public Iterator<SparseArray<T>> iterator() {
        int[] indices = this.values.indices();
        List<Iterator<T>> iterators = Arrays.stream(indices).mapToObj(i -> values.get(i).iterator()).toList();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterators.stream().anyMatch(Iterator::hasNext);
            }

            @Override
            public SparseArray<T> next() {
                SparseArray<T> result = new SparseArray<>(length());
                for (int i = 0; i < indices.length; i++) {
                    if (iterators.get(i).hasNext()) {
                        result.set(indices[i], iterators.get(i).next());
                    }
                }
                return result;
            }
        };
    }
}
