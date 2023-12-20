/*
 *
 */
package org.redkale.util;

/**
 * 根据参数动态生成key
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public interface CombinedKey {

    public String keyFor(Object... args);

    /**
     * 生成Key, paramTypes与paramNames长度必须一致
     *
     * @param paramTypes 参数类型
     * @param paramNames 参数名
     * @param key        key表达式
     *
     * @return CombinedKey
     */
    public static CombinedKey create(Class[] paramTypes, String[] paramNames, String key) {
        return CombinedKeys.create(paramTypes, paramNames, key);
    }
}
