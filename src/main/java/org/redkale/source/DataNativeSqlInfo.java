/*
 *
 */
package org.redkale.source;

import java.util.ArrayList;
import java.util.List;
import org.redkale.annotation.Nullable;
import org.redkale.convert.ConvertDisabled;

/**
 * 原生的sql解析基本信息对象 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class DataNativeSqlInfo {

    // 原始sql语句
    protected String rawSql;

    // 不包含${}且参数为:arg0xx格式的jdbc版sql语句, 只有dollarNames为空时才有值
    @Nullable
    protected String templetSql;

    // 包含IN表达式的参数, 例如: name IN #{names}、status IN (1,2,#{status.normal})
    protected boolean containsInExpr;

    // sql类型
    protected SqlMode sqlMode;

    // 根参数名， 如bean.userid、bean.username的根参数名为: bean
    protected final List<String> rootParamNames = new ArrayList<>();

    @ConvertDisabled
    public boolean isDynamic() {
        return templetSql == null || containsInExpr;
    }

    public boolean isContainsInExpr() {
        return containsInExpr;
    }

    public String getRawSql() {
        return rawSql;
    }

    public String getTempletSql() {
        return templetSql;
    }

    public SqlMode getSqlMode() {
        return sqlMode;
    }

    public List<String> getRootParamNames() {
        return rootParamNames;
    }

    public enum SqlMode {
        SELECT,
        INSERT,
        DELETE,
        UPDATE,
        OTHERS;
    }
}
