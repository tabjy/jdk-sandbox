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
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private class TracableValueInterpreter extends Interpreter<TraceableValue> {
        public static final Type NULL_TYPE = Type.getObjectType("null");
        public static final Type STRING_TYPE = Type.getObjectType("java/lang/String");
        public static final Type CLASS_TYPE = Type.getObjectType("java/lang/Class");
        public static final Type METHOD_TYPE = Type.getObjectType("java/lang/invoke/MethodType");
        public static final Type METHOD_HANDLE_TYPE = Type.getObjectType("java/lang/invoke/MethodHandle");

        protected TracableValueInterpreter(int api) {
            super(api);
        }

        @Override
        public TraceableValue newValue(Type type) {
            throw new UnsupportedOperationException("use specific operation methods");
        }

        @Override
        public TraceableValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            switch (insn.getOpcode()) {
                case Opcodes.ACONST_NULL -> {
                    return new TraceableValue(NULL_TYPE, null);
                }
                case Opcodes.ICONST_M1 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(-1));
                }
                case Opcodes.ICONST_0 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(0));
                }
                case Opcodes.ICONST_1 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(1));
                }
                case Opcodes.ICONST_2 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(2));
                }
                case Opcodes.ICONST_3 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(3));
                }
                case Opcodes.ICONST_4 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(4));
                }
                case Opcodes.ICONST_5 -> {
                    return new TraceableValue(Type.INT_TYPE, Optional.of(5));
                }
                case Opcodes.LCONST_0 -> {
                    return new TraceableValue(Type.LONG_TYPE, Optional.of(0L));
                }
                case Opcodes.LCONST_1 -> {
                    return new TraceableValue(Type.LONG_TYPE, Optional.of(1L));
                }
                case Opcodes.FCONST_0 -> {
                    return new TraceableValue(Type.FLOAT_TYPE, Optional.of(0F));
                }
                case Opcodes.FCONST_1 -> {
                    return new TraceableValue(Type.FLOAT_TYPE, Optional.of(1F));
                }
                case Opcodes.FCONST_2 -> {
                    return new TraceableValue(Type.FLOAT_TYPE, Optional.of(2F));
                }
                case Opcodes.DCONST_0 -> {
                    return new TraceableValue(Type.DOUBLE_TYPE, Optional.of(0D));
                }
                case Opcodes.DCONST_1 -> {
                    return new TraceableValue(Type.DOUBLE_TYPE, Optional.of(1D));
                }
                case Opcodes.BIPUSH -> {
                    return new TraceableValue(Type.BYTE_TYPE, Optional.of((byte) ((IntInsnNode) insn).operand));
                }
                case Opcodes.SIPUSH -> {
                    return new TraceableValue(Type.SHORT_TYPE, Optional.of((short) ((IntInsnNode) insn).operand));
                }
                case Opcodes.LDC -> {
                    Object value = ((LdcInsnNode) insn).cst;
                    if (value instanceof Integer i) {
                        return new TraceableValue(Type.INT_TYPE, Optional.of(i));
                    } else if (value instanceof Float f) {
                        return new TraceableValue(Type.FLOAT_TYPE, Optional.of(f));
                    } else if (value instanceof Long l) {
                        return new TraceableValue(Type.LONG_TYPE, Optional.of(l));
                    } else if (value instanceof Double d) {
                        return new TraceableValue(Type.DOUBLE_TYPE, Optional.of(d));
                    } else if (value instanceof String s) {
                        return new TraceableValue(STRING_TYPE, Optional.of(s));
                    } else if (value instanceof Type t) {
                        int sort = t.getSort();
                        // FIXME: might as well use ConstantDesc al-together?
                        if (sort == Type.OBJECT || sort == Type.ARRAY) {
                            return new TraceableValue(CLASS_TYPE, Optional.of(ClassDesc.ofDescriptor(t.getDescriptor())));
                        } else if (sort == Type.METHOD) {
                            // TODO: verify this is correct
                            return new TraceableValue(METHOD_TYPE, Optional.of(MethodTypeDesc.ofDescriptor(t.getDescriptor())));
                        } else {
                            throw new AnalyzerException(insn, "Illegal LDC value " + value);
                        }
                    } else if (value instanceof Handle h) {
                        // TODO: is this remotely relevant to the purpose of constant propagation?
                        DirectMethodHandleDesc.Kind kind = switch (((Handle) value).getTag()) {
                            case Opcodes.H_GETFIELD -> DirectMethodHandleDesc.Kind.GETTER;
                            case Opcodes.H_GETSTATIC -> DirectMethodHandleDesc.Kind.STATIC_GETTER;
                            case Opcodes.H_PUTFIELD -> DirectMethodHandleDesc.Kind.SETTER;
                            case Opcodes.H_PUTSTATIC -> DirectMethodHandleDesc.Kind.STATIC_SETTER;
                            case Opcodes.H_INVOKEVIRTUAL -> DirectMethodHandleDesc.Kind.VIRTUAL;
                            case Opcodes.H_INVOKESTATIC -> DirectMethodHandleDesc.Kind.STATIC;
                            case Opcodes.H_INVOKESPECIAL -> DirectMethodHandleDesc.Kind.SPECIAL;
                            case Opcodes.H_NEWINVOKESPECIAL -> DirectMethodHandleDesc.Kind.CONSTRUCTOR;
                            case Opcodes.H_INVOKEINTERFACE -> DirectMethodHandleDesc.Kind.INTERFACE_VIRTUAL;
                            default ->
                                    throw new AnalyzerException(insn, "Unexpected value: " + ((Handle) value).getTag());
                        };

                        return new TraceableValue(METHOD_HANDLE_TYPE, Optional.of(MethodHandleDesc.ofMethod(
                                kind,
                                ClassDesc.ofInternalName(h.getOwner()),
                                h.getName(),
                                MethodTypeDesc.ofDescriptor(h.getDesc())
                        )));
                    } else if (value instanceof ConstantDynamic) {
                        return newValue(Type.getType(((ConstantDynamic) value).getDescriptor()));
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }
                }
            }

            throw new AssertionError();
        }

        @Override
        public TraceableValue copyOperation(AbstractInsnNode insn, TraceableValue value) throws AnalyzerException {
            return value;
        }

        @Override
        public TraceableValue unaryOperation(AbstractInsnNode insn, TraceableValue value) throws AnalyzerException {
            switch (insn.getOpcode()) {
                case Opcodes.INEG -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> -1 * (Integer) values[0]);
                }
                case Opcodes.IINC -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> (Integer) values[0] + 1);
                }
                case Opcodes.L2I -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Long) values[0]).intValue());
                }
                case Opcodes.F2I -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Float) values[0]).intValue());
                }
                case Opcodes.D2I -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Double) values[0]).intValue());
                }
                case Opcodes.I2B -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Integer) values[0]).byteValue());
                }
                case Opcodes.I2C -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> (char) ((Integer) values[0]).intValue());
                }
                case Opcodes.I2S -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Integer) values[0]).shortValue());
                }
                case Opcodes.FNEG -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> -1 * (Float) values[0]);
                }
                case Opcodes.I2F -> {
                    return new TraceableValue(Type.INT_TYPE, List.of(value), (values) -> ((Integer) values[0]).floatValue());
                }
            }

            return null;
        }

        @Override
        public TraceableValue binaryOperation(AbstractInsnNode insn, TraceableValue value1, TraceableValue value2) throws AnalyzerException {
            return null;
        }

        @Override
        public TraceableValue ternaryOperation(AbstractInsnNode insn, TraceableValue value1, TraceableValue value2, TraceableValue value3) throws AnalyzerException {
            return null;
        }

        @Override
        public TraceableValue naryOperation(AbstractInsnNode insn, List<? extends TraceableValue> values) throws AnalyzerException {
            return null;
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, TraceableValue value, TraceableValue expected) throws AnalyzerException {

        }

        @Override
        public TraceableValue merge(TraceableValue value1, TraceableValue value2) {
            return null;
        }
    }

    private static class TraceableValue implements Value {
        private final Type type;
        private Optional<Object> constant; // FIXME: we currently use Optional to distinguish between null and empty. Is
                                           //        this considered an anti-pattern?
                                           //        We need something like ConstantDesc, but it is too sealed for
                                           //        extension.

        private final List<TraceableValue> sources;
        private final TraceableValueFunction function;

        public TraceableValue(final Type type) {
            this(type, Optional.empty(), List.of(), null);
        }

        public TraceableValue(final Type type, Optional<Object> constant) {
            this(type, constant, List.of(), null);
            Objects.requireNonNull(constant);
        }

        public TraceableValue(final Type type, List<TraceableValue> sources, TraceableValueFunction function) {
            this(type, Optional.empty(), sources, function);
            Objects.requireNonNull(sources);
            Objects.requireNonNull(function);

            computeFromSource();
        }

        private TraceableValue(final Type type, Optional<Object> constant, List<TraceableValue> sources, TraceableValueFunction function) {
            Objects.requireNonNull(type);

            // TODO: how to inference type from sources?
            this.type = type;
            this.constant = constant;
            this.sources = sources;
            this.function = function;
        }

        @Override
        public int getSize() {
            return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
        }

        private boolean computeFromSource() {
            if (function == null) {
                throw new IllegalStateException("Cannot compute from source without a function");
            }

            if (!sources.stream().map(TraceableValue::getConstant).allMatch(Optional::isPresent)) {
                return false;
            }

            constant = Optional.ofNullable(function.apply(sources.stream().map(TraceableValue::getConstant).map(Optional::get)
                    .toArray()));
            return true;
        }

        public Optional<Object> getConstant() {
            if (constant.isPresent()) {
                return constant;
            }

            if (computeFromSource()) {
                return constant;
            }

            return Optional.empty();
        }
    }

    private interface TraceableValueFunction {
        Object apply(Object... sources);
    }
}
