package jdk.tools.jlink.internal.plugins;

import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class ClassPathPlugin extends AbstractPlugin {
    private static final String NAME = "class-path";

    private static final List<Path> classPath = new ArrayList<>();

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
        String[] dirs = config.get(getName()).split(File.pathSeparator);
        Arrays.stream(dirs)
                .map(Paths::get)
                .forEach(classPath::add);
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        // TODO:
        //   1. load resources from class path
        //   2. generate and compile a module-info.class
        //   3. add resources and module-info to resource pool

        in.transformAndCopy(Function.identity(), out);
        return out.build();
    }
}
