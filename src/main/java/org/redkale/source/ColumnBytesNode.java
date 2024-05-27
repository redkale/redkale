/*
 *
 */
package org.redkale.source;

import org.redkale.convert.ConvertColumn;

/**
 * byte[]的ColumnNode
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ColumnBytesNode implements ColumnNode {

	@ConvertColumn(index = 1)
	private byte[] value;

	public ColumnBytesNode() {}

	public ColumnBytesNode(byte[] value) {
		this.value = value;
	}

	public static ColumnBytesNode create(byte[] value) {
		return new ColumnBytesNode(value);
	}

	public byte[] getValue() {
		return value;
	}

	public void setValue(byte[] value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "{\"value\":" + value + "}";
	}
}
