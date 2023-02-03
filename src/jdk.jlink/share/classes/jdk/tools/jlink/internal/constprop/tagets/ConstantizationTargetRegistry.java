package jdk.tools.jlink.internal.constprop.tagets;

import java.util.Collection;

public abstract class ConstantizationTargetRegistry {
    public abstract Collection<ConstantizationTarget> getClassScopeTargets(String clazz);

    public abstract Collection<ConstantizationTarget> getMethodScopeTargets(String clazz, String method, String descriptor);
}
