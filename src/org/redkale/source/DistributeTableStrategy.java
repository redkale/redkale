/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Entity类型
 */
public interface DistributeTableStrategy<T> {

    /**
     * 获取对象的表名
     * 查询单个对象时调用本方法获取表名
     *
     * @param table   模板表的表名
     * @param primary 记录主键
     *
     * @return 带库名的全表名
     */
    default String getTable(String table, Serializable primary) {
        return null;
    }

    /**
     * 获取对象的表名
     * 查询、修改、删除对象时调用本方法获取表名
     * 注意： 需保证FilterNode过滤的结果集合必须在一个数据库表中
     *
     * @param table 模板表的表名
     * @param node  过滤条件
     *
     * @return 带库名的全表名
     */
    default String getTable(String table, FilterNode node) {
        return null;
    }

    /**
     * 获取对象的表名
     * 新增对象或更新单个对象时调用本方法获取表名
     *
     * @param table 模板表的表名
     * @param bean  实体对象
     *
     * @return 带库名的全表名
     */
    public String getTable(String table, T bean);
}
