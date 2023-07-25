/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.annotation.Comment;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.annotation.LogExcludeLevel;
import org.redkale.annotation.LogLevel;
import org.redkale.convert.json.*;
import org.redkale.persistence.*;
import org.redkale.util.*;

/**
 * Entity操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Entity类的泛型
 */
@SuppressWarnings("unchecked")
public final class EntityInfo<T> {

    private static final JsonConvert DEFAULT_JSON_CONVERT = JsonFactory.create().skipAllIgnore(true).getConvert();

    //全局静态资源
    private static final ConcurrentHashMap<Class, EntityInfo> entityInfos = new ConcurrentHashMap<>();

    private static final ReentrantLock infosLock = new ReentrantLock();

    //日志
    private static final Logger logger = Logger.getLogger(EntityInfo.class.getSimpleName());

    //Entity类名
    private final Class<T> type;

    //类对应的数据表名, 如果是VirtualEntity 类， 则该字段为null
    final String table;

    //table的单一元素数组
    final String[] tableOneArray;

    //JsonConvert
    final JsonConvert jsonConvert;

    //Entity构建器
    private final EntityBuilder<T> builder;

    //Entity数值构建器
    private final IntFunction<T[]> arrayer;

    //主键
    final Attribute<T, Serializable> primary;

    //table的单一元素数组
    final Attribute<T, Serializable>[] primaryOneArray;

    //DDL字段
    final EntityColumn primaryColumn;

    //DDL字段
    final EntityColumn[] primaryColumnOneArray;

    //DDL字段集合
    final EntityColumn[] ddlColumns;

    //Entity缓存对象
    private final EntityCache<T> cache;

    //用于存储绑定在EntityInfo上的对象
    private final ConcurrentHashMap<String, Object> subobjectMap = new ConcurrentHashMap<>();

    //key是field的name， 不是sql字段。
    //存放所有与数据库对应的字段， 包括主键
    private final HashMap<String, Attribute<T, Serializable>> attributeMap = new HashMap<>();

    //存放所有与数据库对应的字段， 包括主键
    final Attribute<T, Serializable>[] attributes;

    //key是field的name， value是Column的别名，即数据库表的字段名
    //只有field.name 与 Column.name不同才存放在aliasmap里.
    private final Map<String, String> aliasmap;

    //所有可更新字段，即排除了主键字段和标记为&#064;Column(updatable=false)的字段
    private final Map<String, Attribute<T, Serializable>> updateAttributeMap = new HashMap<>();

    //用于存在database.table_20160202类似这种分布式表, 服务分布式部署时不存在的表名不一定真实不存在
    private final Set<String> disTables = new CopyOnWriteArraySet<>();

    private final ReentrantLock disTableLock = new ReentrantLock();

    //不能为null的字段名
    private final Set<String> notNullColumns = new CopyOnWriteArraySet<>();

    //分表 策略
    private final DistributeTableStrategy<T> tableStrategy;

    //根据主键查找所有对象的SQL
    private final String allQueryPrepareSQL;

    //根据主键查找单个对象的SQL， 含 ？
    private final String findQuestionPrepareSQL;

    //根据主键查找单个对象的SQL， 含 $
    private final String findDollarPrepareSQL;

    //根据主键查找单个对象的SQL， 含 :name
    private final String findNamesPrepareSQL;

    //根据主键查找单个对象的SQL， 含 ？; findQuestionPrepareSQL + ' ' 为了不覆盖find SQL的缓存
    private final String findsQuestionPrepareSQL;

    //根据主键查找单个对象的SQL， 含 $; findsDollarPrepareSQL + ' ' 为了不覆盖find SQL的缓存
    private final String findsDollarPrepareSQL;

    //根据主键查找单个对象的SQL， 含 :name; findNamesPrepareSQL + ' ' 为了不覆盖find SQL的缓存
    private final String findsNamesPrepareSQL;

    //数据库中所有字段
    private final String[] querySqlColumns;

    private final String querySqlColumnSequence;

    private final String querySqlColumnSequenceA;

    //数据库中所有字段, 顺序必须与querySqlColumns、querySqlColumnSequence一致
    private final Attribute<T, Serializable>[] queryAttributes;

    //数据库中所有字段, 顺序必须与querySqlColumns、querySqlColumnSequence一致
    private final EntityColumn[] queryColumns;

    //新增SQL， 含 ？，即排除了自增长主键和标记为&#064;Column(insertable=false)的字段
    private final String insertQuestionPrepareSQL;

    //新增SQL， 含 $，即排除了自增长主键和标记为&#064;Column(insertable=false)的字段    
    private final String insertDollarPrepareSQL;

    //新增SQL， 含 :name，即排除了自增长主键和标记为&#064;Column(insertable=false)的字段
    private final String insertNamesPrepareSQL;

    //数据库中所有可新增字段
    final Attribute<T, Serializable>[] insertAttributes;

    //数据库中所有可新增字段
    final EntityColumn[] insertColumns;

    //根据主键更新所有可更新字段的SQL，含 ？
    private final String updateQuestionPrepareSQL;

    //根据主键更新所有可更新字段的SQL，含 $
    private final String updateDollarPrepareSQL;

    //根据主键更新所有可更新字段的SQL，含 ？
    private final String[] updateQuestionPrepareCaseSQLs;

    //根据主键更新所有可更新字段的SQL，含 $
    private final String[] updateDollarPrepareCaseSQLs;

    //根据主键更新所有可更新字段的SQL，含 :name
    private final String updateNamesPrepareSQL;

    //数据库中所有可更新字段
    final Attribute<T, Serializable>[] updateAttributes;

    //数据库中所有可更新字段+主键字段
    final Attribute<T, Serializable>[] updateEntityAttributes;

    //数据库中所有可更新字段
    final EntityColumn[] updateColumns;

    //数据库中所有可更新字段+主键字段
    final EntityColumn[] updateEntityColumns;

    //根据主键删除记录的SQL，含 ？
    private final String deleteQuestionPrepareSQL;

    //根据主键删除记录的SQL，含 $
    private final String deleteDollarPrepareSQL;

    //根据主键删除记录的SQL，含 :name
    private final String deleteNamesPrepareSQL;

    //日志级别，从LogLevel获取
    private final int logLevel;

    //日志控制
    private final Map<Integer, String[]> excludeLogLevels;

    //Flipper.sort转换成以ORDER BY开头SQL的缓存
    private final Map<String, String> sortOrderbySqls = new ConcurrentHashMap<>();

    //所属的DataSource
    final DataSource source;

    //全量数据的加载器
    final BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader;
    //------------------------------------------------------------

    /**
     * 加载EntityInfo
     *
     * @param clazz          Entity类
     * @param cacheForbidden 是否禁用EntityCache
     * @param conf           配置信息, source.properties中的属性节点值
     * @param source         DataSource,可为null
     * @param fullloader     全量加载器,可为null
     */
    static <T> EntityInfo<T> load(Class<T> clazz, final boolean cacheForbidden, final Properties conf,
        DataSource source, BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader) {
        EntityInfo rs = entityInfos.get(clazz);
        if (rs != null && (rs.cache == null || rs.cache.isFullLoaded())) {
            return rs;
        }
        infosLock.lock();
        try {
            rs = entityInfos.get(clazz);
            if (rs == null) {
                rs = new EntityInfo(clazz, cacheForbidden, conf, source, fullloader);
                entityInfos.put(clazz, rs);
            }
            if (rs.cache != null && !rs.isCacheFullLoaded()) {
                if (fullloader == null) {
                    throw new IllegalArgumentException(clazz.getName() + " auto loader  is illegal");
                }
                rs.cache.fullLoad();
            }
            return rs;
        } finally {
            infosLock.unlock();
        }
    }

    /**
     * 获取Entity类对应的EntityInfo对象
     *
     * @param <T>   泛型
     * @param clazz Entity类
     *
     * @return EntityInfo
     */
    static <T> EntityInfo<T> get(Class<T> clazz) {
        return entityInfos.get(clazz);
    }

    /**
     * 给PrepareCompiler使用，用于预动态生成Attribute
     *
     * @since 2.5.0
     * @param <T>    泛型
     * @param clazz  Entity实体类
     * @param source 数据源
     *
     * @return EntityInfo
     */
    public static <T> EntityInfo<T> compile(Class<T> clazz, DataSource source) {
        return new EntityInfo<>(clazz, false, null, source, (BiFunction) null);
    }

    /**
     * 构造函数
     *
     * @param type           Entity类
     * @param cacheForbidden 是否禁用EntityCache
     * @param conf           配置信息, persistence.xml中的property节点值
     * @param source         DataSource,可为null
     * @param fullloader     全量加载器,可为null
     */
    private EntityInfo(Class<T> type, final boolean cacheForbidden,
        Properties conf, DataSource source, BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader) {
        this.type = type;
        this.source = source;
        //---------------------------------------------

        LogLevel ll = type.getAnnotation(LogLevel.class);
        org.redkale.util.LogLevel ll2 = type.getAnnotation(org.redkale.util.LogLevel.class);
        String levelName = ll != null ? ll.value() : (ll2 != null ? ll2.value() : null);
        this.logLevel = levelName == null ? Integer.MIN_VALUE : Level.parse(levelName).intValue();
        Map<Integer, HashSet<String>> logmap = new HashMap<>();
        for (LogExcludeLevel lel : type.getAnnotationsByType(LogExcludeLevel.class)) {
            for (String onelevel : lel.levels()) {
                int level = Level.parse(onelevel).intValue();
                HashSet<String> set = logmap.get(level);
                if (set == null) {
                    set = new HashSet<>();
                    logmap.put(level, set);
                }
                for (String key : lel.keys()) {
                    set.add(key);
                }
            }
        }
        for (org.redkale.util.LogExcludeLevel lel : type.getAnnotationsByType(org.redkale.util.LogExcludeLevel.class)) {
            for (String onelevel : lel.levels()) {
                int level = Level.parse(onelevel).intValue();
                HashSet<String> set = logmap.get(level);
                if (set == null) {
                    set = new HashSet<>();
                    logmap.put(level, set);
                }
                for (String key : lel.keys()) {
                    set.add(key);
                }
            }
        }
        if (logmap.isEmpty()) {
            this.excludeLogLevels = null;
        } else {
            this.excludeLogLevels = new HashMap<>();
            logmap.forEach((l, set) -> excludeLogLevels.put(l, set.toArray(new String[set.size()])));
        }
        //---------------------------------------------
        org.redkale.persistence.Table t1 = type.getAnnotation(org.redkale.persistence.Table.class);
        javax.persistence.Table t2 = type.getAnnotation(javax.persistence.Table.class);
        final String tableName0 = t1 != null ? t1.name() : (t2 != null ? t2.name() : null);
        final String tableCcatalog0 = t1 != null ? t1.catalog() : (t2 != null ? t2.catalog() : null);

        if (type.getAnnotation(org.redkale.persistence.VirtualEntity.class) != null
            || type.getAnnotation(org.redkale.source.VirtualEntity.class) != null
            || (source == null || "memory".equalsIgnoreCase(source.getType()))) {
            this.table = null;
            this.tableOneArray = null;
            BiFunction<DataSource, EntityInfo, CompletableFuture<List>> loader = null;
            try {
                org.redkale.persistence.VirtualEntity ve = type.getAnnotation(org.redkale.persistence.VirtualEntity.class);
                if (ve != null) {
                    loader = ve.loader().getDeclaredConstructor().newInstance();
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ve.loader(), ve.loader().getName());
                }
                org.redkale.source.VirtualEntity ve2 = type.getAnnotation(org.redkale.source.VirtualEntity.class);
                if (ve2 != null) {
                    loader = ve2.loader().getDeclaredConstructor().newInstance();
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ve2.loader(), ve2.loader().getName());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, type + " init @VirtualEntity.loader error", e);
            }
            this.fullloader = loader;
        } else {
            this.fullloader = fullloader;
            if (tableName0 != null && !tableName0.isEmpty() && tableName0.indexOf('.') >= 0) {
                throw new SourceException(type + " have illegal table.name on @Table");
            }
            this.table = (tableCcatalog0 == null) ? type.getSimpleName().toLowerCase() : (tableCcatalog0.isEmpty()) ? (tableName0.isEmpty() ? type.getSimpleName().toLowerCase() : tableName0) : (tableCcatalog0 + '.' + (tableName0.isEmpty() ? type.getSimpleName().toLowerCase() : tableName0));
            this.tableOneArray = new String[]{this.table};
        }
        DistributeTable dt = type.getAnnotation(DistributeTable.class);
        DistributeTableStrategy dts = null;
        try {
            dts = (dt == null) ? null : dt.strategy().getDeclaredConstructor().newInstance();
            if (dts != null) {
                RedkaleClassLoader.putReflectionDeclaredConstructors(dt.strategy(), dt.strategy().getName());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, type + " init DistributeTableStrategy error", e);
        }
        this.tableStrategy = dts;

        this.arrayer = Creator.arrayFunction(type);
        Creator creator = Creator.create(type);
        String[] cps = null;
        try {
            Method cm = creator.getClass().getMethod("create", Object[].class);
            RedkaleClassLoader.putReflectionPublicMethods(creator.getClass().getName());
            RedkaleClassLoader.putReflectionMethod(creator.getClass().getName(), cm);
            ConstructorParameters cp = cm.getAnnotation(ConstructorParameters.class);
            if (cp != null && cp.value().length > 0) {
                cps = cp.value();
            } else {
                org.redkale.util.ConstructorParameters cp2 = cm.getAnnotation(org.redkale.util.ConstructorParameters.class);
                if (cp2 != null && cp2.value().length > 0) {
                    cps = cp2.value();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, type + " cannot find ConstructorParameters Creator", e);
        }
        String[] constructorParameters = cps;
        Attribute idAttr0 = null;
        Map<String, String> aliasmap0 = null;
        Class cltmp = type;
        Set<String> fields = new HashSet<>();
        List<String> queryCols = new ArrayList<>();
        List<Attribute<T, Serializable>> queryAttrs = new ArrayList<>();
        List<String> insertCols = new ArrayList<>();
        List<Attribute<T, Serializable>> insertAttrs = new ArrayList<>();
        List<String> updateCols = new ArrayList<>();
        List<Attribute<T, Serializable>> updateAttrs = new ArrayList<>();
        List<List<EntityColumn>> ddlList = new ArrayList<>();
        do {
            List<EntityColumn> ddl = new ArrayList<>();
            ddlList.add(ddl);
            RedkaleClassLoader.putReflectionDeclaredFields(cltmp.getName());
            for (Field field : cltmp.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (field.getAnnotation(Transient.class) != null) {
                    continue;
                }
                if (field.getAnnotation(javax.persistence.Transient.class) != null) {
                    continue;
                }
                if (fields.contains(field.getName())) {
                    continue;
                }
                final String fieldName = field.getName();
                final Column col = field.getAnnotation(Column.class);
                final String sqlField = col == null || col.name().isEmpty() ? fieldName : col.name();
                if (!fieldName.equals(sqlField)) {
                    if (aliasmap0 == null) {
                        aliasmap0 = new HashMap<>();
                    }
                    aliasmap0.put(fieldName, sqlField);
                }
                Attribute attr;
                try {
                    attr = Attribute.create(type, cltmp, field);
                } catch (RuntimeException e) {
                    continue;
                }

                boolean idFlag = field.getAnnotation(Id.class) != null || field.getAnnotation(javax.persistence.Id.class) != null;
                if (idFlag && idAttr0 == null) {
                    idAttr0 = attr;
                    insertCols.add(sqlField);
                    insertAttrs.add(attr);
                    RedkaleClassLoader.putReflectionField(cltmp.getName(), field);
                } else {
                    if (col == null || col.insertable()) {
                        insertCols.add(sqlField);
                        insertAttrs.add(attr);
                    }
                    if (col == null || col.updatable()) {
                        updateCols.add(sqlField);
                        updateAttrs.add(attr);
                        updateAttributeMap.put(fieldName, attr);
                    }
                    if (col != null && !col.nullable()) {
                        notNullColumns.add(fieldName);
                    }
                }
                ddl.add(new EntityColumn(idFlag, col, attr.field(), attr.type(), field.getAnnotation(Comment.class)));
                queryCols.add(sqlField);
                queryAttrs.add(attr);
                fields.add(fieldName);
                attributeMap.put(fieldName, attr);
            }
        } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        if (idAttr0 == null) {
            throw new SourceException(type.getName() + " have no primary column by @org.redkale.persistence.Id");
        }
        cltmp = type;
        JsonConvert convert = DEFAULT_JSON_CONVERT;
        do {
            for (Method method : cltmp.getDeclaredMethods()) {
                if (method.getAnnotation(SourceConvert.class) == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new SourceException("@SourceConvert method(" + method + ") must be static");
                }
                if (method.getReturnType() != JsonConvert.class) {
                    throw new SourceException("@SourceConvert method(" + method + ") must be return JsonConvert.class");
                }
                if (method.getParameterCount() > 0) {
                    throw new SourceException("@SourceConvert method(" + method + ") must be 0 parameter");
                }
                try {
                    method.setAccessible(true);
                    convert = (JsonConvert) method.invoke(null);
                } catch (Exception e) {
                    throw new SourceException(method + " invoke error", e);
                }
                if (convert != null) {
                    break;
                }
            }
        } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        this.jsonConvert = convert == null ? DEFAULT_JSON_CONVERT : convert;

        this.primary = idAttr0;
        this.primaryOneArray = new Attribute[]{this.primary};
        this.aliasmap = aliasmap0;
        List<EntityColumn> ddls = new ArrayList<>();
        Collections.reverse(ddlList);  //父类的字段排在前面
        for (List<EntityColumn> ls : ddlList) {
            ddls.addAll(ls);
        }
        this.ddlColumns = ddls.toArray(new EntityColumn[ddls.size()]);
        this.attributes = attributeMap.values().toArray(new Attribute[attributeMap.size()]);
        this.primaryColumn = Utility.find(this.ddlColumns, c -> c.field.equals(primary.field()));
        this.primaryColumnOneArray = new EntityColumn[]{this.primaryColumn};
        this.insertAttributes = insertAttrs.toArray(new Attribute[insertAttrs.size()]);
        this.insertColumns = new EntityColumn[this.insertAttributes.length];
        for (int i = 0; i < this.insertAttributes.length; i++) {
            String field = this.insertAttributes[i].field();
            this.insertColumns[i] = Utility.find(this.ddlColumns, c -> c.field.equals(field));
        }
        this.updateAttributes = updateAttrs.toArray(new Attribute[updateAttrs.size()]);
        this.updateColumns = new EntityColumn[this.updateAttributes.length];
        for (int i = 0; i < this.updateAttributes.length; i++) {
            String field = this.updateAttributes[i].field();
            this.updateColumns[i] = Utility.find(this.ddlColumns, c -> c.field.equals(field));
        }
        this.updateEntityAttributes = Utility.append(this.updateAttributes, this.primary);
        this.updateEntityColumns = Utility.append(this.updateColumns, this.primaryColumn);

        Attribute<T, Serializable>[] constructorAttributes;
        Attribute<T, Serializable>[] unconstructorAttributes;
        if (constructorParameters == null) {
            constructorAttributes = null;
            unconstructorAttributes = null;
        } else {
            constructorAttributes = new Attribute[constructorParameters.length];
            List<Attribute<T, Serializable>> unconstructorAttrs = new ArrayList<>();
            List<String> newquerycols1 = new ArrayList<>();
            List<String> newquerycols2 = new ArrayList<>();
            for (Attribute<T, Serializable> attr : new ArrayList<>(queryAttrs)) {
                int pos = -1;
                for (int i = 0; i < constructorParameters.length; i++) {
                    if (attr.field().equals(constructorParameters[i])) {
                        pos = i;
                        break;
                    }
                }
                if (pos >= 0) {
                    constructorAttributes[pos] = attr;
                    newquerycols1.add(queryCols.get(queryAttrs.indexOf(attr)));
                } else {
                    unconstructorAttrs.add(attr);
                    newquerycols2.add(queryCols.get(queryAttrs.indexOf(attr)));
                }
            }
            unconstructorAttributes = unconstructorAttrs.toArray(new Attribute[unconstructorAttrs.size()]);
            newquerycols1.addAll(newquerycols2);
            queryCols = newquerycols1;
            List<Attribute<T, Serializable>> newqueryattrs = new ArrayList<>();
            newqueryattrs.addAll(List.of(constructorAttributes));
            newqueryattrs.addAll(unconstructorAttrs);
            queryAttrs = newqueryattrs;
        }
        this.querySqlColumns = queryCols.toArray(new String[queryCols.size()]);
        this.querySqlColumnSequence = Utility.joining(querySqlColumns, ',');
        this.querySqlColumnSequenceA = "a." + Utility.joining(querySqlColumns, ",a.");
        this.queryAttributes = queryAttrs.toArray(new Attribute[queryAttrs.size()]);
        this.queryColumns = new EntityColumn[this.queryAttributes.length];
        for (int i = 0; i < this.queryAttributes.length; i++) {
            String field = this.queryAttributes[i].field();
            this.queryColumns[i] = Utility.find(this.ddlColumns, c -> c.field.equals(field));
        }
        this.builder = new EntityBuilder<>(creator, aliasmap, constructorParameters, constructorAttributes, unconstructorAttributes, attributeMap, queryAttributes);

        if (table != null) {
            StringBuilder querydb = new StringBuilder();
            int index = 0;
            for (String col : queryCols) {
                if (index > 0) {
                    querydb.append(',');
                }
                querydb.append(col);
                index++;
            }
            StringBuilder insertsb = new StringBuilder();
            StringBuilder insertsbquestion = new StringBuilder();
            StringBuilder insertsbdollar = new StringBuilder();
            StringBuilder insertsbnames = new StringBuilder();
            index = 0;
            for (String col : insertCols) {
                if (index > 0) {
                    insertsb.append(',');
                }
                insertsb.append(col);
                if (index > 0) {
                    insertsbquestion.append(',');
                    insertsbdollar.append(',');
                    insertsbnames.append(',');
                }
                insertsbquestion.append('?');
                insertsbdollar.append("$").append(++index);
                insertsbnames.append(":").append(col);
            }
            this.insertQuestionPrepareSQL = "INSERT INTO " + (this.tableStrategy == null ? table : "#{newtable}") + "(" + insertsb + ") VALUES(" + insertsbquestion + ")";
            this.insertDollarPrepareSQL = "INSERT INTO " + (this.tableStrategy == null ? table : "#{newtable}") + "(" + insertsb + ") VALUES(" + insertsbdollar + ")";
            this.insertNamesPrepareSQL = "INSERT INTO " + (this.tableStrategy == null ? table : "#{newtable}") + "(" + insertsb + ") VALUES(" + insertsbnames + ")";
            StringBuilder updatesbquestion = new StringBuilder();
            StringBuilder updatesbdollar = new StringBuilder();
            StringBuilder updatesbnames = new StringBuilder();
            index = 0;
            for (String col : updateCols) {
                if (index > 0) {
                    updatesbquestion.append(", ");
                    updatesbdollar.append(", ");
                    updatesbnames.append(", ");
                }
                updatesbquestion.append(col).append(" = ?");
                updatesbdollar.append(col).append(" = ").append("$").append(++index);
                updatesbnames.append(col).append(" = :").append(col);
            }
            this.updateQuestionPrepareSQL = "UPDATE " + (this.tableStrategy == null ? table : "#{newtable}") + " SET " + updatesbquestion + " WHERE " + getPrimarySQLColumn(null) + " = ?";
            this.updateDollarPrepareSQL = "UPDATE " + (this.tableStrategy == null ? table : "#{newtable}") + " SET " + updatesbdollar + " WHERE " + getPrimarySQLColumn(null) + " = $" + (++index);
            this.updateNamesPrepareSQL = "UPDATE " + (this.tableStrategy == null ? table : "#{newtable}") + " SET " + updatesbnames + " WHERE " + getPrimarySQLColumn(null) + " = :" + getPrimarySQLColumn(null);
            this.deleteQuestionPrepareSQL = "DELETE FROM " + (this.tableStrategy == null ? table : "#{newtable}") + " WHERE " + getPrimarySQLColumn(null) + " = ?";
            this.deleteDollarPrepareSQL = "DELETE FROM " + (this.tableStrategy == null ? table : "#{newtable}") + " WHERE " + getPrimarySQLColumn(null) + " = $1";
            this.deleteNamesPrepareSQL = "DELETE FROM " + (this.tableStrategy == null ? table : "#{newtable}") + " WHERE " + getPrimarySQLColumn(null) + " = :" + getPrimarySQLColumn(null);
            this.allQueryPrepareSQL = "SELECT " + querydb + " FROM " + table;
            this.findQuestionPrepareSQL = "SELECT " + querydb + " FROM " + table + " WHERE " + getPrimarySQLColumn(null) + " = ?";
            this.findDollarPrepareSQL = "SELECT " + querydb + " FROM " + table + " WHERE " + getPrimarySQLColumn(null) + " = $1";
            this.findNamesPrepareSQL = "SELECT " + querydb + " FROM " + table + " WHERE " + getPrimarySQLColumn(null) + " = :" + getPrimarySQLColumn(null);
            this.findsQuestionPrepareSQL = this.findQuestionPrepareSQL + " ";
            this.findsDollarPrepareSQL = this.findDollarPrepareSQL + " ";
            this.findsNamesPrepareSQL = this.findNamesPrepareSQL + " ";

            if (this.tableStrategy == null && this.updateAttributes.length == 1) { //不分表且只有两个字段的表才使用Case方式
                String[] dollarPrepareCaseSQLs = new String[51]; //上限50个
                String[] questionPrepareCaseSQLs = new String[dollarPrepareCaseSQLs.length];
                String idSqlField = getPrimarySQLColumn();
                String otherField = getSQLColumn(null, this.updateAttributes[0].field());
                for (int i = 2; i < dollarPrepareCaseSQLs.length; i++) {
                    StringBuilder ds = new StringBuilder();
                    StringBuilder qs = new StringBuilder();
                    ds.append("UPDATE ").append(table).append(" SET ").append(otherField).append(" = CASE ").append(idSqlField);
                    qs.append("UPDATE ").append(table).append(" SET ").append(otherField).append(" = ( CASE");
                    for (int j = 1; j <= i; j++) {
                        ds.append(" WHEN $").append(j).append(" THEN $").append(j + i);
                        qs.append(" WHEN ").append(idSqlField).append(" = ? THEN ?");
                    }
                    ds.append(" ELSE ").append(otherField).append(" END WHERE ").append(idSqlField).append(" IN ($1");
                    qs.append(" END ) WHERE ").append(idSqlField).append(" IN (?");
                    for (int j = 2; j <= i; j++) {
                        ds.append(",$").append(j);
                        qs.append(",?");
                    }
                    ds.append(")");
                    qs.append(")");
                    dollarPrepareCaseSQLs[i] = ds.toString();
                    questionPrepareCaseSQLs[i] = qs.toString();
                }
                this.updateDollarPrepareCaseSQLs = dollarPrepareCaseSQLs;
                this.updateQuestionPrepareCaseSQLs = questionPrepareCaseSQLs;
            } else {
                this.updateDollarPrepareCaseSQLs = null;
                this.updateQuestionPrepareCaseSQLs = null;
            }

        } else {
            this.allQueryPrepareSQL = null;

            this.insertQuestionPrepareSQL = null;
            this.updateQuestionPrepareSQL = null;
            this.deleteQuestionPrepareSQL = null;
            this.findQuestionPrepareSQL = null;
            this.findsQuestionPrepareSQL = null;

            this.insertDollarPrepareSQL = null;
            this.updateDollarPrepareSQL = null;
            this.deleteDollarPrepareSQL = null;
            this.findDollarPrepareSQL = null;
            this.findsDollarPrepareSQL = null;

            this.insertNamesPrepareSQL = null;
            this.updateNamesPrepareSQL = null;
            this.deleteNamesPrepareSQL = null;
            this.findNamesPrepareSQL = null;
            this.findsNamesPrepareSQL = null;

            this.updateDollarPrepareCaseSQLs = null;
            this.updateQuestionPrepareCaseSQLs = null;
        }
        //----------------cache--------------
        Cacheable c1 = type.getAnnotation(Cacheable.class);
        javax.persistence.Cacheable c2 = type.getAnnotation(javax.persistence.Cacheable.class);
        if (this.table == null || (!cacheForbidden && c1 != null && c1.value()) || (!cacheForbidden && c2 != null && c2.value())) {
            this.cache = new EntityCache<>(this,
                c1 == null ? (c2 == null ? 0 : c2.interval()) : c1.interval(),
                c1 == null ? (c2 == null ? false : c2.direct()) : c1.direct(),
                c1 == null ? false : c1.continuousid());
        } else {
            this.cache = null;
        }
    }

    @SuppressWarnings("unchecked")
    public <V> V getSubobject(String name) {
        return (V) this.subobjectMap.get(name);
    }

    public void setSubobject(String name, Object value) {
        this.subobjectMap.put(name, value);
    }

    public void removeSubobject(String name) {
        this.subobjectMap.remove(name);
    }

    public void clearSubobjects() {
        this.subobjectMap.clear();
    }

    /**
     * 获取JsonConvert
     *
     * @return JsonConvert
     */
    public JsonConvert getJsonConvert() {
        return jsonConvert;
    }

    /**
     * 获取Entity缓存器
     *
     * @return EntityCache
     */
    public EntityCache<T> getCache() {
        return cache;
    }

    /**
     * 判断缓存器是否已经全量加载过
     *
     * @return boolean
     */
    public boolean isCacheFullLoaded() {
        return cache != null && cache.isFullLoaded();
    }

    /**
     * 获取Entity构建器
     *
     * @return Creator
     */
    public EntityBuilder<T> getBuilder() {
        return builder;
    }

    /**
     * 获取Entity构建器
     *
     * @return Creator
     */
    public Creator<T> getCreator() {
        return builder.getCreator();
    }

    /**
     * 获取Entity数组构建器
     *
     * @return Creator
     */
    public IntFunction<T[]> getArrayer() {
        return arrayer;
    }

    /**
     * 获取Entity类名
     *
     * @return Class
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * 判断Entity是否为虚拟类
     *
     * @return boolean
     */
    public boolean isVirtualEntity() {
        return table == null;
    }

    public DistributeTableStrategy<T> getTableStrategy() {
        return tableStrategy;
    }

    public ReentrantLock disTableLock() {
        return disTableLock;
    }

    public boolean containsDisTable(String tableKey) {
        return disTables.contains(tableKey);
    }

    public void addDisTable(String tableKey) {
        disTables.add(tableKey);
    }

    public boolean removeDisTable(String tableKey) {
        return disTables.remove(tableKey);
    }

    public EntityColumn[] getDDLColumns() {
        return ddlColumns;
    }

    public EntityColumn[] getInsertColumns() {
        return insertColumns;
    }

    public EntityColumn[] getUpdateColumns() {
        return updateColumns;
    }

    public EntityColumn[] getUpdateEntityColumns() {
        return updateEntityColumns;
    }

    public EntityColumn[] getQueryColumns() {
        return queryColumns;
    }

    public Attribute<T, Serializable>[] getInsertAttributes() {
        return insertAttributes;
    }

    public Attribute<T, Serializable>[] getUpdateAttributes() {
        return updateAttributes;
    }

    public Attribute<T, Serializable>[] getUpdateEntityAttributes() {
        return updateEntityAttributes;
    }

    public Attribute<T, Serializable>[] getQueryAttributes() {
        return queryAttributes;
    }

    /**
     * 获取Entity的QUERY SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindQuestionPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findQuestionPrepareSQL;
        }
        return findQuestionPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的QUERY FINDS SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindsQuestionPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findsQuestionPrepareSQL;
        }
        return findsQuestionPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的QUERY SQL
     *
     *
     * @return String
     */
    public String getAllQueryPrepareSQL() {
        return this.allQueryPrepareSQL;
    }

    /**
     * 获取Entity的QUERY SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindDollarPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findDollarPrepareSQL;
        }
        return findDollarPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的QUERY FINDS SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindsDollarPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findsDollarPrepareSQL;
        }
        return findsDollarPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的QUERY SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindNamesPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findNamesPrepareSQL;
        }
        return findNamesPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的QUERY FINDS SQL
     *
     * @param pk 主键值
     *
     * @return String
     */
    public String getFindsNamesPrepareSQL(Serializable pk) {
        if (this.tableStrategy == null) {
            return findsNamesPrepareSQL;
        }
        return findsNamesPrepareSQL.replace("#{newtable}", getTable(pk));
    }

    /**
     * 获取Entity的INSERT SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getInsertQuestionPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return insertQuestionPrepareSQL;
        }
        return insertQuestionPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的INSERT SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getInsertDollarPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return insertDollarPrepareSQL;
        }
        return insertDollarPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的INSERT SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getInsertNamesPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return insertNamesPrepareSQL;
        }
        return insertNamesPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的UPDATE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getUpdateQuestionPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return updateQuestionPrepareSQL;
        }
        return updateQuestionPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的UPDATE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getUpdateDollarPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return updateDollarPrepareSQL;
        }
        return updateDollarPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的UPDATE CASE SQL
     *
     * @param beans Entity对象
     *
     * @return String
     */
    public String getUpdateQuestionPrepareCaseSQL(T[] beans) {
        if (beans == null || beans.length < 2) {
            return null;
        }
        if (this.updateQuestionPrepareCaseSQLs == null) {
            return null;
        }
        if (this.updateQuestionPrepareCaseSQLs.length <= beans.length) {
            return null;
        }
        return this.updateQuestionPrepareCaseSQLs[beans.length];
    }

    /**
     * 获取Entity的UPDATE CASE SQL
     *
     * @param beans Entity对象
     *
     * @return String
     */
    public String getUpdateDollarPrepareCaseSQL(T[] beans) {
        if (beans == null || beans.length < 2) {
            return null;
        }
        if (this.updateDollarPrepareCaseSQLs == null) {
            return null;
        }
        if (this.updateDollarPrepareCaseSQLs.length <= beans.length) {
            return null;
        }
        return this.updateDollarPrepareCaseSQLs[beans.length];
    }

    /**
     * 获取Entity的UPDATE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getUpdateNamesPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return updateNamesPrepareSQL;
        }
        return updateNamesPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的DELETE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getDeleteQuestionPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return deleteQuestionPrepareSQL;
        }
        return deleteQuestionPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的DELETE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getDeleteDollarPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return deleteDollarPrepareSQL;
        }
        return deleteDollarPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取Entity的DELETE SQL
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getDeleteNamesPrepareSQL(T bean) {
        if (this.tableStrategy == null) {
            return deleteNamesPrepareSQL;
        }
        return deleteNamesPrepareSQL.replace("#{newtable}", getTable(bean));
    }

    /**
     * 获取查询字段列表
     *
     * @param tabalis 表别名
     * @param selects 过滤字段
     *
     * @return String
     */
    public CharSequence getQueryColumns(String tabalis, SelectColumn selects) {
        if (selects == null) {
            if (tabalis == null) {
                return querySqlColumnSequence;
            }
            if ("a".equals(tabalis)) {
                return querySqlColumnSequenceA;
            } else {
                StringBuilder sb = new StringBuilder();
                String s = tabalis + ".";
                for (String col : querySqlColumns) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(s).append(col);
                }
                return sb;
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (Attribute attr : this.attributes) {
                if (!selects.test(attr.field())) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(getSQLColumn(tabalis, attr.field()));
            }
            if (sb.length() == 0) {
                sb.append('*');
            }
            return sb;
        }
    }

    public String getOriginTable() {
        return table;
    }

    public Map<String, List<Serializable>> getTableMap(Serializable... pks) {
        if (tableStrategy == null) {
            return Utility.ofMap(table, Utility.ofList(pks));
        }
        Map<String, List<Serializable>> map = new LinkedHashMap<>();
        for (Serializable pk : pks) {
            String t = getTable(pk);
            map.computeIfAbsent(t, k -> new ArrayList()).add(pk);
        }
        return map;
    }

    public Map<String, List<T>> getTableMap(T... entitys) {
        if (tableStrategy == null) {
            return Utility.ofMap(table, Utility.ofList(entitys));
        }
        Map<String, List<T>> map = new LinkedHashMap<>();
        for (T entity : entitys) {
            String t = getTable(entity);
            map.computeIfAbsent(t, k -> new ArrayList()).add(entity);
        }
        return map;
    }

    /**
     * 根据主键值获取Entity的表名
     *
     * @param primary Entity主键值
     *
     * @return String
     */
    public String getTable(Serializable primary) {
        if (tableStrategy == null) {
            return table;
        }
        String t = tableStrategy.getTable(table, primary);
        if (t == null || t.isEmpty()) {
            throw new SourceException(table + " tableStrategy.getTable is empty, primary=" + primary);
        }
        return t;
    }

    /**
     * 根据主键值获取Entity的表名单一元素数组
     *
     * @param primary Entity主键值
     *
     * @return String[]
     */
    public String[] getTableOneArray(Serializable primary) {
        if (tableStrategy == null) {
            return tableOneArray;
        }
        String t = tableStrategy.getTable(table, primary);
        if (t == null || t.isEmpty()) {
            throw new SourceException(table + " tableStrategy.getTable is empty, primary=" + primary);
        }
        return new String[]{t};
    }

    /**
     * 根据过滤条件获取Entity的表名
     *
     * @param node 过滤条件
     *
     * @return String
     */
    public String[] getTables(FilterNode node) {
        if (tableStrategy == null) {
            return new String[]{table};
        }
        String[] t = tableStrategy.getTables(table, node);
        if (t == null || t.length < 1) {
            throw new SourceException(table + " tableStrategy.getTable is empty, filter=" + node);
        }
        return t;
    }

    /**
     * 根据Entity对象获取Entity的表名
     *
     * @param bean Entity对象
     *
     * @return String
     */
    public String getTable(T bean) {
        if (tableStrategy == null) {
            return table;
        }
        String t = tableStrategy.getTable(table, bean);
        if (t == null || t.isEmpty()) {
            throw new SourceException(table + " tableStrategy.getTable is empty, entity=" + bean);
        }
        return t;
    }

    /**
     * 获取主键字段的EntityColumn
     *
     * @return Attribute
     */
    public EntityColumn getPrimaryColumn() {
        return this.primaryColumn;
    }

    /**
     * 获取主键字段的EntityColumn单一元素数组
     *
     * @return Attribute[]
     */
    public EntityColumn[] getPrimaryColumnOneArray() {
        return this.primaryColumnOneArray;
    }

    /**
     * 获取主键字段的Attribute
     *
     * @return Attribute
     */
    public Attribute<T, Serializable> getPrimary() {
        return this.primary;
    }

    /**
     * 获取主键字段的值
     *
     * @param entity 实体对象
     *
     * @return 主键值
     */
    public Serializable getPrimaryValue(T entity) {
        return this.primary.get(entity);
    }

    /**
     * 获取主键字段的Attribute单一元素数组
     *
     * @return Attribute[]
     */
    public Attribute<T, Serializable>[] getPrimaryOneArray() {
        return this.primaryOneArray;
    }

    /**
     * 遍历数据库表对应的所有字段, 不包含&#64;Transient字段
     *
     * @param action BiConsumer
     */
    public void forEachAttribute(BiConsumer<String, Attribute<T, Serializable>> action) {
        this.attributeMap.forEach(action);
    }

    /**
     * 根据Entity字段名获取字段的Attribute
     *
     * @param fieldname Class字段名
     *
     * @return Attribute
     */
    public Attribute<T, Serializable> getAttribute(String fieldname) {
        if (fieldname == null) {
            return null;
        }
        return this.attributeMap.get(fieldname);
    }

    /**
     * 根据Entity字段名获取可更新字段的Attribute
     *
     * @param fieldname Class字段名
     *
     * @return Attribute
     */
    public Attribute<T, Serializable> getUpdateAttribute(String fieldname) {
        return this.updateAttributeMap.get(fieldname);
    }

    /**
     * 判断Entity类的字段名与表字段名s是否存在不一致的值
     *
     * @return boolean
     */
    public boolean isNoAlias() {
        return this.aliasmap == null;
    }

    /**
     * 根据Flipper获取ORDER BY的SQL语句，不存在Flipper或sort字段返回空字符串
     *
     * @param flipper 翻页对象
     *
     * @return String
     */
    protected String createSQLOrderby(Flipper flipper) {
        if (flipper == null || flipper.getSort() == null) {
            return "";
        }
        final String sort = flipper.getSort();
        if (sort.isEmpty() || sort.indexOf(';') >= 0 || sort.indexOf('\n') >= 0) {
            return "";
        }
        String sql = this.sortOrderbySqls.get(sort);
        if (sql != null) {
            return sql;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(" ORDER BY ");
        if (isNoAlias()) {
            sb.append(sort);
        } else {
            boolean flag = false;
            for (String item : sort.split(",")) {
                if (item.isEmpty()) {
                    continue;
                }
                String[] sub = item.split("\\s+");
                if (flag) {
                    sb.append(',');
                }
                if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                    sb.append(getSQLColumn("a", sub[0])).append(" ASC");
                } else {
                    sb.append(getSQLColumn("a", sub[0])).append(" DESC");
                }
                flag = true;
            }
        }
        sql = sb.toString();
        this.sortOrderbySqls.put(sort, sql);
        return sql;
    }

    /**
     * 根据field字段名获取数据库对应的字段名
     *
     * @param tabalis   表别名
     * @param fieldname 字段名
     *
     * @return String
     */
    public String getSQLColumn(String tabalis, String fieldname) {
        return this.aliasmap == null ? (tabalis == null ? fieldname : (tabalis + '.' + fieldname))
            : (tabalis == null ? aliasmap.getOrDefault(fieldname, fieldname) : (tabalis + '.' + aliasmap.getOrDefault(fieldname, fieldname)));
    }

    /**
     * 字段值转换成数据库的值
     *
     * @param fieldname  字段名
     * @param fieldvalue 字段值
     *
     * @return Object
     */
    public Object getSQLValue(String fieldname, Serializable fieldvalue) {
        if (fieldvalue == null && fieldname != null && isNotNullable(fieldname)) {
            if (isNotNullJson(getAttribute(fieldname))) {
                return "";
            }
        }
        return fieldvalue;
    }

    /**
     * 字段值转换成带转义的数据库的值
     *
     * @param fieldname    字段名
     * @param fieldvalue   字段值
     * @param sqlFormatter 转义器
     *
     * @return CharSequence
     */
    public CharSequence formatSQLValue(String fieldname, Serializable fieldvalue, BiFunction<EntityInfo, Object, CharSequence> sqlFormatter) {
        Object val = getSQLValue(fieldname, fieldvalue);
        return sqlFormatter == null ? formatToString(val) : sqlFormatter.apply(this, val);
    }

    /**
     * 字段值转换成带转义的数据库的值
     *
     * @param value        字段值
     * @param sqlFormatter 转义器
     *
     * @return CharSequence
     */
    public CharSequence formatSQLValue(Object value, BiFunction<EntityInfo, Object, CharSequence> sqlFormatter) {
        return sqlFormatter == null ? formatToString(value) : sqlFormatter.apply(this, value);
    }

    /**
     * 字段值转换成数据库的值
     *
     * @param <F>    泛型
     * @param attr   Attribute
     * @param entity 记录对象
     *
     * @return Object
     */
    public <F> Serializable getSQLValue(Attribute<T, F> attr, T entity) {
        return (Serializable) attr.get(entity);
    }

    /**
     * 字段值转换成带转义的数据库的值
     *
     * @param <F>          泛型
     * @param attr         Attribute
     * @param entity       记录对象
     * @param sqlFormatter 转义器
     *
     * @return CharSequence
     */
    public <F> CharSequence formatSQLValue(Attribute<T, F> attr, T entity, BiFunction<EntityInfo, Object, CharSequence> sqlFormatter) {
        Serializable val = getSQLValue(attr, entity);
        return sqlFormatter == null ? formatToString(val) : sqlFormatter.apply(this, val);
    }

    /**
     * 数据库的值转换成数字段值
     *
     * @param attr   Attribute
     * @param entity 记录对象
     *
     * @return Object
     */
    public Serializable getFieldValue(Attribute<T, Serializable> attr, T entity) {
        return attr.get(entity);
    }

    /**
     * 获取主键字段名
     *
     * @return String
     */
    public String getPrimaryField() {
        return this.primary.field();
    }

    /**
     * 获取主键字段的表字段名
     *
     * @return String
     */
    public String getPrimarySQLColumn() {
        return getSQLColumn(null, this.primary.field());
    }

    /**
     * 获取主键字段的带有表别名的表字段名
     *
     * @param tabalis 表别名
     *
     * @return String
     */
    public String getPrimarySQLColumn(String tabalis) {
        return getSQLColumn(tabalis, this.primary.field());
    }

    /**
     * 拼接UPDATE给字段赋值的SQL片段
     *
     * @param sqlColumn 表字段名
     * @param attr      Attribute
     * @param cv        ColumnValue
     * @param formatter 转义器
     *
     * @return CharSequence
     */
    protected CharSequence formatSQLValue(String sqlColumn, Attribute<T, Serializable> attr, final ColumnValue cv, BiFunction<EntityInfo, Object, CharSequence> formatter) {
        if (cv == null) {
            return null;
        }
        Object val = cv.getValue();
        //ColumnNodeValue时 cv.getExpress() == ColumnExpress.MOV 只用于updateColumn
        if (val instanceof ColumnNodeValue) {
            return formatSQLValue(attr, null, (ColumnNodeValue) val, formatter);
        }
        if (val instanceof ColumnFuncNode) {
            return formatSQLValue(attr, null, (ColumnFuncNode) val, formatter);
        }
        switch (cv.getExpress()) {
            case INC:
                return new StringBuilder().append(sqlColumn).append(" + ").append(val);
            case DEC:
                return new StringBuilder().append(sqlColumn).append(" - ").append(val);
            case MUL:
                return new StringBuilder().append(sqlColumn).append(" * ").append(val);
            case DIV:
                return new StringBuilder().append(sqlColumn).append(" / ").append(val);
            case MOD:
                return new StringBuilder().append(sqlColumn).append(" % ").append(val);
            case AND:
                return new StringBuilder().append(sqlColumn).append(" & ").append(val);
            case ORR:
                return new StringBuilder().append(sqlColumn).append(" | ").append(val);
            case MOV:
                CharSequence rs = formatter == null ? formatToString(val) : formatter.apply(this, val);
                if (rs == null && isNotNullJson(attr)) {
                    rs = "";
                }
                return rs;
        }
        return formatter == null ? formatToString(val) : formatter.apply(this, val);
    }

    protected CharSequence formatSQLValue(Attribute<T, Serializable> attr, String tabalis, final ColumnFuncNode node, BiFunction<EntityInfo, Object, CharSequence> formatter) {
        if (node.getValue() instanceof ColumnNodeValue) {
            return node.getFunc().getColumn(formatSQLValue(attr, tabalis, (ColumnNodeValue) node.getValue(), formatter).toString());
        } else {
            return node.getFunc().getColumn(this.getSQLColumn(tabalis, String.valueOf(node.getValue())));
        }
    }

    protected CharSequence formatSQLValue(Attribute<T, Serializable> attr, String tabalis, final ColumnNodeValue node, BiFunction<EntityInfo, Object, CharSequence> formatter) {
        Serializable left = node.getLeft();
        if (left instanceof CharSequence) {
            left = this.getSQLColumn(tabalis, left.toString());
            if (node.getExpress() == ColumnExpress.MOV) {
                return (String) left;
            }
        } else if (left instanceof ColumnNodeValue) {
            left = "(" + formatSQLValue(attr, tabalis, (ColumnNodeValue) left, formatter) + ")";
        } else if (left instanceof ColumnFuncNode) {
            left = "(" + formatSQLValue(attr, tabalis, (ColumnFuncNode) left, formatter) + ")";
        }
        Serializable right = node.getRight();
        if (right instanceof CharSequence) {
            right = this.getSQLColumn(null, right.toString());
        } else if (left instanceof ColumnNodeValue) {
            right = "(" + formatSQLValue(attr, tabalis, (ColumnNodeValue) right, formatter) + ")";
        } else if (left instanceof ColumnFuncNode) {
            right = "(" + formatSQLValue(attr, tabalis, (ColumnFuncNode) right, formatter) + ")";
        }
        switch (node.getExpress()) {
            case INC:
                return new StringBuilder().append(left).append(" + ").append(right);
            case DEC:
                return new StringBuilder().append(left).append(" - ").append(right);
            case MUL:
                return new StringBuilder().append(left).append(" * ").append(right);
            case DIV:
                return new StringBuilder().append(left).append(" / ").append(right);
            case MOD:
                return new StringBuilder().append(left).append(" % ").append(right);
            case AND:
                return new StringBuilder().append(left).append(" & ").append(right);
            case ORR:
                return new StringBuilder().append(left).append(" | ").append(right);
        }
        throw new IllegalArgumentException(node + " express cannot be null or MOV");
    }

    /**
     * 获取所有数据表字段的Attribute, 不包含&#64;Transient字段
     *
     * @return Map
     */
    protected Map<String, Attribute<T, Serializable>> getAttributes() {
        return attributeMap;
    }

    /**
     * 判断日志级别
     *
     * @param logger Logger
     * @param l      Level
     *
     * @return boolean
     */
    public boolean isLoggable(Logger logger, Level l) {
        return logger.isLoggable(l) && l.intValue() >= this.logLevel;
    }

    public boolean isNotNullable(String fieldname) {
        return notNullColumns.contains(fieldname);
    }

    public boolean isNotNullable(Attribute<T, Serializable> attr) {
        return attr == null ? false : notNullColumns.contains(attr.field());
    }

    public boolean isNotNullJson(Attribute<T, Serializable> attr) {
        if (attr == null) {
            return false;
        }
        return notNullColumns.contains(attr.field())
            && !Number.class.isAssignableFrom(attr.type())
            && !CharSequence.class.isAssignableFrom(attr.type())
            && boolean.class != attr.type() && Boolean.class != attr.type()
            && byte[].class != attr.type()
            && java.util.Date.class != attr.type()
            && !attr.type().getName().startsWith("java.sql.") //避免引用import java.sql.* 减少模块依赖
            && !attr.type().getName().startsWith("java.time.");
    }

    /**
     * 判断日志级别
     *
     * @param logger Logger
     * @param l      Level
     * @param str    String
     *
     * @return boolean
     */
    public boolean isLoggable(Logger logger, Level l, String str) {
        boolean rs = logger.isLoggable(l) && l.intValue() >= this.logLevel;
        if (this.excludeLogLevels == null || !rs || str == null) {
            return rs;
        }
        String[] keys = this.excludeLogLevels.get(l.intValue());
        if (keys == null) {
            return rs;
        }
        for (String key : keys) {
            if (str.contains(key)) {
                return false;
            }
        }
        return rs;
    }

    /**
     * 将字段值序列化为可SQL的字符串
     *
     * @param value 字段值
     *
     * @return String
     */
    private String formatToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            return new StringBuilder().append('\'').append(value.toString().replace("'", "\\'")).append('\'').toString();
        } else if (!(value instanceof Number) && !(value instanceof java.util.Date)
            && !value.getClass().getName().startsWith("java.sql.") && !value.getClass().getName().startsWith("java.time.")) {
            return new StringBuilder().append('\'').append(jsonConvert.convertTo(value).replace("'", "\\'")).append('\'').toString();
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + type.getName() + ")@" + Objects.hashCode(this);
    }

    public static interface DataResultSetRow {

        //可以为空
        public EntityInfo getEntityInfo();

        //index从1开始
        public Object getObject(int index);

        public Object getObject(String columnLabel);

        //index从1开始
        default <T> Serializable getObject(Attribute<T, Serializable> attr, int index, String columnLabel) {
            return DataResultSet.getRowColumnValue(this, attr, index, columnLabel);
        }

        /**
         * 判断当前行值是否为null
         *
         * @return boolean
         */
        public boolean wasNull();

        /**
         * 获取字段名集合，尽量不要多次调用
         *
         * @return List
         */
        public List<String> getColumnLabels();
    }

    public static class EntityColumn {

        public final boolean primary; //是否主键

        public final String field;

        public final String column;

        public final Class type;

        public final String comment;

        public final boolean nullable;

        public final boolean unique;

        public final int length;

        public final int precision;

        public final int scale;

        protected EntityColumn(boolean primary, Column col, String name, Class type, Comment comment) {
            this.primary = primary;
            this.field = name;
            this.column = col == null || col.name().isEmpty() ? name : col.name();
            this.type = type;
            this.comment = (col == null || col.comment().isEmpty()) && comment != null && !comment.value().isEmpty() ? comment.value() : (col == null ? "" : col.comment());
            this.nullable = col == null ? false : col.nullable();
            this.unique = col == null ? false : col.unique();
            this.length = col == null ? 255 : col.length();
            this.precision = col == null ? 0 : col.precision();
            this.scale = col == null ? 0 : col.scale();
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
