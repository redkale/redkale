/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep.stun;

import com.wentch.redkale.net.icep.*;
import com.wentch.redkale.util.*;
import java.nio.*;
import java.security.*;

/**
 *
 * @author zhangjx
 */
public class StunHeader implements IcepCoder<StunHeader> {

    public static final int MAGIC_COOKIE = 0x2112A442;

    //---------------------------- type -------------------------
    private static final int TYPE_BIT_MASK = 0x0110;

    public static final short TYPE_REQUEST = 0x0000;

    public static final short TYPE_INDICATION = 0x0010;

    public static final short TYPE_SUCCESS = 0x0100;

    public static final short TYPE_ERROR = 0x0110;

    //---------------------------- action -------------------------
    public static final int ACTION_BIT_MASK = 0xCEEF;

    public static final short ACTION_BINDING = 0x0001;

    public static final short ACTION_ALLOCATE = 0x0003;

    public static final short ACTION_REFRESH = 0x0004;

    public static final short ACTION_SEND = 0x0006;

    public static final short ACTION_DATA = 0x0007;

    public static final short ACTION_CREATE_PERMISSION = 0x0008;

    public static final short ACTION_CHANNELBIND = 0x0009;

    //-----------------------------------------------------------
    private short requestid;   //无符号 2bytes  首位2bits必定是00， 所以该值不会为负数

    private char bodysize;  //无符号 2bytes

    private byte[] transactionid;  //RFC5389 =MAGIC_COOKIE +  byte[12]  = byte[16];

    public static byte[] generateTransactionid() {
        byte[] transactions = new byte[16];
        // Get a secure PRNG
        SecureRandom random = new SecureRandom();
        random.nextBytes(transactions);
        transactions[0] = 0x21;
        transactions[1] = 0x12;
        transactions[2] = (byte) 0xA4;
        transactions[3] = 0x42;
        return transactions;
    }

    public StunHeader(short requestid, byte[] transactionid0) {
        this.requestid = requestid;
        this.transactionid = transactionid0 == null ? generateTransactionid() : transactionid0;
    }

    @Override
    public StunHeader decode(final ByteBuffer buffer) {
        this.requestid = buffer.getShort();
        this.bodysize = buffer.getChar();
        this.transactionid = new byte[16];
        buffer.get(transactionid);
        return this;
    }

    @Override
    public ByteBuffer encode(final ByteBuffer buffer) {
        buffer.putShort(this.requestid);
        buffer.putChar(this.bodysize);
        buffer.put(transactionid);
        return buffer;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[requestid = " + format(requestid) + ", bodysize = " + (int) (bodysize) + ", transactionid = " + Utility.binToHexString(transactionid) + "]";
    }

    private static String format(short value) {
        String str = Integer.toHexString(value);
        if (str.length() == 1) return "0x000" + str;
        if (str.length() == 2) return "0x00" + str;
        if (str.length() == 3) return "0x0" + str;
        return "0x" + str;
    }

    public void setRequestid(int requestid) {
        this.requestid = (short) requestid;
    }

    public void setRequestid(short requestid) {
        this.requestid = requestid;
    }

    public void setBodysize(char bodysize) {
        this.bodysize = bodysize;
    }

    public void setBodysize(int bodysize) {
        this.bodysize = (char) bodysize;
    }

    public int getBodysize() {
        return bodysize;
    }

    public byte[] getTransactionid() {
        return transactionid;
    }

}
