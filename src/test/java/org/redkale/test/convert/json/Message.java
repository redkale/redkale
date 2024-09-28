/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.util.ByteArray;

/** @author zhangjx */
public final class Message {

    protected boolean flag;

    private int[] ints;

    private List<Long> longs;

    @ConvertSmallString
    private String message;

    public Message() {}

    public List<Long> getLongs() {
        return longs;
    }

    public void setLongs(List<Long> longs) {
        this.longs = longs;
    }

    public int[] getInts() {
        return ints;
    }

    public void setInts(int[] ints) {
        this.ints = ints;
    }

    public Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public static void main(String[] args) throws Throwable {
        Message msg = new Message();
        msg.message = "dddd";
        List<Long> longs = new ArrayList<>();
        longs.add(2222L);
        longs.add(3333L);
        msg.longs = longs;
        msg.ints = new int[] {-2, 3, 4};
        JsonConvert convert = JsonFactory.root().getConvert();
        Encodeable encoder = JsonFactory.root().loadEncoder(Message.class);
        System.out.println(encoder);
        ByteArray array = new ByteArray();
        array.put("数据: ".getBytes(StandardCharsets.UTF_8));
        convert.convertToBytes(array, msg);
        System.out.println(array);
        Message[] mss = new Message[] {msg};
        System.out.println(convert.convertTo(mss));
    }
}
