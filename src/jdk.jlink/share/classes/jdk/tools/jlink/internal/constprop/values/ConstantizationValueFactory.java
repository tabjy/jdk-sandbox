package jdk.tools.jlink.internal.constprop.values;

import jdk.tools.jlink.internal.constprop.values.collections.ConstantCollection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ConstantizationValueFactory {
    private final Map<String, Supplier<? extends ConstantizationValue<?>>> registrations = new HashMap<>();

    public static ConstantizationValueFactory createDefaultFactory() {
        ConstantizationValueFactory factory = new ConstantizationValueFactory();

        factory.registryTypeValue("I", IntegerTypeValue::new);
        factory.registryTypeValue("F", FloatTypeValue::new);
        factory.registryTypeValue("L", LongTypeValue::new);
        factory.registryTypeValue("D", DoubleTypeValue::new);
        factory.registryTypeValue("Ljava/lang/Object;", ImmutableReferenceTypeValue::new);
        factory.registryTypeValue("Ljava/lang/String;", StringTypeValue::new);
        factory.registryTypeValue("Ljava/lang/Class;", ClassTypeValue::new);

        return factory;
    }

    public void registryTypeValue(String descriptor, Supplier<? extends ConstantizationValue<?>> registration) {
        registrations.put(descriptor, registration);
    }

    public <V, T extends ConstantizationValue<V>> T createValue(String descriptor) {
        // TODO: handle array types

        Supplier<? extends ConstantizationValue<?>> supplier = registrations.get(descriptor);
        if (supplier == null) {
            if (descriptor.startsWith("L")) {
                // TODO: better typing
                @SuppressWarnings("unchecked")
                T t = (T) new ImmutableReferenceTypeValue<V>();
                return t;
            }

            throw new IllegalArgumentException("No registration for descriptor " + descriptor);
        }

        // TODO: better typing
        @SuppressWarnings("unchecked")
        T t = (T) supplier.get();
        return t;
    }

    public <V, T extends ConstantizationValue<V>> T createValue(String descriptor, V value) {
        T constantizationValue = createValue(descriptor);
        constantizationValue.setDirectValue(value);
        return constantizationValue;
    }

    //     TODO: createArrayValue(String descriptor, )

    @SafeVarargs
    public final <T> ConstantizationValue<T> mergeValues(ConstantizationValue<T>... values) {
        return new ConstantizationValue.MergedConstantizationValue<>(values);
    }
}
