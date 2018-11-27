/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class One {

    protected String key;

    protected int code;

    protected byte[] bytes = new byte[]{3, 4, 5};

    protected int[] ints = new int[]{3000, 4000, 5000};

    public One(int code) {
        this.code = code;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public int[] getInts() {
        return ints;
    }

    public void setInts(int[] ints) {
        this.ints = ints;
    }

    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
