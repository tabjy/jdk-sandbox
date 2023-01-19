package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.FieldInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.MethodInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.Interpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import jdk.tools.jlink.plugin.ResourcePool;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class ConstantPropagationAnalyzer {

    private final Map<String, Function<Optional<? extends ConstantDesc>[], Optional<? extends ConstantDesc>>> emulations = new HashMap<>();

    public ConstantPropagationAnalyzer() {
        // add type inductions for well-known methods
        // by no means comprehensive, just so we have something to test with
        this.registerMethodEmulation(
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> ClassDesc.of((String) desc))
        );

        this.registerMethodEmulation(
                "java/lang/Class",
                "getName()",
                "()Ljava/lang/String;",
                (receiver, args) -> receiver
//                        .filter(desc -> desc instanceof ClassDesc)
                        .map(clazz -> Type.getType(((ClassDesc) clazz).descriptorString()).getClassName())
        );

        this.registerMethodEmulation(
                "java/lang/Integer",
                "parseInt",
                "(Ljava/lang/String;)I",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> Integer.parseInt((String) desc))
        );

        this.registerMethodEmulation(
                "java/lang/Float",
                "parseFloat",
                "(Ljava/lang/String;)F",
                (args) -> args[0]
//                        .filter(desc -> desc instanceof String)
                        .map(desc -> Float.parseFloat((String) desc))
        );

        this.registerMethodEmulation(
                "java/lang/String",
                "<init>",
                "(Ljava/lang/String)V",
                (args) -> args[0] // returns the created string (same as the input argument)
//                        .filter(desc -> desc instanceof String)
        );
    }

    public void analyze(ResourcePool resources) {
    }

//    public void addStaticFieldTarget(String owner, String name) {
//    }
//
//    public void addReturnTarget(String owner, String method, String descriptor) {
//    }
//
//    public void addLocalVariableTarget(String owner, String method, String descriptor, int index) {
//    }

    public void registerMethodEmulation(String owner, String method, String descriptor,
                                        BiFunction<Optional<? extends ConstantDesc>, Optional<? extends ConstantDesc>[], Optional<ConstantDesc>> function) {
        // TODO: check owner is not an interface

        // TODO: check if the method is declared on a final class, a sealed class with no permitted child, or an
        //       effectively final class
        emulations.put(owner + "." + method + descriptor, args -> function.apply(args[0], Arrays.copyOfRange(args, 1, args.length)));
    }

    public void registerMethodEmulation(String owner, String method, String descriptor, Function<Optional<? extends ConstantDesc>[], Optional<? extends ConstantDesc>> function) {
        emulations.put(owner + "." + method + descriptor, function);
    }

    public interface StaticMethodTypeDescription {
        Optional<ConstantDesc> describe(ConstantDesc... args);
    }

    private class TracableValueInterpreter extends Interpreter<ConstableValue> {
//        public static final Type NULL_TYPE = Type.getObjectType("null");
//        public static final Type STRING_TYPE = Type.getObjectType("java/lang/String");
//        public static final Type CLASS_TYPE = Type.getObjectType("java/lang/Class");
//        public static final Type METHOD_TYPE = Type.getObjectType("java/lang/invoke/MethodType");
//        public static final Type METHOD_HANDLE_TYPE = Type.getObjectType("java/lang/invoke/MethodHandle");

        public static final ClassDesc NULL_CONSTANT_DESC = ClassDesc.of("null");

        protected TracableValueInterpreter(int api) {
            super(api);
        }

        @Override
        public ConstableValue newValue(Type type) {
            throw new UnsupportedOperationException("use specific operation methods");
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
                    // FIXME: might as well use ConstantDesc al-together?
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

            // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE, MULTIANEWARRAY and INVOKEDYNAMIC
            if (insn.getOpcode() == Opcodes.MULTIANEWARRAY) {
                // TODO: handle multidimensional arrays
                return null;
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
                    Optional<? extends ConstantDesc>[] constantDescs =
                            Arrays.stream(optionals).map(optional -> optional.flatMap(Constable::describeConstable))
                                    .toArray(Optional[]::new);
                    return emulations.get(key).apply(constantDescs);
                });
            }

            throw new AssertionError();
        }

//        @Override
//        public void returnOperation(AbstractInsnNode insn, TraceableValue value, TraceableValue expected) throws AnalyzerException {
//
//        }
//
//        @Override
//        public TraceableValue merge(TraceableValue value1, TraceableValue value2) {
//            return null;
//        }
    }

    private static class ConstableValue implements Value, Constable {
        private final int size;
        private ConstantDesc constantDesc;
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

        @Override
        public Optional<? extends ConstantDesc> describeConstable() {
            if (constantDesc != null) {
                return Optional.of(constantDesc);
            }

            if (describer != null) {
                // FIXME: toArray() causes generic typing information erased
                constantDesc = ((Optional<? extends ConstantDesc>) describer.apply(
                        sources.stream().map(Constable::describeConstable).toArray(Optional[]::new)
                )).orElse(null);
                return Optional.ofNullable(constantDesc);
            }

            return Optional.empty();
        }
    }
}
