/*
 *
 */
package org.redkale.source.spi;

import static java.lang.classfile.ClassFile.*;
import static org.redkale.source.DataNativeSqlInfo.SqlMode.SELECT;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import org.redkale.annotation.Param;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeMethodParam;
import org.redkale.convert.json.JsonObject;
import org.redkale.persistence.Entity;
import org.redkale.persistence.Sql;
import org.redkale.source.AbstractDataSqlSource;
import org.redkale.source.DataNativeSqlInfo;
import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;
import org.redkale.source.EntityBuilder;
import org.redkale.source.RowBound;
import org.redkale.source.SourceException;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Sheet;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * DataSqlMapper工厂类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class DataSqlMapperBuilder {

    private DataSqlMapperBuilder() {}

    public static <T, M extends DataSqlMapper<T>> M createMapper(
            DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        if (!mapperType.isInterface()) {
            throw new SourceException(mapperType + " is not interface");
        }
        final RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        final Class entityType = entityType(mapperType);
        final String supDynName = mapperType.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/source/mapper/_DynDataSqlMapper_"
                + mapperType.getName().replace('.', '_').replace('$', '_');
        try {
            Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            { // DataSqlSource
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            { // Entity Class
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (ClassNotFoundException e) {
            // do nothing
        } catch (Throwable t) {
            t.printStackTrace();
        }
        EntityBuilder.load(entityType);
        List<Item> items = new ArrayList<>();
        Map<String, CodeMethodBean> selfMethodBeans = CodeMethodBoost.getMethodBeans(mapperType);
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
                    throw new SourceException(mapperType.getSimpleName() + "." + method.getName() + " require @"
                            + Sql.class.getSimpleName());
                }
                continue;
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new SourceException(mapperType.getSimpleName() + "." + method.getName()
                        + " is not abstract, but contains @" + Sql.class.getSimpleName());
            }
            if (method.getExceptionTypes().length > 0) {
                throw new SourceException(
                        "@" + Sql.class.getSimpleName() + " cannot on throw-exception method, but " + method);
            }
            IntFunction<String> signFunc = null;
            if (source instanceof AbstractDataSqlSource) {
                signFunc = ((AbstractDataSqlSource) source).getSignFunc();
            }
            DataNativeSqlInfo sqlInfo = nativeSqlParser.parse(signFunc, source.getType(), sql.value());
            CodeMethodBean methodBean = selfMethodBeans.get(CodeMethodBoost.getMethodBeanKey(method));
            List<String> fieldNames = methodBean.paramNameList(method);
            Class resultClass = resultClass(method);
            int roundIndex = -1;
            if (resultClass.isAssignableFrom(Sheet.class)) {
                Class[] pts = method.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    if (RowBound.class.isAssignableFrom(pts[i])) {
                        roundIndex = i;
                        break;
                    }
                }
                if (roundIndex < 0) {
                    throw new SourceException(
                            mapperType.getSimpleName() + "." + method.getName() + " need RowBound type parameter on @"
                                    + Sql.class.getSimpleName() + "(" + sql.value() + ")");
                }
                fieldNames.remove(roundIndex);
            }
            if (!Utility.equalsElement(sqlInfo.getRootParamNames(), fieldNames)) {
                throw new SourceException(mapperType.getSimpleName() + "." + method.getName()
                        + " parameters not match, fieldNames = " + fieldNames + ", sqlParams = "
                        + sqlInfo.getRootParamNames() + ", methodBean = " + methodBean);
            }
            if (sqlInfo.getSqlMode() != SELECT) { // 非SELECT语句只能返回int或void
                if (resultClass != Integer.class && resultClass != int.class) {
                    throw new SourceException("Update SQL must on return int method, but " + method);
                }
            }
            items.add(new Item(method, sqlInfo, methodBean, roundIndex));
        }
        // ------------------------------------------------------------------------------

        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String sheetDesc = ByteCodes.descriptor(Sheet.class);
        final String roundDesc = ByteCodes.descriptor(RowBound.class);
        final String entityDesc = ByteCodes.descriptor(entityType);
        final String sqlSourceName = DataSqlSource.class.getName().replace('.', '/');
        final String sqlSourceDesc = ByteCodes.descriptor(DataSqlSource.class);

        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc("java/lang/Object"));

            cw.withInterfaceSymbols(Arrays.stream(new String[] {supDynName})
                    .map(ByteCodes::classDesc)
                    .toList());
            {
                cw.withField("_source", ClassDesc.ofDescriptor(sqlSourceDesc), ACC_PRIVATE);
            }
            {
                cw.withField("_type", ClassDesc.ofDescriptor("Ljava/lang/Class;"), ACC_PRIVATE);
            }
            {
                cw.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.invokespecial(
                            ByteCodes.classDesc("java/lang/Object"),
                            "<init>",
                            MethodTypeDesc.ofDescriptor("()V"),
                            false);
                    mv.return_();
                });
            }
            {
                cw.withMethodBody("dataSource", MethodTypeDesc.ofDescriptor("()" + sqlSourceDesc), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.getfield(ByteCodes.classDesc(newDynName), "_source", ClassDesc.ofDescriptor(sqlSourceDesc));
                    mv.areturn();
                });
            }
            {
                cw.withMethod("entityType", MethodTypeDesc.ofDescriptor("()Ljava/lang/Class;"), ACC_PUBLIC, mb -> {
                    mb.with(SignatureAttribute.of(
                            MethodSignature.parseFrom("()Ljava/lang/Class<" + entityDesc + ">;")));

                    mb.withCode(mv -> {
                        mv.aload(0);
                        mv.getfield(
                                ByteCodes.classDesc(newDynName), "_type", ClassDesc.ofDescriptor("Ljava/lang/Class;"));
                        mv.areturn();
                    });
                });
            }

            // sql系列方法
            // int nativeUpdate(String sql)
            // CompletableFuture<Integer> nativeUpdateAsync(String sql)
            // int nativeUpdate(String sql, Map<String, Object> params)
            // CompletableFuture<Integer> nativeUpdateAsync(String sql, Map<String, Object> params)
            //
            // V nativeQueryOne(Class<V> type, String sql)
            // CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql)
            // V nativeQueryOne(Class<V> type, String sql, Map<String, Object> params)
            // CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql, Map<String, Object> params)
            //
            // Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params)
            // CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql,
            // Map<String,
            // Object> params)
            //
            // nativeQueryOne、nativeQueryList、nativeQuerySheet
            for (Item item : items) {
                Method method = item.method;
                DataNativeSqlInfo sqlInfo = item.sqlInfo;
                CodeMethodBean methodBean = item.methodBean;
                int roundIndex = item.roundIndex;
                Sql sql = method.getAnnotation(Sql.class);
                Class resultClass = resultClass(method);
                Class[] componentTypes = resultComponentType(method);
                final boolean async = method.getReturnType().isAssignableFrom(CompletableFuture.class);
                Parameter[] params = method.getParameters();
                Class[] paramTypes = method.getParameterTypes();
                List<CodeMethodParam> methodParams = methodBean.getParams();
                List<Integer> insns = new ArrayList<>();
                if (!EntityBuilder.isSimpleType(componentTypes[0])) {
                    EntityBuilder.load(componentTypes[0]);
                }

                cw.withMethod(method.getName(), MethodTypeDesc.ofDescriptor(methodBean.getDesc()), ACC_PUBLIC, mb -> {
                    if (methodBean.getSignature() != null)
                        mb.with(SignatureAttribute.of(MethodSignature.parseFrom(methodBean.getSignature())));

                    mb.withCode(mv -> {
                        Label l0 = mv.newLabel();
                        mv.labelBinding(l0);
                        mv.aload(0);
                        mv.invokevirtual(
                                ByteCodes.classDesc(newDynName),
                                "dataSource",
                                MethodTypeDesc.ofDescriptor("()" + sqlSourceDesc));
                        if (sqlInfo.getSqlMode() == SELECT) {
                            // 参数：结果类
                            mv.loadConstant(ByteCodes.constantType(ByteCodes.descriptor(componentTypes[0])));
                            if (resultClass.isAssignableFrom(Map.class)) {
                                mv.loadConstant(ByteCodes.constantType(ByteCodes.descriptor(componentTypes[1])));
                            }
                        }
                        // 参数：sql
                        mv.loadConstant(sql.value());
                        int parameterSlot = 1;
                        for (Class<?> parameterType : paramTypes) {
                            insns.add(parameterSlot);
                            parameterSlot += TypeKind.fromDescriptor(parameterType.descriptorString())
                                    .slotSize();
                        }
                        if (roundIndex >= 0) {
                            mv.aload(insns.get(roundIndex));
                        }
                        // 参数: params
                        mv.loadConstant(paramTypes.length * 2 - (roundIndex >= 0 ? 2 : 0));
                        mv.anewarray(ByteCodes.classDesc("java/lang/Object"));
                        int arrayIndex = 0;
                        for (int i = 0; i < paramTypes.length; i++) {
                            if (i != roundIndex) {
                                Class<?> pt = paramTypes[i];
                                Param p = params[i].getAnnotation(Param.class);
                                String k = p == null ? methodParams.get(i).getName() : p.value();
                                mv.dup()
                                        .loadConstant(arrayIndex++)
                                        .loadConstant(k)
                                        .aastore();
                                mv.dup().loadConstant(arrayIndex++);
                                mv.loadLocal(TypeKind.fromDescriptor(pt.descriptorString()), insns.get(i));
                                ByteCodes.visitPrimitiveValueOf(mv, pt);
                                mv.aastore();
                            }
                        }

                        mv.invokestatic(
                                ByteCodes.classDesc(utilClassName),
                                "ofMap",
                                MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)Ljava/util/HashMap;"),
                                false);

                        // One:   "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/lang/Object;"
                        // Map:   "(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/Map;"
                        // List:  "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;"
                        // Sheet:
                        // "(Ljava/lang/Class;Ljava/lang/String;Lorg/redkale/source/RowRound;Ljava/util/Map;)Lorg/redkale/util/Sheet;"
                        // Async:
                        // "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;"
                        if (sqlInfo.getSqlMode() == SELECT) {
                            String queryMethodName = "nativeQueryOne";
                            String queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                                    + (async ? "Ljava/util/concurrent/CompletableFuture;" : "Ljava/lang/Object;");
                            boolean oneMode = !async;
                            if (resultClass.isAssignableFrom(Map.class)) {
                                oneMode = false;
                                queryMethodName = "nativeQueryMap";
                                queryMethodDesc =
                                        "(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                                                + (async
                                                        ? "Ljava/util/concurrent/CompletableFuture;"
                                                        : "Ljava/util/Map;");
                            } else if (resultClass.isAssignableFrom(List.class)) {
                                oneMode = false;
                                queryMethodName = "nativeQueryList";
                                queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                                        + (async ? "Ljava/util/concurrent/CompletableFuture;" : "Ljava/util/List;");
                            } else if (resultClass.isAssignableFrom(Sheet.class)) {
                                oneMode = false;
                                queryMethodName = "nativeQuerySheet";
                                queryMethodDesc =
                                        "(Ljava/lang/Class;Ljava/lang/String;" + roundDesc + "Ljava/util/Map;)"
                                                + (async ? "Ljava/util/concurrent/CompletableFuture;" : sheetDesc);
                            }
                            mv.invokeinterface(
                                    ByteCodes.classDesc(sqlSourceName),
                                    queryMethodName + (async ? "Async" : ""),
                                    MethodTypeDesc.ofDescriptor(queryMethodDesc));
                            if (oneMode) {
                                mv.checkcast(ByteCodes.classDesc(
                                        componentTypes[0].getName().replace('.', '/')));
                            }
                            mv.areturn();
                        } else {
                            String updateMethodName = "nativeUpdate" + (async ? "Async" : "");
                            String updateMethodDesc = "(Ljava/lang/String;Ljava/util/Map;)"
                                    + (async ? "Ljava/util/concurrent/CompletableFuture;" : "I");
                            mv.invokeinterface(
                                    ByteCodes.classDesc(sqlSourceName),
                                    updateMethodName,
                                    MethodTypeDesc.ofDescriptor(updateMethodDesc));
                            if (resultClass == int.class) {
                                mv.ireturn();
                            } else if (!async && resultClass == Integer.class) {
                                mv.invokestatic(
                                        ByteCodes.classDesc("java/lang/Integer"),
                                        "valueOf",
                                        MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Integer;"),
                                        false);
                                mv.areturn();
                            } else if (resultClass == void.class) {
                                mv.pop();
                                mv.return_();
                            } else {
                                mv.areturn();
                            }
                        }
                        Label l2 = mv.newLabel();
                        mv.labelBinding(l2);
                        mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), l0, l2);
                        for (int i = 0; i < paramTypes.length; i++) {
                            CodeMethodParam param = methodParams.get(i);
                            mv.localVariable(
                                    insns.get(i),
                                    param.getName(),
                                    ClassDesc.ofDescriptor(param.description(paramTypes[i])),
                                    l0,
                                    l2);
                            if (param.signature(paramTypes[i]) != null)
                                mv.localVariableType(
                                        insns.get(i),
                                        param.getName(),
                                        Signature.parseFrom(param.signature(paramTypes[i])),
                                        l0,
                                        l2);
                        }
                    });
                });
            }
        });

        byte[] bytes = classBytes;
        Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
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
                Class<?> entityClass = TypeToken.typeToClass(((ParameterizedType) t).getActualTypeArguments()[0]);
                if (entityClass.getAnnotation(Entity.class) == null) {
                    throw new SourceException(
                            "Entity Class " + entityClass.getName() + " must be on Annotation @Entity");
                }
                return entityClass;
            }
        }
        throw new SourceException("Not found entity class from " + mapperType.getName());
    }

    private static Class resultClass(Method method) {
        Class type = method.getReturnType();
        if (type.isAssignableFrom(CompletableFuture.class)) {
            ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
            return TypeToken.typeToClass(pt.getActualTypeArguments()[0]);
        }
        return type;
    }

    private static Class[] resultComponentType(Method method) {
        if (method.getReturnType().isAssignableFrom(CompletableFuture.class)) {
            ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
            return resultComponentType(pt.getActualTypeArguments()[0]);
        }
        return resultComponentType(method.getGenericReturnType());
    }

    private static Class[] resultComponentType(java.lang.reflect.Type type) {
        Class clzz = TypeToken.typeToClass(type);
        if (clzz.isAssignableFrom(Map.class)) {
            if (type instanceof ParameterizedType) {
                java.lang.reflect.Type[] ts = ((ParameterizedType) type).getActualTypeArguments();
                return new Class[] {TypeToken.typeToClass(ts[0]), TypeToken.typeToClass(ts[1])};
            } else {
                return new Class[] {String.class, JsonObject.class};
            }
        } else if (clzz.isAssignableFrom(List.class)) {
            if (type instanceof ParameterizedType) {
                clzz = TypeToken.typeToClass(((ParameterizedType) type).getActualTypeArguments()[0]);
            } else {
                clzz = JsonObject.class;
            }
        } else if (clzz.isAssignableFrom(Sheet.class)) {
            if (type instanceof ParameterizedType) {
                clzz = TypeToken.typeToClass(((ParameterizedType) type).getActualTypeArguments()[0]);
            } else {
                clzz = JsonObject.class;
            }
        }
        return new Class[] {clzz};
    }

    private static class Item {

        public Method method;

        public DataNativeSqlInfo sqlInfo;

        public CodeMethodBean methodBean;

        public int roundIndex = -1;

        public Item(Method method, DataNativeSqlInfo sqlInfo, CodeMethodBean methodBean, int roundIndex) {
            this.method = method;
            this.sqlInfo = sqlInfo;
            this.methodBean = methodBean;
            this.roundIndex = roundIndex;
        }
    }
}
