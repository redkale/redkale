/*
 *
 */
package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ACC_SUPER;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ARETURN;
import static org.redkale.asm.Opcodes.ASTORE;
import static org.redkale.asm.Opcodes.DUP;
import static org.redkale.asm.Opcodes.GETFIELD;
import static org.redkale.asm.Opcodes.INVOKEINTERFACE;
import static org.redkale.asm.Opcodes.INVOKESPECIAL;
import static org.redkale.asm.Opcodes.INVOKEVIRTUAL;
import static org.redkale.asm.Opcodes.NEW;
import static org.redkale.asm.Opcodes.POP;
import static org.redkale.asm.Opcodes.RETURN;
import static org.redkale.asm.Opcodes.V11;
import org.redkale.asm.Type;
import org.redkale.persistence.Sql;
import org.redkale.source.AbstractDataSqlSource;
import org.redkale.source.DataNativeSqlInfo;
import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;
import org.redkale.source.SourceException;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * DataSqlMapper工厂类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public final class DataSqlMapperBuilder {

    private static Map<String, AsmMethodBean> baseMethodBeans;

    private DataSqlMapperBuilder() {
    }

    public static <T, M extends DataSqlMapper<T>> M createMapper(DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        if (!mapperType.isInterface()) {
            throw new SourceException(mapperType + " is not interface");
        }
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Class entityType = entityType(mapperType);
        final String supDynName = mapperType.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/source/mapper/_DynDataSqlMapper__" + supDynName.replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            {
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (ClassNotFoundException e) {
            //do nothing
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (baseMethodBeans == null) {
            baseMethodBeans = AsmMethodBoost.getMethodBeans(DataSqlMapper.class);
        }
        List<Item> items = new ArrayList<>();
        Map<String, AsmMethodBean> selfMethodBeans = AsmMethodBoost.getMethodBeans(mapperType);
        for (Method method : mapperType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if ("dataSource".equals(method.getName()) && method.getParameterCount() == 0) {
                continue;
            }
            if ("entityType".equals(method.getName()) && method.getParameterCount() == 0) {
                continue;
            }
            Sql sql = method.getAnnotation(Sql.class);
            if (sql == null) {
                if (Modifier.isAbstract(method.getModifiers())) {
                    throw new SourceException(method + " require @" + Sql.class.getSimpleName());
                }
                continue;
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new SourceException(method + " is not abstract, but contains @" + Sql.class.getSimpleName());
            }
            IntFunction<String> signFunc = null;
            if (source instanceof AbstractDataSqlSource) {
                signFunc = ((AbstractDataSqlSource) source).getSignFunc();
            }
            DataNativeSqlInfo sqlInfo = nativeSqlParser.parse(signFunc, source.getType(), sql.value());
            AsmMethodBean methodBean = selfMethodBeans.get(AsmMethodBoost.getMethodBeanKey(method));
            if (!Utility.equalsElement(sqlInfo.getRootParamNames(), methodBean.fieldNameList())) {
                throw new SourceException(method + " parameters not match @" + Sql.class.getSimpleName() + "(" + sql.value() + ")");
            }
            items.add(new Item(method, sqlInfo, methodBean));
        }
        //------------------------------------------------------------------------------

        final String entityDesc = Type.getDescriptor(entityType);
        final String sqlSourceDesc = Type.getDescriptor(DataSqlSource.class);

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, "java/lang/Object", new String[]{supDynName});
        {
            fv = cw.visitField(ACC_PRIVATE, "_source", sqlSourceDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_type", "Ljava/lang/Class;", null, null);
            fv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "dataSource", "()Lorg/redkale/source/DataSqlSource;", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, "org/redkale/test/source/parser/DynForumInfoMapperImpl", "source", sqlSourceDesc);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "entityType", "()Ljava/lang/Class;", "()Ljava/lang/Class<" + entityDesc + ">;", null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "type", "Ljava/lang/Class;");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        //sql系列方法
        for (Item item : items) {
            Method method = item.method;
            DataNativeSqlInfo sqlInfo = item.sqlInfo;
            AsmMethodBean methodBean = item.methodBean;
            Sql sql = method.getAnnotation(Sql.class);

            mv = cw.visitMethod(ACC_PUBLIC, "queryForumResultAsync", "(Lorg/redkale/test/source/parser/ForumBean;)Ljava/util/concurrent/CompletableFuture;", "(Lorg/redkale/test/source/parser/ForumBean;)Ljava/util/concurrent/CompletableFuture<Ljava/util/List<Lorg/redkale/test/source/parser/ForumResult;>;>;", null);
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLdcInsn(sql.value());
            mv.visitVarInsn(ASTORE, 2);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitTypeInsn(NEW, "java/util/HashMap");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            mv.visitVarInsn(ASTORE, 3);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitLdcInsn("bean");
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitInsn(POP);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/test/source/parser/DynForumInfoMapperImpl", "dataSource", "()Lorg/redkale/source/DataSqlSource;", false);
            mv.visitLdcInsn(Type.getType("Lorg/redkale/test/source/parser/ForumResult;"));
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEINTERFACE, "org/redkale/source/DataSqlSource", "nativeQueryListAsync", "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;", true);
            mv.visitInsn(ARETURN);
            Label l4 = new Label();
            mv.visitLabel(l4);
            mv.visitLocalVariable("this", "Lorg/redkale/test/source/parser/DynForumInfoMapperImpl;", null, l0, l4, 0);
            mv.visitLocalVariable("bean", "Lorg/redkale/test/source/parser/ForumBean;", null, l0, l4, 1);
            mv.visitLocalVariable("sql", "Ljava/lang/String;", null, l1, l4, 2);
            mv.visitLocalVariable("params", "Ljava/util/Map;", "Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;", l2, l4, 3);
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionPublicConstructors(newClazz, newDynName.replace('/', '.'));
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            {
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (Exception ex) {
            throw new SourceException(ex);
        }
    }

    private static Class entityType(Class mapperType) {
        for (java.lang.reflect.Type t : mapperType.getGenericInterfaces()) {
            if (DataSqlMapper.class.isAssignableFrom(TypeToken.typeToClass(t))) {
                return TypeToken.typeToClass(((ParameterizedType) t).getActualTypeArguments()[0]);
            }
        }
        throw new SourceException("Not found entity class from " + mapperType.getName());
    }

    private static class Item {

        public Method method;

        public DataNativeSqlInfo sqlInfo;

        public AsmMethodBean methodBean;

        public Item(Method method, DataNativeSqlInfo sqlInfo, AsmMethodBean methodBean) {
            this.method = method;
            this.sqlInfo = sqlInfo;
            this.methodBean = methodBean;
        }

    }
}
