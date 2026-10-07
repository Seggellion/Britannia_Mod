import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

/** Disposable measurement only. Not installed in either release artifact. */
public final class BakeTimingAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(Module module, ClassLoader loader, String name,
                    Class<?> redefined, ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraft/client/resources/model/ModelBakery".equals(name)) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                        if (!"bakeModels".equals(method) || !descriptor.endsWith(")V")) return delegate;
                        return new AdviceAdapter(Opcodes.ASM9, delegate, access, method, descriptor) {
                            private int start;
                            @Override protected void onMethodEnter() {
                                invokeStatic(Type.getType(System.class), new org.objectweb.asm.commons.Method("nanoTime", "()J"));
                                start = newLocal(Type.LONG_TYPE);
                                storeLocal(start);
                            }
                            @Override protected void onMethodExit(int opcode) {
                                if (opcode == ATHROW) return;
                                getStatic(Type.getType(System.class), "out", Type.getType(java.io.PrintStream.class));
                                newInstance(Type.getType(StringBuilder.class)); dup();
                                invokeConstructor(Type.getType(StringBuilder.class), new org.objectweb.asm.commons.Method("<init>", "()V"));
                                push("FENCE_TIMING_MODEL_BAKE_MILLIS=");
                                invokeVirtual(Type.getType(StringBuilder.class), new org.objectweb.asm.commons.Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
                                invokeStatic(Type.getType(System.class), new org.objectweb.asm.commons.Method("nanoTime", "()J"));
                                loadLocal(start); math(SUB, Type.LONG_TYPE); cast(Type.LONG_TYPE, Type.DOUBLE_TYPE);
                                push(1000000.0); math(DIV, Type.DOUBLE_TYPE);
                                invokeVirtual(Type.getType(StringBuilder.class), new org.objectweb.asm.commons.Method("append", "(D)Ljava/lang/StringBuilder;"));
                                invokeVirtual(Type.getType(StringBuilder.class), new org.objectweb.asm.commons.Method("toString", "()Ljava/lang/String;"));
                                invokeVirtual(Type.getType(java.io.PrintStream.class), new org.objectweb.asm.commons.Method("println", "(Ljava/lang/String;)V"));
                            }
                        };
                    }
                }, ClassReader.EXPAND_FRAMES);
                System.out.println("FENCE_TIMING_MODEL_BAKE_INSTRUMENTED=" + name);
                return writer.toByteArray();
            }
        });
    }
}
