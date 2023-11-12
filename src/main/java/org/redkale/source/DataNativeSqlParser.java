/*
 *
 */
package org.redkale.source;

import java.util.*;
import java.util.function.IntFunction;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * 原生的sql解析器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataNativeSqlParser {

    NativeSqlStatement parse(IntFunction<String> signFunc, String dbtype, String rawSql, Map<String, Object> params);

    public static DataNativeSqlParser loadFirst() {
        if (NativeSqlStatement._first_parser != NativeSqlStatement.PARSER_NIL) {
            return NativeSqlStatement._first_parser;
        }
        Iterator<DataNativeSqlParserProvider> it = ServiceLoader.load(DataNativeSqlParserProvider.class).iterator();
        RedkaleClassLoader.putServiceLoader(DataNativeSqlParserProvider.class);
        while (it.hasNext()) {
            DataNativeSqlParserProvider provider = it.next();
            if (provider != null && provider.acceptsConf(null)) {
                return provider.createInstance();
            }
        }
        NativeSqlStatement._first_parser = null;
        return null;
    }

    public static class NativeSqlStatement {

        private static final DataNativeSqlParser PARSER_NIL = new DataNativeSqlParser() {
            @Override
            public NativeSqlStatement parse(IntFunction<String> signFunc, String dbtype, String rawSql, Map<String, Object> params) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        };

        private static DataNativeSqlParser _first_parser = PARSER_NIL;

        //根据参数值集合重新生成的带?参数可执行的sql
        protected String nativeSql;

        //根据参数值集合重新生成的带?参数可执行的计算总数sql,用于返回Sheet对象
        protected String nativeCountSql;

        //需要预编译的${xxx}参数名, 数量与sql中的?数量一致
        protected List<String> paramNames;

        //需要预编译的jdbc参数名, 数量与sql中的?数量一致
        protected List<String> jdbcNames;

        //jdbc参数值集合, paramNames中的key必然会存在
        protected Map<String, Object> paramValues;

        /**
         * 是否带有参数
         *
         * @return 是否带有参数
         */
        @ConvertDisabled
        public boolean isEmptyNamed() {
            return paramNames == null || paramNames.isEmpty();
        }

        public String getNativeSql() {
            return nativeSql;
        }

        public void setNativeSql(String nativeSql) {
            this.nativeSql = nativeSql;
        }

        public String getNativeCountSql() {
            return nativeCountSql;
        }

        public void setNativeCountSql(String nativeCountSql) {
            this.nativeCountSql = nativeCountSql;
        }

        public List<String> getParamNames() {
            return paramNames;
        }

        public void setParamNames(List<String> paramNames) {
            this.paramNames = paramNames;
        }

        public Map<String, Object> getParamValues() {
            return paramValues;
        }

        public void setParamValues(Map<String, Object> paramValues) {
            this.paramValues = paramValues;
        }

        public List<String> getJdbcNames() {
            return jdbcNames;
        }

        public void setJdbcNames(List<String> jdbcNames) {
            this.jdbcNames = jdbcNames;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
