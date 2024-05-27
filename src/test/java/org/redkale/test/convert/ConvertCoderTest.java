/*
 */
package org.redkale.test.convert;

import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertCoder;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.ext.BigIntegerSimpledCoder.BigIntegerHexJsonSimpledCoder;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class ConvertCoderTest {

	private boolean main;

	public static void main(String[] args) throws Throwable {
		ConvertCoderTest test = new ConvertCoderTest();
		test.main = true;
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
		if (!main) {
			Assertions.assertEquals(convert.convertTo(msg), json);
		}
		BigMessage msg12 = convert.convertFrom(BigMessage.class, json);
		if (!main) {
			Assertions.assertEquals(convert.convertTo(msg12), json);
		}

		byte[] bs1 = BsonConvert.root().convertTo(msg);
		byte[] bs2 = BsonConvert.root().convertTo(msg2);
		if (!main) {
			Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(bs2));
		}
	}

	public static class BigMessage {

		@ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
		public BigInteger big;

		@ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
		public BigInteger big2;

		public BigInteger big3;

		public int num1;

		@ConvertCoder(encoder = BigIntegerHexJsonSimpledCoder.class, decoder = BigIntegerHexJsonSimpledCoder.class)
		public Map<String, BigInteger> map;
	}

	public static class BigMessage2 {

		public BigInteger big;

		public BigInteger big2;

		public BigInteger big3;

		public int num1;

		public Map<String, BigInteger> map;
	}
}
