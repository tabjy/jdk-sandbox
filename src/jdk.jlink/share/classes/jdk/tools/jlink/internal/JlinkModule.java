package jdk.tools.jlink.internal;

import java.lang.module.ModuleDescriptor;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class JlinkModule {

    private String moduleName;
    private boolean isOpen;
    private ModuleDescriptor moduleDescriptor;
    private Map<String, JlinkModule> readAccessHashTable;


    public JlinkModule(String moduleName, boolean isOpen, ModuleDescriptor moduleDescriptor) {
        Objects.requireNonNull(moduleName);
        this.moduleName = moduleName;
        this.isOpen = isOpen;
        this.moduleDescriptor = moduleDescriptor;
        this.readAccessHashTable = new HashMap<>();
    }

    public void addAccessibleModule(JlinkModule module) {
        this.readAccessHashTable.put(module.name(), module);
    }

    public String name() {
        return this.moduleName;
    }
    public ModuleDescriptor moduleDescriptor() {
        return this.moduleDescriptor;
    }
    public boolean hasReadAccess(JlinkModule destModule) {
        JlinkModule readable = readAccessHashTable.get(destModule.name());
        return readable != null;
    }
    public static JlinkModule lookup(String moduleName, Map<JlinkModule, JlinkModule> jlinkModuleMap) {
        JlinkModule needle = new JlinkModule(moduleName, false, null);
        return jlinkModuleMap.get(needle);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof JlinkModule other) {
            return this.moduleName.equals(other.name());
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 17;
        result = prime * result
                + (moduleName.hashCode());
        return result;
    }
}
