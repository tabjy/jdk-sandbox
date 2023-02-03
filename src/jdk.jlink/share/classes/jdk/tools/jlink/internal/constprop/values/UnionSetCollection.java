package jdk.tools.jlink.internal.constprop.values;

import java.util.HashSet;
import java.util.Set;

public class UnionSetCollection<T> extends ConstantCollection<T> {
    private final int limit;
    private final Set<T> values = new HashSet<>();

    public UnionSetCollection() {
        this(-1);
    }

    public UnionSetCollection(int limit) {
        this.limit = limit;
    }

    public UnionSetCollection(T value, int limit) {
        this(limit);

        add(value);
    }


    @Override
    public boolean add(T value) {
        if (limit == -1 || values.size() < limit) {
            return values.add(value);
        }

        // TODO: need to degrade to reference type
        return false;
    }

    @Override
    public ConstantCollection<T> merge(ConstantCollection<T> other) {
        if (!(other instanceof UnionSetCollection<T> that)) {
            throw new IllegalArgumentException("Incompatible collection type");
        } else {
            UnionSetCollection<T> result = new UnionSetCollection<>(Math.max(limit, that.limit));
            values.forEach(result::add);
            that.values.forEach(result::add);

            return result;
        }
    }
}
