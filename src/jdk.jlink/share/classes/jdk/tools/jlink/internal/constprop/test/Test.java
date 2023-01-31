package jdk.tools.jlink.internal.constprop.test;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
import jdk.tools.jlink.internal.constprop.ConstantizationTarget;
import jdk.tools.jlink.internal.constprop.ConstantizationTargetFactory;

import java.io.InputStream;
import java.util.List;

public class Test {
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

        public static void test4() throws Exception {
            test(999);
        }
    }

    public static void main(String[] args) throws Exception {
        String path = Type.getType(TestTarget.class).getInternalName();
        InputStream is = Test.class.getClassLoader().getResourceAsStream(path + ".class");
        byte[] bytes = is.readAllBytes();
        is.close();

        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.EXPAND_FRAMES);
        MethodNode mn = cn.methods.stream()
                .filter(method -> method.name.equals("test") && method.desc.equals("(I)V"))
                .findFirst()
                .orElseThrow();

        List<List<ConstantizationTarget>> descriptors = ConstantizationTargetFactory.createStackValueTarget(
                Type.getType(Class.class).getInternalName(),
                "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                mn);

        descriptors = ConstantizationTargetFactory.createStackValueTarget(
                Type.getType(String.class).getInternalName(),
                "length",
                "()I",
                mn);

        System.out.println("Done");
    }
}
