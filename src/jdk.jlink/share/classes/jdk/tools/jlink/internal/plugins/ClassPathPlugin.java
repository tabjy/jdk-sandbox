package jdk.tools.jlink.internal.plugins;

import com.sun.tools.jdeps.JdepsConfiguration;
import com.sun.tools.jdeps.ModuleInfoBuilder;
import jdk.internal.access.SharedSecrets;
import jdk.internal.opt.CommandLine;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.AdviceAdapter;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.internal.JlinkTask;
import jdk.tools.jlink.internal.ModularJarArchive;
import jdk.tools.jlink.internal.ResourcePoolEntryFactory;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassPathPlugin extends AbstractPlugin {
    private static final String NAME = "class-path";
    private static final String JAVA_BASE_MOD = "java.base";
    private static final String CLASS_EXT = ".class";
    private static final String TEMP_DIR = "jlink-class-path-plugin-tmp";
    private static final String[] MODULE_PATH_OPTIONS = {"--module-path", "-p"};

    private static final List<Path> classPath = new ArrayList<>();
    private static final List<Path> modulePath = new ArrayList<>();

    public ClassPathPlugin() {
        super(NAME);
    }

    @Override
    public Category getType() {
        return Category.TRANSFORMER;
    }

    @Override
    public Set<State> getState() {
        return EnumSet.of(State.FUNCTIONAL);
    }

    @Override
    public String getOption() {
        return "class-path";
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public boolean hasRawArgument() {
        return true; // we parse colon separated list ourselves
    }

    @Override
    public void configure(Map<String, String> config) {
        Arrays.stream(config.get(getName()).split(File.pathSeparator))
                .map(Paths::get)
                .forEach(classPath::add);

        // FIXME: Don't use the hack below
        // HACK: there is no direct way to get module path for plugins,
        //       so we parse it from the command line
        try {
            String cmd = ProcessHandle.current().info().commandLine().orElseThrow();
            List<String> args = new ArrayList<>();
            CommandLine.loadCmdFile(new ByteArrayInputStream(cmd.getBytes(StandardCharsets.UTF_8)), args);
            args = CommandLine.parse(args);
            for (int i = 0; i < args.size(); i++) {
                String key = args.get(i);
                String value = null;
                for (String option : MODULE_PATH_OPTIONS) {
                    if (option.startsWith("--") && key.startsWith(option + "=")) {
                        value = key.substring(option.length() + 1);
                        break;
                    }
                    if (key.equals(option)) {
                        value = args.get(i + 1);
                        break;
                    }
                }

                if (value != null) {
                    Arrays.stream(value.split(File.pathSeparator))
                            .map(Paths::get)
                            .forEach(modulePath::add);

                    break;
                }
            }
        } catch (NoSuchElementException | IOException e) {
            // ignore
        }

        Path jmods = Paths.get(System.getProperty("java.home"), "jmods");
        if (modulePath.isEmpty() && jmods.toFile().isDirectory()) {
            modulePath.add(jmods);
        }

        if (modulePath.isEmpty()) {
            throw new IllegalArgumentException("Module path is not set");
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        // For multi-release jars, use the version of java.base module or fallback to the current runtime version
        final Runtime.Version[] version = {Runtime.version()};
        in.moduleView().findModule(JAVA_BASE_MOD)
                .flatMap(m -> m.descriptor().version())
                .ifPresent(v -> version[0] = Runtime.Version.parse(v.toString()));

        JdepsConfiguration.Builder builder = new JdepsConfiguration.Builder()
                .appModulePath(
                        String.join(File.pathSeparator, modulePath.stream().map(Path::toString).toArray(String[]::new)))
                .multiRelease(version[0]);

        for (Path p : classPath) {
            if (Files.exists(p)) {
                builder.addRoot(p);
            } else {
                throw new IllegalArgumentException("Class path entry " + p + " does not exist");
            }
        }

        // TODO:
        //   1. load resources from class path
        //   2. generate and compile a module-info.class
        //   3. add resources and module-info to resource pool

        // generator module descriptors with jdeps
        List<ModuleDescriptor> descriptors;
        try (JdepsConfiguration config = builder.build()) {
            File tmp = Files.createTempDirectory(TEMP_DIR).toFile();
            tmp.deleteOnExit();
            ModuleInfoBuilder miBuilder = new ModuleInfoBuilder(
                    config,
                    classPath.stream().map(Path::toString).toList(),
                    tmp.toPath(), // not used
                    false);
            miBuilder.run(true, null, true);
            tmp.delete();
            descriptors = miBuilder.descriptors().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // TODO: verify classpath and descriptors are one-to-one mappings and the order is the same
        List<Archive> archives = new ArrayList<>();
        List<PatchedModuleDirectives> directives = new ArrayList<>();
        for (int i = 0; i < descriptors.size(); i++) {
            ModuleDescriptor descriptor = descriptors.get(i);
            Path path = classPath.get(i);
            ModularJarArchive archive = new ModularJarArchive(descriptor.name(), path, version[0]);

            PatchedModuleDirectives directive = new PatchedModuleDirectives(descriptor);
            stripNonExistingProvideDirectives(directive, archive);
            addUseDirectives(directive, archive);

            archives.add(archive);
            directives.add(directive);
        }

        // TODO: clean this up
        byte[][] moduleClassBytes = new byte[1][];
        in.entries().forEach(entry -> {
            if (entry.moduleName().equals("java.base") && entry.path().equals("/java.base/java/lang/Module.class")) {
//                moduleClassEntry[0] = entry;
                moduleClassBytes[0] = entry.contentBytes();
                return;
            }

            out.add(entry);
        });

        Set<String> roots = new HashSet<>();

        // TODO: add generated module resources to pool
        for (int i = 0; i < descriptors.size(); i++) {
            ModuleDescriptor descriptor = descriptors.get(i);
            Archive archive = archives.get(i);
            PatchedModuleDirectives directive = directives.get(i);

            // FIXME: reduce memory footprint by not loading all resources into memory
            //        expose ArchiveEntryResourcePoolEntry as public or add the corresponding factory method?

            archive.entries().forEach(entry -> {
                try (InputStream is = entry.stream()) {
                    out.add(ResourcePoolEntryFactory.create(
                            "/" + descriptor.name() + "/" + entry.name(),
                            ResourcePoolEntry.Type.CLASS_OR_RESOURCE,
                            is.readAllBytes()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            // FIXME: generated module might include (possibly transitive) dependency modules not already in the module
            //        view
            directive.requires().stream()
                    .map(ModuleDescriptor.Requires::name)
//                    .filter(name -> !roots.contains(name))
                    .filter(name -> in.moduleView().findModule(name).isEmpty())
                    .filter(name -> descriptors.stream().noneMatch(d -> d.name().equals(name)))
                    .forEach(roots::add);

            byte[] moduleInfo = compileModuleInfo(descriptor, directive);
            out.add(ResourcePoolEntryFactory.create(
                    "/" + descriptor.name() + "/module-info.class",
                    ResourcePoolEntry.Type.CLASS_OR_RESOURCE,
                    moduleInfo));
        }

        ModuleFinder finder = JlinkTask.newModuleFinder(modulePath, Set.of(), roots);
        Configuration.empty().resolve(finder, ModuleFinder.of(), roots).modules().stream()
                .filter(module -> in.moduleView().findModule(module.name()).isEmpty())
                .forEach(module -> {
                    ModuleReference reference = module.reference();
                    try (ModuleReader reader = reference.open()) {
                        reader.list().forEach(path -> {
                            try (InputStream is = reader.open(path).orElseThrow()) {
                                // TODO: clean this up
                                if (reference.descriptor().name().equals("java.base") && path.equals("java/lang/Module.class")) {
                                    moduleClassBytes[0] = is.readAllBytes();
                                    return;
                                }

                                // FIXME: avoid loading all resources into memory, reader.open(path) is not designed for loading
                                //        small resources like classes
                                out.add(ResourcePoolEntryFactory.create(
                                        "/" + reference.descriptor().name() + "/" + path,
                                        ResourcePoolEntry.Type.CLASS_OR_RESOURCE,
                                        is.readAllBytes()));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

        if (moduleClassBytes[0] != null) {
            out.add(ResourcePoolEntryFactory.create(
                    "/java.base/java/lang/Module.class",
                    ResourcePoolEntry.Type.CLASS_OR_RESOURCE,
                    transformModuleClass(
                            moduleClassBytes[0],
                            descriptors.stream().map(ModuleDescriptor::name).collect(Collectors.toSet()))));
        }

        return out.build();
    }

    private byte[] transformModuleClass(byte[] classFile, Set<String> moduleNames) {
        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        boolean[] moduleNameFieldGenerated = new boolean[]{false};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

                if (name.equals("<clinit>") && descriptor.equals("()V")) {
                    moduleNameFieldGenerated[0] = true;

                    visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                            "_JLINK_CONVERTED_MODULES", "Ljava/util/Set;", null, null);
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                        @Override
                        protected void onMethodExit(int opcode) {
                            initializeModuleNameField(mv, moduleNames);
                            super.onMethodExit(opcode);
                        }
                    };
                }

                if (name.equals("canUse") && descriptor.equals("(Ljava/lang/Class;)Z")) {
                    return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor) {
                        @Override
                        protected void onMethodEnter() {
                            mv.visitFieldInsn(GETSTATIC, "java/lang/Module", "_JLINK_CONVERTED_MODULES",
                                    "Ljava/util/Set;");
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Module", "getDescriptor",
                                    "()Ljava/lang/module/ModuleDescriptor;", false);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/module/ModuleDescriptor", "name",
                                    "()Ljava/lang/String;", false);
                            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Set", "contains",
                                    "(Ljava/lang/Object;)Z", true);

                            Label label = new Label();
                            mv.visitJumpInsn(IFEQ, label);
                            mv.visitInsn(ICONST_1);
                            mv.visitInsn(IRETURN);

                            mv.visitLabel(label);
                        }
                    };
                }

                return mv;
            }
        };
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        if (!moduleNameFieldGenerated[0]) {
            visitor.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    "_JLINK_CONVERTED_MODULES", "Ljava/util/Set;", null, null);
            MethodVisitor mv = visitor.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
            initializeModuleNameField(mv, moduleNames);
        }

        return writer.toByteArray();
//        return classFile;
    }

    private void initializeModuleNameField(MethodVisitor mv, Set<String> moduleNames) {
        mv.visitIntInsn(Opcodes.BIPUSH, moduleNames.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        mv.visitInsn(Opcodes.DUP);

        List<String> names = moduleNames.stream().toList();
        for (int i = 0; i < names.size(); i++) {
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            mv.visitLdcInsn(names.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }

        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "java/util/Set", "of", "([Ljava/lang/Object;)Ljava/util/Set;", true);
        mv.visitFieldInsn(Opcodes.PUTSTATIC,
                "java/lang/Module", "_JLINK_CONVERTED_MODULES", "Ljava/util/Set;");
    }

    private void stripNonExistingProvideDirectives(PatchedModuleDirectives directives, ModularJarArchive archive) {
        Set<ModuleDescriptor.Provides> newProvides = new HashSet<>();

        for (Iterator<ModuleDescriptor.Provides> it = directives.provides().iterator(); it.hasNext(); ) {
            ModuleDescriptor.Provides provides = it.next();

            if (archive.entries().noneMatch(entry ->
                    entry.name().equals(classToInternalName(provides.service()) + ".class"))) {
                it.remove();
                continue;
            }

            List<String> newProviders = provides.providers().stream()
                    .filter(provider -> archive.entries().anyMatch(entry ->
                            entry.name().equals(classToInternalName(provider) + ".class")))
                    .toList();

            if (newProviders.size() != provides.providers().size()) {
                newProvides.add(SharedSecrets.getJavaLangModuleAccess().newProvides(
                        provides.service(), List.copyOf(newProviders)));
                it.remove();
            }
        }

        for (ModuleDescriptor.Provides provides : newProvides) {
            directives.addProvides(provides);
        }
    }

    private void addUseDirectives(PatchedModuleDirectives directives, ModularJarArchive archive) {
        // Scan for ServiceLoader.load() calls
        archive.entries().forEach(entry -> {
            if (entry.type() != Archive.Entry.EntryType.CLASS_OR_RESOURCE || !entry.name().endsWith(CLASS_EXT)) {
                return;
            }

            try (InputStream is = entry.stream()) {
                ClassReader cr = new ClassReader(is.readAllBytes());
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);

                cn.methods.forEach(method -> {
                    AbstractInsnNode[] lastTwoInstructions = new AbstractInsnNode[2];
                    int[] i = new int[]{0};

                    method.instructions.forEach(instruction -> {
                        lastTwoInstructions[i[0]++ % 2] = instruction;

                        // FIXME: Argument could be already on stack. How to do data-flow analysis properly?
                        if (instruction instanceof MethodInsnNode invocation
                                && invocation.getOpcode() == Opcodes.INVOKESTATIC
                                && invocation.name.equals("load")
                                && invocation.owner.equals("java/util/ServiceLoader")) {
                            // one argument variant: ServiceLoader.load(Class):ServiceLoader
                            if (invocation.desc.equals("(Ljava/lang/Class;)Ljava/util/ServiceLoader;")) {
                                if (lastTwoInstructions[0] instanceof LdcInsnNode ldc) {
                                    if (ldc.cst instanceof Type type) {
                                        if (type.getSort() == Type.OBJECT) {
                                            directives.uses().add(type.getClassName());
                                            return;
                                        }
                                    }
                                }
                            }

                            // two argument variant: ServiceLoader.load(Class, ClassLoader):ServiceLoader
                            if (invocation.desc.equals("(Ljava/lang/Class;Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;")) {
                                // TODO: the loading classloader argument to stack might be non-trivial, unlike LDC
                            }

                            // We don't care about loading services with ModuleLayer. Legacy code don't use it anyway.
//                            System.err.println("Unknown ServiceLoader.load() invocation in: " + cn.name + "." + method.name + method.desc);
                        }


                    });
                });

            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private byte[] compileModuleInfo(ModuleDescriptor descriptor, PatchedModuleDirectives directives) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V19, Opcodes.ACC_MODULE, "module-info", null, null, null);

        // TODO: might as well include the source, too?

        ModuleVisitor mv = cw.visitModule(descriptor.name(), 0, null);

        for (ModuleDescriptor.Requires require : directives.requires()) {
            mv.visitRequire(require.name(),
                    require.name().equals(JAVA_BASE_MOD)
                            ? Opcodes.ACC_MANDATED
                            : requireModifiersToMask(require.modifiers()),
                    require.rawCompiledVersion().orElse(null));
        }

        for (ModuleDescriptor.Exports export : directives.exports()) {
            mv.visitExport(classToInternalName(export.source()),
                    exportModifiersToMask(export.modifiers()),
                    export.targets().stream().map(this::classToInternalName).toArray(String[]::new));
        }

        for (ModuleDescriptor.Opens open : directives.opens()) {
            mv.visitOpen(open.source(),
                    openModifiersToMask(open.modifiers()),
                    open.targets().stream().map(this::classToInternalName).toArray(String[]::new));
        }

        for (String use : directives.uses()) {
            mv.visitUse(classToInternalName(use));
        }

        for (ModuleDescriptor.Provides provide : directives.provides()) {
            mv.visitProvide(classToInternalName(provide.service()),
                    provide.providers().stream().map(this::classToInternalName).toArray(String[]::new));
        }
        mv.visitEnd();

        // TODO: Do we need to generate InnerClasses attribute?
        // cw.visitInnerClass(...);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private int requireModifiersToMask(Set<ModuleDescriptor.Requires.Modifier> modifiers) {
        int mask = 0;
        for (ModuleDescriptor.Requires.Modifier modifier : modifiers) {
            switch (modifier) {
                case TRANSITIVE -> mask |= Opcodes.ACC_TRANSITIVE;
                case STATIC -> mask |= Opcodes.ACC_STATIC_PHASE;
                case SYNTHETIC -> mask |= Opcodes.ACC_SYNTHETIC;
                case MANDATED -> mask |= Opcodes.ACC_MANDATED;
                default -> throw new AssertionError("Unknown modifier: " + modifier);
            }
        }
        return mask;
    }

    private int exportModifiersToMask(Set<ModuleDescriptor.Exports.Modifier> modifiers) {
        int mask = 0;
        for (ModuleDescriptor.Exports.Modifier modifier : modifiers) {
            switch (modifier) {
                case SYNTHETIC -> mask |= Opcodes.ACC_SYNTHETIC;
                case MANDATED -> mask |= Opcodes.ACC_MANDATED;
                default -> throw new AssertionError("Unknown modifier: " + modifier);
            }
        }
        return mask;
    }

    private int openModifiersToMask(Set<ModuleDescriptor.Opens.Modifier> modifiers) {
        int mask = 0;
        for (ModuleDescriptor.Opens.Modifier modifier : modifiers) {
            switch (modifier) {
                case SYNTHETIC -> mask |= Opcodes.ACC_SYNTHETIC;
                case MANDATED -> mask |= Opcodes.ACC_MANDATED;
                default -> throw new AssertionError("Unknown modifier: " + modifier);
            }
        }
        return mask;
    }

    private String classToInternalName(String className) {
        return className.replace('.', '/');
    }

    private static class PatchedModuleDirectives {
        private final ModuleDescriptor descriptor;
        private final Set<ModuleDescriptor.Requires> requires = new HashSet<>();
        private final Set<ModuleDescriptor.Exports> exports = new HashSet<>();
        private final Set<ModuleDescriptor.Opens> opens = new HashSet<>();
        private final Set<String> uses = new HashSet<>();
        private final Set<ModuleDescriptor.Provides> provides = new HashSet<>();

        private PatchedModuleDirectives() {
            descriptor = null;
        }

        private PatchedModuleDirectives(ModuleDescriptor descriptor) {
            super();
            this.descriptor = descriptor;
            this.requires.addAll(descriptor.requires());
            this.exports.addAll(descriptor.exports());
            this.opens.addAll(descriptor.opens());
            this.uses.addAll(descriptor.uses());
            this.provides.addAll(descriptor.provides());
        }

        public void addRequires(ModuleDescriptor.Requires require) {
            requires.stream().filter(r -> r.name().equals(require.name())).findAny().ifPresent(requires::remove);
            requires.add(require);
        }

        public Set<ModuleDescriptor.Requires> requires() {
            return requires;
        }

        public void addExports(ModuleDescriptor.Exports export) {
            exports.stream().filter(e -> e.source().equals(export.source())).findAny().ifPresent(exports::remove);
            exports.add(export);
        }

        public Set<ModuleDescriptor.Exports> exports() {
            return exports;
        }

        public void addOpens(ModuleDescriptor.Opens open) {
            opens.stream().filter(o -> o.source().equals(open.source())).findAny().ifPresent(opens::remove);
            opens.add(open);
        }

        public Set<ModuleDescriptor.Opens> opens() {
            return opens;
        }

        public void addUses(String use) {
            uses.add(use);
        }

        public Set<String> uses() {
            return uses;
        }

        public void addProvides(ModuleDescriptor.Provides provide) {
            provides.stream().filter(p -> p.service().equals(provide.service())).findAny().ifPresent(provides::remove);
            provides.add(provide);
        }

        public Set<ModuleDescriptor.Provides> provides() {
            return provides;
        }
    }
}
