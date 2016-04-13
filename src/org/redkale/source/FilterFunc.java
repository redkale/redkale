/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 *
 * <p> 详情见: http://redkale.org
 * @author zhangjx
 */
public enum FilterFunc {
    AVG,
    COUNT,
    DISTINCTCOUNT,
    MAX,
    MIN,
    SUM;

    public String getColumn(String col) {
        if (this == DISTINCTCOUNT) return "COUNT(DISTINCT " + col + ")";
        return this.name() + "(" + col + ")";
    }
}
