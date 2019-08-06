/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import static org.redkale.source.ColumnExpress.*;

/**
 * 作为ColumnValue的value字段值，用于复杂的字段表达式 。
 * String 视为 字段名
 * Number 视为 数值
 *
 * @author zhangjx
 */
public class ColumnNodeValue implements Serializable {

    private Serializable left;//类型只能是String、Number、ColumnNode

    private ColumnExpress express; //不能是MOV

    private Serializable right;//类型只能是String、Number、ColumnNode

    public ColumnNodeValue() {
    }

    public ColumnNodeValue(Serializable left, ColumnExpress express, Serializable right) {
        if (express == null || express == ColumnExpress.MOV) throw new IllegalArgumentException("express cannot be null or MOV");
        this.left = left;
        this.express = express;
        this.right = right;
    }

    public static ColumnNodeValue create(Serializable left, ColumnExpress express, Serializable right) {
        return new ColumnNodeValue(left, express, right);
    }

    public static ColumnNodeValue inc(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, INC, right);
    }

    public static ColumnNodeValue mul(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, MUL, right);
    }

    public static ColumnNodeValue div(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, DIV, right);
    }

    public static ColumnNodeValue mod(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, MOD, right);
    }

    public static ColumnNodeValue and(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, AND, right);
    }

    public static ColumnNodeValue orr(Serializable left, Serializable right) {
        return new ColumnNodeValue(left, ORR, right);
    }

    public ColumnNodeValue inc(Serializable right) {
        return any(INC, right);
    }

    public ColumnNodeValue mul(Serializable right) {
        return any(MUL, right);
    }

    public ColumnNodeValue div(Serializable right) {
        return any(DIV, right);
    }

    public ColumnNodeValue mod(Serializable right) {
        return any(MOD, right);
    }

    public ColumnNodeValue and(Serializable right) {
        return any(AND, right);
    }

    public ColumnNodeValue orr(Serializable right) {
        return any(ORR, right);
    }

    protected ColumnNodeValue any(ColumnExpress express, Serializable right) {
        ColumnNodeValue one = new ColumnNodeValue(this.left, this.express, this.right);
        this.left = one;
        this.express = express;
        this.right = right;
        return this;
    }

    public Serializable getLeft() {
        return left;
    }

    public void setLeft(Serializable left) {
        this.left = left;
    }

    public ColumnExpress getExpress() {
        return express;
    }

    public void setExpress(ColumnExpress express) {
        this.express = express;
    }

    public Serializable getRight() {
        return right;
    }

    public void setRight(Serializable right) {
        this.right = right;
    }

    @Override
    public String toString() {
        return "{\"column\":" + ((left instanceof CharSequence) ? ("\"" + left + "\"") : left) + ", \"express\":" + express + ", \"value\":" + ((right instanceof CharSequence) ? ("\"" + right + "\"") : right) + "}";
    }
}
