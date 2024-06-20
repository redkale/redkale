/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.redkale.convert.json.JsonConvert;

/**
 * 判断字符串数组是否包含或排除指定字符串的操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SelectColumn implements Predicate<String> {

    private Pattern[] patterns;

    private String[] columns;

    private boolean excludable; // 是否排除

    public SelectColumn() {}

    protected SelectColumn(final String[] columns0, final boolean excludable) {
        this.excludable = excludable;
        final int len = columns0.length;
        if (len < 1) {
            return;
        }
        Pattern[] regxs = null;
        String[] cols = null;
        int regcount = 0;
        int colcount = 0;
        for (String col : columns0) {
            boolean regx = false;
            for (int i = 0; i < col.length(); i++) {
                char ch = col.charAt(i);
                if (ch == '^' || ch == '$' || ch == '*' || ch == '?' || ch == '+' || ch == '[' || ch == '(') {
                    regx = true;
                    break;
                }
            }
            if (regx) {
                if (regxs == null) {
                    regxs = new Pattern[len];
                }
                regxs[regcount++] = Pattern.compile(col);
            } else {
                if (cols == null) {
                    cols = new String[len];
                }
                cols[colcount++] = col;
            }
        }
        if (regxs != null) {
            if (regcount == len) {
                this.patterns = regxs;
            } else {
                this.patterns = Arrays.copyOf(regxs, regcount);
            }
        }
        if (cols != null) {
            if (colcount == len) {
                this.columns = cols;
            } else {
                this.columns = Arrays.copyOf(cols, colcount);
            }
        }
    }

    /**
     * class中的字段名
     *
     * @param columns 包含的字段名集合
     * @return SelectColumn
     */
    //    @Deprecated
    //    public static SelectColumn createIncludes(String... columns) {
    //        return new SelectColumn(columns, false);
    //    }
    //
    //
    /**
     * class中的字段名
     *
     * @param funcs 包含的字段名Lambda集合
     * @param <T> 泛型
     * @return SelectColumn
     */
    public static <T> SelectColumn includes(LambdaFunction<T, ?>... funcs) {
        return includes(LambdaFunction.readColumns(funcs));
    }

    /**
     * class中的字段名
     *
     * @param columns 包含的字段名集合
     * @return SelectColumn
     */
    public static SelectColumn includes(String... columns) {
        return new SelectColumn(columns, false);
    }

    /**
     * class中的字段名
     *
     * @param cols 包含的字段名集合
     * @param columns 包含的字段名集合
     * @return SelectColumn
     */
    //    @Deprecated
    //    public static SelectColumn createIncludes(String[] cols, String... columns) {
    //        return new SelectColumn(Utility.append(cols, columns), false);
    //    }
    //
    //
    /**
     * class中的字段名
     *
     * @param cols 包含的字段名集合
     * @param columns 包含的字段名集合
     * @return SelectColumn
     */
    public static SelectColumn includes(String[] cols, String... columns) {
        return new SelectColumn(Utility.append(cols, columns), false);
    }

    /**
     * class中的字段名
     *
     * @param columns 排除的字段名集合
     * @return SelectColumn
     */
    //    @Deprecated
    //    public static SelectColumn createExcludes(String... columns) {
    //        return new SelectColumn(columns, true);
    //    }
    //
    //
    /**
     * class中的字段名
     *
     * @param funcs 包含的字段名Lambda集合
     * @param <T> 泛型
     * @return SelectColumn
     */
    public static <T> SelectColumn excludes(LambdaFunction<T, ?>... funcs) {
        return excludes(LambdaFunction.readColumns(funcs));
    }

    /**
     * class中的字段名
     *
     * @param columns 排除的字段名集合
     * @return SelectColumn
     */
    public static SelectColumn excludes(String... columns) {
        return new SelectColumn(columns, true);
    }

    /**
     * class中的字段名
     *
     * @param cols 排除的字段名集合
     * @param columns 排除的字段名集合
     * @return SelectColumn
     */
    //    @Deprecated
    //    public static SelectColumn createExcludes(String[] cols, String... columns) {
    //        return new SelectColumn(Utility.append(cols, columns), true);
    //    }
    //
    //
    /**
     * class中的字段名
     *
     * @param cols 排除的字段名集合
     * @param columns 排除的字段名集合
     * @return SelectColumn
     */
    public static SelectColumn excludes(String[] cols, String... columns) {
        return new SelectColumn(Utility.append(cols, columns), true);
    }

    public boolean isOnlyOneColumn() {
        return !excludable && columns != null && columns.length == 1;
    }

    @Override
    public boolean test(final String column) {
        if (this.columns != null) {
            for (String col : this.columns) {
                if (col.equalsIgnoreCase(column)) {
                    return !excludable;
                }
            }
        }
        if (this.patterns != null) {
            for (Pattern reg : this.patterns) {
                if (reg.matcher(column).find()) {
                    return !excludable;
                }
            }
        }
        return excludable;
    }

    public String[] getColumns() {
        return columns;
    }

    public void setColumns(String[] columns) {
        this.columns = columns;
    }

    public boolean isExcludable() {
        return excludable;
    }

    public void setExcludable(boolean excludable) {
        this.excludable = excludable;
    }

    public Pattern[] getPatterns() {
        return patterns;
    }

    public void setPatterns(Pattern[] patterns) {
        this.patterns = patterns;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Arrays.deepHashCode(this.patterns);
        hash = 29 * hash + Arrays.deepHashCode(this.columns);
        hash = 29 * hash + (this.excludable ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SelectColumn other = (SelectColumn) obj;
        if (this.excludable != other.excludable) {
            return false;
        }
        if (!Arrays.deepEquals(this.patterns, other.patterns)) {
            return false;
        }
        return Arrays.deepEquals(this.columns, other.columns);
    }

    @Override
    public String toString() {
        //        StringBuilder sb = new StringBuilder();
        //        sb.append(getClass().getSimpleName()).append("{\"excludable\":").append(excludable);
        //        if (columns != null) {
        //            sb.append(", columns=").append(Arrays.toString(columns));
        //        }
        //        if (patterns != null) {
        //            sb.append(", patterns=").append(Arrays.toString(patterns));
        //        }
        //        return sb.append('}').toString();
        return JsonConvert.root().convertTo(this);
    }
}
