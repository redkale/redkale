/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.Serializable;
import org.redkale.convert.ConvertColumn;

/**
 * 主要供 JsonConvert.writeWrapper 使用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class StringWrapper implements Serializable {

	@ConvertColumn(index = 1)
	protected String value;

	public StringWrapper() {}

	public StringWrapper(String value) {
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
		return value;
	}
}
