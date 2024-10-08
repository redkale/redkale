/*

*/

package org.redkale.util;

/**
 * 读取yml的解析器
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.util.YamlReader
 * @since 2.8.0
 * @author zhangjx
 */
public interface YamlProvider {

    /**
     *
     * 创建 YamlLoader
     *
     * @return  YamlLoader
     */
    public YamlLoader createLoader();

    public interface YamlLoader {

        /**
         * 将yml内容转换成AnyValue
         *
         * @param content yml内容
         * @return  AnyValue
         */
        public AnyValue read(String content);
    }
}
