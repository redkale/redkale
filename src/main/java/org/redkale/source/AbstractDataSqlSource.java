/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.math.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceType;
import static org.redkale.boot.Application.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.inject.ResourceEvent;
import org.redkale.net.AsyncGroup;
import org.redkale.net.WorkThread;
import org.redkale.persistence.Table;
import org.redkale.service.Local;
import static org.redkale.source.DataSources.*;
import org.redkale.util.*;
import static org.redkale.util.Utility.isEmpty;

/**
 * DataSource的SQL抽象实现类 <br>
 * 注意: 所有的操作只能作用在一张表上，不能同时变更多张表
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public abstract class AbstractDataSqlSource extends AbstractDataSource
        implements DataSqlSource, Function<Class, EntityInfo> {

    // 不存在分表时最大重试次数
    protected static final int MAX_RETRYS = 3;

    protected static final Flipper FLIPPER_ONE = new Flipper(1);

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected boolean cacheForbidden;

    protected String dbtype;

    private boolean autoDDL;

    protected Properties readConfProps;

    protected Properties writeConfProps;

    @Resource(name = RESNAME_APP_CLIENT_ASYNCGROUP, required = false)
    protected AsyncGroup clientAsyncGroup;

    // 配置<executor threads="0"> APP_EXECUTOR资源为null
    @Resource(name = RESNAME_APP_EXECUTOR, required = false)
    protected ExecutorService workExecutor;

    @Resource(required = false)
    protected DataNativeSqlParser nativeSqlParser;

    @Resource(required = false)
    protected DataSqlMonitor sqlMonitor;

    protected BiFunction<EntityInfo, Object, CharSequence> sqlFormatter;

    protected BiConsumer errorCompleteConsumer = (r, t) -> {
        // if (t != null) logger.log(Level.INFO, "CompletableFuture complete error", (Throwable) t);
    };

    protected final BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader = (s, i) ->
            ((CompletableFuture<Sheet>) querySheetDBAsync(i, false, false, false, null, null, (FilterNode) null))
                    .thenApply(e -> e == null ? new ArrayList() : e.list(true));

    protected final IntFunction<String> signFunc = this::prepareParamSign;

    // Flipper.sort转换成以ORDER BY开头SQL的缓存
    protected final Map<String, String> sortOrderbySqls = new ConcurrentHashMap<>();

    // 超过多少毫秒视为较慢, 会打印警告级别的日志, 默认值: 2000
    protected long slowmsWarn;

    // 超过多少毫秒视为很慢, 会打印错误级别的日志, 默认值: 3000
    protected long slowmsError;

    // 是否非阻塞式, 非阻塞模式下不会在runWork里执行结果回调, 默认值: false
    protected boolean clientNonBlocking;

    // 用于反向LIKE使用
    protected String containSQL;

    // 用于反向LIKE使用
    protected String notContainSQL;

    // 用于判断表不存在的使用, 多个SQLState用;隔开
    protected String tableNotExistSqlstates;

    // 用于复制表结构使用, sql语句必须包含IF NOT EXISTS判断，确保重复执行不会报错
    protected String tablecopySQL;

    protected AnyValue config;

    private EntityInfo currEntityInfo;

    public AbstractDataSqlSource() {}

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
        this.config = conf;
        if (conf.getAnyValue("read") == null) { // 没有读写分离
            Properties rwConf = new Properties();
            conf.forEach((k, v) -> rwConf.put(k, decryptProperty(k, v)));
            this.dbtype = parseDbtype(rwConf.getProperty(DATA_SOURCE_URL));
            initProperties(rwConf);
            this.readConfProps = rwConf;
            this.writeConfProps = rwConf;
        } else { // 读写分离
            Properties readConf = new Properties();
            Properties writeConf = new Properties();
            conf.getAnyValue("read").forEach((k, v) -> readConf.put(k, decryptProperty(k, v)));
            conf.getAnyValue("write").forEach((k, v) -> writeConf.put(k, decryptProperty(k, v)));
            this.dbtype = parseDbtype(readConf.getProperty(DATA_SOURCE_URL));
            initProperties(readConf);
            initProperties(writeConf);
            this.readConfProps = readConf;
            this.writeConfProps = writeConf;
        }
        this.name = conf.getValue("name", "");
        this.sqlFormatter = (info, val) -> formatValueToString(info, val);
        afterResourceChange();
    }

    protected void afterResourceChange() {
        this.containSQL =
                readConfProps.getProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "LOCATE(#{keystr}, #{column}) > 0");
        this.notContainSQL =
                readConfProps.getProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "LOCATE(#{keystr}, #{column}) = 0");

        this.tableNotExistSqlstates =
                ";" + readConfProps.getProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42000;42S02") + ";";
        this.tablecopySQL = readConfProps.getProperty(
                DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE IF NOT EXISTS #{newtable} LIKE #{oldtable}");

        this.autoDDL = "true".equals(readConfProps.getProperty(DATA_SOURCE_TABLE_AUTODDL, "false"));
        this.cacheForbidden = "NONE".equalsIgnoreCase(readConfProps.getProperty(DATA_SOURCE_CACHEMODE));
        this.clientNonBlocking = "true".equalsIgnoreCase(readConfProps.getProperty(DATA_SOURCE_NON_BLOCKING, "false"));
        this.slowmsWarn = Integer.parseInt(
                readConfProps.getProperty(DATA_SOURCE_SLOWMS_WARN, "2000").trim());
        this.slowmsError = Integer.parseInt(
                readConfProps.getProperty(DATA_SOURCE_SLOWMS_ERROR, "3000").trim());
    }

    protected <T> PageCountSql createPageCountSql(
            EntityInfo<T> info,
            boolean readCache,
            boolean needTotal,
            boolean distinct,
            SelectColumn selects,
            String[] tables,
            Flipper flipper,
            FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final String joinAndWhere = (join == null ? "" : join) + (Utility.isEmpty(where) ? "" : (" WHERE " + where));
        String pageSql = null;
        String countSql = null;
        // 组装pageSql、countSql
        String listSubSql;
        StringBuilder union = new StringBuilder();
        if (tables.length == 1) {
            listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getQueryColumns("a", selects) + " FROM "
                    + tables[0] + " a" + joinAndWhere;
        } else {
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                union.append("SELECT ")
                        .append(info.getQueryColumns("a", selects))
                        .append(" FROM ")
                        .append(table)
                        .append(" a")
                        .append(joinAndWhere);
            }
            listSubSql = "SELECT " + (distinct ? "DISTINCT " : "") + info.getQueryColumns("a", selects) + " FROM ("
                    + (union) + ") a";
        }
        // pageSql
        pageSql = listSubSql + createOrderbySql(info, flipper);
        if (Flipper.hasLimit(flipper)) {
            if ("oracle".equals(dbtype)) {
                int start = flipper.getOffset();
                int end = flipper.getOffset() + flipper.getLimit();
                pageSql = "SELECT * FROM (SELECT T_.*, ROWNUM RN_ FROM (" + pageSql + ") T_) WHERE RN_ BETWEEN " + start
                        + " AND " + end;
            } else if ("sqlserver".equals(dbtype)) {
                int offset = flipper.getOffset();
                int limit = flipper.getLimit();
                pageSql += " OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
            } else { // 按mysql、postgresql、mariadb、h2处理
                pageSql += " LIMIT " + flipper.getLimit() + " OFFSET " + flipper.getOffset();
            }
        }
        // countSql
        if (needTotal) {
            String countSubSql;
            if (tables.length == 1) {
                countSubSql = "SELECT "
                        + (distinct ? "DISTINCT COUNT(" + info.getQueryColumns("a", selects) + ")" : "COUNT(*)")
                        + " FROM " + tables[0] + " a" + joinAndWhere;
            } else {
                countSubSql = "SELECT "
                        + (distinct ? "DISTINCT COUNT(" + info.getQueryColumns("a", selects) + ")" : "COUNT(*)")
                        + " FROM (" + (union) + ") a";
            }
            countSql = countSubSql;
        }
        if (readCache && info.isLoggable(logger, Level.FINEST, pageSql)) {
            String prefix = needTotal ? " querySheet" : " queryList";
            if (countSql != null) {
                logger.finest(info.getType().getSimpleName() + prefix + " count-sql=" + countSql);
            }
            logger.finest(
                    info.getType().getSimpleName() + prefix + (needTotal ? " page-sql=" : " list-sql=") + pageSql);
        }
        return new PageCountSql(pageSql, countSql);
    }

    /**
     * 根据Flipper获取ORDER BY的SQL语句，不存在Flipper或sort字段返回空字符串
     *
     * @param info EntityInfo
     * @param flipper 翻页对象
     * @return String
     */
    protected String createOrderbySql(EntityInfo info, Flipper flipper) {
        if (flipper == null || Utility.isEmpty(flipper.getSort())) {
            return "";
        }
        final String sort = flipper.getSort();
        if (sort.indexOf(';') >= 0 || sort.indexOf('\n') >= 0) {
            return "";
        }
        return sortOrderbySqls.computeIfAbsent(sort.trim(), s -> {
            final StringBuilder sb = new StringBuilder();
            sb.append(" ORDER BY ");
            if (info.getBuilder().isNoAlias()) {
                sb.append(s);
            } else {
                boolean flag = false;
                for (String item : s.split(",")) {
                    item = item.trim();
                    if (item.isEmpty()) {
                        continue;
                    }
                    String[] sub = item.split("\\s+");
                    if (flag) {
                        sb.append(',');
                    }
                    if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                        sb.append(info.getSQLColumn("a", sub[0])).append(" ASC");
                    } else {
                        sb.append(info.getSQLColumn("a", sub[0])).append(" DESC");
                    }
                    flag = true;
                }
            }
            return sb.toString();
        });
    }

    @Override
    @ResourceChanged
    public void onResourceChange(ResourceEvent[] events) {
        if (Utility.isEmpty(events)) {
            return;
        }
        // 不支持读写分离模式的动态切换
        if (readConfProps == writeConfProps
                && (events[0].name().startsWith("read.") || events[0].name().startsWith("write."))) {
            throw new SourceException(
                    "DataSource(name=" + resourceName() + ") not support to change to read/write separation mode");
        }
        if (readConfProps != writeConfProps
                && (!events[0].name().startsWith("read.") && !events[0].name().startsWith("write."))) {
            throw new SourceException(
                    "DataSource(name=" + resourceName() + ") not support to change to non read/write separation mode");
        }

        StringBuilder sb = new StringBuilder();
        if (readConfProps == writeConfProps) {
            List<ResourceEvent> allEvents = new ArrayList<>();
            Properties newProps = new Properties();
            newProps.putAll(this.readConfProps);
            for (ResourceEvent event : events) { // 可能需要解密
                String newValue = decryptProperty(
                        event.name(),
                        event.newValue() == null ? null : event.newValue().toString());
                allEvents.add(ResourceEvent.create(event.name(), newValue, event.oldValue()));
                newProps.put(event.name(), newValue);
                sb.append("DataSource(name=")
                        .append(resourceName())
                        .append(") change '")
                        .append(event.name())
                        .append("' to '")
                        .append(event.coverNewValue())
                        .append("'\r\n");
            }
            updateOneResourceChange(newProps, allEvents.toArray(new ResourceEvent[allEvents.size()]));
            for (ResourceEvent event : allEvents) {
                this.readConfProps.put(event.name(), event.newValue());
            }
        } else {
            List<ResourceEvent> readEvents = new ArrayList<>();
            List<ResourceEvent> writeEvents = new ArrayList<>();
            Properties newReadProps = new Properties();
            newReadProps.putAll(this.readConfProps);
            Properties newWriteProps = new Properties();
            newWriteProps.putAll(this.writeConfProps);
            for (ResourceEvent event : events) {
                if (event.name().startsWith("read.")) {
                    String newName = event.name().substring("read.".length());
                    String newValue =
                            decryptProperty(event.name(), event.newValue().toString());
                    readEvents.add(ResourceEvent.create(newName, newValue, event.oldValue()));
                    newReadProps.put(event.name(), newValue);
                } else {
                    String newName = event.name().substring("write.".length());
                    String newValue =
                            decryptProperty(event.name(), event.newValue().toString());
                    writeEvents.add(ResourceEvent.create(newName, newValue, event.oldValue()));
                    newWriteProps.put(event.name(), newValue);
                }
                sb.append("DataSource(name=")
                        .append(resourceName())
                        .append(") change '")
                        .append(event.name())
                        .append("' to '")
                        .append(event.coverNewValue())
                        .append("'\r\n");
            }
            if (!readEvents.isEmpty()) {
                updateReadResourceChange(newReadProps, readEvents.toArray(new ResourceEvent[readEvents.size()]));
            }
            if (!writeEvents.isEmpty()) {
                updateWriteResourceChange(newWriteProps, writeEvents.toArray(new ResourceEvent[writeEvents.size()]));
            }
            // 更新Properties
            if (!readEvents.isEmpty()) {
                for (ResourceEvent event : readEvents) {
                    this.readConfProps.put(event.name(), event.newValue());
                }
            }
            if (!writeEvents.isEmpty()) {
                for (ResourceEvent event : writeEvents) {
                    this.writeConfProps.put(event.name(), event.newValue());
                }
            }
        }
        afterResourceChange();
        if (sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    @Override
    protected <T> void complete(WorkThread workThread, CompletableFuture<T> future, T value) {
        if (clientNonBlocking) {
            future.complete(value);
        } else {
            super.complete(workThread, future, value);
        }
    }

    protected void updateOneResourceChange(Properties newProps, ResourceEvent[] events) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected void updateReadResourceChange(Properties newReadProps, ResourceEvent[] events) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected void updateWriteResourceChange(Properties newWriteProps, ResourceEvent[] events) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected void slowLog(long startTime, String... sqls) {
        long cost = System.currentTimeMillis() - startTime;
        if (slowmsError > 0 || slowmsWarn > 0) {
            if (slowmsError > 0 && cost > slowmsError) {
                logger.log(
                        Level.SEVERE,
                        DataSource.class.getSimpleName() + "(name='" + resourceName() + "') very slow sql cost " + cost
                                + " ms, content: " + Arrays.toString(sqls));
            } else if (slowmsWarn > 0 && cost > slowmsWarn) {
                logger.log(
                        Level.WARNING,
                        DataSource.class.getSimpleName() + "(name='" + resourceName() + "') slow sql cost " + cost
                                + " ms, content: " + Arrays.toString(sqls));
            }
        }
        if (sqlMonitor != null) {
            sqlMonitor.visitCostTime(this, cost, sqls);
        }
    }

    protected String parseNotExistTableName(SQLException e) {
        String errmsg = e.getMessage();
        char quote = '"';
        String tableName = null;
        int pos = errmsg.indexOf(quote);
        if (pos < 0) {
            quote = '\'';
            pos = errmsg.indexOf(quote);
        }
        if (pos >= 0) {
            int pos2 = errmsg.indexOf(quote, pos + 1);
            if (pos2 > pos) {
                tableName = errmsg.substring(pos + 1, pos2);
            }
        }
        return tableName;
    }

    // 解密可能存在的加密字段, 可重载
    protected String decryptProperty(String key, String value) {
        return value;
    }

    protected void initProperties(Properties props) {
        if ("oracle".equals(this.dbtype)) {
            props.setProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "INSTR(#{keystr}, #{column}) > 0");
            props.setProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "INSTR(#{keystr}, #{column}) = 0");
            if (!props.containsKey(DATA_SOURCE_TABLENOTEXIST_SQLSTATES)) {
                props.setProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42000;42S02");
            }
            if (!props.containsKey(DATA_SOURCE_TABLECOPY_SQLTEMPLATE)) {
                // 注意：此语句复制表结构会导致默认值和主键信息的丢失
                props.setProperty(
                        DATA_SOURCE_TABLECOPY_SQLTEMPLATE,
                        "CREATE TABLE IF NOT EXISTS #{newtable} AS SELECT * FROM #{oldtable} WHERE 1=2");
            }
        } else if ("sqlserver".equals(this.dbtype)) {
            props.setProperty(DATA_SOURCE_CONTAIN_SQLTEMPLATE, "CHARINDEX(#{column}, #{keystr}) > 0");
            props.setProperty(DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE, "CHARINDEX(#{column}, #{keystr}) = 0");
        } else if ("postgresql".equals(this.dbtype)) {
            if (!props.containsKey(DATA_SOURCE_TABLECOPY_SQLTEMPLATE)) { // 注意：此语句复制表结构会导致默认值和主键信息的丢失
                // 注意：postgresql不支持跨库复制表结构
                // props.setProperty(DATA_SOURCE_TABLECOPY_SQLTEMPLATE, "CREATE TABLE #{newtable} AS (SELECT * FROM
                // #{oldtable} LIMIT 0)");
                props.setProperty(
                        DATA_SOURCE_TABLECOPY_SQLTEMPLATE,
                        "CREATE TABLE IF NOT EXISTS #{newtable} (LIKE #{oldtable} "
                                + "INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING COMMENTS INCLUDING INDEXES)");
            }
            if (!props.containsKey(DATA_SOURCE_TABLENOTEXIST_SQLSTATES)) {
                props.setProperty(DATA_SOURCE_TABLENOTEXIST_SQLSTATES, "42P01;3F000");
            }
        }
    }

    @Override
    public String toString() {
        if (readConfProps == null) { // compileMode模式下会为null
            return getClass().getSimpleName() + "{}";
        }
        if (readConfProps == writeConfProps) {
            String url = readConfProps.getProperty(DATA_SOURCE_URL);
            int pos = url.indexOf('?');
            if (pos > 0) {
                url = url.substring(0, pos) + "...";
            }
            String nb = clientNonBlocking ? (", " + DATA_SOURCE_NON_BLOCKING + "=true") : "";
            return getClass().getSimpleName() + "{url=" + url + ", maxconns=" + readMaxConns() + ", dbtype=" + dbtype()
                    + nb + ", " + DATA_SOURCE_TABLE_AUTODDL + "=" + autoDDL + executorToString() + "}";
        } else {
            String readUrl = readConfProps.getProperty(DATA_SOURCE_URL);
            int pos = readUrl.indexOf('?');
            if (pos > 0) {
                readUrl = readUrl.substring(0, pos) + "...";
            }
            String writeUrl = writeConfProps.getProperty(DATA_SOURCE_URL);
            pos = writeUrl.indexOf('?');
            if (pos > 0) {
                writeUrl = writeUrl.substring(0, pos) + "...";
            }
            return getClass().getSimpleName() + "{read-url=" + readUrl + ", read-maxconns=" + readMaxConns()
                    + ",write-url=" + writeUrl + ", write-maxconns=" + writeMaxConns()
                    + ", dbtype=" + dbtype() + ", " + DATA_SOURCE_TABLE_AUTODDL + "=" + autoDDL + executorToString()
                    + "}";
        }
    }

    protected abstract int readMaxConns();

    protected abstract int writeMaxConns();

    // 生成创建表的SQL
    protected <T> String[] createTableSqls(EntityInfo<T> info) {
        if (info == null || !autoDDL) {
            return null;
        }
        Table table = info.getType().getAnnotation(Table.class);
        if ("mysql".equals(dbtype())) { // mysql
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS `")
                    .append(info.getOriginTable())
                    .append("`(\n");
            EntityColumn primary = null;
            T one = !info.getBuilder().hasConstructorAttribute()
                    ? info.getCreator().create()
                    : null;
            for (EntityColumn column : info.getDDLColumns()) {
                if (column.isPrimary()) {
                    primary = column;
                }
                String sqltype = "VARCHAR(" + column.getLength() + ")";
                String sqlnull = column.isPrimary() ? "NOT NULL" : "NULL";
                if (column.getType() == boolean.class || column.getType() == Boolean.class) {
                    sqltype = "TINYINT(1)";
                    Boolean val = one == null
                            ? null
                            : (Boolean) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val != null && val ? 1 : 0);
                } else if (column.getType() == byte.class || column.getType() == Byte.class) {
                    sqltype = "TINYINT";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.byteValue());
                } else if (column.getType() == short.class || column.getType() == Short.class) {
                    sqltype = "SMALLINT";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == char.class || column.getType() == Character.class) {
                    sqltype = "SMALLINT UNSIGNED";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.intValue());
                } else if (column.getType() == int.class
                        || column.getType() == Integer.class
                        || column.getType() == AtomicInteger.class) {
                    sqltype = "INT";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == long.class
                        || column.getType() == Long.class
                        || column.getType() == AtomicLong.class
                        || column.getType() == LongAdder.class) {
                    sqltype = "BIGINT";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == float.class || column.getType() == Float.class) {
                    sqltype = "FLOAT";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == double.class || column.getType() == Double.class) {
                    sqltype = "DOUBLE";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == BigInteger.class) {
                    sqltype = "DECIMAL";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    } else {
                        sqltype += "(19,2)";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == BigDecimal.class) {
                    sqltype = "DECIMAL";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == String.class) {
                    if (column.getLength() < 65535) {
                        String val = one == null
                                ? null
                                : (String) info.getAttribute(column.getField()).get(one);
                        if (val != null) {
                            sqlnull = "NOT NULL DEFAULT '" + val.replace('\'', '"') + "'";
                        } else if (column.isPrimary()) {
                            sqlnull = "NOT NULL DEFAULT ''";
                        }
                    } else if (column.getLength() == 65535) {
                        sqltype = "TEXT";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    } else if (column.getLength() <= 16777215) {
                        sqltype = "MEDIUMTEXT";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    } else {
                        sqltype = "LONGTEXT";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    }
                } else if (column.getType() == byte[].class) {
                    if (column.getLength() <= 65535) {
                        sqltype = "BLOB";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    } else if (column.getLength() <= 16777215) {
                        sqltype = "MEDIUMBLOB";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    } else {
                        sqltype = "LONGBLOB";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    }
                } else if (column.getType() == java.time.LocalDate.class
                        || column.getType() == java.util.Date.class
                        || "java.sql.Date".equals(column.getType().getName())) {
                    sqltype = "DATE";
                } else if (column.getType() == java.time.LocalTime.class
                        || "java.sql.Time".equals(column.getType().getName())) {
                    sqltype = "TIME";
                } else if (column.getType() == java.time.LocalDateTime.class
                        || "java.sql.Timestamp".equals(column.getType().getName())) {
                    sqltype = "DATETIME";
                } else { // JavaBean
                    sqltype = column.getLength() >= 65535 ? "TEXT" : ("VARCHAR(" + column.getLength() + ")");
                    sqlnull = !column.isNullable() ? "NOT NULL DEFAULT ''" : "NULL";
                }
                sb.append("   `")
                        .append(column.getColumn())
                        .append("` ")
                        .append(sqltype)
                        .append(column.isPrimary() && info.isAutoGenerated() ? " AUTO_INCREMENT " : " ")
                        .append(column.isPrimary() && info.isAutoGenerated() ? "" : sqlnull);
                if (column.getComment() != null && !column.getComment().isEmpty()) {
                    sb.append(" COMMENT '")
                            .append(column.getComment().replace('\'', '"'))
                            .append("'");
                }
                sb.append(",\n");
            }
            sb.append("   PRIMARY KEY (`").append(primary.getColumn()).append("`)\n");
            sb.append(")ENGINE=InnoDB DEFAULT CHARSET=UTF8MB4");
            if (table != null && !table.comment().isEmpty()) {
                sb.append(" COMMENT '")
                        .append(table.comment().replace('\'', '"'))
                        .append("'");
            }
            return Utility.ofArray(sb.toString());
        } else if ("postgresql".equals(dbtype())) { // postgresql
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE IF NOT EXISTS ")
                    .append(info.getOriginTable())
                    .append("(\n");
            EntityColumn primary = null;
            T one = !info.getBuilder().hasConstructorAttribute()
                    ? info.getCreator().create()
                    : null;
            List<String> comments = new ArrayList<>();
            if (table != null && !table.comment().isEmpty()) {
                comments.add("COMMENT ON TABLE " + info.getOriginTable() + " IS '"
                        + table.comment().replace('\'', '"') + "'");
            }
            for (EntityColumn column : info.getDDLColumns()) {
                if (column.isPrimary()) {
                    primary = column;
                }
                String sqltype = "VARCHAR(" + column.getLength() + ")";
                String sqlnull = column.isPrimary() ? "NOT NULL" : "NULL";
                if (column.getType() == boolean.class || column.getType() == Boolean.class) {
                    sqltype = "BOOL";
                    Boolean val = one == null
                            ? null
                            : (Boolean) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val != null && val ? 1 : 0);
                } else if (column.getType() == byte.class || column.getType() == Byte.class) {
                    sqltype = "INT2";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.byteValue());
                } else if (column.getType() == short.class || column.getType() == Short.class) {
                    sqltype = "INT2";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == char.class || column.getType() == Character.class) {
                    sqltype = "INT2 UNSIGNED";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val.intValue());
                } else if (column.getType() == int.class
                        || column.getType() == Integer.class
                        || column.getType() == AtomicInteger.class) {
                    sqltype = "INT4";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == long.class
                        || column.getType() == Long.class
                        || column.getType() == AtomicLong.class
                        || column.getType() == LongAdder.class) {
                    sqltype = "INT8";
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == float.class || column.getType() == Float.class) {
                    sqltype = "FLOAT4";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == double.class || column.getType() == Double.class) {
                    sqltype = "FLOAT8";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == BigInteger.class) {
                    sqltype = "NUMERIC";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    } else {
                        sqltype += "(19,2)";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == BigDecimal.class) {
                    sqltype = "NUMERIC";
                    if (column.getPrecision() > 0) {
                        sqltype += "(" + column.getPrecision() + "," + column.getScale() + ")";
                    }
                    Number val = one == null
                            ? null
                            : (Number) info.getAttribute(column.getField()).get(one);
                    sqlnull = "NOT NULL DEFAULT " + (val == null ? 0 : val);
                } else if (column.getType() == String.class) {
                    if (column.getLength() < 65535) {
                        String val = one == null
                                ? null
                                : (String) info.getAttribute(column.getField()).get(one);
                        if (val != null) {
                            sqlnull = "NOT NULL DEFAULT '" + val.replace('\'', '"') + "'";
                        } else if (column.isPrimary()) {
                            sqlnull = "NOT NULL DEFAULT ''";
                        }
                    } else {
                        sqltype = "TEXT";
                        if (!column.isNullable()) {
                            sqlnull = "NOT NULL";
                        }
                    }
                } else if (column.getType() == byte[].class) {
                    sqltype = "BYTEA";
                    if (!column.isNullable()) {
                        sqlnull = "NOT NULL";
                    }
                } else if (column.getType() == java.time.LocalDate.class
                        || column.getType() == java.util.Date.class
                        || "java.sql.Date".equals(column.getType().getName())) {
                    sqltype = "DATE";
                } else if (column.getType() == java.time.LocalTime.class
                        || "java.sql.Time".equals(column.getType().getName())) {
                    sqltype = "TIME";
                } else if (column.getType() == java.time.LocalDateTime.class
                        || "java.sql.Timestamp".equals(column.getType().getName())) {
                    sqltype = "TIMESTAMP";
                } else { // JavaBean
                    sqltype = column.getLength() >= 65535 ? "TEXT" : ("VARCHAR(" + column.getLength() + ")");
                    sqlnull = !column.isNullable() ? "NOT NULL DEFAULT ''" : "NULL";
                }
                if (column.isPrimary() && info.isAutoGenerated()) {
                    sqltype = "SERIAL";
                }
                sb.append("   ")
                        .append(column.getColumn())
                        .append(" ")
                        .append(sqltype)
                        .append(" ")
                        .append(column.isPrimary() && info.isAutoGenerated() ? "" : sqlnull);
                if (column.getComment() != null && !column.getComment().isEmpty()) {
                    // postgresql不支持DDL中直接带comment
                    comments.add("COMMENT ON COLUMN " + info.getOriginTable() + "." + column.getColumn() + " IS '"
                            + column.getComment().replace('\'', '"') + "'");
                }
                sb.append(",\n");
            }
            sb.append("   PRIMARY KEY (").append(primary.getColumn()).append(")\n");
            sb.append(")");
            return Utility.append(Utility.ofArray(sb.toString()), comments);
        }
        return null;
    }

    protected boolean isTableNotExist(EntityInfo info, Throwable exp, String sqlCode) {
        if (exp.getMessage().contains("syntax")) {
            return false;
        }
        return sqlCode != null && !sqlCode.isEmpty() && tableNotExistSqlstates.contains(';' + sqlCode + ';');
    }

    protected String getTableCopySql(EntityInfo info, String newTable) {
        return tablecopySQL.replace("#{newtable}", newTable).replace("#{oldtable}", info.table);
    }

    protected Serializable getSQLAttrValue(EntityInfo info, Attribute attr, Serializable val) {
        if (val != null
                && !(val instanceof Number)
                && !(val instanceof CharSequence)
                && !(val instanceof java.util.Date)
                && !val.getClass().getName().startsWith("java.sql.")
                && !val.getClass().getName().startsWith("java.time.")) {
            val = info.jsonConvert.convertTo(attr.genericType(), val);
        } else if (val == null && info.isNotNullJson(attr)) {
            val = "";
        }
        return val;
    }

    protected <T> Map<String, PrepareInfo<T>> getInsertQuestionPrepareInfo(EntityInfo<T> info, T... entitys) {
        Map<String, PrepareInfo<T>> map = new LinkedHashMap<>(); // 一定要是LinkedHashMap
        for (T entity : entitys) {
            String table = info.getTable(entity);
            map.computeIfAbsent(table, t -> new PrepareInfo(info.getInsertQuestionPrepareSQL(entity)))
                    .addEntity(entity);
        }
        return map;
    }

    protected <T> Map<String, PrepareInfo<T>> getInsertDollarPrepareInfo(EntityInfo<T> info, T... entitys) {
        Map<String, PrepareInfo<T>> map = new LinkedHashMap<>(); // 一定要是LinkedHashMap
        for (T entity : entitys) {
            String table = info.getTable(entity);
            map.computeIfAbsent(table, t -> new PrepareInfo(info.getInsertDollarPrepareSQL(entity)))
                    .addEntity(entity);
        }
        return map;
    }

    protected <T> Map<String, PrepareInfo<T>> getUpdateQuestionPrepareInfo(EntityInfo<T> info, T... entitys) {
        Map<String, PrepareInfo<T>> map = new LinkedHashMap<>(); // 一定要是LinkedHashMap
        for (T entity : entitys) {
            String table = info.getTable(entity);
            map.computeIfAbsent(table, t -> new PrepareInfo(info.getUpdateQuestionPrepareSQL(entity)))
                    .addEntity(entity);
        }
        return map;
    }

    protected <T> Map<String, PrepareInfo<T>> getUpdateDollarPrepareInfo(EntityInfo<T> info, T... entitys) {
        Map<String, PrepareInfo<T>> map = new LinkedHashMap<>(); // 一定要是LinkedHashMap
        for (T entity : entitys) {
            String table = info.getTable(entity);
            map.computeIfAbsent(table, t -> new PrepareInfo(info.getUpdateDollarPrepareSQL(entity)))
                    .addEntity(entity);
        }
        return map;
    }

    protected <T> Serializable getEntityAttrValue(EntityInfo info, Attribute attr, T entity) {
        Serializable val = info.getSQLValue(attr, entity);
        Class clazz = attr.type();
        if (clazz == String.class
                || clazz == int.class
                || clazz == long.class
                || clazz == Integer.class
                || clazz == Long.class
                || clazz == short.class
                || clazz == Short.class
                || clazz == float.class
                || clazz == Float.class
                || clazz == double.class
                || clazz == Double.class) {
            return val;
        }
        return getSQLAttrValue(info, attr, val);
    }

    protected DataNativeSqlStatement nativeParse(
            String nativeSql, boolean countable, RowBound round, Map<String, Object> params) {
        if (nativeSqlParser == null) {
            throw new SourceException("not found " + DataNativeSqlParser.class.getSimpleName() + " instance");
        }
        return nativeSqlParser.parse(
                signFunc, dbtype(), nativeSql, countable, round, params == null ? Collections.emptyMap() : params);
    }

    @ConvertDisabled
    public IntFunction<String> getSignFunc() {
        return signFunc;
    }

    @Override
    public void destroy(AnyValue config) {
        super.destroy(config);
    }

    @Override
    public <T> void compile(Class<T> clazz) {
        EntityInfo.compile(clazz, this);
    }

    public final String dbtype() {
        if (dbtype == null) {
            throw new NullPointerException("dbtype is null");
        }
        return dbtype;
    }

    public final boolean autoddl() {
        return autoDDL;
    }

    // 是否异步
    protected abstract boolean isAsync();

    // index从1开始
    protected abstract String prepareParamSign(int index);

    // 插入纪录
    protected abstract <T> CompletableFuture<Integer> insertDBAsync(final EntityInfo<T> info, T... entitys);

    // 删除记录
    protected abstract <T> CompletableFuture<Integer> deleteDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            Flipper flipper,
            FilterNode node,
            Map<String, List<Serializable>> pkmap,
            final String... sqls);

    // 清空表
    protected abstract <T> CompletableFuture<Integer> clearTableDBAsync(
            EntityInfo<T> info, String[] tables, FilterNode node, String... sqls);

    // 建表
    protected abstract <T> CompletableFuture<Integer> createTableDBAsync(
            EntityInfo<T> info, String copyTableSql, Serializable pk, String... sqls);

    // 删除表
    protected abstract <T> CompletableFuture<Integer> dropTableDBAsync(
            EntityInfo<T> info, String[] tables, FilterNode node, String... sqls);

    // 更新纪录
    protected abstract <T> CompletableFuture<Integer> updateEntityDBAsync(final EntityInfo<T> info, T... entitys);

    // 更新纪录
    protected abstract <T> CompletableFuture<Integer> updateColumnDBAsync(
            final EntityInfo<T> info, Flipper flipper, final UpdateSqlInfo sql);

    // 查询Number Map数据
    protected abstract <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final FilterNode node,
            final FilterFuncColumn... columns);

    // 查询Number数据
    protected abstract <T> CompletableFuture<Number> getNumberResultDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node);

    // 查询Map数据
    protected abstract <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            FilterNode node);

    // 查询Map数据
    protected abstract <T, K extends Serializable, N extends Number>
            CompletableFuture<Map<K[], N[]>> queryColumnMapDBAsync(
                    final EntityInfo<T> info,
                    String[] tables,
                    final String sql,
                    final ColumnNode[] funcNodes,
                    final String[] groupByColumns,
                    final FilterNode node);

    // 查询单条记录
    protected abstract <T> CompletableFuture<T> findDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final boolean onlypk,
            final SelectColumn selects,
            final Serializable pk,
            final FilterNode node);

    // 查询单条记录的单个字段
    protected abstract <T> CompletableFuture<Serializable> findColumnDBAsync(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final boolean onlypk,
            final String column,
            final Serializable defValue,
            final Serializable pk,
            final FilterNode node);

    // 判断记录是否存在
    protected abstract <T> CompletableFuture<Boolean> existsDBAsync(
            final EntityInfo<T> info,
            final String[] tables,
            final String sql,
            final boolean onlypk,
            final Serializable pk,
            final FilterNode node);

    // 查询一页数据
    protected abstract <T> CompletableFuture<Sheet<T>> querySheetDBAsync(
            final EntityInfo<T> info,
            final boolean readcache,
            final boolean needtotal,
            final boolean distinct,
            final SelectColumn selects,
            final Flipper flipper,
            final FilterNode node);

    // 插入纪录
    protected <T> int insertDB(final EntityInfo<T> info, T... entitys) {
        return insertDBAsync(info, entitys).join();
    }

    // 删除记录
    protected <T> int deleteDB(
            final EntityInfo<T> info,
            String[] tables,
            Flipper flipper,
            FilterNode node,
            Map<String, List<Serializable>> pkmap,
            final String... sqls) {
        return deleteDBAsync(info, tables, flipper, node, pkmap, sqls).join();
    }

    // 清空表
    protected <T> int clearTableDB(final EntityInfo<T> info, String[] tables, FilterNode node, final String... sqls) {
        return clearTableDBAsync(info, tables, node, sqls).join();
    }

    // 建表
    protected <T> int createTableDB(
            final EntityInfo<T> info, String copyTableSql, Serializable pk, final String... sqls) {
        return createTableDBAsync(info, copyTableSql, pk, sqls).join();
    }

    // 删除表
    protected <T> int dropTableDB(final EntityInfo<T> info, String[] tables, FilterNode node, final String... sqls) {
        return dropTableDBAsync(info, tables, node, sqls).join();
    }

    // 更新纪录
    protected <T> int updateEntityDB(final EntityInfo<T> info, T... entitys) {
        return updateEntityDBAsync(info, entitys).join();
    }

    // 更新纪录
    protected <T> int updateColumnDB(final EntityInfo<T> info, Flipper flipper, final UpdateSqlInfo sql) {
        return updateColumnDBAsync(info, flipper, sql).join();
    }

    // 查询Number Map数据
    protected <T, N extends Number> Map<String, N> getNumberMapDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final FilterNode node,
            final FilterFuncColumn... columns) {
        return (Map) getNumberMapDBAsync(info, tables, sql, node, columns).join();
    }

    // 查询Number数据
    protected <T> Number getNumberResultDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node) {
        return getNumberResultDBAsync(info, tables, sql, func, defVal, column, node)
                .join();
    }

    // 查询Map数据
    protected <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMapDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            FilterNode node) {
        return (Map) queryColumnMapDBAsync(info, tables, sql, keyColumn, func, funcColumn, node)
                .join();
    }

    // 查询Map数据
    protected <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMapDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node) {
        return (Map) queryColumnMapDBAsync(info, tables, sql, funcNodes, groupByColumns, node)
                .join();
    }

    // 查询单条记录
    protected <T> T findDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final boolean onlypk,
            final SelectColumn selects,
            final Serializable pk,
            final FilterNode node) {
        return findDBAsync(info, tables, sql, onlypk, selects, pk, node).join();
    }

    // 查询单条记录的单个字段
    protected <T> Serializable findColumnDB(
            final EntityInfo<T> info,
            String[] tables,
            final String sql,
            final boolean onlypk,
            final String column,
            final Serializable defValue,
            final Serializable pk,
            final FilterNode node) {
        return findColumnDBAsync(info, tables, sql, onlypk, column, defValue, pk, node)
                .join();
    }

    // 判断记录是否存在
    protected <T> boolean existsDB(
            final EntityInfo<T> info,
            final String[] tables,
            final String sql,
            final boolean onlypk,
            final Serializable pk,
            final FilterNode node) {
        return existsDBAsync(info, tables, sql, onlypk, pk, node).join();
    }

    // 查询一页数据
    protected <T> Sheet<T> querySheetDB(
            final EntityInfo<T> info,
            final boolean readcache,
            final boolean needtotal,
            final boolean distinct,
            final SelectColumn selects,
            final Flipper flipper,
            final FilterNode node) {
        return querySheetDBAsync(info, readcache, needtotal, distinct, selects, flipper, node)
                .join();
    }

    protected <T> CharSequence createSQLJoin(
            FilterNode node,
            final Function<Class, EntityInfo> func,
            final boolean update,
            final Map<Class, String> joinTabalis,
            final Set<String> haset,
            final EntityInfo<T> info) {
        return node == null ? null : node.createSQLJoin(func, update, joinTabalis, haset, info);
    }

    protected <T> CharSequence createSQLExpress(
            FilterNode node, final EntityInfo<T> info, final Map<Class, String> joinTabalis) {
        return node == null ? null : node.createSQLExpress(this, info, joinTabalis);
    }

    @Override
    public String getType() {
        return "sql";
    }

    @Override
    public EntityInfo apply(Class t) {
        return loadEntityInfo(t);
    }

    @Override
    public void close() throws Exception {}

    protected <T> EntityInfo<T> loadEntityInfo(Class<T> clazz) {
        EntityInfo info = this.currEntityInfo;
        if (info != null && info.getType() == clazz) {
            return info;
        }
        info = loadEntityInfo(clazz, this.cacheForbidden, readConfProps, fullloader);
        this.currEntityInfo = info;
        return info;
    }

    public <T> EntityCache<T> loadCache(Class<T> clazz) {
        EntityInfo<T> info = loadEntityInfo(clazz);
        return info.getCache();
    }

    /**
     * 将entity的对象全部加载到Cache中去，如果clazz没有被@org.redkale.persistence.Cacheable注解则不做任何事
     *
     * @param <T> Entity类泛型
     * @param clazz Entity类
     */
    public <T> void refreshCache(Class<T> clazz) {
        EntityInfo<T> info = loadEntityInfo(clazz);
        EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return;
        }
        cache.fullLoadAsync();
    }

    protected <T> CharSequence formatValueToString(final EntityInfo<T> info, Object value) {
        if (value == null) {
            return null;
        }
        if ("mysql".equals(dbtype)) {
            if (value instanceof CharSequence) {
                return new StringBuilder()
                        .append('\'')
                        .append(value.toString().replace("\\", "\\\\").replace("'", "\\'"))
                        .append('\'')
                        .toString();
            } else if (!(value instanceof Number)
                    && !(value instanceof java.util.Date)
                    && !value.getClass().getName().startsWith("java.sql.")
                    && !value.getClass().getName().startsWith("java.time.")) {
                return new StringBuilder()
                        .append('\'')
                        .append(info.getJsonConvert()
                                .convertTo(value)
                                .replace("\\", "\\\\")
                                .replace("'", "\\'"))
                        .append('\'')
                        .toString();
            }
            return String.valueOf(value);
        } else if (value instanceof CharSequence && "postgresql".equals(dbtype)) {
            String s = String.valueOf(value);
            int pos = s.indexOf('\'');
            if (pos >= 0) {
                return new StringBuilder()
                        .append("E'")
                        .append(value.toString().replace("\\", "\\\\").replace("'", "\\'"))
                        .append('\'')
                        .toString();
            }
        }
        return info.formatSQLValue(value, null);
    }

    // ----------------------------- insert -----------------------------
    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T> Entity类泛型
     * @param entitys Entity对象
     * @return 影响的记录条数
     */
    @Override
    public <T> int insert(T... entitys) {
        if (entitys.length == 0) {
            return 0;
        }
        checkEntity("insert", entitys);
        final EntityInfo<T> info = loadEntityInfo((Class<T>) entitys[0].getClass());
        if (isOnlyCache(info)) {
            return insertCache(info, entitys);
        }
        if (isAsync()) {
            int rs = insertDBAsync(info, entitys).join();
            insertCache(info, entitys);
            return rs;
        } else {
            int rs = insertDB(info, entitys);
            insertCache(info, entitys);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> insertAsync(T... entitys) {
        if (entitys.length == 0) {
            return CompletableFuture.completedFuture(0);
        }
        checkEntity("insert", entitys);
        final EntityInfo<T> info = loadEntityInfo((Class<T>) entitys[0].getClass());
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(insertCache(info, entitys));
        }
        if (isAsync()) {
            return insertDBAsync(info, entitys).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    insertCache(info, entitys);
                }
            });
        } else {
            return supplyAsync(() -> insertDB(info, entitys)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    insertCache(info, entitys);
                }
            });
        }
    }

    protected <T> int insertCache(final EntityInfo<T> info, T... entitys) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return 0;
        }
        int c = 0;
        for (final T value : entitys) {
            c += cache.insert(value);
        }
        return c;
    }

    // ----------------------------- deleteCompose -----------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T> Entity类泛型
     * @param entitys Entity对象
     * @return 删除的数据条数
     */
    @Override
    public <T> int delete(T... entitys) {
        if (entitys.length == 0) {
            return -1;
        }
        checkEntity("delete", entitys);
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
        if (entitys.length == 0) {
            return CompletableFuture.completedFuture(-1);
        }
        checkEntity("delete", entitys);
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
        if (pks.length == 0) {
            return -1;
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return deleteCache(info, -1, pks);
        }
        Map<String, List<Serializable>> pkmap = info.getTableMap(pks);
        String[] tables = pkmap.keySet().toArray(new String[pkmap.size()]);
        String[] sqls = deleteSql(info, pkmap);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " delete sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            int rs = deleteDBAsync(info, tables, null, null, pkmap, sqls).join();
            deleteCache(info, rs, pks);
            return rs;
        } else {
            int rs = deleteDB(info, tables, null, null, pkmap, sqls);
            deleteCache(info, rs, pks);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Serializable... pks) {
        if (pks.length == 0) {
            return CompletableFuture.completedFuture(-1);
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(deleteCache(info, -1, pks));
        }
        Map<String, List<Serializable>> pkmap = info.getTableMap(pks);
        String[] tables = pkmap.keySet().toArray(new String[pkmap.size()]);
        String[] sqls = deleteSql(info, pkmap);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " delete sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            return deleteDBAsync(info, tables, null, null, pkmap, sqls).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, pks);
                }
            });
        } else {
            return supplyAsync(() -> deleteDB(info, tables, null, null, pkmap, sqls))
                    .whenComplete((rs, t) -> {
                        if (t != null) {
                            errorCompleteConsumer.accept(rs, t);
                        } else {
                            deleteCache(info, rs, pks);
                        }
                    });
        }
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
        if (isOnlyCache(info)) {
            return deleteCache(info, -1, flipper, node);
        }
        String[] tables = info.getTables(node);
        String[] sqls = deleteSql(info, tables, flipper, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " delete sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            int rs = deleteDBAsync(info, tables, flipper, node, null, sqls).join();
            deleteCache(info, rs, flipper, sqls);
            return rs;
        } else {
            int rs = deleteDB(info, tables, flipper, node, null, sqls);
            deleteCache(info, rs, flipper, sqls);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> deleteAsync(final Class<T> clazz, final Flipper flipper, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(deleteCache(info, -1, flipper, node));
        }
        String[] tables = info.getTables(node);
        String[] sqls = deleteSql(info, tables, flipper, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " delete sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            return deleteDBAsync(info, tables, flipper, node, null, sqls).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    deleteCache(info, rs, flipper, node);
                }
            });
        } else {
            return supplyAsync(() -> deleteDB(info, tables, flipper, node, null, sqls))
                    .whenComplete((rs, t) -> {
                        if (t != null) {
                            errorCompleteConsumer.accept(rs, t);
                        } else {
                            deleteCache(info, rs, flipper, node);
                        }
                    });
        }
    }

    protected <T> String[] deleteSql(final EntityInfo<T> info, final Map<String, List<Serializable>> pkmap) {
        List<String> sqls = new ArrayList<>();
        final String pkSQLColumn = info.getPrimarySQLColumn();
        pkmap.forEach((table, pks) -> {
            String sql;
            if (pks.size() == 1) {
                sql = "DELETE FROM " + table + " WHERE " + pkSQLColumn + " = "
                        + info.formatSQLValue(pkSQLColumn, pks.get(0), sqlFormatter);
            } else {
                sql = "DELETE FROM " + table + " WHERE " + pkSQLColumn + " IN (";
                for (int i = 0; i < pks.size(); i++) {
                    if (i > 0) {
                        sql += ',';
                    }
                    sql += info.formatSQLValue(pkSQLColumn, pks.get(i), sqlFormatter);
                }
                sql += ")";
            }
            sqls.add(sql);
        });
        return sqls.toArray(new String[sqls.size()]);
    }

    protected <T> String[] deleteSql(
            final EntityInfo<T> info, String[] tables, final Flipper flipper, final FilterNode node) {
        Map<Class, String> joinTabalis;
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
            join1 = multiSplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multiSplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        final String join2AndWhere = ((where == null || where.length() == 0)
                ? (join2 == null ? "" : (" WHERE " + join2))
                : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));

        if ("postgresql".equals(dbtype()) && flipper != null && flipper.getLimit() > 0) {
            List<String> sqls = new ArrayList<>();
            for (String table : tables) {
                String sql = "DELETE FROM " + table + " a" + (join1 == null ? "" : (", " + join1))
                        + " WHERE " + info.getPrimarySQLColumn() + " IN (SELECT " + info.getPrimaryField() + " FROM "
                        + table
                        + join2AndWhere + createOrderbySql(info, flipper) + " OFFSET 0 LIMIT " + flipper.getLimit()
                        + ")";
                sqls.add(sql);
            }
            return sqls.toArray(new String[sqls.size()]);
        } else {
            boolean mysql = "mysql".equals(dbtype());
            List<String> sqls = new ArrayList<>();
            for (String table : tables) {
                String sql = "DELETE " + (mysql ? "a" : "") + " FROM " + table + " a"
                        + (join1 == null ? "" : (", " + join1)) + join2AndWhere + createOrderbySql(info, flipper)
                        + ((mysql && flipper != null && flipper.getLimit() > 0)
                                ? (" LIMIT " + flipper.getLimit())
                                : "");
                sqls.add(sql);
            }
            return sqls.toArray(new String[sqls.size()]);
        }
    }

    // ----------------------------- clearTableCompose -----------------------------
    @Override
    public <T> int clearTable(Class<T> clazz) {
        return clearTable(clazz, (FilterNode) null);
    }

    @Override
    public <T> int clearTable(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return clearTableCache(info, node);
        }
        final String[] tables = info.getTables(node);
        String[] sqls = clearTableSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " clearTable sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            int rs = clearTableDBAsync(info, tables, node, sqls).join();
            clearTableCache(info, node);
            return rs;
        } else {
            int rs = clearTableDB(info, tables, node, sqls);
            clearTableCache(info, node);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> clearTableAsync(final Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(clearTableCache(info, node));
        }
        final String[] tables = info.getTables(node);
        String[] sqls = clearTableSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " clearTable sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            return clearTableDBAsync(info, tables, node, sqls).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    clearTableCache(info, node);
                }
            });
        } else {
            return supplyAsync(() -> clearTableDB(info, tables, node, sqls)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    clearTableCache(info, node);
                }
            });
        }
    }

    protected <T> String[] clearTableSql(final EntityInfo<T> info, String[] tables, final FilterNode node) {
        List<String> sqls = new ArrayList<>();
        for (String table : tables) {
            sqls.add("TRUNCATE TABLE " + table);
        }
        return sqls.toArray(new String[sqls.size()]);
    }

    // ----------------------------- dropTable -----------------------------
    @Override
    public <T> int createTable(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final String[] sqls = createTableSqls(info);
        if (sqls == null) {
            return -1;
        }
        String copyTableSql = info.getTableStrategy() == null ? null : getTableCopySql(info, info.getTable(pk));
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " createTable sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            int rs = createTableDBAsync(info, copyTableSql, pk, sqls).join();
            return rs;
        } else {
            int rs = createTableDB(info, copyTableSql, pk, sqls);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> createTableAsync(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final String[] sqls = createTableSqls(info);
        if (sqls == null) {
            return CompletableFuture.completedFuture(-1);
        }
        String copyTableSql = info.getTableStrategy() == null ? null : getTableCopySql(info, info.getTable(pk));
        if (copyTableSql == null) {
            if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
                logger.finest(info.getType().getSimpleName() + " createTable sql=" + Arrays.toString(sqls));
            }
        } else {
            if (info.isLoggable(logger, Level.FINEST, copyTableSql)) {
                logger.finest(info.getType().getSimpleName() + " createTable sql=" + copyTableSql);
            }
        }
        if (isAsync()) {
            return createTableDBAsync(info, copyTableSql, pk, sqls).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                }
            });
        } else {
            return supplyAsync(() -> createTableDB(info, copyTableSql, pk, sqls))
                    .whenComplete((rs, t) -> {
                        if (t != null) {
                            errorCompleteConsumer.accept(rs, t);
                        }
                    });
        }
    }

    // ----------------------------- dropTable -----------------------------
    @Override
    public <T> int dropTable(Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return dropTableCache(info, node);
        }
        final String[] tables = info.getTables(node);
        String[] sqls = dropTableSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " dropTable sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            int rs = dropTableDBAsync(info, tables, node, sqls).join();
            dropTableCache(info, node);
            return rs;
        } else {
            int rs = dropTableDB(info, tables, node, sqls);
            dropTableCache(info, node);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> dropTableAsync(final Class<T> clazz, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(dropTableCache(info, node));
        }
        final String[] tables = info.getTables(node);
        String[] sqls = dropTableSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sqls[0])) {
            logger.finest(info.getType().getSimpleName() + " dropTable sql=" + Arrays.toString(sqls));
        }
        if (isAsync()) {
            return dropTableDBAsync(info, tables, node, sqls).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    dropTableCache(info, node);
                }
            });
        } else {
            return supplyAsync(() -> dropTableDB(info, tables, node, sqls)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    dropTableCache(info, node);
                }
            });
        }
    }

    protected <T> String[] dropTableSql(final EntityInfo<T> info, String[] tables, final FilterNode node) {
        List<String> sqls = new ArrayList<>();
        for (String table : tables) {
            sqls.add("DROP TABLE IF EXISTS " + table);
        }
        return sqls.toArray(new String[sqls.size()]);
    }

    protected <T> int clearTableCache(final EntityInfo<T> info, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        return cache.clear();
    }

    protected <T> int dropTableCache(final EntityInfo<T> info, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        return cache.drop();
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Flipper flipper, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        Serializable[] ids = cache.delete(flipper, node);
        return count >= 0 ? count : (ids == null ? 0 : ids.length);
    }

    protected <T> int deleteCache(final EntityInfo<T> info, int count, Serializable... pks) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        int c = 0;
        for (Serializable key : pks) {
            c += cache.delete(key);
        }
        return count >= 0 ? count : c;
    }

    protected static StringBuilder multiSplit(
            char ch1, char ch2, String split, StringBuilder sb, String str, int from) {
        if (str == null) {
            return sb;
        }
        int pos1 = str.indexOf(ch1, from);
        if (pos1 < 0) {
            return sb;
        }
        int pos2 = str.indexOf(ch2, from);
        if (pos2 < 0) {
            return sb;
        }
        if (sb.length() > 0) {
            sb.append(split);
        }
        sb.append(str.substring(pos1 + 1, pos2));
        return multiSplit(ch1, ch2, split, sb, str, pos2 + 1);
    }

    // ---------------------------- update ----------------------------
    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T> Entity类泛型
     * @param entitys Entity对象
     * @return 更新的数据条数
     */
    @Override
    public <T> int update(T... entitys) {
        if (entitys.length == 0) {
            return -1;
        }
        checkEntity("update", entitys);
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return updateCache(info, -1, entitys);
        }
        if (isAsync()) {
            int rs = updateEntityDBAsync(info, entitys).join();
            updateCache(info, rs, entitys);
            return rs;
        } else {
            int rs = updateEntityDB(info, entitys);
            updateCache(info, rs, entitys);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateAsync(final T... entitys) {
        if (entitys.length == 0) {
            return CompletableFuture.completedFuture(-1);
        }
        checkEntity("update", entitys);
        final Class<T> clazz = (Class<T>) entitys[0].getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, entitys));
        }
        if (isAsync()) {
            return updateEntityDBAsync(info, entitys).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, entitys);
                }
            });
        } else {
            return supplyAsync(() -> updateEntityDB(info, entitys)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, entitys);
                }
            });
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T> Entity类的泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, Serializable pk, String column, Serializable colval) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return updateCache(info, -1, pk, column, colval);
        }

        UpdateSqlInfo sql = updateColumnSql(info, pk, column, colval);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, pk, column, colval);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, pk, column, colval);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, Serializable pk, String column, Serializable colval) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, pk, column, colval));
        }

        UpdateSqlInfo sql = updateColumnSql(info, pk, column, colval);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, column, colval);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, column, colval);
                }
            });
        }
    }

    protected <T> UpdateSqlInfo updateColumnSql(
            final EntityInfo<T> info, Serializable pk, String column, final Serializable colval) {
        Attribute attr = info.getAttribute(column);
        Serializable val = getSQLAttrValue(info, attr, colval);
        if (val instanceof byte[]) {
            return new UpdateSqlInfo(
                    true,
                    "UPDATE " + info.getTable(pk) + " SET " + info.getSQLColumn(null, column) + "="
                            + prepareParamSign(1) + " WHERE " + info.getPrimarySQLColumn() + "="
                            + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter),
                    (byte[]) val);
        } else {
            return new UpdateSqlInfo(
                    false,
                    "UPDATE " + info.getTable(pk) + " SET " + info.getSQLColumn(null, column) + "="
                            + info.formatSQLValue(column, val, sqlFormatter)
                            + " WHERE " + info.getPrimarySQLColumn() + "="
                            + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter));
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T> Entity类的泛型
     * @param clazz Entity类
     * @param column 过滤字段名
     * @param colval 过滤字段值
     * @param node 过滤node 不能为null
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(Class<T> clazz, String column, Serializable colval, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return updateCache(info, -1, column, colval, node);
        }

        UpdateSqlInfo sql = updateColumnSql(info, column, colval, node);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, column, colval, node);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, column, colval, node);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, String column, Serializable colval, FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, column, colval, node));
        }
        UpdateSqlInfo sql = updateColumnSql(info, column, colval, node);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, column, colval, node);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, column, colval, node);
                }
            });
        }
    }

    protected <T> UpdateSqlInfo updateColumnSql(
            final EntityInfo<T> info, String column, Serializable colval, FilterNode node) {
        Map<Class, String> joinTabalis = node.getJoinTabalis();
        CharSequence join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
        CharSequence where = node.createSQLExpress(this, info, joinTabalis);

        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multiSplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multiSplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        Attribute attr = info.getAttribute(column);
        Serializable val = getSQLAttrValue(info, attr, colval);
        String alias = "postgresql".equals(dbtype()) ? null : "a"; // postgresql的BUG， UPDATE的SET中不能含别名
        String[] tables = info.getTables(node);
        String sql;
        if (val instanceof byte[]) {
            sql = "UPDATE " + tables[0] + " a " + (join1 == null ? "" : (", " + join1))
                    + " SET " + info.getSQLColumn(alias, column) + "=" + prepareParamSign(1)
                    + ((where == null || where.length() == 0)
                            ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return new UpdateSqlInfo(true, sql, tables.length == 1 ? null : tables, (byte[]) val);
        } else {
            sql = "UPDATE " + tables[0] + " a " + (join1 == null ? "" : (", " + join1))
                    + " SET " + info.getSQLColumn(alias, column) + "=" + info.formatSQLValue(val, sqlFormatter)
                    + ((where == null || where.length() == 0)
                            ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return new UpdateSqlInfo(false, sql, tables.length == 1 ? null : tables);
        }
    }

    /**
     * 根据主键值更新对象的多个column对应的值， 必须是Entity Class
     *
     * @param <T> Entity类的泛型
     * @param clazz Entity类
     * @param pk 主键值
     * @param values 字段值
     * @return 更新的数据条数
     */
    @Override
    public <T> int updateColumn(final Class<T> clazz, final Serializable pk, final ColumnValue... values) {
        if (Utility.isEmpty(values)) {
            return -1;
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return updateCache(info, -1, pk, values);
        }

        UpdateSqlInfo sql = updateColumnSql(info, pk, values);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, pk, values);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, pk, values);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final Class<T> clazz, Serializable pk, ColumnValue... values) {
        if (Utility.isEmpty(values)) {
            return CompletableFuture.completedFuture(-1);
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, pk, values));
        }
        UpdateSqlInfo sql = updateColumnSql(info, pk, values);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, values);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, pk, values);
                }
            });
        }
    }

    protected <T> UpdateSqlInfo updateColumnSql(
            final EntityInfo<T> info, final Serializable pk, final ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        for (ColumnValue col : values) {
            if (col == null) {
                continue;
            }
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) {
                throw new SourceException(info.getType() + " cannot found column " + col.getColumn());
            }
            if (setsql.length() > 0) {
                setsql.append(", ");
            }
            String sqlColumn = info.getSQLColumn(null, col.getColumn());
            if (col.getValue() instanceof ColumnBytesNode) {
                if (blobs == null) {
                    blobs = new ArrayList<>();
                }
                blobs.add(((ColumnBytesNode) col.getValue()).getValue());
                setsql.append(sqlColumn).append("=").append(prepareParamSign(++index));
            } else {
                setsql.append(sqlColumn).append("=").append(info.formatSQLValue(sqlColumn, attr, col, sqlFormatter));
            }
        }
        if (setsql.length() < 1) {
            throw new SourceException("update non column-value array");
        }
        String sql = "UPDATE " + info.getTable(pk) + " SET " + setsql + " WHERE " + info.getPrimarySQLColumn() + "="
                + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
        return new UpdateSqlInfo(false, sql, blobs);
    }

    @Override
    public <T> int updateColumn(
            final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values) {
        if (Utility.isEmpty(values)) {
            return -1;
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return updateCache(info, -1, node, flipper, values);
        }
        UpdateSqlInfo sql = updateColumnSql(info, node, flipper, values);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, node, flipper, values);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, node, flipper, values);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values) {
        if (Utility.isEmpty(values)) {
            return CompletableFuture.completedFuture(-1);
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, node, flipper, values));
        }
        UpdateSqlInfo sql = updateColumnSql(info, node, flipper, values);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, node, flipper, values);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, node, flipper, values);
                }
            });
        }
    }

    protected <T> UpdateSqlInfo updateColumnSql(
            final EntityInfo<T> info, FilterNode node, Flipper flipper, ColumnValue... values) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        boolean pgsql = "postgresql".equals(dbtype());
        String alias = pgsql ? null : "a"; // postgresql的BUG， UPDATE的SET中不能含别名
        for (ColumnValue col : values) {
            if (col == null) {
                continue;
            }
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) {
                continue;
            }
            if (setsql.length() > 0) {
                setsql.append(", ");
            }
            String sqlColumn = info.getSQLColumn(alias, col.getColumn());
            if (col.getValue() instanceof ColumnBytesNode) {
                if (blobs == null) {
                    blobs = new ArrayList<>();
                }
                blobs.add(((ColumnBytesNode) col.getValue()).getValue());
                setsql.append(sqlColumn).append("=").append(prepareParamSign(++index));
            } else {
                setsql.append(sqlColumn).append("=").append(info.formatSQLValue(sqlColumn, attr, col, sqlFormatter));
            }
        }
        if (setsql.length() < 1) {
            throw new SourceException("update non column-value array");
        }
        Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        CharSequence join = node == null ? null : node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
        CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        StringBuilder join1 = null;
        StringBuilder join2 = null;
        if (join != null) {
            String joinstr = join.toString();
            join1 = multiSplit('[', ']', ",", new StringBuilder(), joinstr, 0);
            join2 = multiSplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
        }
        String sql;
        String[] tables = info.getTables(node);
        if (pgsql && flipper != null && flipper.getLimit() > 0) {
            String wherestr = ((where == null || where.length() == 0)
                    ? (join2 == null ? "" : (" WHERE " + join2))
                    : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            sql = "UPDATE " + tables[0] + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                    + " WHERE " + info.getPrimarySQLColumn() + " IN (SELECT " + info.getPrimaryField() + " FROM "
                    + tables[0]
                    + wherestr + createOrderbySql(info, flipper) + " OFFSET 0 LIMIT " + flipper.getLimit() + ")";

        } else {
            sql = "UPDATE " + tables[0] + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                    + ((where == null || where.length() == 0)
                            ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))))
                    + createOrderbySql(info, flipper)
                    + (("mysql".equals(dbtype()) && flipper != null && flipper.getLimit() > 0)
                            ? (" LIMIT " + flipper.getLimit())
                            : "");
        }
        return new UpdateSqlInfo(blobs != null, sql, tables.length == 1 ? null : tables, blobs);
    }

    // 返回不存在的字段名,null表示字段都合法;
    protected <T> String checkIllegalColumn(final EntityInfo<T> info, SelectColumn selects) {
        if (selects == null) {
            return null;
        }
        String[] columns = selects.getColumns();
        if (columns == null) {
            return null;
        }
        for (String col : columns) {
            if (info.getAttribute(col) == null) {
                return col;
            }
        }
        return null;
    }

    @Override
    public <T> int updateColumn(final T entity, final SelectColumn selects) {
        if (entity == null || selects == null) {
            return -1;
        }
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            throw new SourceException(info.getType() + " cannot found column " + illegalColumn);
        }
        if (isOnlyCache(info)) {
            return updateCache(info, -1, false, entity, null, selects);
        }

        UpdateSqlInfo sql = updateColumnSql(info, false, entity, null, selects);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, false, entity, null, selects);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, false, entity, null, selects);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(final T entity, final SelectColumn selects) {
        if (entity == null || selects == null) {
            return CompletableFuture.completedFuture(-1);
        }
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            return CompletableFuture.failedFuture(
                    new SourceException(info.getType() + " cannot found column " + illegalColumn));
        }
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, false, entity, null, selects));
        }

        UpdateSqlInfo sql = updateColumnSql(info, false, entity, null, selects);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, false, entity, null, selects);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, false, entity, null, selects);
                }
            });
        }
    }

    @Override
    public <T> int updateColumn(final T entity, final FilterNode node, final SelectColumn selects) {
        if (entity == null || node == null || selects == null) {
            return -1;
        }
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            throw new SourceException(info.getType() + " cannot found column " + illegalColumn);
        }
        if (isOnlyCache(info)) {
            return updateCache(info, -1, true, entity, node, selects);
        }

        UpdateSqlInfo sql = updateColumnSql(info, true, entity, node, selects);
        if (isAsync()) {
            int rs = updateColumnDBAsync(info, null, sql).join();
            updateCache(info, rs, true, entity, node, selects);
            return rs;
        } else {
            int rs = updateColumnDB(info, null, sql);
            updateCache(info, rs, true, entity, node, selects);
            return rs;
        }
    }

    @Override
    public <T> CompletableFuture<Integer> updateColumnAsync(
            final T entity, final FilterNode node, final SelectColumn selects) {
        if (entity == null || node == null || selects == null) {
            return CompletableFuture.completedFuture(-1);
        }
        Class<T> clazz = (Class) entity.getClass();
        final EntityInfo<T> info = loadEntityInfo(clazz);
        String illegalColumn = checkIllegalColumn(info, selects);
        if (illegalColumn != null) {
            return CompletableFuture.failedFuture(
                    new SourceException(info.getType() + " cannot found column " + illegalColumn));
        }
        if (isOnlyCache(info)) {
            return CompletableFuture.completedFuture(updateCache(info, -1, true, entity, node, selects));
        }

        UpdateSqlInfo sql = updateColumnSql(info, true, entity, node, selects);
        if (isAsync()) {
            return updateColumnDBAsync(info, null, sql).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, true, entity, node, selects);
                }
            });
        } else {
            return supplyAsync(() -> updateColumnDB(info, null, sql)).whenComplete((rs, t) -> {
                if (t != null) {
                    errorCompleteConsumer.accept(rs, t);
                } else {
                    updateCache(info, rs, true, entity, node, selects);
                }
            });
        }
    }

    protected <T> UpdateSqlInfo updateColumnSql(
            EntityInfo<T> info, boolean needNode, T entity, FilterNode node, SelectColumn selects) {
        StringBuilder setsql = new StringBuilder();
        List<byte[]> blobs = null;
        int index = 0;
        String alias = "postgresql".equals(dbtype()) ? null : "a"; // postgresql的BUG， UPDATE的SET中不能含别名
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) {
                continue;
            }
            if (setsql.length() > 0) {
                setsql.append(", ");
            }
            setsql.append(info.getSQLColumn(alias, attr.field()));
            Serializable val = info.getFieldValue(attr, entity);
            if (val instanceof byte[]) {
                if (blobs == null) {
                    blobs = new ArrayList<>();
                }
                blobs.add((byte[]) val);
                setsql.append("=").append(prepareParamSign(++index));
            } else {
                CharSequence sqlval = info.formatSQLValue(val, sqlFormatter);
                if (sqlval == null && info.isNotNullJson(attr)) {
                    sqlval = "''";
                }
                setsql.append("=").append(sqlval);
            }
        }
        if (needNode) {
            Map<Class, String> joinTabalis = node.getJoinTabalis();
            CharSequence join = node.createSQLJoin(this, true, joinTabalis, new HashSet<>(), info);
            CharSequence where = node.createSQLExpress(this, info, joinTabalis);
            StringBuilder join1 = null;
            StringBuilder join2 = null;
            if (join != null) {
                String joinstr = join.toString();
                join1 = multiSplit('[', ']', ",", new StringBuilder(), joinstr, 0);
                join2 = multiSplit('{', '}', " AND ", new StringBuilder(), joinstr, 0);
            }
            String sql;
            String[] tables = info.getTables(node);
            sql = "UPDATE " + tables[0] + " a " + (join1 == null ? "" : (", " + join1)) + " SET " + setsql
                    + ((where == null || where.length() == 0)
                            ? (join2 == null ? "" : (" WHERE " + join2))
                            : (" WHERE " + where + (join2 == null ? "" : (" AND " + join2))));
            return new UpdateSqlInfo(blobs != null, sql, tables.length == 1 ? null : tables, blobs);
        } else {
            final Serializable id = (Serializable) info.getSQLValue(info.getPrimary(), entity);
            String sql = "UPDATE " + info.getTable(id) + " a SET " + setsql + " WHERE " + info.getPrimarySQLColumn()
                    + "=" + info.formatSQLValue(id, sqlFormatter);
            return new UpdateSqlInfo(blobs != null, sql, blobs);
        }
    }

    protected <T> int updateCache(
            final EntityInfo<T> info,
            int count,
            final boolean needNode,
            final T entity,
            final FilterNode node,
            final SelectColumn selects) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return count;
        }
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        for (Attribute<T, Serializable> attr : info.updateAttributes) {
            if (!selects.test(attr.field())) {
                continue;
            }
            attrs.add(attr);
        }
        if (needNode) {
            T[] rs = cache.update(entity, attrs, node);
            return count >= 0 ? count : (rs == null ? 0 : rs.length);
        } else {
            T rs = cache.update(entity, attrs);
            return count >= 0 ? count : (rs == null ? 0 : 1);
        }
    }

    protected <T> int updateCache(
            final EntityInfo<T> info,
            int count,
            final FilterNode node,
            final Flipper flipper,
            final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return count;
        }
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            if (col == null) {
                continue;
            }
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) {
                continue;
            }
            attrs.add(attr);
            cols.add(col);
        }
        T[] rs = cache.updateColumn(node, flipper, attrs, cols);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(
            final EntityInfo<T> info, int count, final Serializable pk, final ColumnValue... values) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return count;
        }
        final List<Attribute<T, Serializable>> attrs = new ArrayList<>();
        final List<ColumnValue> cols = new ArrayList<>();
        for (ColumnValue col : values) {
            if (col == null) {
                continue;
            }
            Attribute<T, Serializable> attr = info.getUpdateAttribute(col.getColumn());
            if (attr == null) {
                continue;
            }
            attrs.add(attr);
            cols.add(col);
        }
        T rs = cache.updateColumn(pk, attrs, cols);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(
            final EntityInfo<T> info, int count, String column, final Serializable colval, FilterNode node) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return count;
        }
        T[] rs = cache.update(info.getAttribute(column), colval, node);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(
            final EntityInfo<T> info,
            int count,
            final Serializable pk,
            final String column,
            final Serializable colval) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return count;
        }
        T rs = cache.update(pk, info.getAttribute(column), colval);
        return count >= 0 ? count : (rs == null ? 0 : 1);
    }

    protected <T> int updateCache(final EntityInfo<T> info, int count, T... entitys) {
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        int c2 = 0;
        for (final T value : entitys) {
            c2 += cache.update(value);
        }
        return count >= 0 ? count : c2;
    }

    public <T> int reloadCache(Class<T> clazz, Serializable... pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache == null) {
            return -1;
        }
        String column = info.getPrimary().field();
        int c = 0;
        for (Serializable id : pks) {
            Sheet<T> sheet = querySheet(false, true, false, clazz, null, FLIPPER_ONE, FilterNodes.create(column, id));
            T value = sheet.isEmpty() ? null : sheet.list().get(0);
            if (value != null) {
                c += cache.update(value);
            }
        }
        return c;
    }

    // ------------------------- getNumberMapCompose -------------------------
    @Override
    public <N extends Number> Map<String, N> getNumberMap(
            final Class entityClass, final FilterNode node, final FilterFuncColumn... columns) {
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
        final String[] tables = info.getTables(node);
        String sql = getNumberMapSql(info, tables, node, columns);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " getNumberMap sql=" + sql);
        }
        if (isAsync()) {
            return (Map) getNumberMapDBAsync(info, tables, sql, node, columns).join();
        } else {
            return getNumberMapDB(info, tables, sql, node, columns);
        }
    }

    @Override
    public <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
            Class entityClass, FilterNode node, FilterFuncColumn... columns) {
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
        final String[] tables = info.getTables(node);
        String sql = getNumberMapSql(info, tables, node, columns);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " getNumberMap sql=" + sql);
        }
        if (isAsync()) {
            return getNumberMapDBAsync(info, tables, sql, node, columns);
        } else {
            return supplyAsync(() -> getNumberMapDB(info, tables, sql, node, columns));
        }
    }

    protected <T> String getNumberMapSql(
            final EntityInfo<T> info, final String[] tables, final FilterNode node, final FilterFuncColumn... columns) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        StringBuilder sb = new StringBuilder();
        for (FilterFuncColumn ffc : columns) {
            for (String col : ffc.cols()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(ffc.func.getColumn((isEmpty(col) ? "*" : info.getSQLColumn("a", col))));
            }
        }
        final String sql = "SELECT " + sb + " FROM " + tables[0] + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        return sql;
    }

    protected <T, N extends Number> CompletableFuture<Map<String, N>> getNumberMapDBApply(
            EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, FilterFuncColumn... columns) {
        return future.thenApply((DataResultSet dataset) -> {
            final Map map = new HashMap<>();
            if (dataset.next()) {
                int index = 0;
                for (FilterFuncColumn ffc : columns) {
                    for (String col : ffc.cols()) {
                        Object o = dataset.getObject(++index);
                        Number rs = ffc.getDefvalue();
                        if (o != null) {
                            rs = (Number) o;
                        }
                        map.put(ffc.col(col), rs);
                    }
                }
            }
            dataset.close();
            return map;
        });
    }

    // ------------------------ getNumberResultCompose -----------------------
    @Override
    public Number getNumberResult(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node) {
        final EntityInfo<?> info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.getNumberResult(func, defVal, column, node);
            }
        }

        final String[] tables = info.getTables(node);
        String sql = getNumberResultSql(info, entityClass, tables, func, defVal, column, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " getNumberResult sql=" + sql);
        }
        if (isAsync()) {
            return getNumberResultDBAsync(info, tables, sql, func, defVal, column, node)
                    .join();
        } else {
            return getNumberResultDB(info, tables, sql, func, defVal, column, node);
        }
    }

    @Override
    public CompletableFuture<Number> getNumberResultAsync(
            final Class entityClass,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.getNumberResult(func, defVal, column, node));
            }
        }
        final String[] tables = info.getTables(node);
        String sql = getNumberResultSql(info, entityClass, tables, func, defVal, column, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " getNumberResult sql=" + sql);
        }
        if (isAsync()) {
            return getNumberResultDBAsync(info, tables, sql, func, defVal, column, node);
        } else {
            return supplyAsync(() -> getNumberResultDB(info, tables, sql, func, defVal, column, node));
        }
    }

    protected <T> String getNumberResultSql(
            final EntityInfo<T> info,
            final Class entityClass,
            final String[] tables,
            final FilterFunc func,
            final Number defVal,
            final String column,
            final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String sql = "SELECT " + func.getColumn((isEmpty(column) ? "*" : info.getSQLColumn("a", column)))
                + " FROM " + tables[0] + " a" + (join == null ? "" : join)
                + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        return sql;
    }

    protected <T> CompletableFuture<Number> getNumberResultDBApply(
            EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, Number defVal, String column) {
        return future.thenApply((DataResultSet dataset) -> {
            Number rs = defVal;
            if (dataset.next()) {
                Object o = dataset.getObject(1);
                if (o != null) {
                    rs = (Number) o;
                }
            }
            dataset.close();
            return rs;
        });
    }

    // ------------------------ queryColumnMapCompose ------------------------
    @Override
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.queryColumnMap(keyColumn, func, funcColumn, node);
            }
        }

        final String[] tables = info.getTables(node);
        String sql = AbstractDataSqlSource.this.queryColumnMapSql(info, tables, keyColumn, func, funcColumn, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
        }
        if (isAsync()) {
            return (Map) queryColumnMapDBAsync(info, tables, sql, keyColumn, func, funcColumn, node)
                    .join();
        } else {
            return queryColumnMapDB(info, tables, sql, keyColumn, func, funcColumn, node);
        }
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
            final Class<T> entityClass,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.queryColumnMap(keyColumn, func, funcColumn, node));
            }
        }
        final String[] tables = info.getTables(node);
        String sql = AbstractDataSqlSource.this.queryColumnMapSql(info, tables, keyColumn, func, funcColumn, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
        }
        if (isAsync()) {
            return queryColumnMapDBAsync(info, tables, sql, keyColumn, func, funcColumn, node);
        } else {
            return supplyAsync(() -> queryColumnMapDB(info, tables, sql, keyColumn, func, funcColumn, node));
        }
    }

    protected <T> String queryColumnMapSql(
            final EntityInfo<T> info,
            final String[] tables,
            final String keyColumn,
            final FilterFunc func,
            final String funcColumn,
            FilterNode node) {
        final String keySqlColumn = info.getSQLColumn(null, keyColumn);
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        final String funcSqlColumn = func == null
                ? info.getSQLColumn("a", funcColumn)
                : func.getColumn(
                        (funcColumn == null || funcColumn.isEmpty() ? "*" : info.getSQLColumn("a", funcColumn)));

        String joinAndWhere =
                (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        String sql;
        if (tables.length == 1) {
            sql = "SELECT a." + keySqlColumn + ", " + funcSqlColumn + " FROM " + tables[0] + " a" + joinAndWhere;
        } else {
            int b = 0;
            StringBuilder union = new StringBuilder();
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                union.append("SELECT a.")
                        .append(keySqlColumn)
                        .append(", ")
                        .append(funcSqlColumn)
                        .append(" FROM ")
                        .append(table)
                        .append(" a")
                        .append(joinAndWhere);
            }
            sql = "SELECT a." + keySqlColumn + ", " + funcSqlColumn + " FROM (" + (union) + ") a";
        }
        return sql;
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapDBApply(
            EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, final String keyColumn) {
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
    public <T, K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterNode node) {
        Map<K[], N[]> map = queryColumnMap(entityClass, funcNodes, Utility.ofArray(groupByColumn), node);
        final Map<K, N[]> rs = new LinkedHashMap<>();
        map.forEach((keys, values) -> rs.put(keys[0], values));
        return rs;
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String groupByColumn,
            final FilterNode node) {
        CompletableFuture<Map<K[], N[]>> future =
                queryColumnMapAsync(entityClass, funcNodes, Utility.ofArray(groupByColumn), node);
        return future.thenApply(map -> {
            final Map<K, N[]> rs = new LinkedHashMap<>();
            map.forEach((keys, values) -> rs.put(keys[0], values));
            return rs;
        });
    }

    @Override
    public <T, K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return cache.queryColumnMap(funcNodes, groupByColumns, node);
            }
        }
        final String[] tables = info.getTables(node);
        String sql = queryColumnMapSql(info, tables, funcNodes, groupByColumns, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
        }
        if (isAsync()) {
            return (Map) queryColumnMapDBAsync(info, tables, sql, funcNodes, groupByColumns, node)
                    .join();
        } else {
            return queryColumnMapDB(info, tables, sql, funcNodes, groupByColumns, node);
        }
    }

    @Override
    public <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
            final Class<T> entityClass,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node) {
        final EntityInfo info = loadEntityInfo(entityClass);
        final EntityCache cache = info.getCache();
        if (cache != null && (isOnlyCache(info) || cache.isFullLoaded())) {
            if (node == null || isCacheUseable(node, this)) {
                return CompletableFuture.completedFuture(cache.queryColumnMap(funcNodes, groupByColumns, node));
            }
        }
        final String[] tables = info.getTables(node);
        String sql = queryColumnMapSql(info, tables, funcNodes, groupByColumns, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " queryColumnMap sql=" + sql);
        }
        if (isAsync()) {
            return queryColumnMapDBAsync(info, tables, sql, funcNodes, groupByColumns, node);
        } else {
            return supplyAsync(() -> queryColumnMapDB(info, tables, sql, funcNodes, groupByColumns, node));
        }
    }

    protected <T> String queryColumnMapSql(
            final EntityInfo<T> info,
            final String[] tables,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns,
            final FilterNode node) {
        final StringBuilder groupBySqlColumns = new StringBuilder();
        if (groupByColumns != null && groupByColumns.length > 0) {
            for (String groupByColumn : groupByColumns) {
                if (groupBySqlColumns.length() > 0) {
                    groupBySqlColumns.append(", ");
                }
                groupBySqlColumns.append(info.getSQLColumn("a", groupByColumn));
            }
        }
        final StringBuilder funcSqlColumns = new StringBuilder();
        for (ColumnNode funcNode : funcNodes) {
            if (funcSqlColumns.length() > 0) {
                funcSqlColumns.append(", ");
            }
            if (funcNode instanceof ColumnFuncNode) {
                funcSqlColumns.append(info.formatColumnFuncNodeSQLValue(
                        (Attribute) null, "a", (ColumnFuncNode) funcNode, sqlFormatter));
            } else {
                funcSqlColumns.append(info.formatColumnExpNodeSQLValue(
                        (Attribute) null, "a", (ColumnExpNode) funcNode, sqlFormatter));
            }
        }
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final Set<String> haset = new HashSet<>();
        final CharSequence join = node == null ? null : node.createSQLJoin(this, false, joinTabalis, haset, info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);

        String joinAndWhere =
                (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        String sql;
        if (tables.length == 1) {
            sql = "SELECT ";
            if (groupBySqlColumns.length() > 0) {
                sql += groupBySqlColumns + ", ";
            }
            sql += funcSqlColumns + " FROM " + tables[0] + " a" + joinAndWhere;
        } else {
            StringBuilder union = new StringBuilder();
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                String subsql = "SELECT ";
                if (groupBySqlColumns.length() > 0) {
                    subsql += groupBySqlColumns.toString() + ", ";
                }
                subsql += funcSqlColumns.toString() + " FROM " + table + " a" + joinAndWhere;
                union.append(subsql);
            }
            sql = "SELECT ";
            if (groupBySqlColumns.length() > 0) {
                sql += groupBySqlColumns + ", ";
            }
            sql += funcSqlColumns + " FROM (" + (union) + ") a";
        }
        if (groupBySqlColumns.length() > 0) {
            sql += " GROUP BY " + groupBySqlColumns;
        }
        return sql;
    }

    protected <T, K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapDBApply(
            EntityInfo<T> info,
            CompletableFuture<? extends DataResultSet> future,
            final ColumnNode[] funcNodes,
            final String[] groupByColumns) {
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

    // ----------------------------- find -----------------------------
    @Override
    public <T> T[] finds(Class<T> clazz, final SelectColumn selects, Serializable... pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (pks == null || pks.length == 0) {
            return info.getArrayer().apply(0);
        }
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded()) {
            return selects == null ? cache.finds(pks) : cache.finds(selects, pks);
        }
        return findsDBAsync(info, selects, pks).join();
    }

    @Override
    public <T> CompletableFuture<T[]> findsAsync(Class<T> clazz, final SelectColumn selects, Serializable... pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        if (pks == null || pks.length == 0) {
            return CompletableFuture.completedFuture(info.getArrayer().apply(0));
        }
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded()) {
            return CompletableFuture.completedFuture(selects == null ? cache.finds(pks) : cache.finds(selects, pks));
        }
        return findsDBAsync(info, selects, pks);
    }

    protected <T> CompletableFuture<T[]> findsDBAsync(
            final EntityInfo<T> info, final SelectColumn selects, Serializable... pks) {
        final Attribute<T, Serializable> primary = info.getPrimary();
        return queryListAsync(info.getType(), selects, null, FilterNodes.in(info.getPrimarySQLColumn(), pks))
                .thenApply(list -> {
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
    public <D extends Serializable, T> CompletableFuture<List<T>> findsListAsync(
            final Class<T> clazz, final Stream<D> pks) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        Serializable[] ids = pks.toArray(serialArrayFunc);
        return queryListAsync(info.getType(), null, null, FilterNodes.in(info.getPrimarySQLColumn(), ids));
    }

    @Override
    public <T> T find(Class<T> clazz, final SelectColumn selects, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = selects == null ? cache.find(pk) : cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) {
                return rs;
            }
        }
        return findUnCache(info, selects, pk);
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            T rs = selects == null ? cache.find(pk) : cache.find(selects, pk);
            if (cache.isFullLoaded() || rs != null) {
                return CompletableFuture.completedFuture(rs);
            }
        }
        return findUnCacheAsync(info, selects, pk);
    }

    protected <T> T findUnCache(final EntityInfo<T> info, final SelectColumn selects, final Serializable pk) {
        String[] tables = info.getTableOneArray(pk);
        String sql = AbstractDataSqlSource.this.findSql(info, selects, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        }
        if (isAsync()) {
            return findDBAsync(info, tables, sql, true, selects, pk, null).join();
        } else {
            return findDB(info, tables, sql, true, selects, pk, null);
        }
    }

    protected <T> CompletableFuture<T> findUnCacheAsync(
            final EntityInfo<T> info, final SelectColumn selects, final Serializable pk) {
        String[] tables = info.getTableOneArray(pk);
        String sql = AbstractDataSqlSource.this.findSql(info, selects, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        }
        if (isAsync()) {
            return findDBAsync(info, tables, sql, true, selects, pk, null);
        } else {
            return supplyAsync(() -> findDB(info, tables, sql, true, selects, pk, null));
        }
    }

    protected <T> String findSql(final EntityInfo<T> info, final SelectColumn selects, Serializable pk) {
        String column = info.getPrimarySQLColumn();
        final String sql = "SELECT " + info.getQueryColumns(null, selects) + " FROM " + info.getTable(pk) + " WHERE "
                + column + "=" + info.formatSQLValue(column, pk, sqlFormatter);
        return sql;
    }

    @Override
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || isCacheUseable(node, this))) {
            return cache.find(selects, node);
        }
        final String[] tables = info.getTables(node);
        String sql = findSql(info, tables, selects, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        }
        if (isAsync()) {
            return findDBAsync(info, tables, sql, false, selects, null, node).join();
        } else {
            return findDB(info, tables, sql, false, selects, null, node);
        }
    }

    @Override
    public <T> CompletableFuture<T> findAsync(final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded() && (node == null || isCacheUseable(node, this))) {
            return CompletableFuture.completedFuture(cache.find(selects, node));
        }
        final String[] tables = info.getTables(node);
        String sql = findSql(info, tables, selects, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " find sql=" + sql);
        }
        if (isAsync()) {
            return findDBAsync(info, tables, sql, false, selects, null, node);
        } else {
            return supplyAsync(() -> findDB(info, tables, sql, false, selects, null, node));
        }
    }

    protected <T> String findSql(
            final EntityInfo<T> info, final String[] tables, final SelectColumn selects, final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join =
                node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String joinAndWhere =
                (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        String sql;
        if (tables.length == 1) {
            sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM " + tables[0] + " a" + joinAndWhere;
        } else {
            StringBuilder union = new StringBuilder();
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                union.append("SELECT ")
                        .append(info.getQueryColumns("a", selects))
                        .append(" FROM ")
                        .append(table)
                        .append(" a")
                        .append(joinAndWhere);
            }
            sql = "SELECT " + info.getQueryColumns("a", selects) + " FROM (" + (union) + ") a";
        }
        return sql;
    }

    protected <T> CompletableFuture<T> findDBApply(
            EntityInfo<T> info,
            CompletableFuture<? extends DataResultSet> future,
            boolean onlypk,
            SelectColumn selects) {
        return future.thenApply((DataResultSet pgset) -> {
            T rs = pgset.next()
                    ? (onlypk && selects == null
                            ? getEntityValue(info, null, pgset)
                            : getEntityValue(info, selects, pgset))
                    : null;
            pgset.close();
            return rs;
        });
    }

    @Override
    public <T> Serializable findColumn(
            final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) {
                return val;
            }
        }
        String[] tables = info.getTableOneArray(pk);
        String sql = findColumnSql(info, column, defValue, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " findColumn sql=" + sql);
        }
        if (isAsync()) {
            return findColumnDBAsync(info, tables, sql, true, column, defValue, pk, null)
                    .join();
        } else {
            return findColumnDB(info, tables, sql, true, column, defValue, pk, null);
        }
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, pk);
            if (cache.isFullLoaded() || val != null) {
                return CompletableFuture.completedFuture(val);
            }
        }
        String[] tables = info.getTableOneArray(pk);
        String sql = findColumnSql(info, column, defValue, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " findColumn sql=" + sql);
        }
        if (isAsync()) {
            return findColumnDBAsync(info, tables, sql, true, column, defValue, pk, null);
        } else {
            return supplyAsync(() -> findColumnDB(info, tables, sql, true, column, defValue, pk, null));
        }
    }

    protected <T> String findColumnSql(
            final EntityInfo<T> info, String column, final Serializable defValue, final Serializable pk) {
        return "SELECT " + info.getSQLColumn(null, column) + " FROM " + info.getTable(pk) + " WHERE "
                + info.getPrimarySQLColumn() + "=" + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
    }

    @Override
    public <T> Serializable findColumn(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) {
                return val;
            }
        }
        final String[] tables = info.getTables(node);
        String sql = findColumnSql(info, tables, column, defValue, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " findColumn sql=" + sql);
        }
        if (isAsync()) {
            return findColumnDBAsync(info, tables, sql, false, column, defValue, null, node)
                    .join();
        } else {
            return findColumnDB(info, tables, sql, false, column, defValue, null, node);
        }
    }

    @Override
    public <T> CompletableFuture<Serializable> findColumnAsync(
            final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            Serializable val = cache.findColumn(column, defValue, node);
            if (cache.isFullLoaded() || val != null) {
                return CompletableFuture.completedFuture(val);
            }
        }
        final String[] tables = info.getTables(node);
        String sql = findColumnSql(info, tables, column, defValue, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " findColumn sql=" + sql);
        }
        if (isAsync()) {
            return findColumnDBAsync(info, tables, sql, false, column, defValue, null, node);
        } else {
            return supplyAsync(() -> findColumnDB(info, tables, sql, false, column, defValue, null, node));
        }
    }

    protected <T> String findColumnSql(
            final EntityInfo<T> info,
            String[] tables,
            String column,
            final Serializable defValue,
            final FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join =
                node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String joinAndWhere =
                (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        String sql;
        if (tables.length == 1) {
            sql = "SELECT " + info.getSQLColumn("a", column) + " FROM " + tables[0] + " a" + joinAndWhere;
        } else {
            StringBuilder union = new StringBuilder();
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                union.append("SELECT ")
                        .append(info.getSQLColumn("a", column))
                        .append(" FROM ")
                        .append(table)
                        .append(" a")
                        .append(joinAndWhere);
            }
            sql = "SELECT " + info.getSQLColumn("a", column) + " FROM (" + (union) + ") a";
        }
        return sql;
    }

    protected <T> CompletableFuture<Serializable> findColumnDBApply(
            EntityInfo<T> info,
            CompletableFuture<? extends DataResultSet> future,
            boolean onlypk,
            String column,
            Serializable defValue) {
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

    // ---------------------------- existsCompose ----------------------------
    @Override
    public <T> boolean exists(Class<T> clazz, Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) {
                return rs;
            }
        }
        String[] tables = info.getTableOneArray(pk);
        String sql = existsSql(info, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        }
        if (isAsync()) {
            return existsDBAsync(info, tables, sql, true, pk, null).join();
        } else {
            return existsDB(info, tables, sql, true, pk, null);
        }
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final Serializable pk) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null) {
            boolean rs = cache.exists(pk);
            if (rs || cache.isFullLoaded()) {
                return CompletableFuture.completedFuture(rs);
            }
        }
        String[] tables = info.getTableOneArray(pk);
        String sql = existsSql(info, pk);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        }
        if (isAsync()) {
            return existsDBAsync(info, tables, sql, true, pk, null);
        } else {
            return supplyAsync(() -> existsDB(info, tables, sql, true, pk, null));
        }
    }

    protected <T> String existsSql(final EntityInfo<T> info, Serializable pk) {
        return "SELECT COUNT(*) FROM " + info.getTable(pk) + " WHERE " + info.getPrimarySQLColumn() + "="
                + info.formatSQLValue(info.getPrimarySQLColumn(), pk, sqlFormatter);
    }

    @Override
    public <T> boolean exists(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded()) {
            return cache.exists(node);
        }
        final String[] tables = info.getTables(node);
        String sql = existsSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        }
        if (isAsync()) {
            return existsDBAsync(info, tables, sql, false, null, node).join();
        } else {
            return existsDB(info, tables, sql, false, null, node);
        }
    }

    @Override
    public <T> CompletableFuture<Boolean> existsAsync(final Class<T> clazz, final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (cache != null && cache.isFullLoaded()) {
            return CompletableFuture.completedFuture(cache.exists(node));
        }

        final String[] tables = info.getTables(node);
        String sql = existsSql(info, tables, node);
        if (info.isLoggable(logger, Level.FINEST, sql)) {
            logger.finest(info.getType().getSimpleName() + " exists sql=" + sql);
        }
        if (isAsync()) {
            return existsDBAsync(info, tables, sql, false, null, node);
        } else {
            return supplyAsync(() -> existsDB(info, tables, sql, false, null, node));
        }
    }

    protected <T> String existsSql(final EntityInfo<T> info, String[] tables, FilterNode node) {
        final Map<Class, String> joinTabalis = node == null ? null : node.getJoinTabalis();
        final CharSequence join =
                node == null ? null : node.createSQLJoin(this, false, joinTabalis, new HashSet<>(), info);
        final CharSequence where = node == null ? null : node.createSQLExpress(this, info, joinTabalis);
        String joinAndWhere =
                (join == null ? "" : join) + ((where == null || where.length() == 0) ? "" : (" WHERE " + where));
        String sql;
        if (tables.length == 1) {
            sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM " + tables[0] + " a" + joinAndWhere;
        } else {
            StringBuilder union = new StringBuilder();
            for (String table : tables) {
                if (union.length() > 0) {
                    union.append(" UNION ALL ");
                }
                union.append("SELECT ")
                        .append(info.getPrimarySQLColumn("a"))
                        .append(" FROM ")
                        .append(table)
                        .append(" a")
                        .append(joinAndWhere);
            }
            sql = "SELECT COUNT(" + info.getPrimarySQLColumn("a") + ") FROM (" + (union) + ") a";
        }
        return sql;
    }

    protected <T> CompletableFuture<Boolean> existsDBApply(
            EntityInfo<T> info, CompletableFuture<? extends DataResultSet> future, boolean onlypk) {
        return future.thenApply((DataResultSet pgset) -> {
            boolean rs = pgset.next() && (((Number) pgset.getObject(1)).intValue() > 0);
            pgset.close();
            return rs;
        });
    }

    // -----------------------list set----------------------------
    @Override
    public <T, V extends Serializable> Set<V> queryColumnSet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        final Set<T> list = querySet(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final Set<V> rs = new LinkedHashSet<>();
        if (list.isEmpty()) {
            return rs;
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        for (T t : list) {
            rs.add(selected.get(t));
        }
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySetAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node)
                .thenApply((Set<T> list) -> {
                    final Set<V> rs = new LinkedHashSet<>();
                    if (list.isEmpty()) {
                        return rs;
                    }
                    final EntityInfo<T> info = loadEntityInfo(clazz);
                    final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
                    for (T t : list) {
                        rs.add(selected.get(t));
                    }
                    return rs;
                });
    }

    @Override
    public <T, V extends Serializable> List<V> queryColumnList(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        final List<T> list = queryList(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final List<V> rs = new ArrayList<>();
        if (list.isEmpty()) {
            return rs;
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
        for (T t : list) {
            rs.add(selected.get(t));
        }
        return rs;
    }

    @Override
    public <T, V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return queryListAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node)
                .thenApply((List<T> list) -> {
                    final List<V> rs = new ArrayList<>();
                    if (list.isEmpty()) {
                        return rs;
                    }
                    final EntityInfo<T> info = loadEntityInfo(clazz);
                    final Attribute<T, V> selected = (Attribute<T, V>) info.getAttribute(selectedColumn);
                    for (T t : list) {
                        rs.add(selected.get(t));
                    }
                    return rs;
                });
    }

    @Override
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        Sheet<T> sheet = querySheet(clazz, SelectColumn.includes(selectedColumn), flipper, node);
        final Sheet<V> rs = new Sheet<>();
        if (sheet.isEmpty()) {
            return rs;
        }
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
    public <T, V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
            final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node) {
        return querySheetAsync(clazz, SelectColumn.includes(selectedColumn), flipper, node)
                .thenApply((Sheet<T> sheet) -> {
                    final Sheet<V> rs = new Sheet<>();
                    if (sheet.isEmpty()) {
                        return rs;
                    }
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
     * 查询符合过滤条件记录的Map集合, 主键值为key <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param keyStream 主键Stream
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(
            final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) {
            return new LinkedHashMap<>();
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, Serializable> primary = info.getPrimary();
        List<T> rs = queryList(clazz, FilterNodes.in(primary.field(), keyStream));
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) {
            return new LinkedHashMap<>();
        }
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final SelectColumn selects, final Stream<K> keyStream) {
        if (keyStream == null) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, Serializable> primary = info.getPrimary();
        return queryListAsync(clazz, FilterNodes.in(primary.field(), keyStream)).thenApply((List<T> rs) -> {
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) {
                return new LinkedHashMap<>();
            }
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    /**
     * 查询符合过滤条件记录的Map集合, 主键值为key <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
     *
     * @param <K> 主键泛型
     * @param <T> Entity泛型
     * @param clazz Entity类
     * @param selects 指定字段
     * @param node FilterNode
     * @return Entity的集合
     */
    @Override
    public <K extends Serializable, T> Map<K, T> queryMap(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        List<T> rs = queryList(clazz, selects, node);
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final Attribute<T, Serializable> primary = info.getPrimary();
        Map<K, T> map = new LinkedHashMap<>();
        if (rs.isEmpty()) {
            return new LinkedHashMap<>();
        }
        for (T item : rs) {
            map.put((K) primary.get(item), item);
        }
        return map;
    }

    @Override
    public <K extends Serializable, T> CompletableFuture<Map<K, T>> queryMapAsync(
            final Class<T> clazz, final SelectColumn selects, final FilterNode node) {
        return queryListAsync(clazz, selects, node).thenApply((List<T> rs) -> {
            final EntityInfo<T> info = loadEntityInfo(clazz);
            final Attribute<T, Serializable> primary = info.getPrimary();
            Map<K, T> map = new LinkedHashMap<>();
            if (rs.isEmpty()) {
                return new LinkedHashMap<>();
            }
            for (T item : rs) {
                map.put((K) primary.get(item), item);
            }
            return map;
        });
    }

    @Override
    public <T> Set<T> querySet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, false, true, clazz, selects, flipper, node)
                    .thenApply(rs -> new LinkedHashSet<>(rs.list(true)))
                    .join();
        } else {
            return new LinkedHashSet<>(
                    querySheet(true, false, true, clazz, selects, flipper, node).list(true));
        }
    }

    @Override
    public <T> CompletableFuture<Set<T>> querySetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, false, true, clazz, selects, flipper, node)
                    .thenApply(rs -> new LinkedHashSet<>(rs.list(true)));
        } else {
            return supplyAsync(() -> querySheet(true, false, true, clazz, selects, flipper, node))
                    .thenApply(rs -> new LinkedHashSet<>(rs.list(true)));
        }
    }

    @Override
    public <T> List<T> queryList(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, false, false, clazz, selects, flipper, node)
                    .thenApply(rs -> rs.list(true))
                    .join();
        } else {
            return querySheet(true, false, false, clazz, selects, flipper, node).list(true);
        }
    }

    @Override
    public <T> CompletableFuture<List<T>> queryListAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, false, false, clazz, selects, flipper, node)
                    .thenApply(rs -> rs.list(true));
        } else {
            return supplyAsync(() -> querySheet(true, false, false, clazz, selects, flipper, node))
                    .thenApply(rs -> rs.list(true));
        }
    }

    @Override
    public <T> Sheet<T> querySheet(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, true, false, clazz, selects, flipper, node)
                    .join();
        } else {
            return querySheet(true, true, false, clazz, selects, flipper, node);
        }
    }

    @Override
    public <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node) {
        if (isAsync()) {
            return querySheetAsync(true, true, false, clazz, selects, flipper, node);
        } else {
            return supplyAsync(() -> querySheet(true, true, false, clazz, selects, flipper, node));
        }
    }

    protected <T> Sheet<T> querySheet(
            final boolean readCache,
            final boolean needTotal,
            final boolean distinct,
            final Class<T> clazz,
            final SelectColumn selects,
            final Flipper flipper,
            final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readCache && cache != null && cache.isFullLoaded()) {
            if (node == null || isCacheUseable(node, this)) {
                if (info.isLoggable(logger, Level.FINEST, " cache query predicate = ")) {
                    logger.finest(clazz.getSimpleName() + " cache query predicate = "
                            + (node == null ? null : createPredicate(node, cache)));
                }
                return cache.querySheet(needTotal, distinct, selects, flipper, node);
            }
        }
        return querySheetDB(info, readCache, needTotal, distinct, selects, flipper, node);
    }

    protected <T> CompletableFuture<Sheet<T>> querySheetAsync(
            final boolean readCache,
            final boolean needTotal,
            final boolean distinct,
            final Class<T> clazz,
            final SelectColumn selects,
            final Flipper flipper,
            final FilterNode node) {
        final EntityInfo<T> info = loadEntityInfo(clazz);
        final EntityCache<T> cache = info.getCache();
        if (readCache && cache != null && cache.isFullLoaded()) {
            if (node == null || isCacheUseable(node, this)) {
                if (info.isLoggable(logger, Level.FINEST, " cache query predicate = ")) {
                    logger.finest(clazz.getSimpleName() + " cache query predicate = "
                            + (node == null ? null : createPredicate(node, cache)));
                }
                return CompletableFuture.completedFuture(cache.querySheet(needTotal, distinct, selects, flipper, node));
            }
        }
        return querySheetDBAsync(info, readCache, needTotal, distinct, selects, flipper, node);
    }

    // -------------------------------------------- native SQL --------------------------------------------
    @Override
    public <V> V nativeQuery(
            String sql,
            BiConsumer<Object, Object> consumer,
            Function<DataResultSet, V> handler,
            Map<String, Object> params) {
        return nativeQueryAsync(sql, consumer, handler, params).join();
    }

    @Override
    public <V> V nativeQuery(String sql, BiConsumer<Object, Object> consumer, Function<DataResultSet, V> handler) {
        return nativeQueryAsync(sql, consumer, handler).join();
    }

    @Override
    public <V> Sheet<V> nativeQuerySheet(Class<V> type, String sql, RowBound round, Map<String, Object> params) {
        return nativeQuerySheetAsync(type, sql, round, params).join();
    }

    @Override
    public int nativeUpdate(String sql, Map<String, Object> params) {
        return nativeUpdateAsync(sql, params).join();
    }

    @Override
    public int nativeUpdate(String sql) {
        return nativeUpdateAsync(sql).join();
    }

    @Override
    public int[] nativeUpdates(String... sqls) {
        return nativeUpdatesAsync(sqls).join();
    }

    protected static class UpdateSqlInfo {

        public String sql; // prepare-sql时表名参数只能是最后一个

        public String[] tables; // 存在值则长度必然大于1，sql为[0]构建的sql

        public List<byte[]> blobs; // 要么null，要么有内容，不能是empty-list

        public boolean prepare; // 是否PreparedStatement SQL

        public UpdateSqlInfo(boolean prepare, String sql, byte[]... blobs) {
            this(prepare, sql, null, blobs);
        }

        public UpdateSqlInfo(boolean prepare, String sql, List<byte[]> blobs) {
            this(prepare, sql, null, blobs);
        }

        public UpdateSqlInfo(boolean prepare, String sql, String[] tables, byte[]... blobs) {
            this.prepare = prepare;
            this.sql = sql;
            this.tables = tables;
            if (blobs.length > 0) {
                this.blobs = new ArrayList<>();
                for (byte[] bs : blobs) {
                    this.blobs.add(bs);
                }
            }
        }

        public UpdateSqlInfo(boolean prepare, String sql, String[] tables, List<byte[]> blobs) {
            this.prepare = prepare;
            this.sql = sql;
            this.tables = tables;
            this.blobs = isEmpty(blobs) ? null : blobs;
        }
    }

    protected static class PrepareInfo<T> {

        public String prepareSql;

        public List<T> entitys;

        public PrepareInfo(String prepareSql) {
            this.prepareSql = prepareSql;
        }

        public void addEntity(T entity) {
            if (entitys == null) {
                entitys = new ArrayList<>();
            }
            entitys.add(entity);
        }
    }

    protected static class PageCountSql {

        public final String pageSql;

        @Nullable
        public final String countSql;

        public PageCountSql(String pageSql, String countSql) {
            this.pageSql = pageSql;
            this.countSql = countSql;
        }

        @Override
        public String toString() {
            return "PageCountSql{" + "pageSql=" + pageSql + ", countSql=" + countSql + '}';
        }
    }
}
