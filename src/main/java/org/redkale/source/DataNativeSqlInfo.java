/*
 *
 */
package org.redkale.source;

import java.util.ArrayList;
import java.util.List;
import org.redkale.convert.ConvertDisabled;

/**
 *
 * 原生的sql解析基本信息对象 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class DataNativeSqlInfo {

    //原始sql语句
    protected String rawSql;

    //jdbc版的sql语句, 只有numberSignNames为空时才有值
    protected String jdbcSql;

    //sql类型
    protected SqlMode sqlMode;

    protected final List<String> rootParamNames = new ArrayList<>();

    @ConvertDisabled
    public boolean isDynamic() {
        return jdbcSql == null;
    }

    public String getRawSql() {
        return rawSql;
    }

    public String getJdbcSql() {
        return jdbcSql;
    }

    public SqlMode getSqlMode() {
        return sqlMode;
    }

    public List<String> getRootParamNames() {
        return rootParamNames;
    }

    public enum SqlMode {
        SELECT, INSERT, DELETE, UPDATE, UPSERT, OTHERS;
    }
}
