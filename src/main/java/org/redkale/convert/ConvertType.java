/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 * 序列化类型枚举，结合&#64;ConvertColumn使用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public enum ConvertType {
	JSON(1),
	BSON(2),
	PROTOBUF(64),
	PROTOBUF_JSON(64 + 1),
	PROTOBUF_BSON(64 + 2),
	DIY(256),
	ALL(1023);

	private final int value;

	private ConvertType(int v) {
		this.value = v;
	}

	public int getValue() {
		return value;
	}

	public boolean contains(ConvertType type) {
		if (type == null) return false;
		return this.value >= type.value && (this.value & type.value) > 0;
	}

	public static ConvertType find(int value) {
		for (ConvertType t : ConvertType.values()) {
			if (value == t.value) return t;
		}
		return null;
	}
}
