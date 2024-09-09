/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 * 分表分库策略，结合&#64;DistributeTable使用 <br>
 * 不能与&#64;Cacheable同时使用 <br>
 * 使用分表分库功能重点是主键的生成策略，不同场景生成策略不一样 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.source.DistributeTable
 * @author zhangjx
 * @param <T> Entity类型
 */
public interface DistributeTableStrategy<T> {

    /**
     * 获取DataSource资源名，为null表示没有分布物理库 <br>
     * 查询单个对象（DataSource.find）时调用本方法 <br>
     *
     * @param primary 记录主键
     * @return DataSource资源名
     * @since 2.8.0
     */
    default String getSource(Serializable primary) {
        return null;
    }

    /**
     * 获取DataSource资源名，为null表示没有分布物理库 <br>
     * 新增对象或更新单个对象（DataSource.insert、DataSource.update）时调用本方法 <br>
     *
     * @param entity 实体对象
     * @return DataSource资源名
     * @since 2.8.0
     */
    default String getSource(T entity) {
        return null;
    }

    /**
     * 获取DataSource资源名，为null表示没有分布物理库 <br>
     * 查询、修改、删除对象（DataSource.find、DataSource.query、DataSource.delete、DataSource.update）时调用本方法 <br>
     *
     * @param node 过滤条件
     * @return DataSource资源名
     * @since 2.8.0
     */
    default String getSource(FilterNode node) {
        return null;
    }

    /**
     * 获取对象的表名 <br>
     * 查询单个对象（DataSource.find）时调用本方法获取表名 <br>
     *
     * @param table 模板表的表名
     * @param primary 记录主键
     * @return 带库名的全表名
     */
    public String getTable(String table, Serializable primary);

    /**
     * 获取对象的表名 <br>
     * 新增对象或更新单个对象（DataSource.insert、DataSource.update）时调用本方法获取表名 <br>
     *
     * @param table 模板表的表名
     * @param entity 实体对象
     * @return 带库名的全表名
     */
    public String getTable(String table, T entity);

    /**
     * 获取对象的表名 <br>
     * 查询、修改、删除对象（DataSource.find、DataSource.query、DataSource.delete、DataSource.update）时调用本方法获取表名 <br>
     *
     * @param table 模板表的表名
     * @param node 过滤条件
     * @return 带库名的全表名
     * @since 2.8.0
     */
    public String[] getTables(String table, FilterNode node);

    /**
     * 获取对象的表名 <br>
     * 查询、修改、删除对象（DataSource.find、DataSource.query、DataSource.delete、DataSource.update）时调用本方法获取表名 <br>
     * 注意： 需保证FilterNode过滤的结果集合必须在一个数据库表中 <br>
     *
     * @deprecated 2.8.0 replaced by getTables(String table, FilterNode node)
     * @see #getTables(java.lang.String, org.redkale.source.FilterNode)
     * @param table 模板表的表名
     * @param node 过滤条件
     * @return 带库名的全表名
     */
    @Deprecated(since = "2.8.0")
    default String getTable(String table, FilterNode node) {
        return getTables(table, node)[0];
    }
}
