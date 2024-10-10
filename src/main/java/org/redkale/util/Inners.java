/*
 *
 */
package org.redkale.util;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;

/** @author zhangjx */
class Inners {

    private Inners() {}

    static class CreatorInner {

        static final Logger logger = Logger.getLogger(Creator.class.getSimpleName());

        static final Map<Class, Creator> creatorCacheMap = new ConcurrentHashMap<>();

        static final Map<String, Creator> creatorCacheMap2 = new ConcurrentHashMap<>();

        static final Map<Class, IntFunction> arrayCacheMap = new ConcurrentHashMap<>();

        static final IntFunction<String[]> stringFuncArray = x -> new String[x];

        private CreatorInner() {}

        static {
            creatorCacheMap.put(Object.class, p -> new Object());
            creatorCacheMap.put(ArrayList.class, p -> new ArrayList<>());
            creatorCacheMap.put(HashMap.class, p -> new HashMap<>());
            creatorCacheMap.put(HashSet.class, p -> new HashSet<>());
            creatorCacheMap.put(LinkedHashSet.class, p -> new LinkedHashSet<>());
            creatorCacheMap.put(LinkedHashMap.class, p -> new LinkedHashMap<>());
            creatorCacheMap.put(Stream.class, p -> new ArrayList<>().stream());
            creatorCacheMap.put(ConcurrentHashMap.class, p -> new ConcurrentHashMap<>());
            creatorCacheMap.put(CompletableFuture.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(CompletionStage.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(Future.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(AnyValueWriter.class, p -> new AnyValueWriter());
            creatorCacheMap.put(AnyValue.class, p -> new AnyValueWriter());
            creatorCacheMap.put(Map.Entry.class, new Creator<Map.Entry>() {
                @Override
                @org.redkale.annotation.ConstructorParameters({"key", "value"})
                public Map.Entry create(Object... params) {
                    return new AbstractMap.SimpleEntry(params[0], params[1]);
                }

                @Override
                public Class[] paramTypes() {
                    return new Class[] {Object.class, Object.class};
                }
            });
            creatorCacheMap.put(AbstractMap.SimpleEntry.class, new Creator<AbstractMap.SimpleEntry>() {
                @Override
                @org.redkale.annotation.ConstructorParameters({"key", "value"})
                public AbstractMap.SimpleEntry create(Object... params) {
                    return new AbstractMap.SimpleEntry(params[0], params[1]);
                }

                @Override
                public Class[] paramTypes() {
                    return new Class[] {Object.class, Object.class};
                }
            });

            arrayCacheMap.put(int.class, t -> new int[t]);
            arrayCacheMap.put(byte.class, t -> new byte[t]);
            arrayCacheMap.put(long.class, t -> new long[t]);
            arrayCacheMap.put(String.class, t -> new String[t]);
            arrayCacheMap.put(Object.class, t -> new Object[t]);
            arrayCacheMap.put(boolean.class, t -> new boolean[t]);
            arrayCacheMap.put(short.class, t -> new short[t]);
            arrayCacheMap.put(char.class, t -> new char[t]);
            arrayCacheMap.put(float.class, t -> new float[t]);
            arrayCacheMap.put(double.class, t -> new double[t]);
            arrayCacheMap.put(BigInteger.class, t -> new BigInteger[t]);
            arrayCacheMap.put(BigDecimal.class, t -> new BigDecimal[t]);
            arrayCacheMap.put(ByteBuffer.class, t -> new ByteBuffer[t]);
            arrayCacheMap.put(SocketAddress.class, t -> new SocketAddress[t]);
            arrayCacheMap.put(InetSocketAddress.class, t -> new InetSocketAddress[t]);
            arrayCacheMap.put(CompletableFuture.class, t -> new CompletableFuture[t]);
        }

        static class SimpleClassVisitor extends ClassVisitor {

            private final String constructorDesc;

            private final List<String> fieldNames;

            private boolean started;

            public SimpleClassVisitor(int api, List<String> fieldNames, String constructorDesc) {
                super(api);
                this.fieldNames = fieldNames;
                this.constructorDesc = constructorDesc;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String desc, String signature, String[] exceptions) {
                if (java.lang.reflect.Modifier.isStatic(access) || !"<init>".equals(name)) {
                    return null;
                }
                if (constructorDesc != null && !constructorDesc.equals(desc)) {
                    return null;
                }
                if (this.started) {
                    return null;
                }
                this.started = true;
                // 返回的List中参数列表可能会比方法参数量多，因为方法内的临时变量也会存入list中， 所以需要list的元素集合比方法的参数多
                return new MethodVisitor(Opcodes.ASM6) {
                    @Override
                    public void visitLocalVariable(
                            String name, String description, String signature, Label start, Label end, int index) {
                        if (index < 1) {
                            return;
                        }
                        int size = fieldNames.size();
                        // index不会按顺序执行的
                        if (index > size) {
                            for (int i = size; i < index; i++) {
                                fieldNames.add(" ");
                            }
                            fieldNames.set(index - 1, name);
                        }
                        fieldNames.set(index - 1, name);
                    }
                };
            }
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, String constructorDesc) {
            String n = clazz.getName();
            InputStream in = clazz.getResourceAsStream(n.substring(n.lastIndexOf('.') + 1) + ".class");
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] bytes = new byte[1024];
            int pos;
            try {
                while ((pos = in.read(bytes)) != -1) {
                    out.write(bytes, 0, pos);
                }
                in.close();
            } catch (IOException io) {
                return null;
            }
            final List<String> fieldNames = new ArrayList<>();
            new ClassReader(out.toByteArray())
                    .accept(new SimpleClassVisitor(Opcodes.ASM6, fieldNames, constructorDesc), 0);
            while (fieldNames.remove(" ")) {
                // 删掉空元素
            }
            if (fieldNames.isEmpty()) {
                return null;
            }
            if (paramCount == fieldNames.size()) {
                return getConstructorField(clazz, paramCount, fieldNames.toArray(new String[fieldNames.size()]));
            } else {
                String[] fs = new String[paramCount];
                for (int i = 0; i < fs.length; i++) {
                    fs[i] = fieldNames.get(i);
                }
                return getConstructorField(clazz, paramCount, fs);
            }
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, String[] names) {
            AbstractMap.SimpleEntry<String, Class>[] se = new AbstractMap.SimpleEntry[names.length];
            for (int i = 0; i < names.length; i++) { // 查询参数名对应的Field
                try {
                    Field field = clazz.getDeclaredField(names[i]);
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (NoSuchFieldException fe) {
                    Class cz = clazz;
                    Field field = null;
                    while ((cz = cz.getSuperclass()) != Object.class) {
                        try {
                            field = cz.getDeclaredField(names[i]);
                            break;
                        } catch (NoSuchFieldException nsfe) {
                            // do nothing
                        }
                    }
                    if (field == null) {
                        return null;
                    }
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (Exception e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, clazz + " getConstructorField error", e);
                    }
                    return null;
                }
            }
            return se;
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, Parameter[] params) {
            AbstractMap.SimpleEntry<String, Class>[] se = new AbstractMap.SimpleEntry[params.length];
            for (int i = 0; i < params.length; i++) { // 查询参数名对应的Field
                try {
                    Field field = clazz.getDeclaredField(params[i].getName());
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (Exception e) {
                    return null;
                }
            }
            return se;
        }

        public static <T> IntFunction<T[]> createArrayFunction(final Class<T> clazz) {
            if (Utility.inNativeImage()) {
                return t -> (T[]) Array.newInstance(clazz, t);
            }
            final String interName = clazz.getName().replace('.', '/');
            final String interDesc = org.redkale.asm.Type.getDescriptor(clazz);
            final ClassLoader loader = clazz.getClassLoader();
            final String newDynName = "org/redkaledyn/creator/_DynArrayFunction__"
                    + clazz.getName().replace('.', '_').replace('$', '_');
            try {
                Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
                return (IntFunction) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz)
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable ex) {
                // do nothing
            }

            // -------------------------------------------------------------
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            MethodVisitor mv;
            cw.visit(
                    V11,
                    ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                    newDynName,
                    "Ljava/lang/Object;Ljava/util/function/IntFunction<[" + interDesc + ">;",
                    "java/lang/Object",
                    new String[] {"java/util/function/IntFunction"});

            { // IntFunction自身的构造方法
                mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            { // apply 方法
                mv = cw.visitMethod(ACC_PUBLIC, "apply", "(I)[" + interDesc, null, null);
                mv.visitVarInsn(ILOAD, 1);
                mv.visitTypeInsn(ANEWARRAY, interName);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 2);
                mv.visitEnd();
            }
            { // 虚拟 apply 方法
                mv = cw.visitMethod(
                        ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "apply", "(I)Ljava/lang/Object;", null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ILOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "apply", "(I)[" + interDesc, false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            cw.visitEnd();
            final byte[] bytes = cw.toByteArray();
            try {
                Class<?> resultClazz = new ClassLoader(loader) {
                    public final Class<?> loadClass(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }.loadClass(newDynName.replace('/', '.'), bytes);
                RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, resultClazz);
                RedkaleClassLoader.putReflectionDeclaredConstructors(resultClazz, newDynName.replace('/', '.'));
                return (IntFunction<T[]>) resultClazz.getDeclaredConstructor().newInstance();
            } catch (Throwable ex) {
                // ex.printStackTrace();  //一般不会发生, native-image在没有预编译情况下会报错
                return t -> (T[]) Array.newInstance(clazz, t);
            }
        }
    }

    static class CopierInner {

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Copier>> copierOneCaches =
                new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Copier>>>
                copierTwoCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Function>> copierFuncOneCaches =
                new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Function>>>
                copierFuncTwoCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Class, ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Function>>>
                copierFuncListOneCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<
                        Class, ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Function>>>>
                copierFuncListTwoCaches = new ConcurrentHashMap();

        private CopierInner() {}

        public static void clearCopierCache() {
            copierOneCaches.clear();
            copierTwoCaches.clear();
            copierFuncOneCaches.clear();
            copierFuncTwoCaches.clear();
            copierFuncListOneCaches.clear();
            copierFuncListTwoCaches.clear();
        }
    }

    static class InvokerInner {

        static final ConcurrentHashMap<Class, ConcurrentHashMap<Method, Invoker>> invokerCaches =
                new ConcurrentHashMap();

        private InvokerInner() {}
    }
}
