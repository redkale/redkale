/*
 *
 */
package org.redkale.source;

import java.util.Objects;
import org.redkale.convert.ConvertColumn;

/**
 * 字段名的ColumnNode
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ColumnNameNode implements ColumnNode {

	@ConvertColumn(index = 1)
	private String column;

	public ColumnNameNode() {}

	public ColumnNameNode(String column) {
		Objects.requireNonNull(column, "column is null");
		this.column = column;
	}

	public String getColumn() {
		return column;
	}

	public void setColumn(String column) {
		this.column = column;
	}

	@Override
	public String toString() {
		return "{\"column\":\"" + column + "\"}";
	}
}
