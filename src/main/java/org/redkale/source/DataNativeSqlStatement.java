/*
 *
 */
package org.redkale.source;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.redkale.annotation.Nullable;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;

/**
 * 原生的sql解析对象 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class DataNativeSqlStatement {

    static final DataNativeSqlParser PARSER_NIL = new DataNativeSqlParser() {
        @Override
        public DataNativeSqlInfo parse(IntFunction<String> signFunc, String dbType, String rawSql) {
            throw new UnsupportedOperationException("No available instances found");
        }

        @Override
        public DataNativeSqlStatement parse(
                IntFunction<String> signFunc,
                String dbType,
                String rawSql,
                boolean countable,
                RowBound round,
                Map<String, Object> params) {
            throw new UnsupportedOperationException("No available instances found");
        }
    };

    static DataNativeSqlParser _first_parser = PARSER_NIL;

    // 根据参数值集合重新生成的带?参数可执行的sql
    protected String nativeSql;

    // 根据参数值集合重新生成的带?参数可执行的sql, 用于翻页查询
    protected String nativePageSql;

    // 根据参数值集合重新生成的带?参数可执行的计算总数sql,用于返回Sheet对象
    @Nullable
    protected String nativeCountSql;

    // 需要预编译的##{xxx}、#{xxx}参数名, 数量与sql中的?数量一致
    protected List<String> paramNames;

    // 需要预编译的jdbc参数名, 数量与sql中的?数量一致
    protected List<String> jdbcNames;

    // jdbc参数值集合, paramNames中的key必然会存在
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

    public String getNativePageSql() {
        return nativePageSql == null ? nativeSql : nativePageSql;
    }

    public void setNativePageSql(String nativePageSql) {
        this.nativePageSql = nativePageSql;
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
