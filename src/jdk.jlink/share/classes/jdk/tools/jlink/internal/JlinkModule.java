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
