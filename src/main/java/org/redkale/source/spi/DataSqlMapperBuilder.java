/*
 *
 */
package org.redkale.source.spi;

import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;

/**
 *
 * @author zhangjx
 */
public final class DataSqlMapperBuilder {

    public static <T, M extends DataSqlMapper<T>> M createMapper(DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        return null;
    }
}
