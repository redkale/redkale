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
public interface MultiHashKey {

    public String keyFor(Object... args);

    /**
     * key只支持带#{}的表达式， 且不能嵌套,错误示例:name_#{key_#{id}}
     *
     * @param paramNames 参数名
     * @param key        key表达式
     *
     * @return MultiHashKey
     */
    public static MultiHashKey create(String[] paramNames, String key) {
        return MultiHashKeys.create(paramNames, key);
    }
}
