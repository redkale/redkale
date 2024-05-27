/*
 *
 */
package org.redkale.source;

import java.util.Objects;
import org.redkale.convert.ConvertColumn;

/**
 * 数值的ColumnNode
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ColumnNumberNode implements ColumnNode {

	@ConvertColumn(index = 1)
	private Number value;

	public ColumnNumberNode() {}

	public ColumnNumberNode(Number value) {
		Objects.requireNonNull(value, "number is null");
		this.value = value;
	}

	public Number getValue() {
		return value;
	}

	public void setValue(Number value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "{\"value\":" + value + "}";
	}
}
