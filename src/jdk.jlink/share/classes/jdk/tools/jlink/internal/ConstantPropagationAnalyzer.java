package jdk.tools.jlink.internal;

import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.AbstractInsnNode;
import jdk.internal.org.objectweb.asm.tree.IntInsnNode;
import jdk.internal.org.objectweb.asm.tree.LdcInsnNode;
import jdk.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import jdk.internal.org.objectweb.asm.tree.analysis.BasicValue;
import jdk.internal.org.objectweb.asm.tree.analysis.Interpreter;
import jdk.internal.org.objectweb.asm.tree.analysis.Value;
import jdk.tools.jlink.plugin.ResourcePool;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.util.List;
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

    private class TracableValueInterpreter extends Interpreter<TracableValue> {
        public static final Type NULL_TYPE = Type.getObjectType("null");

        protected TracableValueInterpreter(int api) {
            super(api);
        }

        @Override
        public TracableValue newValue(Type type) {
            throw new UnsupportedOperationException("use specific operation methods");
        }

        @Override
        public TracableValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            switch (insn.getOpcode()) {
                case Opcodes.ACONST_NULL:
                    return new TracableValue(NULL_TYPE, null);
                case Opcodes.ICONST_M1:
                    return new TracableValue(Type.INT_TYPE, Optional.of(-1));
                case Opcodes.ICONST_0:
                    return new TracableValue(Type.INT_TYPE, Optional.of(0));
                case Opcodes.ICONST_1:
                    return new TracableValue(Type.INT_TYPE, Optional.of(1));
                case Opcodes.ICONST_2:
                    return new TracableValue(Type.INT_TYPE, Optional.of(2));
                case Opcodes.ICONST_3:
                    return new TracableValue(Type.INT_TYPE, Optional.of(3));
                case Opcodes.ICONST_4:
                    return new TracableValue(Type.INT_TYPE, Optional.of(4));
                case Opcodes.ICONST_5:
                    return new TracableValue(Type.INT_TYPE, Optional.of(5));
                case Opcodes.LCONST_0:
                    return new TracableValue(Type.INT_TYPE, Optional.of(0L));
                case Opcodes.LCONST_1:
                    return new TracableValue(Type.INT_TYPE, Optional.of(1L));
                case Opcodes.FCONST_0:
                    return new TracableValue(Type.INT_TYPE, Optional.of(0F));
                case Opcodes.FCONST_1:
                    return new TracableValue(Type.INT_TYPE, Optional.of(1F));
                case Opcodes.FCONST_2:
                    return new TracableValue(Type.INT_TYPE, Optional.of(2F));
                case Opcodes.DCONST_0:
                    return new TracableValue(Type.INT_TYPE, Optional.of(0D));
                case Opcodes.DCONST_1:
                    return new TracableValue(Type.INT_TYPE, Optional.of(1D));
                case Opcodes.BIPUSH:
                    return new TracableValue(Type.INT_TYPE, Optional.of((byte) ((IntInsnNode) insn).operand));
                case Opcodes.SIPUSH:
                    return new TracableValue(Type.INT_TYPE, Optional.of((short) ((IntInsnNode) insn).operand));
                case Opcodes.LDC:
                    Object value = ((LdcInsnNode) insn).cst;
                    if (value instanceof Integer integer) {
                        return new TracableValue(Type.INT_TYPE, Optional.of(integer));
                    } else if (value instanceof Float) {
                        return BasicValue.FLOAT_VALUE;
                    } else if (value instanceof Long) {
                        return BasicValue.LONG_VALUE;
                    } else if (value instanceof Double) {
                        return BasicValue.DOUBLE_VALUE;
                    } else if (value instanceof String) {
                        return newValue(Type.getObjectType("java/lang/String"));
                    } else if (value instanceof Type) {
                        int sort = ((Type) value).getSort();
                        if (sort == Type.OBJECT || sort == Type.ARRAY) {
                            return newValue(Type.getObjectType("java/lang/Class"));
                        } else if (sort == Type.METHOD) {
                            return newValue(Type.getObjectType("java/lang/invoke/MethodType"));
                        } else {
                            throw new AnalyzerException(insn, "Illegal LDC value " + value);
                        }
                    } else if (value instanceof Handle) {
                        return newValue(Type.getObjectType("java/lang/invoke/MethodHandle"));
                    } else if (value instanceof ConstantDynamic) {
                        return newValue(Type.getType(((ConstantDynamic) value).getDescriptor()));
                    } else {
                        throw new AnalyzerException(insn, "Illegal LDC value " + value);
                    }

            }
            return null;
        }

        @Override
        public TracableValue copyOperation(AbstractInsnNode insn, TracableValue value) throws AnalyzerException {
            return null;
        }

        @Override
        public TracableValue unaryOperation(AbstractInsnNode insn, TracableValue value) throws AnalyzerException {
            return null;
        }

        @Override
        public TracableValue binaryOperation(AbstractInsnNode insn, TracableValue value1, TracableValue value2) throws AnalyzerException {
            return null;
        }

        @Override
        public TracableValue ternaryOperation(AbstractInsnNode insn, TracableValue value1, TracableValue value2, TracableValue value3) throws AnalyzerException {
            return null;
        }

        @Override
        public TracableValue naryOperation(AbstractInsnNode insn, List<? extends TracableValue> values) throws AnalyzerException {
            return null;
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, TracableValue value, TracableValue expected) throws AnalyzerException {

        }

        @Override
        public TracableValue merge(TracableValue value1, TracableValue value2) {
            return null;
        }
    }

    private static class TracableValue implements Value {
        public final Type type;
        public final Optional<Object> constant; // FIXME: we currently use Optional to distinguish between null and
                                                //        empty. Is this considered an anti-pattern?

        public final List<TracableValue> sources;

        public TracableValue(final Type type) {
            this(type, Optional.empty(), List.of());
        }

        public TracableValue(final Type type, Optional<Object> constant) {
            this(type, constant, List.of());
        }

        private TracableValue(final Type type, Optional<Object> constant, final List<TracableValue> sources) {
            // TODO: how to inference type from sources?
            this.type = type;
            this.constant = constant;
            this.sources = sources;
        }

        @Override
        public int getSize() {
            return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
        }
    }
}
