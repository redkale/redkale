/*
 *
 */
package org.redkale.util;

/**
 * 简单的boolean值引用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class BoolRef {

	private boolean value;

	public BoolRef(boolean initialValue) {
		this.value = initialValue;
	}

	public BoolRef() {}

	public boolean get() {
		return this.value;
	}

	public void set(boolean newValue) {
		this.value = newValue;
	}

	public BoolRef asFalse() {
		this.value = false;
		return this;
	}

	public BoolRef asTrue() {
		this.value = true;
		return this;
	}

	@Override
	public String toString() {
		return String.valueOf(this.value);
	}
}
