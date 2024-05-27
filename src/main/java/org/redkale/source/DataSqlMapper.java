/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.redkale.annotation.ClassDepends;
import org.redkale.util.LambdaFunction;
import org.redkale.util.LambdaSupplier;
import org.redkale.util.SelectColumn;
import org.redkale.util.Sheet;

/**
 * 类似Mybatis的Mapper接口类, 接口系列和DataSource相似度高 <br>
 * 自定义的sql接口的返回结果类型只能是: void/基本数据类型/JavaBean/Map/List/Sheet <br>
 * 异步接口返回的是泛型为以上类型的CompletableFuture
 *
 * <blockquote>
 *
 * <pre>
 * public interface ForumInfoMapper extends BaseMapper&lt;ForumInfo&gt; {
 *
 *   &#64;Sql("SELECT f.forum_groupid, s.forum_section_color "
 *      + "FROM forum_info f, forum_section s "
 *      + " WHERE f.forumid = s.forumid AND "
 *      + "s.forum_sectionid = #{bean.forumSectionid} AND "
 *      + "f.forumid = #{bean.forumid} AND s.forum_section_color = #{bean.forumSectionColor}")
 *   public ForumResult findForumResult(ForumBean bean);
 *
 *   &#64;Sql("SELECT f.forum_groupid, s.forum_section_color "
 *      + "FROM forum_info f, forum_section s "
 *      + " WHERE f.forumid = s.forumid AND "
 *      + "s.forum_sectionid = #{bean.forumSectionid} AND "
 *      + "f.forumid = #{bean.forumid} AND s.forum_section_color = #{bean.forumSectionColor}")
 *   public CompletableFuture&lt;ForumResult&gt; findForumResultAsync(ForumBean bean);
 *
 *   &#64;Sql("SELECT f.forum_groupid, s.forum_section_color "
 *      + "FROM forum_info f, forum_section s "
 *      + " WHERE f.forumid = s.forumid AND "
 *      + "s.forum_sectionid = #{bean.forumSectionid} AND "
 *      + "f.forumid = #{bean.forumid} AND s.forum_section_color = #{bean.forumSectionColor}")
 *   public List&lt;ForumResult&gt; queryForumResult(&#64;Param("bean") ForumBean bean0);
 *
 *   &#64;Sql("SELECT f.forum_groupid, s.forum_section_color "
 *      + "FROM forum_info f, forum_section s "
 *      + " WHERE f.forumid = s.forumid AND "
 *      + "s.forum_sectionid = #{bean.forumSectionid} AND "
 *      + "f.forumid = #{bean.forumid} AND s.forum_section_color = #{bean.forumSectionColor}")
 *   public CompletableFuture&lt;List&lt;ForumResult&gt;&gt; queryForumResultAsync(ForumBean bean);
 * }
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.source.spi.DataSqlMapperBuilder
 * @see org.redkale.persistence.Sql
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public interface DataSqlMapper<T> {

	/**
	 * 获取当前数据源
	 *
	 * @return DataSqlSource
	 */
	@ClassDepends
	DataSqlSource dataSource();

	/**
	 * 获取当前实体类型
	 *
	 * @return Class
	 */
	@ClassDepends
	Class<T> entityType();

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default int insert(T... entitys) {
		return dataSource().insert(entitys);
	}

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default int insert(Collection<T> entitys) {
		return dataSource().insert(entitys);
	}

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default int insert(Stream<T> entitys) {
		return dataSource().insert(entitys);
	}

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default CompletableFuture<Integer> insertAsync(T... entitys) {
		return dataSource().insertAsync(entitys);
	}

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default CompletableFuture<Integer> insertAsync(Collection<T> entitys) {
		return dataSource().insertAsync(entitys);
	}

	/**
	 * 新增记录 <br>
	 *
	 * @param entitys Entity对象
	 * @return CompletableFuture
	 */
	default CompletableFuture<Integer> insertAsync(Stream<T> entitys) {
		return dataSource().insertAsync(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default int delete(T... entitys) {
		return dataSource().delete(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default int delete(Collection<T> entitys) {
		return dataSource().delete(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default int delete(Stream<T> entitys) {
		return dataSource().delete(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> deleteAsync(T... entitys) {
		return dataSource().deleteAsync(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> deleteAsync(Collection<T> entitys) {
		return dataSource().deleteAsync(entitys);
	}

	/**
	 * 删除指定主键值的记录 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {values.id} <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> deleteAsync(Stream<T> entitys) {
		return dataSource().deleteAsync(entitys);
	}

	/**
	 * 删除指定主键值的记录,多主键值必须在同一张表中 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {ids} <br>
	 *
	 * @param pks 主键值
	 * @return 影响的记录条数
	 */
	default int deleteById(Serializable... pks) {
		return dataSource().delete(entityType(), pks);
	}

	/**
	 * 删除指定主键值的记录,多主键值必须在同一张表中 <br>
	 * 等价SQL: DELETE FROM {table} WHERE {primary} IN {ids} <br>
	 *
	 * @param pks 主键值
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> deleteByIdAsync(Serializable... pks) {
		return dataSource().deleteAsync(entityType(), pks);
	}

	// ------------------------update---------------------------
	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数
	 */
	default int update(T... entitys) {
		return dataSource().update(entitys);
	}

	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数
	 */
	default int update(Collection<T> entitys) {
		return dataSource().update(entitys);
	}

	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数
	 */
	default int update(Stream<T> entitys) {
		return dataSource().update(entitys);
	}

	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateAsync(T... entitys) {
		return dataSource().updateAsync(entitys);
	}

	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateAsync(Collection<T> entitys) {
		return dataSource().updateAsync(entitys);
	}

	/**
	 * 更新记录 <br>
	 * 等价SQL: <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id1} <br>
	 * UPDATE {table} SET column1 = value1, column2 = value2, &#183;&#183;&#183; WHERE {primary} = {id2} <br>
	 * &#183;&#183;&#183; <br>
	 *
	 * @param entitys Entity对象
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateAsync(Stream<T> entitys) {
		return dataSource().updateAsync(entitys);
	}

	/**
	 * 更新单个记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键
	 * @param column 待更新的字段名
	 * @param value 更新值
	 * @return 影响的记录条数
	 */
	default int updateColumn(Serializable pk, String column, Serializable value) {
		return dataSource().updateColumn(entityType(), pk, column, value);
	}

	/**
	 * 更新单个记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
	 *
	 * @param <V> 更新值泛型
	 * @param pk 主键
	 * @param func 更新值Lambda
	 * @return 影响的记录条数
	 */
	default <V extends Serializable> int updateColumn(Serializable pk, LambdaSupplier<V> func) {
		return dataSource().updateColumn(entityType(), pk, func);
	}

	/**
	 * 更新单个记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键
	 * @param column 待更新的字段名
	 * @param value 更新值
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(Serializable pk, String column, Serializable value) {
		return dataSource().updateColumnAsync(entityType(), pk, column, value);
	}

	/**
	 * 更新单个记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {primary} = {id} <br>
	 *
	 * @param <V> 更新值泛型
	 * @param pk 主键
	 * @param func 更新值Lambda
	 * @return 影响的记录条数
	 */
	default <V extends Serializable> CompletableFuture<Integer> updateColumnAsync(
			Serializable pk, LambdaSupplier<V> func) {
		return dataSource().updateColumnAsync(entityType(), pk, func);
	}

	/**
	 * 更新符合过滤条件记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
	 *
	 * @param column 待更新的字段名
	 * @param value 更新值
	 * @param node 过滤条件
	 * @return 影响的记录条数
	 */
	default int updateColumn(String column, Serializable value, FilterNode node) {
		return dataSource().updateColumn(entityType(), column, value, node);
	}

	/**
	 * 更新符合过滤条件记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
	 *
	 * @param <V> 更新值泛型
	 * @param func 更新值Lambda
	 * @param node 过滤条件
	 * @return 影响的记录条数
	 */
	default <V extends Serializable> int updateColumn(LambdaSupplier<V> func, FilterNode node) {
		return dataSource().updateColumn(entityType(), func, node);
	}

	/**
	 * 更新符合过滤条件记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
	 *
	 * @param column 待更新的字段名
	 * @param value 更新值
	 * @param node 过滤条件
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(String column, Serializable value, FilterNode node) {
		return dataSource().updateColumnAsync(entityType(), column, value, node);
	}

	/**
	 * 更新符合过滤条件记录的单个字段 <br>
	 * <b>注意</b>：即使字段标记为&#064;Column(updatable=false)也会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column} = {value} WHERE {filter node} <br>
	 *
	 * @param <V> 更新值泛型
	 * @param func 更新值Lambda
	 * @param node 过滤条件
	 * @return 影响的记录条数
	 */
	default <V extends Serializable> CompletableFuture<Integer> updateColumnAsync(
			LambdaSupplier<V> func, FilterNode node) {
		return dataSource().updateColumnAsync(entityType(), func, node);
	}

	/**
	 * 更新指定主键值记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param pk 主键
	 * @param values 更新字段
	 * @return 影响的记录条数
	 */
	default int updateColumn(Serializable pk, ColumnValue... values) {
		return dataSource().updateColumn(entityType(), pk, values);
	}

	/**
	 * 更新指定主键值记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param pk 主键
	 * @param func 更新字段
	 * @param value 更新字段值
	 * @return 影响的记录条数
	 */
	default int updateColumn(Serializable pk, LambdaFunction<T, ?> func, Serializable value) {
		return dataSource().updateColumn(entityType(), pk, func, value);
	}

	/**
	 * 更新指定主键值记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param pk 主键
	 * @param values 更新字段
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(Serializable pk, ColumnValue... values) {
		return dataSource().updateColumnAsync(entityType(), pk, values);
	}

	/**
	 * 更新指定主键值记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param pk 主键
	 * @param func 更新字段
	 * @param value 更新字段值
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(
			Serializable pk, LambdaFunction<T, ?> func, Serializable value) {
		return dataSource().updateColumnAsync(entityType(), pk, func, value);
	}

	/**
	 * 更新符合过滤条件记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @param values 更新字段
	 * @return 影响的记录条数
	 */
	default int updateColumn(FilterNode node, ColumnValue... values) {
		return dataSource().updateColumn(entityType(), node, values);
	}

	/**
	 * 更新符合过滤条件记录的部分字段 <br>
	 * 字段赋值操作选项见 ColumnExpress <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @param values 更新字段
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(FilterNode node, ColumnValue... values) {
		return dataSource().updateColumnAsync(entityType(), node, values);
	}

	/**
	 * 更新符合过滤条件的记录的指定字段 <br>
	 * Flipper中offset字段将被忽略 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} ORDER BY {flipper.sort} <br>
	 *
	 * @param node 过滤条件
	 * @param flipper 翻页对象
	 * @param values 更新字段
	 * @return 影响的记录条数
	 */
	default int updateColumn(FilterNode node, Flipper flipper, ColumnValue... values) {
		return dataSource().updateColumn(entityType(), node, flipper, values);
	}

	/**
	 * 更新符合过滤条件的记录的指定字段 <br>
	 * Flipper中offset字段将被忽略 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} += {value2}, {column3} *= {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} ORDER BY {flipper.sort} <br>
	 *
	 * @param node 过滤条件
	 * @param flipper 翻页对象
	 * @param values 更新字段
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(FilterNode node, Flipper flipper, ColumnValue... values) {
		return dataSource().updateColumnAsync(entityType(), node, flipper, values);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param columns 需更新的字段名
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, String... columns) {
		return dataSource().updateColumn(entityType(), columns);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param funcs 需更新的字段名Lambda集合
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, LambdaFunction<T, ?>... funcs) {
		return dataSource().updateColumn(entityType(), funcs);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param columns 需更新的字段名
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, String... columns) {
		return dataSource().updateColumnAsync(entityType(), columns);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param funcs 需更新的字段名Lambda集合
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, LambdaFunction<T, ?>... funcs) {
		return dataSource().updateColumnAsync(entityType(), funcs);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param columns 需更新的字段名
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, FilterNode node, String... columns) {
		return dataSource().updateColumn(entity, node, columns);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param funcs 需更新的字段名Lambda集合
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, FilterNode node, LambdaFunction<T, ?>... funcs) {
		return dataSource().updateColumn(entity, node, funcs);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param columns 需更新的字段名
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, FilterNode node, String... columns) {
		return dataSource().updateColumnAsync(entity, node, columns);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param funcs 需更新的字段名Lambda集合
	 * @return 影响的记录条数
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, FilterNode node, LambdaFunction<T, ?>... funcs) {
		return dataSource().updateColumnAsync(entity, node, funcs);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param selects 指定字段
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, SelectColumn selects) {
		return dataSource().updateColumn(entity, selects);
	}

	/**
	 * 更新单个记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {primary} = {bean.id} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param selects 指定字段
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, SelectColumn selects) {
		return dataSource().updateColumnAsync(entity, selects);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param selects 指定字段
	 * @return 影响的记录条数
	 */
	default int updateColumn(T entity, FilterNode node, SelectColumn selects) {
		return dataSource().updateColumn(entity, node, selects);
	}

	/**
	 * 更新符合过滤条件记录的指定字段 <br>
	 * <b>注意</b>：Entity类中标记为&#064;Column(updatable=false)不会被更新 <br>
	 * 等价SQL: UPDATE {table} SET {column1} = {value1}, {column2} = {value2}, {column3} = {value3}, &#183;&#183;&#183;
	 * WHERE {filter node} <br>
	 *
	 * @param entity 待更新的Entity对象
	 * @param node 过滤条件
	 * @param selects 指定字段
	 * @return 影响的记录条数CompletableFuture
	 */
	default CompletableFuture<Integer> updateColumnAsync(T entity, FilterNode node, SelectColumn selects) {
		return dataSource().updateColumnAsync(entity, node, selects);
	}

	// -----------------------getXXXXResult-----------------------------
	default Number getNumberResult(FilterFunc func, String column) {
		return dataSource().getNumberResult(entityType(), func, column);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null) 等价于: SELECT COUNT(*) FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param column 指定字段
	 * @return 聚合结果CompletableFuture
	 */
	default CompletableFuture<Number> getNumberResultAsync(FilterFunc func, String column) {
		return dataSource().getNumberResultAsync(entityType(), func, column);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null, (FilterBean)null) 等价于: SELECT COUNT(*) FROM {table}
	 * <br>
	 *
	 * @param func 聚合函数
	 * @param column 指定字段
	 * @param bean 过滤条件
	 * @return 聚合结果
	 */
	default Number getNumberResult(FilterFunc func, String column, FilterBean bean) {
		return dataSource().getNumberResult(entityType(), func, column, bean);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.COUNT, null, (FilterBean)null) 等价于: SELECT COUNT(*) FROM {table}
	 * <br>
	 *
	 * @param func 聚合函数
	 * @param column 指定字段
	 * @param bean 过滤条件
	 * @return 聚合结果CompletableFuture
	 */
	default CompletableFuture<Number> getNumberResultAsync(FilterFunc func, String column, FilterBean bean) {
		return dataSource().getNumberResultAsync(entityType(), func, column, bean);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param column 指定字段
	 * @param node 过滤条件
	 * @return 聚合结果
	 */
	default Number getNumberResult(FilterFunc func, String column, FilterNode node) {
		return dataSource().getNumberResult(entityType(), func, column, node);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回null <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param column 指定字段
	 * @param node 过滤条件
	 * @return 聚合结果
	 */
	default CompletableFuture<Number> getNumberResultAsync(FilterFunc func, String column, FilterNode node) {
		return dataSource().getNumberResultAsync(entityType(), func, column, node);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime") 等价于: SELECT MAX(createtime) FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @return 聚合结果
	 */
	default Number getNumberResult(FilterFunc func, Number defVal, String column) {
		return dataSource().getNumberResult(entityType(), func, defVal, column);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime") 等价于: SELECT MAX(createtime) FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @return 聚合结果CompletableFuture
	 */
	default CompletableFuture<Number> getNumberResultAsync(FilterFunc func, Number defVal, String column) {
		return dataSource().getNumberResultAsync(entityType(), func, defVal, column);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @param bean 过滤条件
	 * @return 聚合结果
	 */
	default Number getNumberResult(FilterFunc func, Number defVal, String column, FilterBean bean) {
		return dataSource().getNumberResult(entityType(), func, defVal, column, bean);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @param bean 过滤条件
	 * @return 聚合结果CompletableFuture
	 */
	default CompletableFuture<Number> getNumberResultAsync(
			FilterFunc func, Number defVal, String column, FilterBean bean) {
		return dataSource().getNumberResultAsync(entityType(), func, defVal, column, bean);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @param node 过滤条件
	 * @return 聚合结果
	 */
	default Number getNumberResult(FilterFunc func, Number defVal, String column, FilterNode node) {
		return dataSource().getNumberResult(entityType(), func, defVal, column, node);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果, 无结果返回默认值 <br>
	 * 等价SQL: SELECT FUNC{column} FROM {table} WHERE {filter node} <br>
	 * 如 getNumberResultAsync(User.class, FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param func 聚合函数
	 * @param defVal 默认值
	 * @param column 指定字段
	 * @param node 过滤条件
	 * @return 聚合结果CompletableFuture
	 */
	default CompletableFuture<Number> getNumberResultAsync(
			FilterFunc func, Number defVal, String column, FilterNode node) {
		return dataSource().getNumberResultAsync(entityType(), func, defVal, column, node);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} <br>
	 * 如 getNumberMapAsync(User.class, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param columns 聚合字段
	 * @return 聚合结果Map
	 */
	default <N extends Number> Map<String, N> getNumberMap(FilterFuncColumn... columns) {
		return dataSource().getNumberMap(entityType(), columns);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} <br>
	 * 如 getNumberMapAsync(User.class, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT MAX(createtime)
	 * FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param columns 聚合字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(FilterFuncColumn... columns) {
		return dataSource().getNumberMapAsync(entityType(), columns);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberMapAsync(User.class, (FilterBean)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
	 * MAX(createtime) FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param bean 过滤条件
	 * @param columns 聚合字段
	 * @return 聚合结果Map
	 */
	default <N extends Number> Map<String, N> getNumberMap(FilterBean bean, FilterFuncColumn... columns) {
		return dataSource().getNumberMap(entityType(), bean, columns);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 * 如 getNumberMapAsync(User.class, (FilterBean)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
	 * MAX(createtime) FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param bean 过滤条件
	 * @param columns 聚合字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
			FilterBean bean, FilterFuncColumn... columns) {
		return dataSource().getNumberMapAsync(entityType(), bean, columns);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 * 如 getNumberMapAsync(User.class, (FilterNode)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
	 * MAX(createtime) FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param node 过滤条件
	 * @param columns 聚合字段
	 * @return 聚合结果Map
	 */
	default <N extends Number> Map<String, N> getNumberMap(FilterNode node, FilterFuncColumn... columns) {
		return dataSource().getNumberMap(entityType(), node, columns);
	}

	/**
	 * 获取符合过滤条件记录的聚合结果Map <br>
	 * 等价SQL: SELECT FUNC1{column1}, FUNC2{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 * 如 getNumberMapAsync(User.class, (FilterNode)null, new FilterFuncColumn(FilterFunc.MAX, "createtime")) 等价于: SELECT
	 * MAX(createtime) FROM {table} <br>
	 *
	 * @param <N> Number
	 * @param node 过滤条件
	 * @param columns 聚合字段
	 * @return 聚合结果Map
	 */
	default <N extends Number> CompletableFuture<Map<String, N>> getNumberMapAsync(
			FilterNode node, FilterFuncColumn... columns) {
		return dataSource().getNumberMapAsync(entityType(), node, columns);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime") 等价于: SELECT name, MAX(createtime) FROM
	 * user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @return 聚合结果Map
	 */
	default <K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
			String keyColumn, FilterFunc func, String funcColumn) {
		return dataSource().queryColumnMap(entityType(), keyColumn, func, funcColumn);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime") 等价于: SELECT name, MAX(createtime) FROM
	 * user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
			String keyColumn, FilterFunc func, String funcColumn) {
		return dataSource().queryColumnMapAsync(entityType(), keyColumn, func, funcColumn);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter bean} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterBean)null) 等价于: SELECT name,
	 * MAX(createtime) FROM user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map
	 */
	default <K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
			String keyColumn, FilterFunc func, String funcColumn, FilterBean bean) {
		return dataSource().queryColumnMap(entityType(), keyColumn, func, funcColumn, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter bean} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterBean)null) 等价于: SELECT name,
	 * MAX(createtime) FROM user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
			String keyColumn, FilterFunc func, String funcColumn, FilterBean bean) {
		return dataSource().queryColumnMapAsync(entityType(), keyColumn, func, funcColumn, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter node} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT name,
	 * MAX(createtime) FROM user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @param node 过滤条件
	 * @return 聚合结果Map
	 */
	default <K extends Serializable, N extends Number> Map<K, N> queryColumnMap(
			String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
		return dataSource().queryColumnMap(entityType(), keyColumn, func, funcColumn, node);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT keyColumn, FUNC{funcColumn} FROM {table} WHERE {filter node} GROUP BY {keyColumn} <br>
	 * 如 queryColumnMapAsync(User.class, "name", FilterFunc.MAX, "createtime", (FilterNode)null) 等价于: SELECT name,
	 * MAX(createtime) FROM user GROUP BY name<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param keyColumn Key字段
	 * @param func 聚合函数
	 * @param funcColumn 聚合字段
	 * @param node 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N>> queryColumnMapAsync(
			String keyColumn, FilterFunc func, String funcColumn, FilterNode node) {
		return dataSource().queryColumnMapAsync(entityType(), keyColumn, func, funcColumn, node);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid") 等价于: SELECT targetid, SUM(money) / 100,
	 * AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
			ColumnNode[] funcNodes, String groupByColumn) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumn);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid") 等价于: SELECT targetid, SUM(money) / 100,
	 * AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String groupByColumn) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumn);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterBean)null) 等价于: SELECT targetid,
	 * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
			ColumnNode[] funcNodes, String groupByColumn, FilterBean bean) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumn, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterBean)null) 等价于: SELECT targetid,
	 * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String groupByColumn, FilterBean bean) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumn, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterNode)null) 等价于: SELECT targetid,
	 * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @param node 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> Map<K, N[]> queryColumnMap(
			ColumnNode[] funcNodes, String groupByColumn, FilterNode node) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumn, node);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), "targetid", (FilterNode)null) 等价于: SELECT targetid,
	 * SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumn GROUP BY字段
	 * @param node 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K, N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String groupByColumn, FilterNode node) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumn, node);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1}, {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid")) 等价于: SELECT fromid,
	 * targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
			ColumnNode[] funcNodes, String[] groupByColumns) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumns);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} GROUP BY {col1}, {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid")) 等价于: SELECT fromid,
	 * targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String[] groupByColumns) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumns);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1},
	 * {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterBean)null)
	 * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
			ColumnNode[] funcNodes, String[] groupByColumns, FilterBean bean) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumns, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter bean} GROUP BY {col1},
	 * {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterBean)null)
	 * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @param bean 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String[] groupByColumns, FilterBean bean) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumns, bean);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1},
	 * {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterNode)null)
	 * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @param node 过滤条件
	 * @return 聚合结果Map
	 */
	default <K extends Serializable, N extends Number> Map<K[], N[]> queryColumnMap(
			ColumnNode[] funcNodes, String[] groupByColumns, FilterNode node) {
		return dataSource().queryColumnMap(entityType(), funcNodes, groupByColumns, node);
	}

	/**
	 * 查询符合过滤条件记录的GROUP BY聚合结果Map <br>
	 * 等价SQL: SELECT col1, col2, FUNC{funcColumn1}, FUNC{funcColumn2} FROM {table} WHERE {filter node} GROUP BY {col1},
	 * {col2} <br>
	 * 如 queryColumnMapAsync(OrderRecord.class, Utility.ofArray(ColumnExpNode.div(ColumnFuncNode.sum("money"), 100),
	 * ColumnFuncNode.avg(ColumnExpNode.dec("money", 20)))), Utility.ofArray("fromid", "targetid"), (FilterNode)null)
	 * 等价于: SELECT fromid, targetid, SUM(money) / 100, AVG(money - 20) FROM orderrecord GROUP BY fromid, targetid<br>
	 *
	 * @param <K> Key字段的数据类型
	 * @param <N> Number
	 * @param funcNodes ColumnNode[]
	 * @param groupByColumns GROUP BY字段
	 * @param node 过滤条件
	 * @return 聚合结果Map CompletableFuture
	 */
	default <K extends Serializable, N extends Number> CompletableFuture<Map<K[], N[]>> queryColumnMapAsync(
			ColumnNode[] funcNodes, String[] groupByColumns, FilterNode node) {
		return dataSource().queryColumnMapAsync(entityType(), funcNodes, groupByColumns, node);
	}

	// -----------------------findAsync----------------------------
	/**
	 * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键值
	 * @return Entity对象
	 */
	default T find(Serializable pk) {
		return dataSource().find(entityType(), pk);
	}

	/**
	 * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键值
	 * @return Entity对象 CompletableFuture
	 */
	default CompletableFuture<T> findAsync(Serializable pk) {
		return dataSource().findAsync(entityType(), pk);
	}

	/**
	 * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param selects 指定字段
	 * @param pk 主键值
	 * @return Entity对象
	 */
	default T find(SelectColumn selects, Serializable pk) {
		return dataSource().find(entityType(), selects, pk);
	}

	/**
	 * 获取指定主键值的单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param selects 指定字段
	 * @param pk 主键值
	 * @return Entity对象CompletableFuture
	 */
	default CompletableFuture<T> findAsync(SelectColumn selects, Serializable pk) {
		return dataSource().findAsync(entityType(), selects, pk);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
	 *
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default T[] finds(Serializable... pks) {
		return dataSource().finds(entityType(), pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
	 *
	 * @param <D> 主键泛型
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default <D extends Serializable> T[] finds(Stream<D> pks) {
		return dataSource().finds(entityType(), pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
	 *
	 * @param pks 主键值集合
	 * @return Entity对象 CompletableFuture
	 */
	default CompletableFuture<T[]> findsAsync(Serializable... pks) {
		return dataSource().findsAsync(entityType(), pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {primary} = {id1,id2, &#183;&#183;&#183;} <br>
	 *
	 * @param <D> 主键泛型
	 * @param pks 主键值集合
	 * @return Entity对象 CompletableFuture
	 */
	default <D extends Serializable> CompletableFuture<T[]> findsAsync(Stream<D> pks) {
		return dataSource().findsAsync(entityType(), pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param selects 指定字段
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default T[] finds(SelectColumn selects, Serializable... pks) {
		return dataSource().finds(entityType(), selects, pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param <D>主键泛型
	 * @param selects 指定字段
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default <D extends Serializable> T[] finds(SelectColumn selects, Stream<D> pks) {
		return dataSource().finds(entityType(), selects, pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param selects 指定字段
	 * @param pks 主键值集合
	 * @return Entity对象CompletableFuture
	 */
	default CompletableFuture<T[]> findsAsync(SelectColumn selects, Serializable... pks) {
		return dataSource().findsAsync(entityType(), selects, pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回数组，数组长度与pks一样 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param <D>主键泛型
	 * @param selects 指定字段
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default <D extends Serializable> CompletableFuture<T[]> findsAsync(SelectColumn selects, Stream<D> pks) {
		return dataSource().findsAsync(entityType(), selects, pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回列表 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param <D>主键泛型
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default <D extends Serializable> List<T> findsList(Stream<D> pks) {
		return dataSource().findsList(entityType(), pks);
	}

	/**
	 * 获取指定主键值的多个记录, 返回列表 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {primary} = {id1,id2,
	 * &#183;&#183;&#183;} <br>
	 *
	 * @param <D>主键泛型
	 * @param pks 主键值集合
	 * @return Entity对象
	 */
	default <D extends Serializable> CompletableFuture<List<T>> findsListAsync(Stream<D> pks) {
		return dataSource().findsListAsync(entityType(), pks);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity对象
	 */
	default T find(String column, Serializable colval) {
		return dataSource().find(entityType(), column, colval);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity对象CompletableFuture
	 */
	default CompletableFuture<T> findAsync(String column, Serializable colval) {
		return dataSource().findAsync(entityType(), column, colval);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param func 更新值Lambda
	 * @return Entity对象
	 */
	default T find(LambdaSupplier<Serializable> func) {
		return dataSource().find(entityType(), func);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param func 更新值Lambda
	 * @return Entity对象
	 */
	default CompletableFuture<T> findAsync(LambdaSupplier<Serializable> func) {
		return dataSource().findAsync(entityType(), func);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity对象
	 */
	default T find(FilterBean bean) {
		return dataSource().find(entityType(), bean);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity对象CompletableFuture
	 */
	default CompletableFuture<T> findAsync(FilterBean bean) {
		return dataSource().findAsync(entityType(), bean);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity对象
	 */
	default T find(FilterNode node) {
		return dataSource().find(entityType(), node);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity对象CompletableFuture
	 */
	default CompletableFuture<T> findAsync(FilterNode node) {
		return dataSource().findAsync(entityType(), node);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity对象
	 */
	default T find(SelectColumn selects, FilterBean bean) {
		return dataSource().find(entityType(), selects, bean);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity对象 CompletableFuture
	 */
	default CompletableFuture<T> findAsync(SelectColumn selects, FilterBean bean) {
		return dataSource().findAsync(entityType(), selects, bean);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity对象
	 */
	default T find(SelectColumn selects, FilterNode node) {
		return dataSource().find(entityType(), selects, node);
	}

	/**
	 * 获取符合过滤条件单个记录, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity对象 CompletableFuture
	 */
	default CompletableFuture<T> findAsync(SelectColumn selects, FilterNode node) {
		return dataSource().findAsync(entityType(), selects, node);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param column 字段名
	 * @param pk 主键值
	 * @return Entity对象
	 */
	default Serializable findColumn(String column, Serializable pk) {
		return dataSource().findColumn(entityType(), column, pk);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param column 字段名
	 * @param pk 主键值
	 * @return Entity对象 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, Serializable pk) {
		return dataSource().findColumnAsync(entityType(), column, pk);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param column 字段名
	 * @param bean 过滤条件
	 * @return 字段值
	 */
	default Serializable findColumn(String column, FilterBean bean) {
		return dataSource().findColumn(entityType(), column, bean);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param column 字段名
	 * @param bean 过滤条件
	 * @return 字段值 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, FilterBean bean) {
		return dataSource().findColumnAsync(entityType(), column, bean);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
	 *
	 * @param column 字段名
	 * @param node 过滤条件
	 * @return 字段值
	 */
	default Serializable findColumn(String column, FilterNode node) {
		return dataSource().findColumn(entityType(), column, node);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 返回null表示不存在值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
	 *
	 * @param column 字段名
	 * @param node 过滤条件
	 * @return 字段值 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, FilterNode node) {
		return dataSource().findColumnAsync(entityType(), column, node);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param pk 主键值
	 * @return 字段值
	 */
	default Serializable findColumn(String column, Serializable defValue, Serializable pk) {
		return dataSource().findColumn(entityType(), column, defValue, pk);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param pk 主键值
	 * @return 字段值 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, Serializable defValue, Serializable pk) {
		return dataSource().findColumnAsync(entityType(), column, defValue, pk);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param bean 过滤条件
	 * @return 字段值
	 */
	default Serializable findColumn(String column, Serializable defValue, FilterBean bean) {
		return dataSource().findColumn(entityType(), column, defValue, bean);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param bean 过滤条件
	 * @return 字段值 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, Serializable defValue, FilterBean bean) {
		return dataSource().findColumnAsync(entityType(), column, defValue, bean);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param node 过滤条件
	 * @return 字段值
	 */
	default Serializable findColumn(String column, Serializable defValue, FilterNode node) {
		return dataSource().findColumn(entityType(), column, defValue, node);
	}

	/**
	 * 获取符合过滤条件单个记录的单个字段值, 不存在值则返回默认值 <br>
	 * 等价SQL: SELECT {column} FROM {table} WHERE {filter node} <br>
	 *
	 * @param column 字段名
	 * @param defValue 默认值
	 * @param node 过滤条件
	 * @return 字段值 CompletableFuture
	 */
	default CompletableFuture<Serializable> findColumnAsync(String column, Serializable defValue, FilterNode node) {
		return dataSource().findColumnAsync(entityType(), column, defValue, node);
	}

	/**
	 * 判断是否存在主键值的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键值
	 * @return 是否存在
	 */
	default boolean exists(Serializable pk) {
		return dataSource().exists(entityType(), pk);
	}

	/**
	 * 判断是否存在主键值的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {primary} = {id} <br>
	 *
	 * @param pk 主键值
	 * @return 是否存在CompletableFuture
	 */
	default CompletableFuture<Boolean> existsAsync(Serializable pk) {
		return dataSource().existsAsync(entityType(), pk);
	}

	/**
	 * 判断是否存在符合过滤条件的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return 是否存在
	 */
	default boolean exists(FilterBean bean) {
		return dataSource().exists(entityType(), bean);
	}

	/**
	 * 判断是否存在符合过滤条件的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return 是否存在CompletableFuture
	 */
	default CompletableFuture<Boolean> existsAsync(FilterBean bean) {
		return dataSource().existsAsync(entityType(), bean);
	}

	/**
	 * 判断是否存在符合过滤条件的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return 是否存在
	 */
	default boolean exists(FilterNode node) {
		return dataSource().exists(entityType(), node);
	}

	/**
	 * 判断是否存在符合过滤条件的记录 <br>
	 * 等价SQL: SELECT COUNT(*) FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return 是否存在CompletableFuture
	 */
	default CompletableFuture<Boolean> existsAsync(FilterNode node) {
		return dataSource().existsAsync(entityType(), node);
	}

	// -----------------------list set----------------------------
	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Set<V> queryColumnSet(String selectedColumn, String column, Serializable colval) {
		return dataSource().queryColumnSet(selectedColumn, entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
			String selectedColumn, String column, Serializable colval) {
		return dataSource().queryColumnSetAsync(selectedColumn, entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param bean 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Set<V> queryColumnSet(String selectedColumn, FilterBean bean) {
		return dataSource().queryColumnSet(selectedColumn, entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param bean 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
			String selectedColumn, FilterBean bean) {
		return dataSource().queryColumnSetAsync(selectedColumn, entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param node 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Set<V> queryColumnSet(String selectedColumn, FilterNode node) {
		return dataSource().queryColumnSet(selectedColumn, entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param node 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
			String selectedColumn, FilterNode node) {
		return dataSource().queryColumnSetAsync(selectedColumn, entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT
	 * {flipper.limit} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Set<V> queryColumnSet(String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnSet(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT
	 * {flipper.limit} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
			String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnSetAsync(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT
	 * {flipper.limit} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Set<V> queryColumnSet(String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnSet(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT
	 * {flipper.limit} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Set<V>> queryColumnSetAsync(
			String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnSetAsync(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return 字段值的集合
	 */
	default <V extends Serializable> List<V> queryColumnList(
			String selectedColumn, String column, Serializable colval) {
		return dataSource().queryColumnList(selectedColumn, entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {column} = {key} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
			String selectedColumn, String column, Serializable colval) {
		return dataSource().queryColumnListAsync(selectedColumn, entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param bean 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> List<V> queryColumnList(String selectedColumn, FilterBean bean) {
		return dataSource().queryColumnList(selectedColumn, entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param bean 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
			String selectedColumn, FilterBean bean) {
		return dataSource().queryColumnListAsync(selectedColumn, entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param node 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> List<V> queryColumnList(String selectedColumn, FilterNode node) {
		return dataSource().queryColumnList(selectedColumn, entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param node 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
			String selectedColumn, FilterNode node) {
		return dataSource().queryColumnListAsync(selectedColumn, entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> List<V> queryColumnList(String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnList(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
			String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnListAsync(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> List<V> queryColumnList(String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnList(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段List集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<List<V>> queryColumnListAsync(
			String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnListAsync(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Sheet集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Sheet<V> queryColumnSheet(
			String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnSheet(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Sheet集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
			String selectedColumn, Flipper flipper, FilterBean bean) {
		return dataSource().queryColumnSheetAsync(selectedColumn, entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Sheet集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合
	 */
	default <V extends Serializable> Sheet<V> queryColumnSheet(
			String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnSheet(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的某个字段Sheet集合 <br>
	 * 等价SQL: SELECT {selectedColumn} FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param <V> 字段类型
	 * @param selectedColumn 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return 字段值的集合CompletableFuture
	 */
	default <V extends Serializable> CompletableFuture<Sheet<V>> queryColumnSheetAsync(
			String selectedColumn, Flipper flipper, FilterNode node) {
		return dataSource().queryColumnSheetAsync(selectedColumn, entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
	 *
	 * @param <K> 主键泛型
	 * @param keyStream 主键Stream
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(Stream<K> keyStream) {
		return dataSource().queryMap(entityType(), keyStream);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
	 *
	 * @param <K> 主键泛型
	 * @param keyStream 主键Stream
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(Stream<K> keyStream) {
		return dataSource().queryMapAsync(entityType(), keyStream);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param bean FilterBean
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(FilterBean bean) {
		return dataSource().queryMap(entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param bean FilterBean
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(FilterBean bean) {
		return dataSource().queryMapAsync(entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param node FilterNode
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(FilterNode node) {
		return dataSource().queryMap(entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param node FilterNode
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(FilterNode node) {
		return dataSource().queryMapAsync(entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param keyStream 主键Stream
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(SelectColumn selects, Stream<K> keyStream) {
		return dataSource().queryMap(entityType(), selects, keyStream);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE id IN {ids} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param keyStream 主键Stream
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(
			SelectColumn selects, Stream<K> keyStream) {
		return dataSource().queryMapAsync(entityType(), selects, keyStream);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param bean FilterBean
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(SelectColumn selects, FilterBean bean) {
		return dataSource().queryMap(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param bean FilterBean
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(SelectColumn selects, FilterBean bean) {
		return dataSource().queryMapAsync(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param node FilterNode
	 * @return Entity的集合
	 */
	default <K extends Serializable> Map<K, T> queryMap(SelectColumn selects, FilterNode node) {
		return dataSource().queryMap(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的List转Map集合， key=主键值, value=Entity对象 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param <K> 主键泛型
	 * @param selects 指定字段
	 * @param node FilterNode
	 * @return Entity的集合CompletableFuture
	 */
	default <K extends Serializable> CompletableFuture<Map<K, T>> queryMapAsync(SelectColumn selects, FilterNode node) {
		return dataSource().queryMapAsync(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合
	 */
	default Set<T> querySet(String column, Serializable colval) {
		return dataSource().querySet(entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(String column, Serializable colval) {
		return dataSource().querySetAsync(entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(FilterBean bean) {
		return dataSource().querySet(entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(FilterBean bean) {
		return dataSource().querySetAsync(entityType(), bean);
	}

	/**
	 * 查询记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} <br>
	 *
	 * @return Entity的集合
	 */
	default Set<T> querySet() {
		return dataSource().querySet(entityType());
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(FilterNode node) {
		return dataSource().querySet(entityType(), node);
	}

	/**
	 * 查询记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} <br>
	 *
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync() {
		return dataSource().querySetAsync(entityType());
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(FilterNode node) {
		return dataSource().querySetAsync(entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(SelectColumn selects, FilterBean bean) {
		return dataSource().querySet(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(SelectColumn selects, FilterBean bean) {
		return dataSource().querySetAsync(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(SelectColumn selects, FilterNode node) {
		return dataSource().querySet(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(SelectColumn selects, FilterNode node) {
		return dataSource().querySetAsync(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合
	 */
	default Set<T> querySet(Flipper flipper, String column, Serializable colval) {
		return dataSource().querySet(entityType(), flipper, column, colval);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(Flipper flipper, String column, Serializable colval) {
		return dataSource().querySetAsync(entityType(), flipper, column, colval);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(Flipper flipper, FilterBean bean) {
		return dataSource().querySet(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(Flipper flipper, FilterBean bean) {
		return dataSource().querySetAsync(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(Flipper flipper, FilterNode node) {
		return dataSource().querySet(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default CompletableFuture<Set<T>> querySetAsync(Flipper flipper, FilterNode node) {
		return dataSource().querySetAsync(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY
	 * {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().querySet(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY
	 * {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().querySetAsync(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY
	 * {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Set<T> querySet(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().querySet(entityType(), selects, flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Set集合 <br>
	 * 等价SQL: SELECT DISTINCT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY
	 * {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Set<T>> querySetAsync(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().querySetAsync(entityType(), selects, flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合
	 */
	default List<T> queryList(String column, Serializable colval) {
		return dataSource().queryList(entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(String column, Serializable colval) {
		return dataSource().queryListAsync(entityType(), column, colval);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(FilterBean bean) {
		return dataSource().queryList(entityType(), bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} <br>
	 *
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(FilterBean bean) {
		return dataSource().queryListAsync(entityType(), bean);
	}

	/**
	 * 查询记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} <br>
	 *
	 * @return Entity的集合
	 */
	default List<T> queryList() {
		return dataSource().queryList(entityType());
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(FilterNode node) {
		return dataSource().queryList(entityType(), node);
	}

	/**
	 * 查询记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} <br>
	 *
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync() {
		return dataSource().queryListAsync(entityType());
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} <br>
	 *
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(FilterNode node) {
		return dataSource().queryListAsync(entityType(), node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(SelectColumn selects, FilterBean bean) {
		return dataSource().queryList(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} <br>
	 *
	 * @param selects 指定字段
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(SelectColumn selects, FilterBean bean) {
		return dataSource().queryListAsync(entityType(), selects, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(SelectColumn selects, FilterNode node) {
		return dataSource().queryList(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} <br>
	 *
	 * @param selects 指定字段
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(SelectColumn selects, FilterNode node) {
		return dataSource().queryListAsync(entityType(), selects, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合
	 */
	default List<T> queryList(Flipper flipper, String column, Serializable colval) {
		return dataSource().queryList(entityType(), flipper, column, colval);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {column} = {key} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param column 过滤字段名
	 * @param colval 过滤字段值
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(Flipper flipper, String column, Serializable colval) {
		return dataSource().queryListAsync(entityType(), flipper, column, colval);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @return Entity的集合
	 */
	default List<T> queryList(Flipper flipper) {
		return dataSource().queryList(entityType(), flipper);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(Flipper flipper) {
		return dataSource().queryListAsync(entityType(), flipper);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(Flipper flipper, FilterBean bean) {
		return dataSource().queryList(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(Flipper flipper, FilterBean bean) {
		return dataSource().queryListAsync(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(Flipper flipper, FilterNode node) {
		return dataSource().queryList(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default CompletableFuture<List<T>> queryListAsync(Flipper flipper, FilterNode node) {
		return dataSource().queryListAsync(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @return Entity的集合
	 */
	default List<T> queryList(SelectColumn selects, Flipper flipper) {
		return dataSource().queryList(entityType(), selects, flipper);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} ORDER BY {flipper.sort} LIMIT {flipper.limit}
	 * <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(SelectColumn selects, Flipper flipper) {
		return dataSource().queryListAsync(entityType(), selects, flipper);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().queryList(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().queryListAsync(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default List<T> queryList(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().queryList(entityType(), selects, flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的List集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<List<T>> queryListAsync(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().queryListAsync(entityType(), selects, flipper, node);
	}

	// -----------------------sheet----------------------------
	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Sheet<T> querySheet(Flipper flipper, FilterBean bean) {
		return dataSource().querySheet(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param <F> 过滤类型
	 * @param pageBean 过滤翻页条件
	 * @return Entity的集合
	 */
	default <F extends FilterBean> Sheet<T> querySheet(PageBean<F> pageBean) {
		return dataSource().querySheet(entityType(), pageBean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Sheet<T>> querySheetAsync(Flipper flipper, FilterBean bean) {
		return dataSource().querySheetAsync(entityType(), flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter bean} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param <F> 过滤类型
	 * @param pageBean 过滤翻页条件
	 * @return Entity的集合
	 */
	default <F extends FilterBean> CompletableFuture<Sheet<T>> querySheetAsync(PageBean<F> pageBean) {
		return dataSource().querySheetAsync(entityType(), pageBean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Sheet<T> querySheet(Flipper flipper, FilterNode node) {
		return dataSource().querySheet(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT * FROM {table} WHERE {filter node} ORDER BY {flipper.sort} LIMIT {flipper.limit} <br>
	 *
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Sheet<T>> querySheetAsync(Flipper flipper, FilterNode node) {
		return dataSource().querySheetAsync(entityType(), flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合
	 */
	default Sheet<T> querySheet(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().querySheet(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param <F> 过滤类型
	 * @param selects 指定字段
	 * @param pageBean 过滤翻页条件
	 * @return Entity的集合
	 */
	default <F extends FilterBean> Sheet<T> querySheet(SelectColumn selects, PageBean<F> pageBean) {
		return dataSource().querySheet(entityType(), selects, pageBean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param bean 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Sheet<T>> querySheetAsync(SelectColumn selects, Flipper flipper, FilterBean bean) {
		return dataSource().querySheetAsync(entityType(), selects, flipper, bean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter bean} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param <F> 过滤类型
	 * @param selects 指定字段
	 * @param pageBean 过滤翻页条件
	 * @return Entity的集合
	 */
	default <F extends FilterBean> CompletableFuture<Sheet<T>> querySheetAsync(
			SelectColumn selects, PageBean<F> pageBean) {
		return dataSource().querySheetAsync(entityType(), selects, pageBean);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合
	 */
	default Sheet<T> querySheet(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().querySheet(entityType(), selects, flipper, node);
	}

	/**
	 * 查询符合过滤条件记录的Sheet集合 <br>
	 * 等价SQL: SELECT {column1},{column2}, &#183;&#183;&#183; FROM {table} WHERE {filter node} ORDER BY {flipper.sort}
	 * LIMIT {flipper.limit} <br>
	 *
	 * @param selects 指定字段
	 * @param flipper 翻页对象
	 * @param node 过滤条件
	 * @return Entity的集合CompletableFuture
	 */
	default CompletableFuture<Sheet<T>> querySheetAsync(SelectColumn selects, Flipper flipper, FilterNode node) {
		return dataSource().querySheetAsync(entityType(), selects, flipper, node);
	}
}
