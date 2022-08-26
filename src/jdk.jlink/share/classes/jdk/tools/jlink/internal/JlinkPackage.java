package jdk.tools.jlink.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JlinkPackage {

    private String packageName;
    private boolean exportToAll;
    private JlinkModule ownerModule;
    private Map<String, List<JlinkModule>> exportsHashTable;

    public JlinkPackage(String packageName, JlinkModule ownerModule, boolean exportToAll) {
        Objects.requireNonNull(packageName);
        this.packageName = packageName;
        this.ownerModule = ownerModule;
        this.exportToAll = exportToAll;
        this.exportsHashTable = new HashMap<>();
    }

    public String name() {
        return this.packageName;
    }

    public boolean exportToAll() {
        return this.exportToAll;
    }

    public JlinkModule module() {
        return this.ownerModule;
    }

    public void addModuleExport(JlinkModule module) {
        List<JlinkModule> exportedToModules = exportsHashTable.computeIfAbsent(module.name(), k -> new ArrayList<>());
        exportedToModules.add(module);
    }

    public boolean isPackageExportedToModule(String packageName, JlinkModule module) {
        List<JlinkModule> exportedToModules = exportsHashTable.get(packageName);
        return exportedToModules != null && exportedToModules.contains(module);
    }

    public static JlinkPackage lookup(String packageName, String moduleName, Map<JlinkPackage,
            JlinkPackage> jlinkPackageMap) {
        JlinkModule module = new JlinkModule(moduleName, false, null);
        JlinkPackage needle = new JlinkPackage(packageName, module, false);
        return jlinkPackageMap.get(needle);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof JlinkPackage other) {
            return packageName.equals(other.name())
                    && ownerModule.equals(other.module());
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 17;
        result = prime * result
                + (packageName.hashCode());
        result = prime * result
                + (ownerModule.hashCode());
        return result;
    }

}
