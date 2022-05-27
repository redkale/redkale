/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.math.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import javax.annotation.Resource;
import static org.redkale.boot.Application.*;
import org.redkale.net.AsyncGroup;
import org.redkale.service.*;
import org.redkale.source.EntityInfo.EntityColumn;
import org.redkale.util.*;

/**
 * DataSource的SQL抽象实现类 <br>
 * 注意: 所有的操作只能作用在一张表上，不能同时变更多张表
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public abstract class DataSqlSource extends AbstractDataSource implements Function<Class, EntityInfo> {

    protected static final Flipper FLIPPER_ONE = new Flipper(1);

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected String name;

    protected URL persistFile;

    protected boolean cacheForbidden;

    protected String dbtype;

    private boolean autoddl;

    protected Properties readConfProps;

    protected Properties writeConfProps;

    @Resource(name = RESNAME_APP_ASYNCGROUP)
    protected AsyncGroup asyncGroup;

    @Resource(name = RESNAME_APP_EXECUTOR)
    protected ExecutorService workExecutor;

    protected BiFunction<EntityInfo, Object, CharSequence> sqlFormatter;

    protected BiConsumer futureCompleteConsumer = (r, t) -> {
        if (t != null) logger.log(Level.INFO, "CompletableFuture complete error", (Throwable) t);
    };

    protected final BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader = (s, i)
        -> ((CompletableFuture<Sheet>) querySheetDB(i, false, false, false, null, null, (FilterNode) null)).thenApply(e -> e == null ? new ArrayList() : e.list(true));

    //用于反向LIKE使用
    protected String containSQL;

    //用于反向LIKE使用
    protected String notContainSQL;

    //用于判断表不存在的使用, 多个SQLState用;隔开
    protected String tableNotExistSqlstates;

    //用于复制表结构使用
    protected String tablecopySQL;

    public DataSqlSource() {
    }

    @Override
    public void init(AnyValue conf) {
        if (conf.getAnyValue("read") == null) { //没有读写分离
            Properties rwConf = new Properties();
            conf.forEach((k, v) -> rwConf.put(k, v));
            this.dbtype = parseDbtype(rwConf.getProperty(DATA_SOURCE_URL));
            decryptProperties(rwConf);
            initProperties(rwConf);
            this.readConfProps = rwConf;
            this.writeConfProps = rwConf;
        } else { //读写分离
            Properties readConf = new Properties();
            Properties writeConf = new Properties();
            conf.getAnyValue("read").forEach((k, v) -> readConf.put(k, v));
            conf.getAnyValue("write").forEach((k, v) -> writeConf.put(k, v));
            this.dbtype = parseDbtype(readConf.getProperty(DATA_SOURCE_URL));
            decryptProperties(readConf);
            decryptProperties(writeConf);
            initProperties(readConf);
            initProperties(writeConf);
            this.readConfProps = readConf;
            this.writeConfProps = writeConf;
        }
        this.name = conf.getValue("name", "");
        this.sqlFormatter = (info, val) -> formatValueToString(info, val);
        this.autoddl = "true".equals(readConfProps.getProperty(DATA_SOURCE_TABLE_AUTODDL, "false").trim());

        this.containSQL = readConfProps.getProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "LOCATE(${keystr}, ${column}) > 0");
        this.notContainSQL = readConfProps.getProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "LOCATE(${keystr}, ${column}) = 0");

        this.tableNotExistSqlstates = ";" + readConfProps.getProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42000;42S02") + ";";
        this.tablecopySQL = readConfProps.getProperty(DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE IF NOT EXISTS ${newtable} LIKE ${oldtable}");
        this.cacheForbidden = "NONE".equalsIgnoreCase(readConfProps.getProperty(DATA_SOURCE_CACHEMODE));
    }

    //解密可能存在的加密字段, 可重载
    protected void decryptProperties(Properties props) {

    }

    protected void initProperties(Properties props) {
        if ("oracle".equals(this.dbtype)) {
            props.setProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) > 0");
            props.setProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) = 0");
            if (!props.containsKey(DATA_SOURCE_TABLENOTEXIST_SQLSTATES)) {
                props.setProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42000;42S02");
            }
            if (!props.containsKey(DATA_SOURCE_TABLECOPY_SQLTEMPLATE)) {
                //注意：此语句复制表结构会导致默认值和主键信息的丢失
                props.setProperty(DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE IF NOT EXISTS ${newtable} AS SELECT * FROM ${oldtable} WHERE 1=2");
            }
        } else if ("sqlserver".equals(this.dbtype)) {
            props.setProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) > 0");
            props.setProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) = 0");
        } else if ("postgresql".equals(this.dbtype)) {
            if (!props.containsKey(DATA_SOURCE_TABLECOPY_SQLTEMPLATE)) { //注意：此语句复制表结构会导致默认值和主键信息的丢失
                //注意：postgresql不支持跨库复制表结构
                //props.setProperty(DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE ${newtable} AS (SELECT * FROM ${oldtable} LIMIT 0)");
                props.setProperty(DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE IF NOT EXISTS ${newtable} (LIKE ${oldtable} INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING COMMENTS INCLUDING INDEXES)");
            }
            if (!props.containsKey(DATA_SOURCE_TABLENOTEXIST_SQLSTATES)) {
                props.setProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42P01;3F000");
            }
        }
    }

    @Override
    public String toString() {
        if (readConfProps == null) return getClass().getSimpleName() + "{}"; //compileMode模式下会为null
        if (readConfProps == writeConfProps) {
            return getClass().getSimpleName() + "{url=" + readConfProps.getProperty(DATA_SOURCE_URL) + "}";
        } else {
            return getClass().getSimpleName() + "{readurl=" + readConfProps.getProperty(DATA_SOURCE_URL) + ",writeurl=" + writeConfProps.getProperty(DATA_SOURCE_URL) + "}";
        }
    }

    //生成创建表的SQL
    protected <T> String[] createTableSqls(EntityInfo<T> info) {
        if (info == null || !autoddl) return null;
        javax.persistence.Table table = info.getType().getAnnotation(javax.persistence.Table.class);
        if ("mysql".equals(dbtype())) {  //mysql
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS `").append(info.getOriginTable()).append("`(\n");
            EntityColumn primary = null;
            T one = info.constructorAttributes == null ? info.getCreator().create() : null;
            for (EntityColumn column : info.getDDLColumns()) {
                if (column.primary) primary = column;
                String sqltype = "VARCHAR(" + column.length + ")";
                String sqlnull = column.primary ? "NOT NULL" : "NULL";
                if (column.type == boolean.class || column.type == Boolean.class) {
                    sqltype = "TINYINT(1)";
                    Boolean val = one == null ? null : (Boolean) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val != null && val ? 1 : 0);
                } else if (column.type == byte.class || column.type == Byte.class) {
                    sqltype = "TINYINT";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.byteValue());
                } else if (column.type == short.class || column.type == Short.class) {
                    sqltype = "SMALLINT";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == char.class || column.type == Character.class) {
                    sqltype = "SMALLINT UNSIGNED";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.intValue());
                } else if (column.type == int.class || column.type == Integer.class || column.type == AtomicInteger.class) {
                    sqltype = "INT";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == long.class || column.type == Long.class || column.type == AtomicLong.class || column.type == LongAdder.class) {
                    sqltype = "BIGINT";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == float.class || column.type == Float.class) {
                    sqltype = "FLOAT";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == double.class || column.type == Double.class) {
                    sqltype = "DOUBLE";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == BigInteger.class) {
                    sqltype = "DECIMAL";
                    if (column.precision > 0) {
                        sqltype += "(" + column.precision + "," + column.scale + ")";
                    } else {
                        sqltype += "(19,2)";
                    }
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == BigDecimal.class) {
                    sqltype = "DECIMAL";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == String.class) {
                    if (column.length < 65535) {
                        String val = one == null ? null : (String) info.getAttribute(column.field).get(one);
                        if (val != null) {
                            sqlnull = "NOT NULL DEFAULT '" + val.replace('\'', '"') + "'";
                        } else if (column.primary) {
                            sqlnull = "NOT NULL DEFAULT ''";
                        }
                    } else if (column.length == 65535) {
                        sqltype = "TEXT";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    } else if (column.length <= 16777215) {
                        sqltype = "MEDIUMTEXT";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    } else {
                        sqltype = "LONGTEXT";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    }
                } else if (column.type == byte[].class) {
                    if (column.length <= 65535) {
                        sqltype = "BLOB";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    } else if (column.length <= 16777215) {
                        sqltype = "MEDIUMBLOB";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    } else {
                        sqltype = "LONGBLOB";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    }
                } else if (column.type == java.time.LocalDate.class || column.type == java.util.Date.class || "java.sql.Date".equals(column.type.getName())) {
                    sqltype = "DATE";
                } else if (column.type == java.time.LocalTime.class || "java.sql.Time".equals(column.type.getName())) {
                    sqltype = "TIME";
                } else if (column.type == java.time.LocalDateTime.class || "java.sql.Timestamp".equals(column.type.getName())) {
                    sqltype = "DATETIME";
                } else { //JavaBean
                    sqltype = column.length >= 65535 ? "TEXT" : ("VARCHAR(" + column.length + ")");
                    sqlnull = !column.nullable ? "NOT NULL DEFAULT ''" : "NULL";
                }
                sb.append("   `").append(column.column).append("` ").append(sqltype).append(" ").append(sqlnull);
                if (column.comment != null && !column.comment.isEmpty()) {
                    sb.append(" COMMENT '").append(column.comment.replace('\'', '"')).append("'");
                }
                sb.append(",\n");
            }
            sb.append("   PRIMARY KEY (`").append(primary.column).append("`)\n");
            sb.append(")ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4");
            if (table != null && !table.comment().isEmpty()) {
                sb.append(" COMMENT '").append(table.comment().replace('\'', '"')).append("'");
            }
            return Utility.ofArray(sb.toString());
        } else if ("postgresql".equals(dbtype())) {  //postgresql
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS ").append(info.getOriginTable()).append("(\n");
            EntityColumn primary = null;
            T one = info.constructorAttributes == null ? info.getCreator().create() : null;
            for (EntityColumn column : info.getDDLColumns()) {
                if (column.primary) primary = column;
                String sqltype = "VARCHAR(" + column.length + ")";
                String sqlnull = column.primary ? "NOT NULL" : "NULL";
                if (column.type == boolean.class || column.type == Boolean.class) {
                    sqltype = "BOOL";
                    Boolean val = one == null ? null : (Boolean) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val != null && val ? 1 : 0);
                } else if (column.type == byte.class || column.type == Byte.class) {
                    sqltype = "INT2";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.byteValue());
                } else if (column.type == short.class || column.type == Short.class) {
                    sqltype = "INT2";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == char.class || column.type == Character.class) {
                    sqltype = "INT2 UNSIGNED";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.intValue());
                } else if (column.type == int.class || column.type == Integer.class || column.type == AtomicInteger.class) {
                    sqltype = "INT4";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == long.class || column.type == Long.class || column.type == AtomicLong.class || column.type == LongAdder.class) {
                    sqltype = "INT8";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == float.class || column.type == Float.class) {
                    sqltype = "FLOAT4";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == double.class || column.type == Double.class) {
                    sqltype = "FLOAT8";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == BigInteger.class) {
                    sqltype = "NUMERIC";
                    if (column.precision > 0) {
                        sqltype += "(" + column.precision + "," + column.scale + ")";
                    } else {
                        sqltype += "(19,2)";
                    }
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == BigDecimal.class) {
                    sqltype = "NUMERIC";
                    if (column.precision > 0) sqltype += "(" + column.precision + "," + column.scale + ")";
                    Number val = one == null ? null : (Number) info.getAttribute(column.field).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.type == String.class) {
                    if (column.length < 65535) {
                        String val = one == null ? null : (String) info.getAttribute(column.field).get(one);
                        if (val != null) {
                            sqlnull = "NOT NULL DEFAULT '" + val.replace('\'', '"') + "'";
                        } else if (column.primary) {
                            sqlnull = "NOT NULL DEFAULT ''";
                        }
                    } else {
                        sqltype = "TEXT";
                        if (!column.nullable) sqlnull = "NOT NULL";
                    }
                } else if (column.type == byte[].class) {
                    sqltype = "BYTEA";
                    if (!column.nullable) sqlnull = "NOT NULL";
                } else if (column.type == java.time.LocalDate.class || column.type == java.util.Date.class || "java.sql.Date".equals(column.type.getName())) {
                    sqltype = "DATE";
                } else if (column.type == java.time.LocalTime.class || "java.sql.Time".equals(column.type.getName())) {
                    sqltype = "TIME";
                } else if (column.type == java.time.LocalDateTime.class || "java.sql.Timestamp".equals(column.type.getName())) {
                    sqltype = "TIMESTAMP";
                } else { //JavaBean
                    sqltype = column.length >= 65535 ? "TEXT" : ("VARCHAR(" + column.length + ")");
                    sqlnull = !column.nullable ? "NOT NULL DEFAULT ''" : "NULL";
                }
                sb.append("   ").append(column.column).append(" ").append(sqltype).append(" ").append(sqlnull);
                if (column.comment != null && !column.comment.isEmpty()) {
                    //postgresql不支持DDL中带comment
                }
                sb.append(",\n");
            }
            sb.append("   PRIMARY KEY (").append(primary.column).append(")\n");
            sb.append(")");
            return Utility.ofArray(sb.toString());
        }
        return null;
    }

    @Local
    protected boolean isTableNotExist(EntityInfo info, String code) {
        if (code == null || code.isEmpty()) return false;
        return tableNotExistSqlstates.contains(';' + code + ';');
    }

    @Local
    protected String getTableCopySQL(EntityInfo info, String newTable) {
        return tablecopySQL.replace("${newtable}", newTable).replace("${oldtable}", info.table);
    }

    @Local
    protected <T> Serializable getSQLAttrValue(EntityInfo info, Attribute attr, Serializable val) {
        if (val != null && !(val instanceof Number) && !(val instanceof CharSequence) && !(val instanceof java.util.Date)
            && !val.getClass().getName().startsWith("java.sql.") && !val.getClass().getName().startsWith("java.time.")) {
            val = info.jsonConvert.convertTo(attr.genericType(), val);
        } else if (val == null && info.isNotNullJson(attr)) {
            val = "";
        }
        return val;
    }

    @Local
    protected <T> Serializable getEntityAttrValue(EntityInfo info, Attribute attr, T entity) {
        Serializable val = info.getSQLValue(attr, entity);
        return getSQLAttrValue(info, attr, val);
    }

    @Override
    public void destroy(AnyValue config) {
        super.destroy(config);
    }

    @Override
    @Local
    public <T> void compile(Class<T> clazz) {
        EntityInfo.compile(clazz, this);
    }

    @Local
    public final String dbtype() {
        if (dbtype == null) throw new NullPointerException("dbtype is null");
        return dbtype;
    }

    @Local
    public final boolean autoddl() {
        return autoddl;
    }

    @Local
    public abstract int directExecute(String sql);

    @Local
    public abstract int[] directExecute(String... sqls);

    @Local
    public abstract <V> V directQuery(String sql, Function<DataResultSet, V> handler);

    //是否异步
    protected abstract boolean isAsync();

    //index从1开始
    protected abstract String prepareParamSign(int index);

    //插入纪录
    protected abstract <T> CompletableFuture<Integer> insertDB(final EntityInfo<T> info, T... entitys);

    //删除记录
    protected abstract <T> CompletableFuture<Integer> deleteDB(final EntityInfo<T> info, Flipper flipper, final String sql);

    //清空表
    protected abstract <T> CompletableFuture<Integer> clearTableDB(final EntityInfo<T> info, final String table, final String sql);

    //删除表
    protected abstract <T> CompletableFuture<Integer> dropTableDB(final EntityInfo<T> info, final String table, final String sql);

    //更新纪录
    protected abstract <T> CompletableFuture<Integer> updateEntityDB(final EntityInfo<T> info, T... entitys);

    //更新纪录
    protected abstract <T> CompletableFuture<Integer> updateColumnDB(final EntityInfo<T> info, Flipper flipper, final String sql, final boolean prepared, Object... params);

    //查询Number Map数据
    protected abstract <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDB(final EntityInfo<T> info, final String sql, final FilterFuncColumn... columns);

    //查询Number数据
    protected abstract <T> CompletableFuture<Number> getNumberResultDB(final EntityInfo<T> info, final String sql, final Number defVal, final String column);

    //查询Map数据
    protected abstract <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDB(final EntityInfo<T> info, final String sql, final String keyColumn);

    //查询Map数据
    protected abstract <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDB(final EntityInfo<T> info, final String sql, final ColumnNode[] funcNodes, final String[] groupByColumns);

    //查询单条记录
    protected abstract <T> CompletableFuture<T> findDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final SelectColumn selects);

    //查询单条记录的单个字段
    protected abstract <T> CompletableFuture<Serializable> findColumnDB(final EntityInfo<T> info, final String sql, final boolean onlypk, final String column, final Serializable defValue);

    //判断记录是否存在
    protected abstract <T> CompletableFuture<Boolean> existsDB(final EntityInfo<T> info, final String sql, final boolean onlypk);

    //查询一页数据
    protected abstract <T> CompletableFuture<Sheet<T>> querySheetDB(final EntityInfo<T> info, final boolean readcache, final boolean needtotal, final boolean distinct, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    protected <T> CharSequence createSQLJoin(FilterNode node, final Function<Class, EntityInfo> func, final boolean update, final Map<Class, String> joinTabalis, final Set<String> haset, final EntityInfo<T> info) {
        return node == null ? null : node.createSQLJoin(func, update, joinTabalis, haset, info);
    }

    protected <T> CharSequence createSQLExpress(FilterNode node, final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return node == null ? null : node.createSQLExpress(this, info, joinTabalis);
    }

    @Local
    @Override
    public String getType() {
        return "sql";
    }

    @Override
    public final String resourceName() {
        return name;
    }

    @Local
    @Override
    public EntityInfo apply(Class t) {
        return loadEntityInfo(t);
    }

    @Local
    @Override
    public void close() throws Exception {
    }

    protected <T> EntityInfo<T> loadEntityInfo(Class<T> clazz) {
        return loadEntityInfo(clazz, this.cacheForbidden, readConfProps, fullloader);
    }

    public <T> EntityCache<T> loadCache(Class<T> clazz) {
        EntityInfo<T> info = loadEntityInfo(clazz);
        return info.getCache();
    }

    /**
     * 将entity的对象全部加载到Cache中去，如果clazz没有被@javax.persistence.Cacheable注解则不做任何事
     *
     * @param <T>   Entity类泛型
     * @param clazz Entity类
     */
    public <T> void refreshCache(Class<T> clazz) {
        EntityInfo<T> info = loadEntityInfo(clazz);
        EntityCache<T> cache = info.getCache();
        if (cache == null) return;
        cache.fullLoadAsync();
    }

    protected <T> CharSequence formatValueToString(final EntityInfo<T> info, Object value) {
        if ("mysql".equals(dbtype)) {
            if (value == null) return null;
            if (value instanceof CharSequence) {
                return new StringBuilder().append('\'').append(value.toString().replace("\\", "\\\\").replace("'", "\\'")).append('\'').toString();
            } else if (!(value instanceof Number) && !(value instanceof java.util.Date)
                && !value.getClass().getName().startsWith("java.sql.") && !value.getClass().getName().startsWith("java.time.")) {
                return new StringBuilder().append('\'').append(info.getJsonConvert().convertTo(value).replace("\\", "\\\\").replace("'", "\\'")).append('\'').toString();
            }
            return String.valueOf(value);
        } else if (value != null && value instanceof CharSequence && "postgresql".equals(dbtype)) {
            String s = String.valueOf(value);
            int pos = s.indexOf('\'');
            if (pos >= 0) return new StringBuilder().append("E'").append(value.toString().replace("\\", "\\\\").replace("'", "\\'")).append('\'').toString();
        }
        return info.formatSQLValue(value, null);
    }

    //----------------------------- insert -----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>     Entity类泛型
     * @param entitys Entity对象
     *
     * @return 影响的记录条数
     */
    @Override
    public <T> int insert(T... entitys) {
        if (entitys.length == 0) return 0;
        checkEntity("insert", false, entitys);
        final EntityInfo<T> info = loadEntityInfo((Class<T>) entitys[0].getClass());
        if (isOnlyCache(info)) return insertCache(info, entitys);
        return insertDB(info, entitys).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, entitys);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> insertAsync(T... entitys) {
        if (entitys.length == 0) return CompletableFuture.completedFuture(0);
        CompletableFuture future = checkEntity("insert", true, entitys);
        if (future != null) return future;
        final EntityInfo<T> info = loadEntityInfo((Class<T>) entitys[0].getClass());
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(insertCache(info, entitys));
        }
        if (isAsync()) return insertDB(info, entitys).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    insertCache(info, entitys);
                }
            });
        return CompletableFuture.supplyAsync(() -> insertDB(info, entitys).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                insertCache(info, entitys);
            }
        });
    }

    protected <T> int insertCache(final EntityInfo<T> info, T... entitys) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return 0;
        int c = 0;
        for (final T value : entitys) {
            c += cache.insert(value);
        }
        return c;
    }

    //----------------------------- deleteCompose -----------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>     Entity类泛型
     * @param entitys Entity对象
     *
     * @return 删除的数据条数
     */
    @Override
    public <T> int delete(T... entitys) {
        if (entitys.length == 0) return -1;
        checkEntity("delete", false, entitys);
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[entitys.length];
        int i = 0;
        for (final T value : entitys) {
            ids[i++] = (Serializable) primary.get(value);
        }
        return delete(clazz, ids);
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final T... entitys) {
        if (entitys.length == 0) return CompletableFuture.completedFuture(-1);
        CompletableFuture future = checkEntity("delete", true, entitys);
        if (future != null) return future;
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute primary = info.getPrimary();
        Serializable[] ids = new Serializable[entitys.length];
        int i = 0;
        for (final T value : entitys) {
            ids[i++] = (Serializable) primary.get(value);
        }
        return deleteAsync(clazz, ids);
    }

    @Override
    public <T> int delete(Class<T> clazz, Serializable... pks) {
        if (pks.length == 0) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return deleteCache(info, -1, pks);
        return deleteCompose(info, pks).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, pks);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Serializable... pks) {
        if (pks.length == 0) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(deleteCache(info, -1, pks));
        }
        if (isAsync()) return deleteCompose(info, pks).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, pks);
                }
            });
        return CompletableFuture.supplyAsync(() -> deleteCompose(info, pks).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, pks);
            }
        });
    }

    @Override
    public <T> int delete(Class<T> clazz, FilterNode node) {
        return delete(clazz, (Flipper) null, node);
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final FilterNode node) {
        return deleteAsync(clazz, (Flipper) null, node);
    }

    @Override
    public <T> int delete(Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return deleteCache(info, -1, flipper, node);
        return this.deleteCompose(info, flipper, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, flipper, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(deleteCache(info, -1, flipper, node));
        }
        if (isAsync()) return this.deleteCompose(info, flipper, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, flipper, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.deleteCompose(info, flipper, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                deleteCache(info, rs, flipper, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> deleteCompose(final EntityInfo<T> info, final Serializable... pks) {
        if (pks.length == 1) {
            String sql = "DELETE FROM " + info.getTable(pks[0]) + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pks[0], sqlFormatter);
            return deleteDB(info, null, sql);
        }
        String sql = "DELETE FROM " + info.getTable(pks[0]) + " WHERE " + info.getPrimarySQLColumn() + " IN (";
        for (int i = 0; i < pks.length; i++) {
            if (i > 0) sql += ',';
            sql += info.formatSQLValue(info.getPrimarySQLColumn(), pks[i], sqlFormatter);
        }
        sql += ")";
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " delete sql=" + sql);
        return deleteDB(info, null, sql);
    }

    protected <T> CompletableFuture<Integer> deleteCompose(final EntityInfo<T> info, final Flipper flipper, final FilterNode node) {
        boolean pgsql = "postgresql".equals(dbtype());
        Map<Class, String> joinTabalis = null;
        CharSequence join = null;
        CharSequence where = null;
        if (node != null) {
            joinTabalis = node.getJoinTabalis();
            join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
            where = node.createSQLExpress(this, info, joinTabalis);
        }
        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multisplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multisplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        String sql;
        if (pgsql && flipper != null && flipper.getLimit() > 0) {
            String wherestr = ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            sql = "DELETE FROM " + info.getTable(node) + " a" + (join1 == null ? "" : (", " + join1))
                + " WHERE " + info.getPrimarySQLColumn() + " IN (SELECT " + info.getPrimaryColumn() + " FROM " + info.getTable(node)
                + wherestr + info.createSQLOrderby(flipper) + " OFFSET 0 LIMIT " + flipper.getLimit() + ")";
        } else {
            sql = "DELETE " + (("mysql".equals(dbtype()) && join1 != null) ? "a" : "") + " FROM " + info.getTable(node) + " a" + (join1 == null ? "" : (", " + join1))
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2)))) + info.createSQLOrderby(flipper)
                + (("mysql".equals(dbtype()) && flipper != null && flipper.getLimit() > 0) ? (" LIMIT " + flipper.getLimit()) : "");
        }
        return deleteDB(info, flipper, sql);
    }

    //----------------------------- clearTableCompose -----------------------------
    @Override
    public <T> int clearTable(Class<T> clazz) {
        return clearTable(clazz, (FilterNode) null);
    }

    @Override
    public <T> int clearTable(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return clearTableCache(info, node);
        return this.clearTableCompose(info, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                clearTableCache(info, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> clearTableAsync(final Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(clearTableCache(info, node));
        }
        if (isAsync()) return this.clearTableCompose(info, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    clearTableCache(info, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.clearTableCompose(info, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                clearTableCache(info, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> clearTableCompose(final EntityInfo<T> info, final FilterNode node) {
        final String table = info.getTable(node);
        String sql = "TRUNCATE TABLE " + table;
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " clearTable sql=" + sql);
        return clearTableDB(info, table, sql);
    }

    //----------------------------- dropTableCompose -----------------------------
    @Override
    public <T> int dropTable(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return dropTableCache(info, node);
        return this.dropTableCompose(info, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                dropTableCache(info, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> dropTableAsync(final Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(dropTableCache(info, node));
        }
        if (isAsync()) return this.dropTableCompose(info, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    dropTableCache(info, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.dropTableCompose(info, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                dropTableCache(info, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> dropTableCompose(final EntityInfo<T> info, final FilterNode node) {
        final String table = node == null ? info.getOriginTable() : info.getTable(node);
        String sql = "DROP TABLE IF EXISTS " + table;
        //if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " dropTable sql=" + sql);
        return dropTableDB(info, table, sql);
    }

    protected <T> int clearTableCache(final EntityInfo<T> info, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        return cache.clear();
    }

    protected <T> int dropTableCache(final EntityInfo<T> info, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        return cache.drop();
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Flipper flipper, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        Serializable[] ids = cache.delete(flipper, node);
        return count >= 0 ? count : (ids == null ? 0 : ids.length);
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Serializable... pks) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c = 0;
        for (Serializable key : pks) {
            c += cache.delete(key);
        }
        return count >= 0 ? count : c;
    }

    protected static StringBuilder multisplit(char ch1, char ch2, String split, StringBuilder sb, String str, int from) {
        if (str == null) return sb;
        int pos1 = str.indexOf(ch1, from);
        if (pos1 < 0) return sb;
        int pos2 = str.indexOf(ch2, from);
        if (pos2 < 0) return sb;
        if (sb.length() > 0) sb.append(split);
        sb.append(str.substring(pos1 + 1, pos2));
        return multisplit(ch1, ch2, split, sb, str, pos2 + 1);
    }

    //---------------------------- update ----------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>     Entity类泛型
     * @param entitys Entity对象
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int update(T... entitys) {
        if (entitys.length == 0) return -1;
        checkEntity("update", false, entitys);
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return updateCache(info, -1, entitys);
        return updateEntityDB(info, entitys).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, entitys);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateAsync(final T... entitys) {
        if (entitys.length == 0) return CompletableFuture.completedFuture(-1);
        CompletableFuture future = checkEntity("update", true, entitys);
        if (future != null) return future;
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, entitys));
        }
        if (isAsync()) return updateEntityDB(info, entitys).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, entitys);
                }
            });
        return CompletableFuture.supplyAsync(() -> updateEntityDB(info, entitys).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, entitys);
            }
        });
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param pk     主键值
     * @param column 过滤字段名
     * @param colval 过滤字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, Serializable pk, String column, Serializable colval) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return updateCache(info, -1, pk, column, colval);
        return updateColumnCompose(info, pk, column, colval).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, pk, column, colval);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final Serializable pk, final String column, final Serializable colval) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, pk, column, colval));
        }
        if (isAsync()) return updateColumnCompose(info, pk, column, colval).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, column, colval);
                }
            });
        return CompletableFuture.supplyAsync(() -> updateColumnCompose(info, pk, column, colval).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, pk, column, colval);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, Serializable pk, String column, final Serializable colval) {
        Attribute attr = info.getAttribute(column);
        Serializable val = getSQLAttrValue(info, attr, colval);
        if (val instanceof byte[]) {
            String sql = "UPDATE " + info.getTable(pk) + " SET " + info.getSQLColumn(null, column) + "=" + prepareParamSign(1) + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
            return updateColumnDB(info, null, sql, true, val);
        } else {
            String sql = "UPDATE " + info.getTable(pk) + " SET " + info.getSQLColumn(null, column) + "="
                + info.formatSQLValue(column, val, sqlFormatter) + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
            return updateColumnDB(info, null, sql, false);
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @param node   过滤node 不能为null
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, String column, Serializable colval, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return updateCache(info, -1, column, colval, node);
        return this.updateColumnCompose(info, column, colval, node).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, column, colval, node);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final String column, final Serializable colval, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, column, colval, node));
        }
        if (isAsync()) return this.updateColumnCompose(info, column, colval, node).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, column, colval, node);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.updateColumnCompose(info, column, colval, node).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, column, colval, node);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final String column, final Serializable colval, final FilterNode node) {
        Map<Class, String> joinTabalis = node.getJoinTabalis();
        CharSequence join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
        CharSequence where = node.createSQLExpress(this, info, joinTabalis);

        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multisplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multisplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        Attribute attr = info.getAttribute(column);
        Serializable val = getSQLAttrValue(info, attr, colval);
        String alias = "postgresql".equals(dbtype()) ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        if (val instanceof byte[]) {
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                + " SET " + info.getSQLColumn(alias, column) + "=" + prepareParamSign(1)
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return updateColumnDB(info, null, sql, true, val);
        } else {
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1))
                + " SET " + info.getSQLColumn(alias, column) + "=" + info.formatSQLValue(val, sqlFormatter)
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return updateColumnDB(info, null, sql, false);
        }
    }

    /**
     * 根据主键值更新对象的多个column对应的值， 必须是Entity Class
     *
     * @param <T>    Entity类的泛型
     * @param clazz  Entity类
     * @param pk     主键值
     * @param values 字段值
     *
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable pk, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return updateCache(info, -1, pk, values);
        return this.updateColumnCompose(info, pk, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, pk, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final Serializable pk, final ColumnValue... values) {
        if (values == null || values.length < 1) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, pk, values));
        }
        if (isAsync()) return this.updateColumnCompose(info, pk, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.updateColumnCompose(info, pk, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, pk, values);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final Serializable pk, final ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        for (ColumnValue col : values) {
            if (col == null) continue;
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) throw new RuntimeException(info.getType() + " cannot found column " + col.getColumn());
            if (setsql.length() > 0) setsql.append(", ");
            String sqlColumn = info.getSQLColumn(null, col.getColumn());
            if (col.getValue() instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) col.getValue());
                setsql.append(sqlColumn).append("=").append(prepareParamSign(++index));
            } else {
                setsql.append(sqlColumn).append("=").append(info.formatSQLValue(sqlColumn, attr, col, sqlFormatter));
            }
        }
        if (setsql.length() < 1) return CompletableFuture.completedFuture(0);
        String sql = "UPDATE " + info.getTable(pk) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
        if (blobs == null) return updateColumnDB(info, null, sql, false);
        return updateColumnDB(info, null, sql, true, blobs.toArray());
    }

    @Override
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (values == null || values.length < 1) return -1;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) return updateCache(info, -1, node, flipper, values);
        return this.updateColumnCompose(info, node, flipper, values).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, node, flipper, values);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (values == null || values.length < 1) return CompletableFuture.completedFuture(-1);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, node, flipper, values));
        }
        if (isAsync()) return this.updateColumnCompose(info, node, flipper, values).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, node, flipper, values);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.updateColumnCompose(info, node, flipper, values).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, node, flipper, values);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        boolean pgsql = "postgresql".equals(dbtype());
        String alias = pgsql ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        for (ColumnValue col : values) {
            if (col == null) continue;
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            if (setsql.length() > 0) setsql.append(", ");
            String sqlColumn = info.getSQLColumn(alias, col.getColumn());
            if (col.getValue() instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) col.getValue());
                setsql.append(sqlColumn).append("=").append(prepareParamSign(++index));
            } else {
                setsql.append(sqlColumn).append("=").append(info.formatSQLValue(sqlColumn, attr, col, sqlFormatter));
            }
        }
        if (setsql.length() < 1) return CompletableFuture.completedFuture(0);
        Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        CharSequence join = node == null ? null : node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
        CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multisplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multisplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        String sql;
        if (pgsql && flipper != null && flipper.getLimit() > 0) {
            String wherestr = ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                + " WHERE " + info.getPrimarySQLColumn() + " IN (SELECT " + info.getPrimaryColumn() + " FROM " + info.getTable(node)
                + wherestr + info.createSQLOrderby(flipper) + " OFFSET 0 LIMIT " + flipper.getLimit() + ")";
        } else {
            sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))))
                + info.createSQLOrderby(flipper)
                + (("mysql".equals(dbtype()) && flipper != null && flipper.getLimit() > 0) ? (" LIMIT " + flipper.getLimit()) : "");
        }
        if (blobs == null) return updateColumnDB(info, flipper, sql, false);
        return updateColumnDB(info, flipper, sql, true, blobs.toArray());
    }

    //返回不存在的字段名,null表示字段都合法;
    protected <T> String checkIllegalColumn(final EntityInfo<T> info, SelectColumn selects) {
        if (selects == null) return null;
        String[] columns = selects.getColumns();
        if (columns == null) return null;
        for (String col : columns) {
            if (info.getAttribute(col) == null) return col;
        }
        return null;
    }

    @Override
    public <T> int updateColumn(final T entity, final SelectColumn selects) {
        if (entity == null || selects == null) return -1;
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            throw new RuntimeException(info.getType() + " cannot found column " + illegalColumn);
        }
        if (isOnlyCache(info)) return updateCache(info, -1, false, entity, null, selects);
        return this.updateColumnCompose(info, false, entity, null, selects).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, false, entity, null, selects);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final SelectColumn selects) {
        if (entity == null || selects == null) return CompletableFuture.completedFuture(-1);
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            return CompletableFuture.failedFuture(new RuntimeException(info.getType() + " cannot found column " + illegalColumn));
        }
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, false, entity, null, selects));
        }
        if (isAsync()) return this.updateColumnCompose(info, false, entity, null, selects).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, false, entity, null, selects);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.updateColumnCompose(info, false, entity, null, selects).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, false, entity, null, selects);
            }
        });
    }

    @Override
    public <T> int updateColumn(final T entity, final FilterNode node, final SelectColumn selects) {
        if (entity == null || node == null || selects == null) return -1;
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            throw new RuntimeException(info.getType() + " cannot found column " + illegalColumn);
        }
        if (isOnlyCache(info)) return updateCache(info, -1, true, entity, node, selects);
        return this.updateColumnCompose(info, true, entity, node, selects).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, true, entity, node, selects);
            }
        }).join();
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final FilterNode node, final SelectColumn selects) {
        if (entity == null || node == null || selects == null) return CompletableFuture.completedFuture(-1);
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            return CompletableFuture.failedFuture(new RuntimeException(info.getType() + " cannot found column " + illegalColumn));
        }
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, true, entity, node, selects));
        }
        if (isAsync()) return this.updateColumnCompose(info, true, entity, node, selects).whenComplete((rs, t) -> {
                if (t != null) {
                    futureCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, true, entity, node, selects);
                }
            });
        return CompletableFuture.supplyAsync(() -> this.updateColumnCompose(info, true, entity, node, selects).join(), getExecutor()).whenComplete((rs, t) -> {
            if (t != null) {
                futureCompleteConsumer.accept(rs, t);
            } else {
                updateCache(info, rs, true, entity, node, selects);
            }
        });
    }

    protected <T> CompletableFuture<Integer> updateColumnCompose(final EntityInfo<T> info, final boolean neednode, final T entity, final FilterNode node, final SelectColumn selects) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        String alias = "postgresql".equals(dbtype()) ? null : "a"; //postgresql的BUG， UPDATE的SET中不能含别名
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) continue;
            if (setsql.length() > 0) setsql.append(", ");
            setsql.append(info.getSQLColumn(alias, attr.field()));
            Serializable val = info.getFieldValue(attr, entity);
            if (val instanceof byte[]) {
                if (blobs == null) blobs = new ArrayList<>();
                blobs.add((byte[]) val);
                setsql.append("=").append(prepareParamSign(++index));
            } else {
                CharSequence sqlval = info.formatSQLValue(val, sqlFormatter);
                if (sqlval == null && info.isNotNullJson(attr)) sqlval = "''";
                setsql.append("=").append(sqlval);
            }
        }
        if (neednode) {
            Map<Class, String> joinTabalis = node.getJoinTabalis();
            CharSequence join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
            CharSequence where = node.createSQLExpress(this, info, joinTabalis);
            StringBuilder join1 = null;
            StringBuilder join2 = null;
            if (join != null) {
                String joinstr = join.toString();
                join1 = multisplit('[', ']', ",", new StringBuilder(), joinstr, 0);
                join2 = multisplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
            }
            String sql = "UPDATE " + info.getTable(node) + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                + ((where == null || where.length() == 0) ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            if (blobs == null) return updateColumnDB(info, null, sql, false);
            return updateColumnDB(info, null, sql, true, blobs.toArray());
        } else {
            final Serializable id = (Serializable) info.getSQLValue(info.getPrimary(), entity);
            String sql = "UPDATE " + info.getTable(id) + " a SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(id, sqlFormatter);
            if (blobs == null) return updateColumnDB(info, null, sql, false);
            return updateColumnDB(info, null, sql, true, blobs.toArray());
        }
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final boolean neednode, final T entity, final FilterNode node, final SelectColumn selects) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) continue;
            attrs.add(attr);
        }
        if (neednode) {
            T[] rs = cache.update(entity, attrs, node);
            return count >= 0 ? count : (rs == null ? 0 : rs.length);
        } else {
            T rs = cache.update(entity, attrs);
            return count >= 0 ? count : (rs == null ? 0 : 1);
        }
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            if (col == null) continue;
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            attrs.add(attr);
            cols.add(col);
        }
        T[] rs = cache.updateColumn(node, flipper, attrs, cols);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final Serializable pk, final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            if (col == null) continue;
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) continue;
            attrs.add(attr);
            cols.add(col);
        }
        T rs = cache.updateColumn(pk, attrs, cols);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, String column, final Serializable colval, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        T[] rs = cache.update(info.getAttribute(column), colval, node);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, final Serializable pk, final String column, final Serializable colval) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return count;
        T rs = cache.update(pk, info.getAttribute(column), colval);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, T... entitys) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        int c2 = 0;
        for (final T value : entitys) {
            c2 += cache.update(value);
        }
        return count >= 0 ? count : c2;
    }

    public <T> int reloadCache(Class<T> clazz, Serializable... pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) return -1;
        String column = info.getPrimary().field();
        int c = 0;
        for (Serializable id : pks) {
            Sheet<T> sheet = querySheetCompose(false, true, false, clazz, null, FLIPPER_ONE, FilterNode.filter(column, id)).join();
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) c += cache.update(value);
        }
        return c;
    }

    //------------------------- getNumberMapCompose -------------------------
    @Override
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            final Map map = new HashMap<>();
            if (node == null || isCacheUseable(node, this)) {
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        map.put(ffc.col(col), cache.getNumberResult(ffc.func, ffc.defvalue, col, node));
                    }
                }
                return map;
            }
        }
        return (Map) getNumberMapCompose(info, node, columns).join();
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            final Map map = new HashMap<>();
            if (node == null || isCacheUseable(node, this)) {
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        map.put(ffc.col(col), cache.getNumberResult(ffc.getFunc(), ffc.getDefvalue(), col, node));
                    }
                }
                return CompletableFuture.completedFuture(map);
            }
        }
        if (isAsync()) return getNumberMapCompose(info, node, columns);
        return CompletableFuture.supplyAsync(() -> (Map) getNumberMapCompose(info, node, columns).join(), getExecutor());
    }

    protected <N extends Number> CompletableFuture<Map<String, N>> getNumberMapCompose(final EntityInfo info, final FilterNode node, final FilterFuncColumn... columns) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        StringBuilder sb = new StringBuilder();
        for (FilterFuncColumn ffc : columns) {
            for (String col : ffc.cols()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(ffc.func.getColumn((col == null || col.isEmpty() ? "*" : info.getSQLColumn("a", col))));
            }
        }
        final String sql = "SELECT " + sb + " FROM " + info.getTable(node) + " a"
            + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " getnumbermap sql=" + sql);
        return getNumberMapDB(info, sql, columns);
    }

    @Local
    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, FilterFuncColumn... columns) {
        return future.thenApply((DataResultSet dataset) -> {
            final Map map = new HashMap<>();
            if (dataset.next()) {
                int index = 0;
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        Object o = dataset.getObject(++index);
                        Number rs = ffc.getDefvalue();
                        if (o != null) rs = (Number) o;
                        map.put(ffc.col(col), rs);
                    }
                }
            }
            dataset.close();
            return map;
        });
    }

    //------------------------ getNumberResultCompose -----------------------
    @Override
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.getNumberResult(func, defVal, column, node);
            }
        }
        return getNumberResultCompose(info, entityClass, func, defVal, column, node).join();
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.getNumberResult(func, defVal, column, node));
            }
        }
        if (isAsync()) return getNumberResultCompose(info, entityClass, func, defVal, column, node);
        return CompletableFuture.supplyAsync(() -> getNumberResultCompose(info, entityClass, func, defVal, column, node).join(), getExecutor());
    }

    protected CompletableFuture<Number> getNumberResultCompose(final EntityInfo info, final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String sql = "SELECT " + func.getColumn((column == null || column.isEmpty() ? "*" : info.getSQLColumn("a", column))) + " FROM " + info.getTable(node) + " a"
            + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(entityClass.getSimpleName() + " getNumberResult sql=" + sql);
        return getNumberResultDB(info, sql, defVal, column);
    }

    @Local
    protected <T> CompletableFuture<Number> getNumberResultDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, Number defVal, String column) {
        return future.thenApply((DataResultSet dataset) -> {
            Number rs = defVal;
            if (dataset.next()) {
                Object o = dataset.getObject(1);
                if (o != null) rs = (Number) o;
            }
            dataset.close();
            return rs;
        });
    }

    //------------------------ queryColumnMapCompose ------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.queryColumnMap(keyColumn, func, funcColumn, node);
            }
        }
        return (Map) queryColumnMapCompose(info, keyColumn, func, funcColumn, node).join();
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.queryColumnMap(keyColumn, func, funcColumn, node));
            }
        }
        if (isAsync()) return queryColumnMapCompose(info, keyColumn, func, funcColumn, node);
        return CompletableFuture.supplyAsync(() -> (Map) queryColumnMapCompose(info, keyColumn, func, funcColumn, node).join(), getExecutor());
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapCompose(final EntityInfo<T> info, final String keyColumn, final FilterFunc func, final String funcColumn, FilterNode node) {
        final String keySqlColumn = info.getSQLColumn(null, keyColumn);
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String funcSqlColumn = func == null ? info.getSQLColumn("a", funcColumn) : func.getColumn((funcColumn == null || funcColumn.isEmpty() ? "*" : info.getSQLColumn("a", funcColumn)));
        final String sql = "SELECT a." + keySqlColumn + ", " + funcSqlColumn
            + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where)) + " GROUP BY a." + keySqlColumn;
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " querycolumnmap sql=" + sql);
        return queryColumnMapDB(info, sql, keyColumn);
    }

    @Local
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, final String keyColumn) {
        return future.thenApply((DataResultSet dataset) -> {
            Map<K, N> rs = new LinkedHashMap<>();
            while (dataset.next()) {
                rs.put((K) dataset.getObject(1), (N) dataset.getObject(2));
            }
            dataset.close();
            return rs;
        });
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn, final FilterNode node) {
        Map<K[], N[]> map = queryColumnMap(entityClass, funcNodes, Utility.ofArray(groupByColumn), node);
        final Map<K, N[]> rs = new LinkedHashMap<>();
        map.forEach((keys, values) -> rs.put(keys[0], values));
        return rs;
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String groupByColumn, final FilterNode node) {
        CompletableFuture<Map<K[], N[]>> future = queryColumnMapAsync(entityClass, funcNodes, Utility.ofArray(groupByColumn), node);
        return future.thenApply(map -> {
            final Map<K, N[]> rs = new LinkedHashMap<>();
            map.forEach((keys, values) -> rs.put(keys[0], values));
            return rs;
        });
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.queryColumnMap(funcNodes, groupByColumns, node);
            }
        }
        return (Map) queryColumnMapCompose(info, funcNodes, groupByColumns, node).join();
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(final Class<T> entityClass, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.queryColumnMap(funcNodes, groupByColumns, node));
            }
        }
        if (isAsync()) return queryColumnMapCompose(info, funcNodes, groupByColumns, node);
        return CompletableFuture.supplyAsync(() -> (Map) queryColumnMapCompose(info, funcNodes, groupByColumns, node).join(), getExecutor());
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapCompose(final EntityInfo<T> info, final ColumnNode[] funcNodes, final String[] groupByColumns, final FilterNode node) {
        final StringBuilder groupBySqlColumns = new StringBuilder();
        if (groupByColumns != null && groupByColumns.length > 0) {
            for (int i = 0; i < groupByColumns.length; i++) {
                if (groupBySqlColumns.length() > 0) groupBySqlColumns.append(", ");
                groupBySqlColumns.append(info.getSQLColumn("a", groupByColumns[i]));
            }
        }
        final StringBuilder funcSqlColumns = new StringBuilder();
        for (int i = 0; i < funcNodes.length; i++) {
            if (funcSqlColumns.length() > 0) funcSqlColumns.append(", ");
            if (funcNodes[i] instanceof ColumnFuncNode) {
                funcSqlColumns.append(info.formatSQLValue((Attribute) null, "a", (ColumnFuncNode) funcNodes[i], sqlFormatter));
            } else {
                funcSqlColumns.append(info.formatSQLValue((Attribute) null, "a", (ColumnNodeValue) funcNodes[i], sqlFormatter));
            }
        }
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String sql = "SELECT ";
        if (groupBySqlColumns.length() > 0) sql += groupBySqlColumns + ", ";
        sql += funcSqlColumns + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (groupBySqlColumns.length() > 0) sql += " GROUP BY " + groupBySqlColumns;
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " querycolumnmap sql=" + sql);
        return queryColumnMapDB(info, sql, funcNodes, groupByColumns);
    }

    @Local
    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, final ColumnNode[] funcNodes, final String[] groupByColumns) {
        return future.thenApply((DataResultSet dataset) -> {
            Map rs = new LinkedHashMap<>();
            while (dataset.next()) {
                int index = 0;
                Serializable[] keys = new Serializable[groupByColumns.length];
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = (Serializable) dataset.getObject(++index);
                }
                Number[] vals = new Number[funcNodes.length];
                for (int i = 0; i < vals.length; i++) {
                    vals[i] = (Number) dataset.getObject(++index);
                }
                rs.put(keys, vals);
            }
            dataset.close();
            return rs;
        });
    }

    //----------------------------- find -----------------------------
    @Override
    public <T> T[] finds(Class<T> clazz, final SelectColumn selects, Serializable... pks) {
        return findsAsync(clazz, selects, pks).join();
    }

    @Override
    public <T> CompletableFuture<T[]> findsAsync(Class<T> clazz, final SelectColumn selects, Serializable... pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (pks == null || pks.length == 0) return CompletableFuture.completedFuture(info.getArrayer().apply(0));
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T[] rs = selects == null ? cache.finds(pks) : cache.finds(selects, pks);
            if (cache.isFullLoaded() || rs != null) return CompletableFuture.completedFuture(rs);
        }
        return findsComposeAsync(info, selects, pks);
    }

    protected <T> CompletableFuture<T[]> findsComposeAsync(final EntityInfo<T> info, final SelectColumn selects, Serializable... pks) {
        final Attribute<T, Serializable> primary = info.getPrimary();
        return queryListAsync(info.getType(), selects, null, FilterNode.filter(info.getPrimarySQLColumn(), FilterExpress.IN, pks)).thenApply(list -> {
            T[] rs = info.getArrayer().apply(pks.length);
            for (int i = 0; i < rs.length; i++) {
                T t = null;
                Serializable pk = pks[i];
                for (T item : list) {
                    if (pk.equals(primary.get(item))) {
                        t = item;
                        break;
                    }
                }
                rs[i] = t;
            }
            return rs;
        });
    }

    @Override
    public <D extends Serializable, T> List<T> findsList(Class<T> clazz, Stream<D> pks) {
        return findsListAsync(clazz, pks).join();
    }

    @Override
    public <D extends Serializable, T> CompletableFuture<List<T>> findsListAsync(final Class<T> clazz, final Stream<D> pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        Serializable[] ids = pks.toArray(v -> new Serializable[v]);
        return queryListAsync(info.getType(), null, null, FilterNode.filter(info.getPrimarySQLColumn(), FilterExpress.IN, ids));
    }

    @Override
    public <T> T find(Class<T> clazz, final SelectColumn selects, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = selects == null ? cache.find(pk) : cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return rs;
        }
        return findCompose(info, selects, pk).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = selects == null ? cache.find(pk) : cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return findCompose(info, selects, pk);
        return CompletableFuture.supplyAsync(() -> findCompose(info, selects, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> findCompose(final EntityInfo<T> info, final SelectColumn selects, Serializable pk) {
        String column = info.getPrimarySQLColumn();
        final String sql = "SELECT " + info.getQueryColumns(null, selects) + " FROM " + info.getTable(pk) + " WHERE " + column + "=" + info.formatSQLValue(column, pk, sqlFormatter);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findDB(info, sql, true, selects);
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || isCacheUseable(node, this))) return cache.find(selects, node);
        return this.findCompose(info, selects, node).join();
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || isCacheUseable(node, this))) {
            return CompletableFuture.completedFuture(cache.find(selects, node));
        }
        if (isAsync()) return this.findCompose(info, selects, node);
        return CompletableFuture.supplyAsync(() -> this.findCompose(info, selects, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<T> findCompose(final EntityInfo<T> info, final SelectColumn selects, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findDB(info, sql, false, selects);
    }

    @Local
    protected <T> CompletableFuture<T> findDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, boolean onlypk, SelectColumn selects) {
        return future.thenApply((DataResultSet pgset) -> {
            T rs = pgset.next() ? (onlypk && selects == null ? getEntityValue(info, null, pgset) : getEntityValue(info, selects, pgset)) : null;
            pgset.close();
            return rs;
        });
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return val;
        }
        return findColumnCompose(info, column, defValue, pk).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return findColumnCompose(info, column, defValue, pk);
        return CompletableFuture.supplyAsync(() -> findColumnCompose(info, column, defValue, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumnCompose(final EntityInfo<T> info, String column, final Serializable defValue, final Serializable pk) {
        final String sql = "SELECT " + info.getSQLColumn(null, column) + " FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findColumnDB(info, sql, true, column, defValue);
    }

    @Override
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) return val;
        }
        return this.findColumnCompose(info, column, defValue, node).join();
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) return CompletableFuture.completedFuture(val);
        }
        if (isAsync()) return this.findColumnCompose(info, column, defValue, node);
        return CompletableFuture.supplyAsync(() -> this.findColumnCompose(info, column, defValue, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Serializable> findColumnCompose(final EntityInfo<T> info, String column, final Serializable defValue, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String sql = "SELECT " + info.getSQLColumn("a", column) + " FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        return findColumnDB(info, sql, false, column, defValue);
    }

    @Local
    protected <T> CompletableFuture<Serializable> findColumnDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, boolean onlypk, String column, Serializable defValue) {
        return future.thenApply((DataResultSet dataset) -> {
            Serializable val = defValue;
            if (dataset.next()) {
                final Attribute<T, Serializable> attr = info.getAttribute(column);
                val = dataset.getObject(attr, 1, null);
            }
            dataset.close();
            return val == null ? defValue : val;
        });
    }

    //---------------------------- existsCompose ----------------------------
    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return rs;
        }
        return existsCompose(info, pk).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return existsCompose(info, pk);
        return CompletableFuture.supplyAsync(() -> existsCompose(info, pk).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> existsCompose(final EntityInfo<T> info, Serializable pk) {
        final String sql = "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        return existsDB(info, sql, true);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(node);
            if (rs || cache.isFullLoaded()) return rs;
        }
        return this.existsCompose(info, node).join();
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(node);
            if (rs || cache.isFullLoaded()) return CompletableFuture.completedFuture(rs);
        }
        if (isAsync()) return this.existsCompose(info, node);
        return CompletableFuture.supplyAsync(() -> this.existsCompose(info, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Boolean> existsCompose(final EntityInfo<T> info, FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + info.getTable(node) + " a" + (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        if (info.isLoggable(logger, Level.FINEST, sql)) logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        return existsDB(info, sql, false);
    }

    @Local
    protected <T> CompletableFuture<Boolean> existsDBApply(EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, boolean onlypk) {
        return future.thenApply((DataResultSet pgset) -> {
            boolean rs = pgset.next() ? (((Number) pgset.getObject(1)).intValue() > 0) : false;
            pgset.close();
            return rs;
        });
    }

    //-----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        final Set<T> list = querySet(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final Set<V> rs = new LinkedHashSet<>();
        if (list.isEmpty()) return rs;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        for (T t : list) {
            rs.add(selected.get(t));
        }
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySetAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node).thenApply((Set<T> list) -> {
            final Set<V> rs = new LinkedHashSet<>();
            if (list.isEmpty()) return rs;
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
            for (T t : list) {
                rs.add(selected.get(t));
            }
            return rs;
        });
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        final List<T> list = queryList(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final List<V> rs = new ArrayList<>();
        if (list.isEmpty()) return rs;
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        for (T t : list) {
            rs.add(selected.get(t));
        }
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryListAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node).thenApply((List<T> list) -> {
            final List<V> rs = new ArrayList<>();
            if (list.isEmpty()) return rs;
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
            for (T t : list) {
                rs.add(selected.get(t));
            }
            return rs;
        });
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> sheet = querySheet(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final Sheet<V> rs = new Sheet<>();
        if (sheet.isEmpty()) return rs;
        rs.setTotal(sheet.getTotal());
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        final List<V> list = new ArrayList<>();
        for (T t : sheet.getRows()) {
            list.add(selected.get(t));
        }
        rs.setRows(list);
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheetAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node).thenApply((Sheet<T> sheet) -> {
            final Sheet<V> rs = new Sheet<>();
            if (sheet.isEmpty()) return rs;
            rs.setTotal(sheet.getTotal());
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
            final List<V> list = new ArrayList<>();
            for (T t : sheet.getRows()) {
                list.add(selected.get(t));
            }
            rs.setRows(list);
            return rs;
        });
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>       主键泛型
     * @param <T>       Entity泛型
     * @param clazz     Entity类
     * @param selects   指定字段
     * @param keyStream 主键Stream
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) return new LinkedHashMap<>();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final ArrayList<K> ids = new ArrayList<>();
        keyStream.forEach(k -> ids.add(k));
        final Attribute<T, Serializable> primary = info.getPrimary();
        List<T> rs = queryList(clazz, FilterNode.filter(primary.field(), ids));
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) return new LinkedHashMap<>();
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) return CompletableFuture.completedFuture(new LinkedHashMap<>());
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final ArrayList<K> pks = new ArrayList<>();
        keyStream.forEach(k -> pks.add(k));
        final Attribute<T, Serializable> primary = info.getPrimary();
        return queryListAsync(clazz, FilterNode.filter(primary.field(), pks)).thenApply((List<T> rs) -> {
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) return new LinkedHashMap<>();
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <K>     主键泛型
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param node    FilterNode
     *
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, node);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, Serializable> primary = info.getPrimary();
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) return new LinkedHashMap<>();
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryListAsync(clazz, selects, node).thenApply((List<T> rs) -> {
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, Serializable> primary = info.getPrimary();
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) return new LinkedHashMap<>();
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    @Override
    public <T> Set<T> querySet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return new LinkedHashSet<>(querySheetCompose(true, false, true, clazz, selects, flipper, node).join().list(true));
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheetCompose(true, false, true, clazz, selects, flipper, node).thenApply((rs) -> new LinkedHashSet<>(rs.list(true)));
    }

    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheetCompose(true, false, false, clazz, selects, flipper, node).join().list(true);
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheetCompose(true, false, false, clazz, selects, flipper, node).thenApply((rs) -> rs.list(true));
    }

    @Override
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        return querySheetCompose(true, true, false, clazz, selects, flipper, node).join();
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) return querySheetCompose(true, true, false, clazz, selects, flipper, node);
        return CompletableFuture.supplyAsync(() -> querySheetCompose(true, true, false, clazz, selects, flipper, node).join(), getExecutor());
    }

    protected <T> CompletableFuture<Sheet<T>> querySheetCompose(final boolean readcache, final boolean needtotal, final boolean distinct, final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readcache && cache != null && cache.isFullLoaded()) {
            if (node == null || isCacheUseable(node, this)) {
                if (info.isLoggable(logger, Level.FINEST, " cache query predicate = ")) logger.finest(clazz.getSimpleName() + " cache query predicate = " + (node == null ? null : createPredicate(node, cache)));
                return CompletableFuture.completedFuture(cache.querySheet(needtotal, distinct, selects, flipper, node));
            }
        }
        return querySheetDB(info, readcache, needtotal, distinct, selects, flipper, node);
    }

    protected static enum UpdateMode {
        INSERT, DELETE, UPDATE, CLEAR, DROP, ALTER, OTHER;
    }

}
