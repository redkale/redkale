/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import static org.redkale.source.ColumnExpress.*;

/**
 * 作为ColumnValue的value字段值，用于复杂的字段表达式
 *
 * @author zhangjx
 */
public class ColumnNode implements Serializable {

    private Serializable left;//类型只能是String、Number、ColumnNode

    private ColumnExpress express; //不能是MOV

    private Serializable right;//类型只能是String、Number、ColumnNode

    public ColumnNode() {
    }

    public ColumnNode(Serializable left, ColumnExpress express, Serializable right) {
        if (express == null || express == ColumnExpress.MOV) throw new IllegalArgumentException("express cannot be null or MOV");
        this.left = left;
        this.express = express;
        this.right = right;
    }

    public static ColumnNode create(Serializable left, ColumnExpress express, Serializable right) {
        return new ColumnNode(left, express, right);
    }

    public static ColumnNode inc(Serializable left, Serializable right) {
        return new ColumnNode(left, INC, right);
    }

    public static ColumnNode mul(Serializable left, Serializable right) {
        return new ColumnNode(left, MUL, right);
    }

    public static ColumnNode div(Serializable left, Serializable right) {
        return new ColumnNode(left, DIV, right);
    }

    public static ColumnNode mod(Serializable left, Serializable right) {
        return new ColumnNode(left, MOD, right);
    }

    public static ColumnNode and(Serializable left, Serializable right) {
        return new ColumnNode(left, AND, right);
    }

    public static ColumnNode orr(Serializable left, Serializable right) {
        return new ColumnNode(left, ORR, right);
    }

    public ColumnNode inc(Serializable right) {
        return any(INC, right);
    }

    public ColumnNode mul(Serializable right) {
        return any(MUL, right);
    }

    public ColumnNode div(Serializable right) {
        return any(DIV, right);
    }

    public ColumnNode mod(Serializable right) {
        return any(MOD, right);
    }

    public ColumnNode and(Serializable right) {
        return any(AND, right);
    }

    public ColumnNode orr(Serializable right) {
        return any(ORR, right);
    }

    protected ColumnNode any(ColumnExpress express, Serializable right) {
        ColumnNode one = new ColumnNode(this.left, this.express, this.right);
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
