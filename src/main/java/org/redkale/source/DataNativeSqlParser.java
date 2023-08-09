/*
 *
 */
package org.redkale.source;

import java.util.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;

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

    NativeSqlInfo parse(String nativeSql, Map<String, Object> params);

    public static class NativeSqlInfo {

        //根据参数值集合重新生成的可执行的sql
        protected String nativeSql;

        //需要预编译的参数名
        protected List<String> paramNames;

        //参数值集合
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

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
