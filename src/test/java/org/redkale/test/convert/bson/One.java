/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.bson;

import java.util.Arrays;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;

/** @author zhangjx */
public class One {

    protected String key;

    protected int code;

    protected byte[] bytes = new byte[] {3, 4, 5};

    protected int[] ints = new int[] {3000, 4000, 5000};

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

    public static void main(String[] args) throws Throwable {
        int count = 100_0000;
        One one = new One(234);
        one.bytes = new byte[] {1, 2, 3};
        one.key = "哈哈";

        System.out.println(Arrays.toString(Utility.encodeUTF8(JsonConvert.root().convertTo(one))));
        System.out.println(Arrays.toString(JsonConvert.root().convertToBytes(one)));
        long s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JsonConvert.root().convertTo(one).getBytes();
        }
        long e = System.currentTimeMillis() - s;

        long s2 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JsonConvert.root().convertToBytes(one);
        }
        long e2 = System.currentTimeMillis() - s2;
        System.out.println(e);
        System.out.println(e2);
    }
}
