/*
 *
 */
package org.redkale.source.spi;

import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;

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

    private DataSqlMapperBuilder() {
    }

    public static <T, M extends DataSqlMapper<T>> M createMapper(DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        return null;
    }
}
