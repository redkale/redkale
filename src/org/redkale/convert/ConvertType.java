/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 *
 * <p> 详情见: https://redkale.org
 * @author zhangjx
 */
public enum ConvertType {

    JSON(1),
    BSON(2),
    ALL(127);

    private final int value;

    private ConvertType(int v) {
        this.value = v;
    }

    public boolean contains(ConvertType type) {
        if (type == null) return false;
        return this.value >= type.value && (this.value & type.value) > 0;
    }
}
