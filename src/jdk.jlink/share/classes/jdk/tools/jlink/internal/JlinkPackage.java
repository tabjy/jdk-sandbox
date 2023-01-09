/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
