package jdk.tools.jlink.internal.plugins;

import com.sun.tools.jdeps.JdepsConfiguration;
import com.sun.tools.jdeps.ModuleInfoBuilder;
import jdk.internal.opt.CommandLine;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ModuleVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.tools.jlink.internal.Archive;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class ClassPathPlugin extends AbstractPlugin {
    private static final String NAME = "class-path";
    private static final String JAVA_BASE_MOD = "java.base";
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
            Path tmp = Files.createTempDirectory("jlink-class-path-plugin-tmp");
            tmp.toFile().deleteOnExit();
            ModuleInfoBuilder miBuilder = new ModuleInfoBuilder(
                    config,
                    classPath.stream().map(Path::toString).toList(),
                    tmp, // not used
                    false);
            miBuilder.run(true, null, true);
            descriptors = miBuilder.descriptors().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // TODO: verify classpath and descriptors are one-to-one mappings and the order is the same

        List<Archive> archives = new ArrayList<>();

//        descriptors.forEach(...);

        in.transformAndCopy(Function.identity(), out);


//        out.add(ResourcePoolEntryFactory.create());

        return out.build();
    }

    private void stripNonExistingProvideClauses(ModuleDescriptor descriptor) {

    }

    private byte[] compileModuleInfo(ModuleDescriptor descriptor) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V19, Opcodes.ACC_MODULE, "module-info", null, null, null);

        // TODO: might as well include the source, too?

        ModuleVisitor mv = cw.visitModule(descriptor.name(), 0, null);

        for (ModuleDescriptor.Requires require : descriptor.requires()) {
            mv.visitRequire(require.name(),
                    require.name().equals(JAVA_BASE_MOD)
                            ? Opcodes.ACC_MANDATED
                            : requireModifiersToMask(require.modifiers()),
                    require.rawCompiledVersion().orElse(null));
        }

        for (ModuleDescriptor.Exports export : descriptor.exports()) {
            mv.visitExport(classToInternalName(export.source()),
                    exportModifiersToMask(export.modifiers()),
                    export.targets().stream().map(this::classToInternalName).toArray(String[]::new));
        }

        for (ModuleDescriptor.Opens open : descriptor.opens()) {
            mv.visitOpen(open.source(),
                    openModifiersToMask(open.modifiers()),
                    open.targets().stream().map(this::classToInternalName).toArray(String[]::new));
        }

        for (String use : descriptor.uses()) {
            mv.visitUse(classToInternalName(use));
        }

        for (ModuleDescriptor.Provides provide : descriptor.provides()) {
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
}
