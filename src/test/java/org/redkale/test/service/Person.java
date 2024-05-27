package org.redkale.test.service;

import java.io.Serializable;

public class Person implements Serializable {

	private byte[] b = new byte[1024 * 2];

	private String name;

	@Override
	public String toString() {
		return "{name=" + name + ", b =" + (b == null ? "null" : "[length=" + b.length + "]") + "}";
	}

	public byte[] getB() {
		return b;
	}

	public void setB(byte[] b) {
		this.b = b;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
