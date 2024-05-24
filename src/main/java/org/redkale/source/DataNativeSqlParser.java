/*
 *
 */
package org.redkale.source;

import java.util.*;
import java.util.function.IntFunction;
import org.redkale.source.spi.DataNativeSqlParserProvider;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * 原生的sql解析器 <br>
 * 参数变量有三种方式(与Mybatis的占位符类似):  <br>
 * ${xx.xx}: 用于直接拼接sql的变量，不做任何转义， 变量值必需的
 * #{xx.xx}: 用于预编译的sql的参数变量
 * ##{xx.xx}: 用于预编译的sql的参数变量， 变量值必需的
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataNativeSqlParser {

    public DataNativeSqlInfo parse(IntFunction<String> signFunc, String dbType, String rawSql);

    public DataNativeSqlStatement parse(IntFunction<String> signFunc, String dbType,
        String rawSql, boolean countable, Map<String, Object> params);

    public static DataNativeSqlParser loadFirst() {
        if (DataNativeSqlStatement._first_parser != DataNativeSqlStatement.PARSER_NIL) {
            return DataNativeSqlStatement._first_parser;
        }
        Iterator<DataNativeSqlParserProvider> it = ServiceLoader.load(DataNativeSqlParserProvider.class).iterator();
        RedkaleClassLoader.putServiceLoader(DataNativeSqlParserProvider.class);
        while (it.hasNext()) {
            DataNativeSqlParserProvider provider = it.next();
            if (provider != null && provider.acceptsConf(null)) {
                return provider.createInstance();
            }
        }
        DataNativeSqlStatement._first_parser = null;
        return null;
    }

}
