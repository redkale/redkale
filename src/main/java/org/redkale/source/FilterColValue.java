/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.Objects;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ConvertColumn;

/**
 * 主要用于自身字段间的表达式, 如： a.recordid = a.parentid , a.parentid就需要FilterColValue来表示 new FilterColValue("parentid")
 <br>
 * 注意：该类型不支持表达式：FV_XXX、BETWEEN、NOTBETWEEN、IN、NOTIN
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class FilterColValue implements java.io.Serializable {

    @ConvertColumn(index = 1)
    private final String column;

    @ConstructorParameters({"column"})
    public FilterColValue(String column) {
        this.column = Objects.requireNonNull(column);
    }

    public String getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return "a." + getColumn();
    }

}
