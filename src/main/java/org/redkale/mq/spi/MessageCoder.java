/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.util.Utility;

/**
 * 将MessageRecord.content内容加解密
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 * @param <T> 泛型
 */
public interface MessageCoder<T> {

	// 编码
	public byte[] encode(T data);

	// 解码
	public T decode(byte[] data);

	// 消息内容的类型
	public byte ctype();

	// type: 1:string, 2:int, 3:long, 4:BigInteger
	public static byte[] encodeUserid(Serializable value) {
		if (value == null) {
			return MessageRecord.EMPTY_BYTES;
		}
		if (value instanceof Integer) {
			int val = (Integer) value;
			return new byte[] {
				(byte) 2,
				(byte) (val >> 24 & 0xFF),
				(byte) (val >> 16 & 0xFF),
				(byte) (val >> 8 & 0xFF),
				(byte) (val & 0xFF)
			};
		} else if (value instanceof Long) {
			long val = (Long) value;
			return new byte[] {
				(byte) 3,
				(byte) (val >> 56 & 0xFF),
				(byte) (val >> 48 & 0xFF),
				(byte) (val >> 40 & 0xFF),
				(byte) (val >> 32 & 0xFF),
				(byte) (val >> 24 & 0xFF),
				(byte) (val >> 16 & 0xFF),
				(byte) (val >> 8 & 0xFF),
				(byte) (val & 0xFF)
			};
		} else if (value instanceof BigInteger) {
			BigInteger val = (BigInteger) value;
			return Utility.append(new byte[] {4}, val.toByteArray());
		}
		String str = value.toString();
		if (str.isEmpty()) {
			return MessageRecord.EMPTY_BYTES;
		}
		return Utility.append(new byte[] {(byte) 1}, str.getBytes(StandardCharsets.UTF_8));
	}

	// type: 1:string, 2:int, 3:long, 4:BigInteger
	public static Serializable decodeUserid(ByteBuffer buffer) {
		int len = buffer.getShort();
		if (len == -1) {
			return null;
		}
		byte type = buffer.get();
		if (type == 2) {
			return buffer.getInt();
		}
		if (type == 3) {
			return buffer.getLong();
		}
		byte[] bs = new byte[len - 1];
		buffer.get(bs);
		if (type == 4) {
			return new BigInteger(bs);
		}
		return new String(bs, StandardCharsets.UTF_8);
	}

	public static byte[] getBytes(byte[] value) {
		if (value == null) {
			return MessageRecord.EMPTY_BYTES;
		}
		return value;
	}

	public static byte[] getBytes(String value) {
		if (value == null || value.isEmpty()) {
			return MessageRecord.EMPTY_BYTES;
		}
		return value.getBytes(StandardCharsets.UTF_8);
	}

	public static byte[] getStringMapBytes(final Map<String, String> map) {
		if (map == null || map.isEmpty()) {
			return new byte[2];
		}
		final AtomicInteger len = new AtomicInteger(2);
		map.forEach((key, value) -> {
			len.addAndGet(2 + (key == null ? 0 : Utility.encodeUTF8Length(key)));
			len.addAndGet(4 + (value == null ? 0 : Utility.encodeUTF8Length(value)));
		});
		final byte[] bs = new byte[len.get()];
		final ByteBuffer buffer = ByteBuffer.wrap(bs);
		buffer.putShort((short) map.size());
		map.forEach((key, value) -> {
			putSmallString(buffer, key);
			putBigString(buffer, value);
		});
		return bs;
	}

	public static Map<String, String> getStringMap(ByteBuffer buffer) {
		int len = buffer.getShort();
		if (len == 0) {
			return null;
		}
		Map<String, String> map = new HashMap<>(len);
		for (int i = 0; i < len; i++) {
			map.put(getSmallString(buffer), getBigString(buffer));
		}
		return map;
	}

	public static byte[] getSeriMapBytes(final Map<String, Serializable> map) {
		if (map == null || map.isEmpty()) {
			return new byte[2];
		}
		final AtomicInteger len = new AtomicInteger(2);
		map.forEach((key, value) -> {
			len.addAndGet(2 + (key == null ? 0 : Utility.encodeUTF8Length(key)));
			len.addAndGet(2 + (value == null ? 0 : lengthSeriStringOrList(value)));
		});
		final byte[] bs = new byte[len.get()];
		final ByteBuffer buffer = ByteBuffer.wrap(bs);
		buffer.putShort((short) map.size());
		map.forEach((key, value) -> {
			putSmallString(buffer, key);
			putSeriStringOrList(buffer, value);
		});
		return bs;
	}

	public static Map<String, Serializable> getSeriMap(ByteBuffer buffer) {
		int len = buffer.getShort();
		if (len <= 0) {
			return null;
		}
		Map<String, Serializable> map = new HashMap<>(len);
		for (int i = 0; i < len; i++) {
			map.put(getSmallString(buffer), getSeriStringOrList(buffer));
		}
		return map;
	}

	public static void putBigString(ByteBuffer buffer, String value) {
		if (value == null) {
			buffer.putInt(-1);
		} else if (value.isEmpty()) {
			buffer.putInt(0);
		} else {
			byte[] bs = value.getBytes(StandardCharsets.UTF_8);
			buffer.putInt(bs.length);
			buffer.put(bs);
		}
	}

	public static String getBigString(ByteBuffer buffer) {
		int len = buffer.getInt();
		if (len == -1) {
			return null;
		} else if (len == 0) {
			return "";
		}
		byte[] bs = new byte[len];
		buffer.get(bs);
		return new String(bs, StandardCharsets.UTF_8);
	}

	// 一般用于存放类名、字段名、map中的key
	public static void putSmallString(ByteBuffer buffer, String value) {
		if (value == null) {
			buffer.putShort((short) -1);
		} else if (value.isEmpty()) {
			buffer.putShort((short) 0);
		} else {
			byte[] bs = value.getBytes(StandardCharsets.UTF_8);
			buffer.putShort((short) bs.length);
			buffer.put(bs);
		}
	}

	public static String getSmallString(ByteBuffer buffer) {
		int len = buffer.getShort();
		if (len == -1) {
			return null;
		} else if (len == 0) {
			return "";
		}
		byte[] bs = new byte[len];
		buffer.get(bs);
		return new String(bs, StandardCharsets.UTF_8);
	}

	private static void putSeriStringOrList(ByteBuffer buffer, Serializable value) {
		if (value == null) {
			buffer.putShort((short) -1);
		} else if (value instanceof Collection) {
			buffer.putShort((short) ((Collection) value).size());
			for (Object val : (Collection) value) {
				putBigString(buffer, val == null ? null : val.toString());
			}
		} else {
			buffer.putShort((short) 0);
			putBigString(buffer, value.toString());
		}
	}

	private static Serializable getSeriStringOrList(ByteBuffer buffer) {
		int size = buffer.getShort();
		if (size == -1) {
			return null;
		} else if (size == 0) { // 单个字符串
			return getBigString(buffer);
		}
		ArrayList list = new ArrayList();
		for (int i = 0; i < size; i++) {
			list.add(getBigString(buffer));
		}
		return list;
	}

	private static int lengthSeriStringOrList(Serializable value) {
		if (value == null) {
			return 0;
		} else if (value instanceof Collection) {
			int c = 0;
			for (Object val : (Collection) value) {
				c += 4 + (val == null ? 0 : Utility.encodeUTF8Length(val.toString()));
			}
			return c;
		} else {
			return 4 + Utility.encodeUTF8Length(value.toString());
		}
	}
}
