/*
 */
package org.redkale.test.convert;

import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.Convert;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class Json5Test {

	private boolean main;

	public static void main(String[] args) throws Throwable {
		Json5Test test = new Json5Test();
		test.main = true;
		test.run1();
		test.run2();
	}

	@Test
	public void run1() throws Exception {
		JsonFactory factory = JsonFactory.root().withFeatures(Convert.FEATURE_TINY | Convert.FEATURE_NULLABLE);
		final JsonConvert convert = factory.getConvert();
		Json5Bean bean = new Json5Bean();
		bean.id = 60;
		System.out.println(convert.convertTo(bean));
	}

	@Test
	public void run2() throws Exception {
		JsonConvert convert = JsonConvert.root();
		Json5Bean bean = new Json5Bean();
		bean.id = 500;
		bean.decmails = 3.2f;
		bean.value = 44444;
		bean.name = "haha";
		String json = "{/*多行\r\n注释**/\"decmails\":3.2,//单行注释\r\n\"id\":0x1F4,\"name\":\"haha\",\"value\":44444,}";
		Json5Bean bean2 = convert.convertFrom(Json5Bean.class, json);
		if (!main) {
			Assertions.assertTrue(bean.equals(bean2));
		}
		System.out.println(convert.convertTo(bean2));

		String arrayJson = "[" + json + "," + json + "," + "]";
		Json5Bean[] beans = convert.convertFrom(Json5Bean[].class, arrayJson);
		System.out.println(convert.convertTo(beans));

		String intjson = "[1,2,3,4,]";
		int[] ints1 = convert.convertFrom(int[].class, intjson);
		System.out.println(Arrays.toString(ints1));
	}

	public static class Json5Bean {

		public int id;

		public float decmails;

		public long value;

		public String name;

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 47 * hash + this.id;
			hash = 47 * hash + Float.floatToIntBits(this.decmails);
			hash = 47 * hash + (int) (this.value ^ (this.value >>> 32));
			hash = 47 * hash + Objects.hashCode(this.name);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final Json5Bean other = (Json5Bean) obj;
			if (this.id != other.id) {
				return false;
			}
			if (Float.floatToIntBits(this.decmails) != Float.floatToIntBits(other.decmails)) {
				return false;
			}
			if (this.value != other.value) {
				return false;
			}
			if (!Objects.equals(this.name, other.name)) {
				return false;
			}
			return true;
		}
	}
}
