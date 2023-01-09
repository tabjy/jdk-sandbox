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
package jdk.tools.jlink.internal.plugins;

import jdk.tools.jlink.internal.JlinkModule;
import jdk.tools.jlink.internal.JlinkPackage;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolModule;

import java.lang.module.ModuleDescriptor;
import java.util.*;

public final class ModuleGraphPlugin implements Plugin, ResourcePrevisitor {

    public static final String NAME = "module-graph";
    private static Map<JlinkModule, JlinkModule> jlinkModuleMap;
    private static Map<JlinkPackage, JlinkPackage> jlinkPackageMap;

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        return in;
    }

    @Override
    public void previsit(ResourcePool resources, StringTable strings) {
        jlinkModuleMap = new HashMap<>();
        jlinkPackageMap = new HashMap<>();
        populateModuleView(resources);
    }

    /**
     * Determines whether a given class in a specific module is accessible to the current module
     * @param destClass the class checked for accessibility
     * @param currModuleName the current module's name
     * @param destModuleName the module containing destClass
     * @return
     */
    public static boolean isAccessible(String destClass, String currModuleName, String destModuleName) {
        /* Find package corresponding to dest class */
        String destPackage = getPackageName(destClass);

        /* Determine if package at destModule is exported at all*/
        JlinkPackage jlinkPackage = JlinkPackage.lookup(destPackage, destModuleName, jlinkPackageMap);

        if (jlinkPackage != null) {

            /* Determine if package is exported to all or current module */
            JlinkModule currModule = JlinkModule.lookup(currModuleName, jlinkModuleMap);
            if (jlinkPackage.exportToAll() || jlinkPackage.isPackageExportedToModule(destPackage, currModule)) {

                /* Determine if current module has read access to dest module */
                JlinkModule destModule = jlinkPackage.module();
                return destModule != null && currModule.hasReadAccess(destModule);
            }
        }
        return false;
    }

    private void populateModuleView(ResourcePool resources) {
        /* Create a hash map with all modules  */
        List<ResourcePoolModule> resourcePoolModules = resources.moduleView().modules().toList();
        for (ResourcePoolModule module : resourcePoolModules) {
            ModuleDescriptor descriptor = module.descriptor();
            JlinkModule jlinkModule = new JlinkModule(descriptor.name(), descriptor.isOpen(), descriptor);
            jlinkModuleMap.put(jlinkModule, jlinkModule);
        }

        /* Create package hash maps with modules they export to and populate read access hash maps for modules */
        for (JlinkModule module : jlinkModuleMap.keySet()) {
            populatePackageMap(module);
            populateReadAccess(module);
        }
    }

    private void populatePackageMap(JlinkModule jlinkModule) {
        for (ModuleDescriptor.Exports export : jlinkModule.moduleDescriptor().exports().stream().toList()) {
            Set<String> targets = export.targets();
            JlinkPackage exportedPackage = new JlinkPackage(export.source(), jlinkModule, targets.isEmpty());
            if (! targets.isEmpty()) {
                for (String target : targets) {
                    JlinkModule targetModule = JlinkModule.lookup(target, jlinkModuleMap);
                    if (targetModule != null) exportedPackage.addModuleExport(targetModule);
                }
            }
            jlinkPackageMap.put(exportedPackage, exportedPackage);
        }
    }

    private void populateReadAccess(JlinkModule jlinkModule) {
        for (ModuleDescriptor.Requires requires : jlinkModule.moduleDescriptor().requires().stream().toList()) {
            JlinkModule accessibleModule = JlinkModule.lookup(requires.name(), jlinkModuleMap);
            if (accessibleModule != null) {
                jlinkModule.addAccessibleModule(accessibleModule);
            }
            if (requires.modifiers().contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE)) {
                /* Handle implied readability with transitive dependencies  */
                for (JlinkModule module : jlinkModuleMap.keySet()) {
                    for (ModuleDescriptor.Requires r : module.moduleDescriptor().requires().stream().toList()) {
                        if (r.name().equals(jlinkModule.name())) {
                            module.addAccessibleModule(accessibleModule);
                        }
                    }
                }
            }
        }
    }

    private static String getPackageName(String binaryName) {
        int index = binaryName.lastIndexOf("/");
        return index == -1 ? "" : binaryName.substring(0, index).replace("/", ".");
    }
}
