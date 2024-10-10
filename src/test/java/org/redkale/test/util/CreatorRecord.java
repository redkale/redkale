/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class CreatorRecord {

    private int id = -1;

    private String name;

    private long lval;

    private boolean tval;

    private byte bval;

    private short sval;

    private char cval;

    private float fval;

    private double dval;

    public CreatorRecord() {}

    @ConstructorParameters({"id", "name", "lval", "tval", "bval", "sval", "cval", "fval", "dval"})
    public CreatorRecord(
            int id, String name, long lval, boolean tval, byte bval, short sval, char cval, float fval, double dval) {
        this.id = id;
        this.name = name;
        this.lval = lval;
        this.tval = tval;
        this.bval = bval;
        this.sval = sval;
        this.cval = cval;
        this.fval = fval;
        this.dval = dval;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLval(long lval) {
        this.lval = lval;
    }

    public void setTval(boolean tval) {
        this.tval = tval;
    }

    public void setBval(byte bval) {
        this.bval = bval;
    }

    public void setSval(short sval) {
        this.sval = sval;
    }

    public void setCval(char cval) {
        this.cval = cval;
    }

    public void setFval(float fval) {
        this.fval = fval;
    }

    public void setDval(double dval) {
        this.dval = dval;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getLval() {
        return lval;
    }

    public boolean isTval() {
        return tval;
    }

    public byte getBval() {
        return bval;
    }

    public short getSval() {
        return sval;
    }

    public char getCval() {
        return cval;
    }

    public float getFval() {
        return fval;
    }

    public double getDval() {
        return dval;
    }
}
