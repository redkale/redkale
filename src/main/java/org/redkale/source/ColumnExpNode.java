/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import org.redkale.convert.ConvertColumn;
import static org.redkale.source.ColumnExpress.*;

/**
 * 作为ColumnValue的value字段值，用于复杂的字段表达式 。 <br>
 * String 视为 字段名 <br>
 * Number 视为 数值 <br>
 * 例如： UPDATE Reord SET updateTime = createTime + 10 WHERE id = 1 <br>
 * source.updateColumn(Record.class, 1, ColumnValue.set("updateTime", ColumnExpNode.inc("createTime", 10))); <br>
 * 例如： UPDATE Reord SET updateTime = createTime * 10 / createCount WHERE id = 1 <br>
 * source.updateColumn(Record.class, 1, ColumnValue.set("updateTime", ColumnExpNode.div(ColumnExpNode.mul("createTime",
 * 10), "createCount"))); <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.0.0
 */
public class ColumnExpNode implements ColumnNode {

    // 类型只能是ColumnNameNode、ColumnNumberNode、ColumnExpNode
    @ConvertColumn(index = 1)
    protected ColumnNode left;

    // SET时，left必须是ColumnNameNode, right必须是null
    @ConvertColumn(index = 2)
    protected ColumnExpress express;

    // 类型只能是ColumnNameNode、ColumnNumberNode、ColumnExpNode
    @ConvertColumn(index = 3)
    protected ColumnNode right;

    public ColumnExpNode() {}

    public ColumnExpNode(Serializable left, ColumnExpress express, Serializable right) {
        if (express == null) {
            throw new IllegalArgumentException("express cannot be null");
        }
        ColumnNode leftNode = createColumnNode(left);
        ColumnNode rightNode = createColumnNode(right);
        if (express == SET) {
            if (!(leftNode instanceof ColumnNameNode) || right != null) {
                throw new IllegalArgumentException(
                        "left value must be ColumnNameNode, right value must be null on ColumnExpress.SET");
            }
        }
        this.left = leftNode;
        this.express = express;
        this.right = rightNode;
    }

    private ColumnNode createColumnNode(Serializable value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return new ColumnNameNode(value.toString());
        }
        if (value instanceof Number) {
            return new ColumnNumberNode((Number) value);
        }
        if (!(value instanceof ColumnNameNode)
                && !(value instanceof ColumnNumberNode)
                && !(value instanceof ColumnExpNode)) {
            throw new IllegalArgumentException("value must be ColumnNameNode、ColumnNumberNode、ColumnExpNode");
        }
        return (ColumnNode) value;
    }

    /**
     * @see org.redkale.source.ColumnNodes#set(java.lang.String)
     * @param left Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode mov(String left) {
        return new ColumnExpNode(left, SET, null);
    }

    /**
     * @see org.redkale.source.ColumnNodes#inc(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode inc(Serializable left, Serializable right) {
        return new ColumnExpNode(left, INC, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#dec(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode dec(Serializable left, Serializable right) {
        return new ColumnExpNode(left, DEC, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#mul(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode mul(Serializable left, Serializable right) {
        return new ColumnExpNode(left, MUL, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#div(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode div(Serializable left, Serializable right) {
        return new ColumnExpNode(left, DIV, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#mod(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode mod(Serializable left, Serializable right) {
        return new ColumnExpNode(left, MOD, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#and(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode and(Serializable left, Serializable right) {
        return new ColumnExpNode(left, AND, right);
    }

    /**
     * @see org.redkale.source.ColumnNodes#orr(org.redkale.source.ColumnNode, org.redkale.source.ColumnNode)
     * @param left Serializable
     * @param right Serializable
     * @return ColumnExpNode
     * @deprecated 2.8.0
     */
    @Deprecated(since = "2.8.0")
    public static ColumnExpNode orr(Serializable left, Serializable right) {
        return new ColumnExpNode(left, ORR, right);
    }

    public ColumnExpNode inc(Serializable right) {
        return any(INC, right);
    }

    public ColumnExpNode dec(Serializable right) {
        return any(DEC, right);
    }

    public ColumnExpNode mul(Serializable right) {
        return any(MUL, right);
    }

    public ColumnExpNode div(Serializable right) {
        return any(DIV, right);
    }

    public ColumnExpNode mod(Serializable right) {
        return any(MOD, right);
    }

    public ColumnExpNode and(Serializable right) {
        return any(AND, right);
    }

    public ColumnExpNode orr(Serializable right) {
        return any(ORR, right);
    }

    protected ColumnExpNode any(ColumnExpress express, Serializable right) {
        ColumnExpNode one = new ColumnExpNode(this.left, this.express, this.right);
        this.left = one;
        this.express = express;
        this.right = createColumnNode(right);
        return this;
    }

    public ColumnNode getLeft() {
        return left;
    }

    public void setLeft(ColumnNode left) {
        this.left = left;
    }

    public ColumnExpress getExpress() {
        return express;
    }

    public void setExpress(ColumnExpress express) {
        this.express = express;
    }

    public ColumnNode getRight() {
        return right;
    }

    public void setRight(ColumnNode right) {
        this.right = right;
    }

    @Override
    public String toString() {
        return "{\"column\":" + left + ", \"express\":" + express + ", \"value\":" + right + "}";
    }
}
