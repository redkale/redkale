/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertCoder;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonCoders.BigIntegerHexJsonSimpledCoder;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;

/** @author zhangjx */
public class ConvertCoderTest {

    public static void main(String[] args) throws Throwable {
        ConvertCoderTest test = new ConvertCoderTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        JsonConvert convert = JsonConvert.root();
        BigMessage msg = new BigMessage();
        msg.big = new BigInteger("255");
        msg.big2 = new BigInteger("255");
        msg.big3 = new BigInteger("255");
        msg.map = new HashMap<>();
        msg.map.put("haha", new BigInteger("254"));

        BigMessage2 msg2 = new BigMessage2();
        msg2.big = new BigInteger("255");
        msg2.big2 = new BigInteger("255");
        msg2.big3 = new BigInteger("255");
        msg2.map = new HashMap<>();
        msg2.map.put("haha", new BigInteger("254"));

        System.out.println(convert.convertTo(msg));
        String json = "{\"big\":\"0xff\",\"big2\":\"0xff\",\"big3\":\"255\",\"map\":{\"haha\":\"0xfe\"},\"num1\":0}";
        Assertions.assertEquals(convert.convertTo(msg), json);
        BigMessage msg12 = convert.convertFrom(BigMessage.class, json);
        Assertions.assertEquals(convert.convertTo(msg12), json);

        byte[] bs1 = ProtobufConvert.root().convertTo(msg);
        byte[] bs2 = ProtobufConvert.root().convertTo(msg2);
        Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(bs2));
    }

    public static class BigMessage {

        @ConvertColumn(index = 1)
        @ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
        public BigInteger big;

        @ConvertColumn(index = 2)
        @ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
        public BigInteger big2;

        @ConvertColumn(index = 3)
        public BigInteger big3;

        @ConvertColumn(index = 4)
        @ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
        public Map<String, BigInteger> map;

        @ConvertColumn(index = 5)
        public int num1;
    }

    public static class BigMessage2 {

        @ConvertColumn(index = 1)
        public BigInteger big;

        @ConvertColumn(index = 2)
        public BigInteger big2;

        @ConvertColumn(index = 3)
        public BigInteger big3;

        @ConvertColumn(index = 4)
        public Map<String, BigInteger> map;

        @ConvertColumn(index = 5)
        public int num1;
    }
}
