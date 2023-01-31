package jdk.tools.jlink.internal.constprop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class DefaultConstantizationTargetRegistry extends ConstantizationTargetRegistry {

    private final Map<String, List<ConstantizationTarget>> descriptors = new HashMap<>();

    public void addClassScopeTarget(String clazz, ConstantizationTarget target) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(target);

        if (target.getScope() != ConstantizationTarget.Scope.CLASS) {
            throw new IllegalArgumentException("Not a class scope target descriptor");
        }

        descriptors.computeIfAbsent(clazz, k -> new ArrayList<>()).add(target);
    }

    public void addMethodScopeTarget(String clazz, String method, String descriptor, ConstantizationTarget target) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(method);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(target);

        if (target.getScope() != ConstantizationTarget.Scope.METHOD) {
            throw new IllegalArgumentException("Not a method scope target descriptor");
        }

        descriptors.computeIfAbsent(clazz + "." + method + descriptor, k -> new ArrayList<>()).add(target);
    }

    @Override
    public Collection<ConstantizationTarget> getClassScopeTargets(String clazz) {
        return descriptors.getOrDefault(clazz, List.of());
    }

    @Override
    public Collection<ConstantizationTarget> getMethodScopeTargets(String clazz, String method, String descriptor) {
        return descriptors.getOrDefault(clazz + "." + method + descriptor, List.of());
    }
}
