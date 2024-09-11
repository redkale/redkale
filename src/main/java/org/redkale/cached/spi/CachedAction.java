/*
 *
 */
package org.redkale.cached.spi;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.cached.CachedManager;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.Environment;
import org.redkale.util.MultiHashKey;
import org.redkale.util.ThrowSupplier;
import org.redkale.util.TypeToken;

/**
 * 缓存的方法对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@ClassDepends
public class CachedAction {

    @Resource
    private Environment environment;

    private CachedManager manager;

    private final CachedEntry cached;

    private final Method method;

    // Supplier对象的类型
    private final Type resultType;

    // 缓存方法是否异步
    private final boolean async;

    // 是否可以缓存null
    private final boolean nullable;

    // 宿主对象的类
    private final Class serviceClass;

    // 无法获取动态的Method，只能存方法名
    private final String methodName;

    // 获取动态的字段名
    private final String fieldName;

    // 方法参数名
    @Nullable
    private final String[] paramNames;

    // 模板key
    final String templetKey;

    // 缓存key生成器
    private CachedKeyGenerator keyGenerator;

    // 父对象
    private Object service;

    // 本地缓存过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
    private Duration localExpire;

    // 远程缓存过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
    private Duration remoteExpire;

    CachedAction(CachedEntry cached, Method method, Class serviceClass, String[] paramNames, String fieldName) {
        this.cached = cached;
        this.method = method;
        this.nullable = cached.isNullable();
        this.serviceClass = Objects.requireNonNull(serviceClass);
        this.paramNames = paramNames;
        this.methodName = method.getName();
        this.fieldName = Objects.requireNonNull(fieldName);
        this.templetKey = cached.getTempletKey();
        Type returnType = method.getGenericReturnType();
        this.async = CompletableFuture.class.isAssignableFrom(TypeToken.typeToClass(returnType));
        this.resultType = this.async ? ((ParameterizedType) returnType).getActualTypeArguments()[0] : returnType;
    }

    String init(ResourceFactory resourceFactory, Object service) {
        this.manager = resourceFactory.load(cached.getManager(), CachedManager.class);
        final String key = environment.getPropertyValue(cached.getTempletKey());
        if (key.startsWith("@")) { // 动态加载缓存key生成器
            String generatorName = key.substring(1);
            this.keyGenerator = resourceFactory.findChild(generatorName, CachedKeyGenerator.class);
        } else {
            MultiHashKey dynKey = MultiHashKey.create(paramNames, key);
            this.keyGenerator = CachedKeyGenerator.create(dynKey);
        }
        this.localExpire = createDuration(cached.getLocalExpire());
        this.remoteExpire = createDuration(cached.getRemoteExpire());
        ((CachedActionFunc) this.manager).addAction(this);
        return key;
    }

    @ClassDepends
    public <T> T get(ThrowSupplier<T> supplier, Object... args) {
        if (async) {
            return (T) manager.bothGetSetAsync(
                    keyGenerator.generate(service, this, args),
                    resultType,
                    nullable,
                    localExpire,
                    remoteExpire,
                    (ThrowSupplier) supplier);
        } else {
            return manager.bothGetSet(
                    keyGenerator.generate(service, this, args),
                    resultType,
                    nullable,
                    localExpire,
                    remoteExpire,
                    supplier);
        }
    }

    private Duration createDuration(String val) {
        String str = environment.getPropertyValue(val);
        if ("-1".equals(str) || "null".equalsIgnoreCase(str)) {
            return null;
        } else if ("0".equals(str)) {
            return Duration.ZERO;
        } else if (str.indexOf('*') > -1) {
            long rs = 1;
            for (String v : str.split("\\*")) {
                if (!v.trim().isEmpty()) {
                    rs *= Long.parseLong(v.trim());
                }
            }
            if (rs < 0) {
                return null;
            } else if (rs == 0) {
                return Duration.ZERO;
            } else {
                return Duration.ofMillis(cached.getTimeUnit().toMillis(rs));
            }
        } else {
            return Duration.ofMillis(cached.getTimeUnit().toMillis(Long.parseLong(str)));
        }
    }

    public CachedEntry getCached() {
        return cached;
    }

    public Method getMethod() {
        return method;
    }

    public String getTempletKey() {
        return templetKey;
    }

    public Duration getLocalExpire() {
        return localExpire;
    }

    public void setLocalExpire(Duration localExpire) {
        this.localExpire = localExpire;
    }

    public Duration getRemoteExpire() {
        return remoteExpire;
    }

    public void setRemoteExpire(Duration remoteExpire) {
        this.remoteExpire = remoteExpire;
    }

    @Override
    public String toString() {
        return "{"
                + "\"serviceClass\":" + serviceClass.getName()
                + ",\"methodName\":\"" + methodName + "\""
                + ",\"fieldName\":\"" + fieldName + "\""
                + ",\"paramTypes\":" + JsonConvert.root().convertTo(method.getParameterTypes())
                + ",\"paramNames\":" + JsonConvert.root().convertTo(paramNames)
                + ",\"templetKey\":\"" + templetKey + "\""
                + ",\"resultType\":\"" + resultType + "\""
                + ",\"cache\":" + cached
                + "}";
    }
}
