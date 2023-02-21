package jdk.tools.jlink.internal.constprop.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Sparse array implementation backed by a hash map. This reduces memory footprint by avoiding allocating large
 * continuous blocks of memory.
 *
 * <p> TODO: use a more efficient sparse array implementation (e.g., backed by two arrays).
 *
 * @param <T>
 */
public class SparseArray<T> implements Iterable<T> {
    private int length;
    private final Map<Integer, T> values;

    public SparseArray() {
        this(-1); // -1: unknown length
    }

    public SparseArray(int length) {
        this(length, new HashMap<>());
    }

    private SparseArray(int length, Map<Integer, T> values) {
        this.length = length;
        this.values = values;
    }

    public int length() {
        return this.length;
    }

    public int size() {
        return values.size();
    }

    public void resize(int length) {
        this.length = length;

        if (length == -1) {
            return;
        }

        values.entrySet().removeIf(entry -> entry.getKey() >= length);
    }

    public Iterator<T> iterator() {
        return this.values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).iterator();
    }

    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + length;
    }

    public T get(int index) {
        if (length != -1 && index >= length) {
            throw new ArrayIndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        return values.get(index);
    }

    public void set(int index, T value) {
        if (length != -1 && index >= length) {
            throw new ArrayIndexOutOfBoundsException(outOfBoundsMsg(index));
        }

        values.put(index, value);
    }

    public int[] indices() {
        return values.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
    }
}
