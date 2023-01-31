package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.LineNumberNode;
import jdk.internal.org.objectweb.asm.tree.LocalVariableNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.Frame;
import jdk.internal.org.objectweb.asm.tree.analysis.Interpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConstantPropagationAnalyzer {

    private static class TestTarget {
        public static void test(int a) throws Exception {
            int a_ = -a;

            int b = 1;
            int b_ = -b;
            int b__ = b_ + 42;

            String s = "123";
            int s_ = Integer.parseInt(s);
            int s__ = s.length();

            String c = "com.example.MySpi";
            Class<?> c_ = Class.forName(c);
            String c__ = c_.getName();

            Object o = new Object();
        }

        public static void test2(long l) throws Exception {
            int i = (int) l / 2;
            test(i);
        }

        public static void test3() throws Exception {
            test2(222L);
        }

//        public static void test4() throws Exception {
//            test(999);
//        }
    }

    public static void main(String[] args) throws Exception {
        String path = Type.getType(TestTarget.class).getInternalName();
        InputStream is = ConstantPropagationAnalyzer.class.getClassLoader().getResourceAsStream(path + ".class");
        byte[] bytes = is.readAllBytes();
        is.close();

        ResourcePoolManager poolManager = new ResourcePoolManager();
        poolManager.add(ResourcePoolEntry.create("/java.jlink/" + path + ".class", bytes));
        ResourcePool pool = poolManager.resourcePool();

        ConstantPropagationAnalyzer analyzer = new ConstantPropagationAnalyzer(pool);
        analyzer.addLocalVariableTarget(path, "test", "(I)V", 55, "b__");

        analyzer.analyze();
    }

    private static class TargetRegistry {
        // class/method -> target descriptors
        private final Map<String, List<TargetDescriptor>> descriptors = new HashMap<>();

        public void addClassScopeTarget(String clazz, TargetDescriptor target) {
            Objects.requireNonNull(clazz);
            Objects.requireNonNull(target);

            if (target.getScope() != TargetDescriptor.Scope.CLASS) {
                throw new IllegalArgumentException("Not a class scope target descriptor");
            }

            descriptors.computeIfAbsent(clazz, k -> new ArrayList<>()).add(target);
        }

        public void addMethodScopeTarget(String clazz, String method, String descriptor, TargetDescriptor target) {
            Objects.requireNonNull(clazz);
            Objects.requireNonNull(method);
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(target);

            if (target.getScope() != TargetDescriptor.Scope.METHOD) {
                throw new IllegalArgumentException("Not a method scope target descriptor");
            }

            descriptors.computeIfAbsent(clazz + "." + method + descriptor, k -> new ArrayList<>()).add(target);
        }
    }

    private abstract static class TargetDescriptor {
        public enum Scope {CLASS, METHOD}

        /* package-private */
        abstract Scope getScope();

        public static LocalVariableTargetDescriptor createLocalVariableTargetDescriptor(int instruction,
                                                                                        int index,
                                                                                        String name,
                                                                                        int line) {
            return new LocalVariableTargetDescriptor(instruction, index, name, line);
        }

        public static LocalVariableTargetDescriptor createLocalVariableTargetDescriptor(int instruction, int index) {
            return new LocalVariableTargetDescriptor(instruction, index);
        }

        public static LocalVariableTargetDescriptor createLocalVariableTargetDescriptor(String name,
                                                                                        int line,
                                                                                        MethodNode methodNode) {
            LineNumberNode lineNumberNode = (LineNumberNode) Arrays.stream(methodNode.instructions.toArray())
                    .filter(inst -> (inst instanceof LineNumberNode lnn) && lnn.line == line)
                    .findAny().orElseThrow(() ->
                            new IllegalArgumentException(
                                    String.format("Line %d not found in method %s", line, methodNode.name)));

            int instruction = methodNode.instructions.indexOf(lineNumberNode);

            LocalVariableNode variableNode = methodNode.localVariables.stream()
                    .filter(var -> var.name.equals(name))
                    .filter(var -> methodNode.instructions.indexOf(var.start) < instruction
                            && methodNode.instructions.indexOf(var.end) > instruction)
                    .findFirst().orElseThrow(() ->
                            new IllegalArgumentException(String.format("Variable \"%s\" not in scope on line %d of %s",
                                    name,
                                    line,
                                    methodNode.name)));

            return new LocalVariableTargetDescriptor(instruction, variableNode.index, name, line);
        }
    }

    private static class LocalVariableTargetDescriptor extends TargetDescriptor {
        public final int instruction;
        public final int index;
        public final int line;
        public final String name;

        private LocalVariableTargetDescriptor(int instruction, int index, String name, int line) {
            this.instruction = instruction;
            this.index = index;
            this.name = name;
            this.line = line;
        }

        private LocalVariableTargetDescriptor(int instruction, int index) {
            this.instruction = instruction;
            this.index = index;
            this.name = null;
            this.line = -1;
        }

        @Override
        Scope getScope() {
            return Scope.METHOD;
        }
    }

    private static class StackValueTargetDescriptor extends TargetDescriptor {

        @Override
        Scope getScope() {
            return Scope.METHOD;
        }

        // TODO
    }

    // TODO: support fields
    private record ClassTarget(String name, Set<MethodTarget> methods) {
        public MethodTarget addMethodTarget(MethodTarget target) {
            return methods.stream()
                    .filter(m -> m.name().equals(target.name()) && m.descriptor().equals(target.descriptor()))
                    .findAny()
                    .orElseGet(() -> {
                        methods.add(target);
                        return target;
                    });
        }
    }

    // TODO: support return values
    private record MethodTarget(String name,
                                String descriptor,
                                Set<StackValueTarget> stackValues,
                                Set<LocalVariableTarget> localVariables) {

        public StackValueTarget addStackValueTarget(StackValueTarget target) {
            return stackValues.stream()
                    .filter(value -> value.instruction == target.instruction && value.index == target.instruction)
                    .findAny()
                    .orElseGet(() -> {
                        stackValues.add(target);
                        return target;
                    });
        }

        public LocalVariableTarget addLocalVariableTarget(LocalVariableTarget target) {
            localVariables.stream()
                    .filter(value -> value.instruction == target.instruction && value.index == target.instruction)
                    .findAny()
                    .ifPresent(localVariables::remove);

            localVariables.add(target);
            return target;
        }
    }

    private record StackValueTarget(int instruction, int index) {
    }

    private record LocalVariableTarget(int instruction, int index, int line, String name) {
    }

    private final Map<String, Function<Optional<? extends ConstantDesc>[], Optional<? extends ConstantDesc>>> emulations = new HashMap<>();

    private final ResourcePool resourcePool;
    private final Map<String, String> classToModuleMap;
    private final Map<String, ClassTarget> targetRegistry = new HashMap<>();

    public ConstantPropagationAnalyzer(ResourcePool resourcePool) {
        this.resourcePool = resourcePool;
        this.classToModuleMap = resourcePool.entries()
                .filter(e -> e.type() == ResourcePoolEntry.Type.CLASS_OR_RESOURCE)
                .filter(e -> e.path().endsWith(".class") && !e.path().equals("/" + e.moduleName() + "/module-info.class"))
                .collect(Collectors.toMap(e -> e.path().substring(
                                e.path().indexOf('/', 1) + 1,
                                e.path().length() - ".class".length()),
                        ResourcePoolEntry::moduleName)
                );

        // add type inductions for well-known methods
        // by no means comprehensive, just so we have something to test with
        // String -> ClassDesc
        this.registerMethodEmulation(
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> ClassDesc.of((String) desc))
        );

        // ClassDesc -> String
        this.registerMethodEmulation(
                "java/lang/Class",
                "getName",
                "()Ljava/lang/String;",
                (receiver, args) -> receiver
//                        .filter(desc -> desc instanceof ClassDesc)
                        .map(clazz -> Type.getType(((ClassDesc) clazz).descriptorString()).getClassName())
        );

        // String -> Integer
        this.registerMethodEmulation(
                "java/lang/Integer",
                "parseInt",
                "(Ljava/lang/String;)I",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> Integer.parseInt((String) desc))
        );

        // String -> Float
        this.registerMethodEmulation(
                "java/lang/Float",
                "parseFloat",
                "(Ljava/lang/String;)F",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> Float.parseFloat((String) desc))
        );

        // String -> String
        this.registerMethodEmulation(
                "java/lang/String",
                "<init>",
                "(Ljava/lang/String)V",
                (args) -> args[0] // returns the created string (same as the input argument)
//                        .filter(desc -> desc instanceof String)
        );

        // String -> int
        this.registerMethodEmulation(
                "java/lang/String",
                "length",
                "()I",
                (receiver, args) -> receiver
//                        .filter(desc -> desc instanceof String)
                        .map(string -> ((String) string).length())
        );
    }

    public void analyze() {
        // TODO: analyze target classes in the resource pool
        for (Map.Entry<String, ClassTarget> klass : targetRegistry.entrySet()) {
            for (Map.Entry<String, Map<String, MethodTarget>> method : klass.getValue().methods.entrySet()) {
                for (Map.Entry<String, MethodTarget> target : method.getValue().entrySet()) {
                    analyzeMethod(klass.getKey(), method.getKey(), target.getKey());
                }
            }
        }
    }

    private ClassTarget createClassTargetIfNotExisted(String owner) {
        return targetRegistry.computeIfAbsent(owner, (key) -> new ClassTarget(/*new HashSet<>(), */new HashMap<>()));
    }

//    public void addStaticFieldTarget(String owner, String name) {
//        createClassTargetIfNotExisted(owner).fields.add(name);
//    }

    private MethodTarget createMethodTargetIfNotExisted(String owner, String name, String descriptor) {
        return createClassTargetIfNotExisted(owner).methods
                .computeIfAbsent(name, (key) -> new HashMap<>())
                .computeIfAbsent(descriptor, (key) -> new MethodTarget(new HashMap<>(), new HashMap<>()));
    }

    // TODO: support capturing returned values?
//    public void addReturnTarget(String owner, String method, String descriptor) {
//    }

    // TODO: support more intuitive variable targeting. e.g., by name and line number
    public void addLocalVariableTarget(String owner, String method, String descriptor, int inst, int index) {
        createMethodTargetIfNotExisted(owner, method, descriptor).variables
                .computeIfAbsent(inst, key -> new HashSet<>())
                .add(index);
    }

    public void addLocalVariableTarget(String owner, String method, String descriptor, int line, String name) {
        ClassNode cn = getClassNode(owner);
        MethodNode mn = getMethodNode(cn, method, descriptor);

//        mn.instructions.forEach(System.out::println);
        LineNumberNode lineNumberNode = (LineNumberNode) Arrays.stream(mn.instructions.toArray()).filter(inst ->
                (inst instanceof LineNumberNode lnn) && lnn.line == line
        ).findAny().orElseThrow();

        int inst = mn.instructions.indexOf(lineNumberNode);

        LocalVariableNode variableNode = mn.localVariables.stream()
                .filter(var -> var.name.equals(name))
                .filter(var -> mn.instructions.indexOf(var.start) < inst && mn.instructions.indexOf(var.end) > inst)
                .findFirst().orElseThrow();

        addLocalVariableTarget(owner, method, descriptor, inst, variableNode.index);
    }

    public void addStackValueTarget(String owner, String method, String descriptor, int inst, int index) {
        createMethodTargetIfNotExisted(owner, method, descriptor).stacks
                .computeIfAbsent(inst, key -> new HashSet<>())
                .add(index);
    }

    // abstract function into a set of outputs (i.e., captured local variables) described by a set of inputs (i.e.,
    // arguments, receiver, fields)
    private static class MethodAbstraction {
        // TODO: find a way to write known output to results

        private final Map<Integer, ConstableValue> parameters = new HashMap<>();
        private final Map<Integer, Map<Integer, ConstableValue>> variables = new HashMap<>();
        private final Map<Integer, Map<Integer, ConstableValue>> stacks = new HashMap<>();
        private final List<ConstableValue> values = new ArrayList<>();

        private MethodAbstraction() {
        }

        // receivers are treated as argument #0
        private void addInputParameter(int index, ConstableValue value) {
            parameters.put(index, value);
        }

        // TODO: support field read references as inputs

        private void addOutputLocalVariable(int inst, int index, ConstableValue value) {
            variables.computeIfAbsent(inst, key -> new HashMap<>()).put(index, value);
            values.add(value);
        }

        private void addOutputStackValue(int inst, int index, ConstableValue value) {
            stacks.computeIfAbsent(inst, key -> new HashMap<>()).put(index, value);
            values.add(value);
        }

        private boolean isResolved() {
            return values.stream().allMatch(constableValue -> constableValue.describeConstable().isPresent());
        }
    }

    private ClassNode getClassNode(String owner) {
        ResourcePoolEntry entry = resourcePool
                .findEntry("/" + classToModuleMap.get(owner) + "/" + owner + ".class").orElseThrow();

        ClassReader cr = new ClassReader(entry.contentBytes());
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        return cn;
    }

    private MethodNode getMethodNode(ClassNode cn, String name, String descriptor) {
        return cn.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow();
    }

    private List<ConstableValue> analyzeMethod(String owner, String method, String descriptor) {
        // TODO: optimize by checking for already-abstracted methods?
        //       while avoiding cyclic structures...
        ClassNode cn = getClassNode(owner);
        MethodNode mn = getMethodNode(cn, method, descriptor);

        MethodAbstraction abstraction = abstractsMethod(owner, mn);

        // START of debug prints ---------------------------------------------------------------------------------------
        System.out.printf("[DEBUG] method output resolution for %s.%s%s: \n", owner, method, descriptor);
        for (Map.Entry<Integer, Map<Integer, ConstableValue>> inst : abstraction.variables.entrySet()) {
            for (Map.Entry<Integer, ConstableValue> var : inst.getValue().entrySet()) {
                Optional<? extends ConstantDesc> constable = var.getValue().describeConstable();
                System.out.printf("\tlocal variable #%d at inst #%d: %s\n",
                        var.getKey(),
                        inst.getKey(),
                        constable.map(c -> c + " (" + c.getClass().getSimpleName() + ")").orElse("UNRESOLVED"));
            }
        }
        for (Map.Entry<Integer, Map<Integer, ConstableValue>> inst : abstraction.stacks.entrySet()) {
            for (Map.Entry<Integer, ConstableValue> stack : inst.getValue().entrySet()) {
                Optional<? extends ConstantDesc> constable = stack.getValue().describeConstable();
                System.out.printf("\tstack value #%d at inst #%d: %s\n",
                        stack.getKey(),
                        inst.getKey(),
                        constable.map(c -> c + " (" + c.getClass().getSimpleName() + ")").orElse("UNRESOLVED"));
            }
        }
        // END of debug prints -----------------------------------------------------------------------------------------

        if (abstraction.isResolved()) {
            return abstraction.values; // TODO: log the resolved values somewhere
        }

        // recursively analyze caller methods
        Map<String, Stream<MethodNode>> callers = scanForCallers(owner, method, descriptor);
        Stream<List<ConstableValue>> callerValues = callers.entrySet().stream()
                .flatMap(e -> e.getValue().map(m -> analyzeMethod(e.getKey(), m.name, m.desc)));

        // TODO: merge values from multiple callers!
        List<ConstableValue> callerValue = callerValues.findAny().orElse(null);
        if (callerValue == null) {
            return abstraction.values;
        }

        for (int i = 0; i < callers.size(); i++) {
            Optional<? extends ConstantDesc> desc = callerValue.get(i).describeConstable();
            if (desc.isPresent()) {
                abstraction.parameters.get(i).setAdhocValue(desc.get());
            }
        }

//        List<ConstableValue> callerValue = callerValues.findAny().get();

        // START of debug prints ---------------------------------------------------------------------------------------
        System.out.printf("[DEBUG] method output resolution for %s.%s%s: \n", owner, method, descriptor);
        for (Map.Entry<Integer, Map<Integer, ConstableValue>> inst : abstraction.variables.entrySet()) {
            for (Map.Entry<Integer, ConstableValue> var : inst.getValue().entrySet()) {
                Optional<? extends ConstantDesc> constable = var.getValue().describeConstable();
                System.out.printf("\tlocal variable #%d at inst #%d: %s\n",
                        var.getKey(),
                        inst.getKey(),
                        constable.map(c -> c + " (" + c.getClass().getSimpleName() + ")").orElse("UNRESOLVED"));
            }
        }
        for (Map.Entry<Integer, Map<Integer, ConstableValue>> inst : abstraction.stacks.entrySet()) {
            for (Map.Entry<Integer, ConstableValue> stack : inst.getValue().entrySet()) {
                Optional<? extends ConstantDesc> constable = stack.getValue().describeConstable();
                System.out.printf("\tstack value #%d at inst #%d: %s\n",
                        stack.getKey(),
                        inst.getKey(),
                        constable.map(c -> c + " (" + c.getClass().getSimpleName() + ")").orElse("UNRESOLVED"));
            }
        }
        // END of debug prints -----------------------------------------------------------------------------------------

        return abstraction.values;
    }

    private Map<String, Stream<MethodNode>> scanForCallers(String owner, String method, String descriptor) {
        Type methodType = Type.getMethodType(descriptor);
        Type[] argumentTypes = methodType.getArgumentTypes();

        // TODO: optimization opportunity: skip impossible caller according to access policy
        //       check module visibility and access modifiers
        return resourcePool.entries().flatMap(entry -> {
            ClassReader cr = new ClassReader(entry.contentBytes());
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.EXPAND_FRAMES);

            return Map.of(cn.name, cn.methods.stream().filter(mn -> {
                boolean found = false;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode ins = mn.instructions.get(i);
                    if (
                            (
                                    ins.getOpcode() == Opcodes.INVOKEVIRTUAL
                                            || ins.getOpcode() == Opcodes.INVOKEINTERFACE // TODO: do we need this?
                                            || ins.getOpcode() == Opcodes.INVOKESTATIC
                            )
                                    && ((MethodInsnNode) ins).owner.equals(owner)
                                    && ((MethodInsnNode) ins).name.equals(method)
                                    && ((MethodInsnNode) ins).desc.equals(descriptor)
                    ) {
                        found = true;
                        if (ins.getOpcode() != Opcodes.INVOKESTATIC) {
                            // receiver as argument #0
                            addStackValueTarget(cn.name, mn.name, mn.desc, i, argumentTypes.length);
                        }

                        // Note: we don't really care that long and double take two slots. ASM interpreter handles that
                        //       for us!
                        for (int j = argumentTypes.length - 1; j >= 0; j--) {
                            addStackValueTarget(cn.name, mn.name, mn.desc, i, j);
                        }
                    }
                }

                return found;
            })).entrySet().stream();
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1)); // TODO: watch out for cyclic calls!
    }

    private MethodAbstraction abstractsMethod(String owner, MethodNode mn) {
        Analyzer<ConstableValue> analyzer = new Analyzer<>(new ConstableValueInterpreter());
        Frame<ConstableValue>[] frames;
        try {
            frames = analyzer.analyze(owner, mn);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e); // TODO: better error handling
        }

        MethodAbstraction abstraction = new MethodAbstraction();

        // extract parameter inputs
        Frame<ConstableValue> first = frames[0];
        for (int i = 0; i < first.getLocals(); i++) {
            abstraction.addInputParameter(i, first.getLocal(i));
        }

        // extract local variable targets
        if (targetRegistry.containsKey(owner)) {
            ClassTarget ct = targetRegistry.get(owner);
            if (ct.methods.containsKey(mn.name)) {
                Map<String, MethodTarget> methods = ct.methods.get(mn.name);
                for (Map.Entry<String, MethodTarget> mt : methods.entrySet()) {
                    for (Map.Entry<Integer, Set<Integer>> vt : mt.getValue().variables.entrySet()) {
                        for (Integer index : vt.getValue()) {
                            // NOTE: the last frame could've been null if the last instruction being a label
                            //      in this case, we really want the last non-null frame
                            Frame<ConstableValue> frame = null;
                            for (int i = vt.getKey(); i >= 0 && frame == null; i--) {
                                frame = frames[i];
                            }

                            ConstableValue variable = frame.getLocal(index);
                            abstraction.addOutputLocalVariable(vt.getKey(), index, variable);
                        }
                    }
                }
            }
        }

        // extract stack value targets
        if (targetRegistry.containsKey(owner)) {
            ClassTarget ct = targetRegistry.get(owner);
            if (ct.methods.containsKey(mn.name)) {
                Map<String, MethodTarget> methods = ct.methods.get(mn.name);
                for (Map.Entry<String, MethodTarget> mt : methods.entrySet()) {
                    for (Map.Entry<Integer, Set<Integer>> st : mt.getValue().stacks.entrySet()) {
                        for (Integer index : st.getValue()) {
                            // NOTE: the last frame could've been null if the last instruction being a label
                            //      in this case, we really want the last non-null frame
                            Frame<ConstableValue> frame = null;
                            for (int i = st.getKey(); i >= 0 && frame == null; i--) {
                                frame = frames[i];
                            }

                            ConstableValue value = frame.getStack(frame.getStackSize() - 1 - index);
                            abstraction.addOutputStackValue(st.getKey(), index, value);
                        }
                    }
                }
            }
        }

        return abstraction;
    }

    public void registerMethodEmulation(String owner,
                                        String method,
                                        String descriptor,
                                        BiFunction< // TODO: maybe easier to use a type alias?
                                                Optional<? extends ConstantDesc>,
                                                Optional<? extends ConstantDesc>[],
                                                Optional<ConstantDesc>
                                                > function) {
        // TODO: check owner is not an interface

        // TODO: check if the method is declared on a final class, a sealed class with no permitted child, or an
        //       effectively final class

        // NOTE: Tako mentioned a plugin for checking effectively finals? Make that plugin a pre-requisite?

        emulations.put(owner + "." + method + descriptor, args -> function.apply(args[0], Arrays.copyOfRange(args, 1, args.length)));
    }

    public void registerMethodEmulation(String owner,
                                        String method,
                                        String descriptor,
                                        Function<
                                                Optional<? extends ConstantDesc>[],
                                                Optional<? extends ConstantDesc>
                                                > function) {
        emulations.put(owner + "." + method + descriptor, function);
    }

    private class ConstableValueInterpreter extends Interpreter<ConstableValue> {
//        public static final Type NULL_TYPE = Type.getObjectType("null");
//        public static final Type STRING_TYPE = Type.getObjectType("java/lang/String");
//        public static final Type CLASS_TYPE = Type.getObjectType("java/lang/Class");
//        public static final Type METHOD_TYPE = Type.getObjectType("java/lang/invoke/MethodType");
//        public static final Type METHOD_HANDLE_TYPE = Type.getObjectType("java/lang/invoke/MethodHandle");

        public static final ClassDesc NULL_CONSTANT_DESC = ClassDesc.of("null");

        protected ConstableValueInterpreter() {
            super(Opcodes.ASM9);
        }

        @Override
        public ConstableValue newValue(Type type) {
            // TODO: might be a better idea to include data type in ConstableValue

            if (type == null) {
                return ConstableValue.UNINITIALIZED_VALUE;
            }

            if (type == Type.VOID_TYPE) {
                return null;
            }

            return new ConstableValue(type.getSize());

            // NOTE: see Interpreter#newParameterValue()
//            throw new UnsupportedOperationException("use specific operation methods");

//
//            return switch (type.getSort()) {
//                case Type.VOID -> null;
//                case Type.BOOLEAN,
//                        Type.CHAR,
//                        Type.BYTE,
//                        Type.SHORT,
//                        Type.INT,
//                        Type.FLOAT,
//                        Type.ARRAY,
//                        Type.OBJECT -> new ConstableValue(1);
//                case Type.LONG,
//                        Type.DOUBLE -> new ConstableValue(2);
//                default -> throw new AssertionError();
//            };
        }

        @Override
        public ConstableValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            if (insn.getOpcode() == Opcodes.LDC) {
                Object value = ((LdcInsnNode) insn).cst;
                if (value instanceof Integer i) {
                    return new ConstableValue(i);
                } else if (value instanceof Float f) {
                    return new ConstableValue(f);
                } else if (value instanceof Long l) {
                    return new ConstableValue(l);
                } else if (value instanceof Double d) {
                    return new ConstableValue(d);
                } else if (value instanceof String s) {
                    return new ConstableValue(s);
                } else if (value instanceof Type t) {
                    int sort = t.getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        return new ConstableValue(ClassDesc.of(t.getDescriptor()));
                    } else if (sort == Type.METHOD) {
                        // TODO: verify this is correct
                        return new ConstableValue(MethodTypeDesc.ofDescriptor(t.getDescriptor()));
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                } else if (value instanceof Handle || value instanceof ConstantDynamic) {
                    Handle handle = value instanceof Handle
                            ? (Handle) value
                            : ((ConstantDynamic) value).getBootstrapMethod();

                    // TODO: is this remotely relevant to the purpose of constant propagation?
                    DirectMethodHandleDesc.Kind kind = switch (handle.getTag()) {
                        case Opcodes.H_GETFIELD -> DirectMethodHandleDesc.Kind.GETTER;
                        case Opcodes.H_GETSTATIC -> DirectMethodHandleDesc.Kind.STATIC_GETTER;
                        case Opcodes.H_PUTFIELD -> DirectMethodHandleDesc.Kind.SETTER;
                        case Opcodes.H_PUTSTATIC -> DirectMethodHandleDesc.Kind.STATIC_SETTER;
                        case Opcodes.H_INVOKEVIRTUAL -> DirectMethodHandleDesc.Kind.VIRTUAL;
                        case Opcodes.H_INVOKESTATIC -> DirectMethodHandleDesc.Kind.STATIC;
                        case Opcodes.H_INVOKESPECIAL -> DirectMethodHandleDesc.Kind.SPECIAL;
                        case Opcodes.H_NEWINVOKESPECIAL -> DirectMethodHandleDesc.Kind.CONSTRUCTOR;
                        case Opcodes.H_INVOKEINTERFACE -> DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL;
                        default -> throw new AnalyzerException(insn, "Unexpected value: " + handle.getTag());
                    };

                    DirectMethodHandleDesc handleDesc = MethodHandleDesc.ofMethod(
                            kind,
                            ClassDesc.ofInternalName(handle.getOwner()),
                            handle.getName(),
                            MethodTypeDesc.ofDescriptor(handle.getDesc())
                    );

                    if (value instanceof Handle) {
                        return new ConstableValue(handleDesc);
                    }

                    ConstantDesc[] arguments = new ConstantDesc[((ConstantDynamic) value)
                            .getBootstrapMethodArgumentCount()];
                    for (int i = 0; i < arguments.length; i++) {
                        // TODO: conversion for Boolean, Char to Integer is probably needed
                        arguments[i] = (ConstantDesc) ((ConstantDynamic) value).getBootstrapMethodArgument(i);
                    }

                    return new ConstableValue(DynamicConstantDesc.of(handleDesc, arguments));
                }
                throw new AnalyzerException(insn, "Illegal LDC value " + value);
            }

            if (insn.getOpcode() == Opcodes.ACONST_NULL) {
                return new ConstableValue(NULL_CONSTANT_DESC);
            }

            if (insn.getOpcode() == Opcodes.JSR) {
                return new ConstableValue(1);
            }

            if (insn.getOpcode() == Opcodes.GETSTATIC) {
                // TODO: figure out whether we want array accesses in constant folding
                return new ConstableValue(Type.getType(((FieldInsnNode) insn).desc).getSize());
            }

            if (insn.getOpcode() == Opcodes.NEW) {
                // TODO: figure out whether we want field access and instance method invocation in constant folding
                return new ConstableValue(1);
            }

            return new ConstableValue(switch (insn.getOpcode()) {
                case Opcodes.ICONST_M1 -> -1;
                case Opcodes.ICONST_0 -> 0;
                case Opcodes.ICONST_1 -> 1;
                case Opcodes.ICONST_2 -> 2;
                case Opcodes.ICONST_3 -> 3;
                case Opcodes.ICONST_4 -> 4;
                case Opcodes.ICONST_5 -> 5;
                case Opcodes.LCONST_0 -> 0L;
                case Opcodes.LCONST_1 -> 1L;
                case Opcodes.FCONST_0 -> 0F;
                case Opcodes.FCONST_1 -> 1F;
                case Opcodes.FCONST_2 -> 2F;
                case Opcodes.DCONST_0 -> 0D;
                case Opcodes.DCONST_1 -> 1D;
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) insn).operand;
                default -> throw new AssertionError();
            });
        }

        @Override
        public ConstableValue copyOperation(AbstractInsnNode insn, ConstableValue value) throws AnalyzerException {
            // ILOAD, LLOAD, FLOAD, DLOAD, ALOAD, ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, DUP, DUP_X1, DUP_X2, DUP2,
            // DUP2_X1, DUP2_X2, SWAP
//            return value;
            return new ConstableValue(1, value);
        }

        @Override
        public ConstableValue unaryOperation(AbstractInsnNode insn, ConstableValue value) throws AnalyzerException {
            return switch (insn.getOpcode()) {
                // void results
                case Opcodes.IFEQ,
                        Opcodes.IFNE,
                        Opcodes.FRETURN,
                        Opcodes.IFLT,
                        Opcodes.IFGE,
                        Opcodes.IFGT,
                        Opcodes.IFLE,
                        Opcodes.TABLESWITCH,
                        Opcodes.LOOKUPSWITCH,
                        Opcodes.IRETURN,
                        Opcodes.LRETURN,
                        Opcodes.DRETURN,
                        Opcodes.ARETURN,
                        Opcodes.PUTSTATIC,
                        Opcodes.ATHROW,
                        Opcodes.MONITORENTER,
                        Opcodes.MONITOREXIT,
                        Opcodes.IFNULL,
                        Opcodes.IFNONNULL -> null;

                // int results
                case Opcodes.INEG -> new ConstableValue(1, value, (v -> (int) v * -1));
                case Opcodes.IINC -> new ConstableValue(1, value, (v -> (int) v + 1));
                case Opcodes.L2I -> new ConstableValue(1, value, (v -> (int) (long) v));
                case Opcodes.F2I -> new ConstableValue(1, value, (v -> (int) (float) v));
                case Opcodes.D2I -> new ConstableValue(1, value, (v -> (int) (double) v));
                case Opcodes.I2B -> new ConstableValue(1, value, (v -> (int) (byte) (int) v));
                case Opcodes.I2C -> new ConstableValue(1, value, (v -> (int) (char) (int) v));
                case Opcodes.I2S -> new ConstableValue(1, value, (v -> (int) (short) (int) v));
                // TODO: figure out whether we want array accesses in constant folding
                case Opcodes.ARRAYLENGTH -> new ConstableValue(1);

                // long results
                case Opcodes.LNEG -> new ConstableValue(2, value, (v -> (long) v * -1));
                case Opcodes.I2L -> new ConstableValue(2, value, (v -> (long) (int) v));
                case Opcodes.F2L -> new ConstableValue(2, value, (v -> (long) (float) v));
                case Opcodes.D2L -> new ConstableValue(2, value, (v -> (long) (double) v));

                // float results
                case Opcodes.FNEG -> new ConstableValue(1, value, (v -> (float) v * -1));
                case Opcodes.I2F -> new ConstableValue(1, value, (v -> (float) (int) v));
                case Opcodes.L2F -> new ConstableValue(1, value, (v -> (float) (long) v));
                case Opcodes.D2F -> new ConstableValue(1, value, (v -> (float) (double) v));

                // double results
                case Opcodes.DNEG -> new ConstableValue(2, value, (v -> (double) v * -1));
                case Opcodes.I2D -> new ConstableValue(2, value, (v -> (double) (int) v));
                case Opcodes.L2D -> new ConstableValue(2, value, (v -> (double) (long) v));
                case Opcodes.F2D -> new ConstableValue(2, value, (v -> (double) (float) v));

                // get field
                // TODO: figure out whether we want field access in constant folding
                case Opcodes.GETFIELD -> new ConstableValue(Type.getType(((FieldInsnNode) insn).desc).getSize());

                // array creation
                // TODO: figure out whether we want array accesses in constant folding
                case Opcodes.NEWARRAY, Opcodes.ANEWARRAY -> new ConstableValue(1);


                // reference type casting
                // TODO: determine actual typing at compile time if possible
                case Opcodes.CHECKCAST -> new ConstableValue(1);
                default -> throw new AssertionError();
            };
        }

        @Override
        public ConstableValue binaryOperation(AbstractInsnNode insn, ConstableValue value1, ConstableValue value2)
                throws AnalyzerException {
            return switch (insn.getOpcode()) {
                // void results
                case Opcodes.IF_ICMPEQ,
                        Opcodes.IF_ICMPNE,
                        Opcodes.IF_ICMPLT,
                        Opcodes.IF_ICMPGE,
                        Opcodes.IF_ICMPGT,
                        Opcodes.IF_ICMPLE,
                        Opcodes.IF_ACMPEQ,
                        Opcodes.IF_ACMPNE,
                        Opcodes.PUTFIELD -> null;

                // array operations
                // TODO: figure out whether we want array accesses in constant folding
                case Opcodes.AALOAD,
                        Opcodes.BALOAD,
                        Opcodes.CALOAD,
                        Opcodes.FALOAD,
                        Opcodes.IALOAD,
                        Opcodes.SALOAD -> new ConstableValue(1);
                case Opcodes.LALOAD,
                        Opcodes.DALOAD -> new ConstableValue(2);

                // integer arithmetics
                case Opcodes.IADD -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 + (int) v2);
                case Opcodes.ISUB -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 - (int) v2);
                case Opcodes.IMUL -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 * (int) v2);
                case Opcodes.IDIV -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 / (int) v2);
                case Opcodes.IREM -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 % (int) v2);
                case Opcodes.ISHL -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 << (int) v2);
                case Opcodes.ISHR -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 >> (int) v2);
                case Opcodes.IUSHR -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 >>> (int) v2);
                case Opcodes.IAND -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 & (int) v2);
                case Opcodes.IOR -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 | (int) v2);
                case Opcodes.IXOR -> new ConstableValue(1, value1, value2, (v1, v2) -> (int) v1 ^ (int) v2);

                // float arithmetics
                case Opcodes.FADD -> new ConstableValue(1, value1, value2, (v1, v2) -> (float) v1 + (float) v2);
                case Opcodes.FSUB -> new ConstableValue(1, value1, value2, (v1, v2) -> (float) v1 - (float) v2);
                case Opcodes.FMUL -> new ConstableValue(1, value1, value2, (v1, v2) -> (float) v1 * (float) v2);
                case Opcodes.FDIV -> new ConstableValue(1, value1, value2, (v1, v2) -> (float) v1 / (float) v2);
                case Opcodes.FREM -> new ConstableValue(1, value1, value2, (v1, v2) -> (float) v1 % (float) v2);

                // long arithmetics
                case Opcodes.LADD -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 + (long) v2);
                case Opcodes.LSUB -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 - (long) v2);
                case Opcodes.LMUL -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 * (long) v2);
                case Opcodes.LDIV -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 / (long) v2);
                case Opcodes.LREM -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 % (long) v2);
                case Opcodes.LSHL -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 << (long) v2);
                case Opcodes.LSHR -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 >> (long) v2);
                case Opcodes.LUSHR -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 >>> (long) v2);
                case Opcodes.LAND -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 & (long) v2);
                case Opcodes.LOR -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 | (long) v2);
                case Opcodes.LXOR -> new ConstableValue(2, value1, value2, (v1, v2) -> (long) v1 ^ (long) v2);

                // double arithmetics
                case Opcodes.DADD -> new ConstableValue(2, value1, value2, (v1, v2) -> (double) v1 + (double) v2);
                case Opcodes.DSUB -> new ConstableValue(2, value1, value2, (v1, v2) -> (double) v1 - (double) v2);
                case Opcodes.DMUL -> new ConstableValue(2, value1, value2, (v1, v2) -> (double) v1 * (double) v2);
                case Opcodes.DDIV -> new ConstableValue(2, value1, value2, (v1, v2) -> (double) v1 / (double) v2);
                case Opcodes.DREM -> new ConstableValue(2, value1, value2, (v1, v2) -> (double) v1 % (double) v2);

                // comparisons
                case Opcodes.LCMP -> new ConstableValue(1, value1, value2, (v1, v2) ->
                        Long.compare((long) v1, (long) v2));
                case Opcodes.FCMPL -> new ConstableValue(1, value1, value2, (v1, v2) ->
                        Float.isNaN((float) v1) || Float.isNaN((float) v2)
                                ? -1
                                : Float.compare((float) v1, (float) v2));
                case Opcodes.FCMPG -> new ConstableValue(1, value1, value2, (v1, v2) ->
                        Float.isNaN((float) v1) || Float.isNaN((float) v2)
                                ? 1
                                : Float.compare((float) v1, (float) v2));
                case Opcodes.DCMPL -> new ConstableValue(1, value1, value2, (v1, v2) ->
                        Double.isNaN((double) v1) || Double.isNaN((double) v2)
                                ? -1
                                : Float.compare((float) v1, (float) v2));
                case Opcodes.DCMPG -> new ConstableValue(1, value1, value2, (v1, v2) ->
                        Double.isNaN((double) v1) || Double.isNaN((double) v2)
                                ? 1
                                : Float.compare((float) v1, (float) v2));

                default -> throw new AssertionError();
            };
        }

        @Override
        public ConstableValue ternaryOperation(AbstractInsnNode insn, ConstableValue value1, ConstableValue value2,
                                               ConstableValue value3) throws AnalyzerException {
            // IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE
            // TODO: figure out whether we want array accesses in constant folding
            return null;
        }

        @Override
        public ConstableValue naryOperation(AbstractInsnNode insn, List<? extends ConstableValue> values) throws AnalyzerException {
            // TODO: result from each function analysis should work together as a call graph
            //       Blocker: path explosion

            // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE, MULTIANEWARRAY and INVOKEDYNAMIC
            if (insn.getOpcode() == Opcodes.MULTIANEWARRAY) {
                // TODO: handle multidimensional arrays
                return new ConstableValue(1);
            }

            MethodInsnNode min = (MethodInsnNode) insn;
            Type rt = Type.getReturnType(min.desc);

            String key = min.owner + "." + min.name + min.desc;
            Function<Optional<? extends ConstantDesc>[], Optional<? extends ConstantDesc>> emulation =
                    emulations.get(key);

            // TODO: verify we don't need to handle INVOKEDYNAMIC
            if (emulation == null || min.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                // INVOKESPECIAL always pushes an object reference to stack
                return new ConstableValue(min.getOpcode() == Opcodes.INVOKESPECIAL ? 1 : rt.getSize());
            }

            // Technically return value from interface invocation cannot be inferred, since the implementation function
            // is unknown at compile time.
            // However, INVOKEINTERFACE can still be used for instance methods. So, we treat it as INVOKEVIRTUAL.
            if (min.getOpcode() == Opcodes.INVOKESTATIC
                    || min.getOpcode() == Opcodes.INVOKEVIRTUAL
                    || min.getOpcode() == Opcodes.INVOKEINTERFACE
                    || min.getOpcode() == Opcodes.INVOKESPECIAL) {
                if (rt.getSort() == Type.VOID && min.getOpcode() != Opcodes.INVOKESPECIAL) {
                    return null;
                }

                return new ConstableValue(rt.getSize(), values, optionals -> {
                    // FIXME: toArray() causes generic typing information erased
                    @SuppressWarnings("unchecked")
                    Optional<? extends ConstantDesc>[] constantDescs =
                            Arrays.stream(optionals).map(optional -> optional.flatMap(Constable::describeConstable))
                                    .toArray(Optional[]::new);
                    return emulations.get(key).apply(constantDescs);
                });
            }

            throw new AssertionError();
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, ConstableValue value, ConstableValue expected) throws AnalyzerException {
            // RETURN, ARETURN, IRETURN, LRETURN, FRETURN, DRETURN
            // noop
        }

        @Override
        public ConstableValue merge(ConstableValue value1, ConstableValue value2) {
//            if (value1.equals(value2)) {
//                return value1;
//            }

            // TODO: properly merge values.
            //       need to create new value instance rather than mutating existing one!
//            return null;
            return value1;
        }
    }

    private static class ConstableValue implements Value, Constable {
        // for e.g., uninitialized local variables. Should not interfere with constant folding anyway
        public static final ConstableValue UNINITIALIZED_VALUE = new ConstableValue(1);

        private final int size;
        protected ConstantDesc constantDesc; // one of: null, String, Integer, Long, Float, Double, ClassDesc,
        //         MethodTypeDesc, MethodHandleDesc
        private ConstantDesc adhocConstantDesc;
        private final List<? extends Constable> sources;
        private final Function<Optional<Constable>[], Optional<? extends ConstantDesc>> describer;

        public ConstableValue(int size) {
            this(size, null, List.of(), null);
        }

        public ConstableValue(ConstantDesc constantDesc) {
            this(-1, constantDesc, List.of(), null);

            Objects.requireNonNull(constantDesc);
        }

        public ConstableValue(int size, Constable source) {
            this(size, List.of(source), (values) ->
                    values[0].isPresent() ? values[0].get().describeConstable() : Optional.empty());
        }

        public ConstableValue(int size, Constable source, Function<Constable, ConstantDesc> operator) {
            this(size, List.of(source), (values) -> values[0].map(operator));
        }

        public ConstableValue(int size, Constable source1, Constable source2, BiFunction<Constable, Constable, ConstantDesc> operator) {
            this(size, List.of(source1, source2), (values) ->
                    Stream.of(values[0], values[1]).allMatch(Optional::isPresent)
                            ? Optional.ofNullable(operator.apply(values[0].get(), values[1].get()))
                            : Optional.empty());
        }

        public ConstableValue(int size, List<? extends Constable> sources, Function<Optional<Constable>[], Optional<? extends ConstantDesc>> describer) {
            this(size, null, sources, describer);

            Objects.requireNonNull(sources);
            sources.forEach(Objects::requireNonNull);
            Objects.requireNonNull(describer);
        }

        private ConstableValue(int size, ConstantDesc constantDesc, List<? extends Constable> sources, Function<Optional<Constable>[], Optional<? extends ConstantDesc>> describer) {
            if (size == -1) {
                Objects.requireNonNull(constantDesc);
                size = constantDesc instanceof Long || constantDesc instanceof Double ? 2 : 1;
            }

            this.size = size;
            this.constantDesc = constantDesc;
            this.sources = sources;
            this.describer = describer;
        }

        @Override
        public int getSize() {
            return size;
        }

        public void setAdhocValue(ConstantDesc constantDesc) {
            if (this.constantDesc != null) {
                throw new IllegalStateException();
            }

            this.adhocConstantDesc = constantDesc;
        }

        public void clearAdhocValue() {
            this.adhocConstantDesc = null;
        }

        @Override
        public Optional<? extends ConstantDesc> describeConstable() {
            if (adhocConstantDesc != null) {
                return Optional.of(adhocConstantDesc);
            }

            if (constantDesc != null) {
                return Optional.of(constantDesc);
            }

            if (describer != null) {
                // FIXME: toArray() causes generic typing information erased
                @SuppressWarnings("unchecked")
                ConstantDesc tmp = ((Optional<? extends ConstantDesc>) describer.apply(
                        sources.stream()
                                .map(Constable::describeConstable)
                                // FIXME: a better typing design please...
                                //        create a WrappedConstantDescConstable class?
                                .map(desc -> desc.isEmpty() || desc.get() instanceof Constable
                                        ? desc
                                        : Optional.of(new ConstableValue(desc.get())))
                                .toArray(Optional[]::new)
                )).orElse(null);
                constantDesc = tmp;
                return Optional.ofNullable(constantDesc);
            }

            return Optional.empty();
        }
    }
}
