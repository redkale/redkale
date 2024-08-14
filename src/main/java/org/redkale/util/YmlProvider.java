/*

*/

package org.redkale.util;

/**
 * 读取yml的解析器
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.8.0
 * @author zhangjx
 */
public interface YmlProvider {

    public static final YmlProvider NIL = c -> {
        throw new UnsupportedOperationException("Not supported yet.");
    };

    /**
     * 将yml内容转换成AnyValue
     * @param content yml内容
     * @return  AnyValue
     */
    public AnyValue read(String content);
}
