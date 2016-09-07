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
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 * @param <T>
 */
public interface DistributeTableStrategy<T> {

    default String getTable(String table, Serializable primary) {
        return null;
    }

    default String getTable(String table, FilterNode node) {
        return null;
    }

    public String getTable(String table, T bean);
}
