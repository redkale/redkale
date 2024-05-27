/*
 *
 */
package org.redkale.source;

import java.util.Objects;
import org.redkale.convert.ConvertColumn;

/**
 * 字符串的ColumnNode
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ColumnStringNode implements ColumnNode {

	@ConvertColumn(index = 1)
	private String value;

	public ColumnStringNode() {}

	public ColumnStringNode(String value) {
		Objects.requireNonNull(value, "string value is null");
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "{\"value\":\"" + value + "\"}";
	}
}
