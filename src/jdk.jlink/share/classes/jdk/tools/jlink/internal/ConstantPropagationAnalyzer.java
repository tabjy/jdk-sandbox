package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ConstantPropagationAnalyzer {

    public ConstantPropagationAnalyzer() {
        // add type inductions for well-known methods
        this.registerStaticMethodTypeDescription(
                "java/lang/Class",
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                (args) -> {
                    if (args[0] instanceof String) {
                        return Optional.of(ClassDesc.of((String) args[0]));
                    }
                    return Optional.empty();
                });

        this.registerMethodTypeDescription(
                "java/lang/Class",
                "getName()",
                "()Ljava/lang/String;",
                (receiver, args) -> {
                    if (receiver instanceof ClassDesc classDesc) {
                        return Optional.of(Type.getType(classDesc.descriptorString()).getClassName());
                    }
                    return Optional.empty();
                });

        this.registerStaticMethodTypeDescription(
                "java/lang/Integer",
                "parseInt",
                "(Ljava/lang/String;)I", (args) -> {
                    if (args[0] instanceof String) {
                        return Optional.of(Integer.parseInt((String) args[0]));
                    }
                    return Optional.empty();
                }
        );

        this.registerStaticMethodTypeDescription(
                "java/lang/Float",
                "parseFloat",
                "(Ljava/lang/String;)F", (args) -> {
                    if (args[0] instanceof String) {
                        return Optional.of(Float.parseFloat((String) args[0]));
                    }
                    return Optional.empty();
                }
        );
    }

    public void analyze(ResourcePool resources) {
    }

    public void addStaticFieldTarget(String owner, String name) {
    }

    public void addInvocationArgumentTarget(String owner, String method, String descriptor, int index) {
    }

    public void addInvocationReturnTarget(String owner, String method, String descriptor) {
    }

    public void addLocalVariableTarget(String owner, String method, String descriptor, int index) {
    }

    public void registerMethodTypeDescription(String owner, String method, String descriptor, MethodTypeDescription induction) {
    }

    public void registerStaticMethodTypeDescription(String owner, String method, String descriptor, StaticMethodTypeDescription induction) {
    }

    public interface MethodTypeDescription {
        Optional<ConstantDesc> describe(ConstantDesc receiver, ConstantDesc... args);
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
            switch (insn.getOpcode()) {
                case Opcodes.ACONST_NULL -> {
                    return new ConstableValue(NULL_CONSTANT_DESC);
                }
                case Opcodes.ICONST_M1 -> {
                    return new ConstableValue((Constable) (-1));
                }
                case Opcodes.ICONST_0 -> {
                    return new ConstableValue((Constable) 0);
                }
                case Opcodes.ICONST_1 -> {
                    return new ConstableValue((Constable) 1);
                }
                case Opcodes.ICONST_2 -> {
                    return new ConstableValue((Constable) 2);
                }
                case Opcodes.ICONST_3 -> {
                    return new ConstableValue((Constable) 3);
                }
                case Opcodes.ICONST_4 -> {
                    return new ConstableValue((Constable) 4);
                }
                case Opcodes.ICONST_5 -> {
                    return new ConstableValue((Constable) 5);
                }
                case Opcodes.LCONST_0 -> {
                    return new ConstableValue((Constable) 0L);
                }
                case Opcodes.LCONST_1 -> {
                    return new ConstableValue((Constable) 1L);
                }
                case Opcodes.FCONST_0 -> {
                    return new ConstableValue((Constable) 0F);
                }
                case Opcodes.FCONST_1 -> {
                    return new ConstableValue((Constable) 1F);
                }
                case Opcodes.FCONST_2 -> {
                    return new ConstableValue((Constable) 2F);
                }
                case Opcodes.DCONST_0 -> {
                    return new ConstableValue((Constable) 0D);
                }
                case Opcodes.DCONST_1 -> {
                    return new ConstableValue((Constable) 1D);
                }
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> {
                    return new ConstableValue((Constable) ((IntInsnNode) insn).operand);
                }
                case Opcodes.LDC -> {
                    Object value = ((LdcInsnNode) insn).cst;
                    if (value instanceof Integer i) {
                        return new ConstableValue((Constable) i);
                    } else if (value instanceof Float f) {
                        return new ConstableValue((Constable) f);
                    } else if (value instanceof Long l) {
                        return new ConstableValue((Constable) l);
                    } else if (value instanceof Double d) {
                        return new ConstableValue((Constable) d);
                    } else if (value instanceof String s) {
                        return new ConstableValue((Constable) s);
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
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                }
            }

            throw new AssertionError();
        }

        @Override
        public ConstableValue copyOperation(AbstractInsnNode insn, ConstableValue value) throws AnalyzerException {
            return new ConstableValue(value);
        }

        @Override
        public ConstableValue unaryOperation(AbstractInsnNode insn, ConstableValue value) throws AnalyzerException {
            switch (insn.getOpcode()) {
                // int results
                case Opcodes.INEG -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (int) i * -1));
                }
                case Opcodes.IINC -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (int) i + 1));
                }
                case Opcodes.L2I -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((l) -> (int) (long) l));
                }
                case Opcodes.F2I -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((f) -> (int) (float) f));
                }
                case Opcodes.D2I -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((d) -> (int) (double) d));
                }
                case Opcodes.I2B -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (int) (byte) (int) i));
                }
                case Opcodes.I2C -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (int) (char) (int) i));
                }
                case Opcodes.I2S -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (int) (short) (int) i));
                }
                // float results
                case Opcodes.FNEG -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((f) -> (float) f * -1));
                }
                case Opcodes.I2F -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (float) (int) i));
                }
                case Opcodes.L2F -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (float) (long) i));
                }
                case Opcodes.D2F -> {
                    return new ConstableValue(List.of(value), (values) -> values[0].map((i) -> (float) (double) i));
                }
                // TODO
            }

            return null;
        }

//        @Override
//        public TraceableValue binaryOperation(AbstractInsnNode insn, TraceableValue value1, TraceableValue value2) throws AnalyzerException {
//            return null;
//        }
//
//        @Override
//        public TraceableValue ternaryOperation(AbstractInsnNode insn, TraceableValue value1, TraceableValue value2, TraceableValue value3) throws AnalyzerException {
//            return null;
//        }
//
//        @Override
//        public TraceableValue naryOperation(AbstractInsnNode insn, List<? extends TraceableValue> values) throws AnalyzerException {
//            return null;
//        }
//
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
        private final List<Constable> sources;
        private final ConstableValueDescriber describer;

        public ConstableValue(int size) {
            this(size, null, List.of(), null);
        }

        public ConstableValue(ConstantDesc constantDesc) {
            this(-1, constantDesc, List.of(), null);

            Objects.requireNonNull(constantDesc);
        }

        public ConstableValue(Constable source) {
            this(List.of(source), (values) -> {
                if (values[0].isEmpty()) {
                    return Optional.empty();
                }
                return values[0].get().describeConstable();
            });
        }

        public ConstableValue(List<Constable> sources, ConstableValueDescriber describer) {
            this(-1, null, sources, describer);

            Objects.requireNonNull(sources);
            sources.forEach(Objects::requireNonNull);
            Objects.requireNonNull(describer);
        }

        private ConstableValue(int size, ConstantDesc constantDesc, List<Constable> sources, ConstableValueDescriber describer) {
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
                constantDesc = ((Optional<? extends ConstantDesc>) describer.describe(
                        sources.stream().map(Constable::describeConstable).toArray(Optional[]::new)
                )).orElse(null);
                return Optional.ofNullable(constantDesc);
            }

            return Optional.empty();
        }
    }

    private interface ConstableValueDescriber {
        Optional<? extends ConstantDesc> describe(Optional<Constable>... sources);
    }
}
