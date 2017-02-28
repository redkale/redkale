/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Consumer;
import org.redkale.util.*;

/**
 *
 * DataSource 为数据库或内存数据库的数据源，提供类似JPA、Hibernate的接口与功能。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface DataSource {

    //----------------------insert-----------------------------
    /**
     * 新增记录， 多对象必须是同一个Entity类  <br>
     *
     * @param <T>    泛型
     * @param values Entity对象
     */
    public <T> void insert(final T... values);

    //-------------------------delete--------------------------
    /**
     * 删除指定主键值的记录， 多对象必须是同一个Entity类  <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id}  <br>
     *
     * @param <T>    泛型
     * @param values Entity对象
     *
     * @return 影响的记录条数
     */
    public <T> int delete(final T... values);

    /**
     * 删除指定主键值的记录  <br>
     * 等价SQL: DELETE FROM {table} WHERE {primary} IN {ids}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param ids   主键值
     *
     * @return 影响的记录条数
     */
    public <T> int delete(final Class<T> clazz, final Serializable... ids);

    /**
     * 删除符合过滤条件的记录  <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param node  过滤条件
     *
     * @return 影响的记录条数
     */
    public <T> int delete(final Class<T> clazz, final FilterNode node);

    /**
     * 删除符合过滤条件且指定最大影响条数的记录  <br>
     * Flipper中offset字段将被忽略  <br>
     * 等价SQL: DELETE FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param node    过滤条件
     *
     * @return 影响的记录条数
     */
    public <T> int delete(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    //------------------------update---------------------------
    /**
     * 更新记录， 多对象必须是同一个Entity类  <br>
     * 等价SQL:  <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1}  <br>
     * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2}  <br>
     * &#183;&#183;&#183;  <br>
     *
     * @param <T>    泛型
     * @param values Entity对象
     *
     * @return 影响的记录条数
     */
    public <T> int update(final T... values);

    /**
     * 更新单个记录的单个字段  <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param id     主键
     * @param column 待更新的字段名
     * @param value  更新值
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final String column, final Serializable value);

    /**
     * 更新符合过滤条件记录的单个字段   <br>
     * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node}   <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 待更新的字段名
     * @param value  更新值
     * @param node   过滤条件
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final String column, final Serializable value, final FilterNode node);

    /**
     * 更新指定主键值记录的部分字段   <br>
     * 字段赋值操作选项见 ColumnExpress   <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183; WHERE {filter node}   <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param id     主键
     * @param values 更新字段
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final Serializable id, final ColumnValue... values);

    /**
     * 更新符合过滤条件记录的部分字段   <br>
     * 字段赋值操作选项见 ColumnExpress   <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新   <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183; WHERE {filter node}   <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param node   过滤条件
     * @param values 更新字段
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final ColumnValue... values);

    /**
     * 更新符合过滤条件的记录的指定字段   <br>
     * Flipper中offset字段将被忽略   <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新   <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183; WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param node    过滤条件
     * @param flipper 翻页对象
     * @param values  更新字段
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final Class<T> clazz, final FilterNode node, final Flipper flipper, final ColumnValue... values);

    /**
     * 更新单个记录的指定字段   <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新   <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183; WHERE {primary} = {bean.id}  <br>
     *
     * @param <T>     Entity泛型
     * @param bean    待更新的Entity对象
     * @param columns 需更新的字段名
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final T bean, final String... columns);

    /**
     * 更新符合过滤条件记录的指定字段   <br>
     * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新   <br>
     * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183; WHERE {filter node}  <br>
     *
     * @param <T>     Entity泛型
     * @param bean    待更新的Entity对象
     * @param node    过滤条件
     * @param columns 需更新的字段名
     *
     * @return 影响的记录条数
     */
    public <T> int updateColumn(final T bean, final FilterNode node, final String... columns);

    /**
     * 由 public int updateColumn(final T bean, final String... columns); 代替
     *
     * @param <T>     T
     * @param bean    bean
     * @param columns columns
     *
     * @return int
     * @deprecated
     */
    @Deprecated
    public <T> int updateColumns(final T bean, final String... columns);

    /**
     * 由 public int updateColumn(final T bean, final FilterNode node, final String... columns); 代替
     *
     * @param <T>     T
     * @param bean    bean
     * @param node    node
     * @param columns columns
     *
     * @return int
     * @deprecated
     */
    @Deprecated
    public <T> int updateColumns(final T bean, final FilterNode node, final String... columns);

    //############################################# 查询接口 #############################################
    //-----------------------getXXXXResult-----------------------------
    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.COUNT, null) 等价于: SELECT COUNT(*) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param column      指定字段
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.COUNT, null, (FilterBean)null) 等价于: SELECT COUNT(*) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param column      指定字段
     * @param bean        过滤条件
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterBean bean);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回null   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param column      指定字段
     * @param node        过滤条件
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final String column, final FilterNode node);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.MAX, "createtime") 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param defVal      默认值
     * @param column      指定字段
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param defVal      默认值
     * @param column      指定字段
     * @param bean        过滤条件
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterBean bean);

    /**
     * 获取符合过滤条件记录的聚合结果, 无结果返回默认值   <br>
     * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node}  <br>
     * 如 getNumberResult(Record.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param entityClass Entity类
     * @param func        聚合函数
     * @param defVal      默认值
     * @param column      指定字段
     * @param node        过滤条件
     *
     * @return 聚合结果
     */
    public Number getNumberResult(final Class entityClass, final FilterFunc func, final Number defVal, final String column, final FilterNode node);

    /**
     * 获取符合过滤条件记录的聚合结果Map   <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table}  <br>
     * 如 getNumberMap(Record.class, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param <N>         Number
     * @param entityClass Entity类
     * @param columns     聚合字段
     *
     * @return 聚合结果Map
     */
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterFuncColumn... columns);

    /**
     * 获取符合过滤条件记录的聚合结果Map   <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean}  <br>
     * 如 getNumberMap(Record.class, (FilterBean)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param <N>         Number
     * @param entityClass Entity类
     * @param bean        过滤条件
     * @param columns     聚合字段
     *
     * @return 聚合结果Map
     */
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterBean bean, final FilterFuncColumn... columns);

    /**
     * 获取符合过滤条件记录的聚合结果Map   <br>
     * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node}  <br>
     * 如 getNumberMap(Record.class, (FilterNode)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime) FROM {table} <br>
     *
     * @param <N>         Number
     * @param entityClass Entity类
     * @param node        过滤条件
     * @param columns     聚合字段
     *
     * @return 聚合结果Map
     */
    public <N extends Number> Map<String, N> getNumberMap(final Class entityClass, final FilterNode node, final FilterFuncColumn... columns);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map   <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} GROUP BY {keyColumn}  <br>
     * 如 queryColumnMap(Record.class, "name", FilterFunc.MAX, "createtime") 等价于: SELECT name, MAX(createtime) FROM record GROUP BY name<br>
     *
     * @param <T>         Entity泛型
     * @param <K>         Key字段的数据类型
     * @param <N>         Number
     * @param entityClass Entity类
     * @param keyColumn   Key字段
     * @param func        聚合函数
     * @param funcColumn  聚合字段
     *
     * @return 聚合结果Map
     */
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map   <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter bean} GROUP BY {keyColumn}  <br>
     * 如 queryColumnMap(Record.class, "name", FilterFunc.MAX, "createtime", (FilterBean)null) 等价于: SELECT name, MAX(createtime) FROM record GROUP BY name<br>
     *
     * @param <T>         Entity泛型
     * @param <K>         Key字段的数据类型
     * @param <N>         Number
     * @param entityClass Entity类
     * @param keyColumn   Key字段
     * @param func        聚合函数
     * @param funcColumn  聚合字段
     * @param bean        过滤条件
     *
     * @return 聚合结果Map
     */
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的GROUP BY聚合结果Map   <br>
     * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter node} GROUP BY {keyColumn}  <br>
     * 如 queryColumnMap(Record.class, "name", FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT name, MAX(createtime) FROM record GROUP BY name<br>
     *
     * @param <T>         Entity泛型
     * @param <K>         Key字段的数据类型
     * @param <N>         Number
     * @param entityClass Entity类
     * @param keyColumn   Key字段
     * @param func        聚合函数
     * @param funcColumn  聚合字段
     * @param node        过滤条件
     *
     * @return 聚合结果Map
     */
    public <T, K extends Serializable, N extends Number> Map<K, N> queryColumnMap(final Class<T> entityClass, final String keyColumn, final FilterFunc func, final String funcColumn, final FilterNode node);

    //-----------------------find----------------------------
    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param pk    主键值
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final Serializable pk);

    /**
     * 获取指定主键值的单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param pk      主键值
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param key    过滤字段值
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final String column, final Serializable key);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param bean  过滤条件
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final FilterBean bean);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param node  过滤条件
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param bean    过滤条件
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    /**
     * 获取符合过滤条件单个记录, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param node    过滤条件
     *
     * @return Entity对象
     */
    public <T> T find(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 字段名
     * @param pk     主键值
     *
     * @return Entity对象
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 字段名
     * @param bean   过滤条件
     *
     * @return 字段值
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterBean bean);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 字段名
     * @param node   过滤条件
     *
     * @return 字段值
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final FilterNode node);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id}  <br>
     *
     * @param <T>      Entity泛型
     * @param clazz    Entity类
     * @param column   字段名
     * @param defValue 默认值
     * @param pk       主键值
     *
     * @return 字段值
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final Serializable pk);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>      Entity泛型
     * @param clazz    Entity类
     * @param column   字段名
     * @param defValue 默认值
     * @param bean     过滤条件
     *
     * @return 字段值
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterBean bean);

    /**
     * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值   <br>
     * 等价SQL: SELECT {column} FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>      Entity泛型
     * @param clazz    Entity类
     * @param column   字段名
     * @param defValue 默认值
     * @param node     过滤条件
     *
     * @return 字段值
     */
    public <T> Serializable findColumn(final Class<T> clazz, final String column, final Serializable defValue, final FilterNode node);

    /**
     * 判断是否存在主键值的记录   <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {primary} = {id}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param pk    主键值
     *
     * @return 是否存在
     */
    public <T> boolean exists(final Class<T> clazz, final Serializable pk);

    /**
     * 判断是否存在符合过滤条件的记录   <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param bean  过滤条件
     *
     * @return 是否存在
     */
    public <T> boolean exists(final Class<T> clazz, final FilterBean bean);

    /**
     * 判断是否存在符合过滤条件的记录   <br>
     * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param node  过滤条件
     *
     * @return 是否存在
     */
    public <T> boolean exists(final Class<T> clazz, final FilterNode node);

    //-----------------------list set----------------------------
    /**
     * 查询符合过滤条件记录的某个字段Set集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param column         过滤字段名
     * @param key            过滤字段值
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    /**
     * 查询符合过滤条件记录的某个字段Set集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param bean           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的某个字段Set集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param node           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> HashSet<V> queryColumnSet(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段List集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param column         过滤字段名
     * @param key            过滤字段值
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final String column, final Serializable key);

    /**
     * 查询符合过滤条件记录的某个字段List集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param bean           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的某个字段List集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param node           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段List集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param bean           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的某个字段List集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param node           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> List<V> queryColumnList(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param bean           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的某个字段Sheet集合   <br>
     * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>            Entity泛型
     * @param <V>            字段类型
     * @param selectedColumn 指定字段
     * @param clazz          Entity类
     * @param flipper        翻页对象
     * @param node           过滤条件
     *
     * @return 字段值的集合
     */
    public <T, V extends Serializable> Sheet<V> queryColumnSheet(final String selectedColumn, final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>    Entity泛型
     * @param clazz  Entity类
     * @param column 过滤字段名
     * @param key    过滤字段值
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final String column, final Serializable key);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param bean  过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>   Entity泛型
     * @param clazz Entity类
     * @param node  过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param bean    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param node    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param column  过滤字段名
     * @param key     过滤字段值
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final String column, final Serializable key);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param node    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的List集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node    过滤条件
     *
     * @return Entity的集合
     */
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //-----------------------sheet----------------------------
    /**
     * 查询符合过滤条件记录的Sheet集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param bean    过滤条件
     *
     * @return Entity的集合
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的Sheet集合   <br>
     * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param flipper 翻页对象
     * @param node    过滤条件
     *
     * @return Entity的集合
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final Flipper flipper, final FilterNode node);

    /**
     * 查询符合过滤条件记录的Sheet集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param bean    过滤条件
     *
     * @return Entity的集合
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean);

    /**
     * 查询符合过滤条件记录的Sheet集合   <br>
     * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}  <br>
     *
     * @param <T>     Entity泛型
     * @param clazz   Entity类
     * @param selects 指定字段
     * @param flipper 翻页对象
     * @param node    过滤条件
     *
     * @return Entity的集合
     */
    public <T> Sheet<T> querySheet(final Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterNode node);

    //-----------------------direct----------------------------
    /**
     * 直接本地执行SQL语句进行查询，远程模式不可用   <br>
     * 通常用于复杂的关联查询   <br>
     *
     * @param sql      SQL语句
     * @param consumer 回调函数
     */
    public void directQuery(String sql, final Consumer<ResultSet> consumer);

    /**
     * 直接本地执行SQL语句进行增删改操作，远程模式不可用   <br>
     * 通常用于复杂的更新操作   <br>
     *
     * @param sqls SQL语句
     *
     * @return 结果数组
     */
    public int[] directExecute(String... sqls);
}
