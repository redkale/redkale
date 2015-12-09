/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 *
 * @author zhangjx
 */
public enum ReckonType {

    AVG, COUNT, DISTINCTCOUNT, MAX, MIN, SUM;

    public String getReckonColumn(String col) {
        if (this == COUNT) return this.name() + "(*)";
        if (this == DISTINCTCOUNT) return "COUNT(DISTINCT " + col + ")";
        return this.name() + "(" + col + ")";
    }
}
