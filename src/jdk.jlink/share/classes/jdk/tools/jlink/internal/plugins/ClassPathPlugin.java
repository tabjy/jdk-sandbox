package jdk.tools.jlink.internal.plugins;

import com.sun.tools.jdeps.JdepsConfiguration;
import com.sun.tools.jdeps.ModuleInfoBuilder;
import jdk.internal.opt.CommandLine;
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
                .appModulePath(String.join(File.pathSeparator, modulePath.stream().map(Path::toString).toArray(String[]::new)))
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


        in.transformAndCopy(Function.identity(), out);

//        out.add(ResourcePoolEntryFactory.create());

        return out.build();
    }

    private byte[] compileModuleInfo(ModuleDescriptor descriptor) {
        return null;
    }
}
