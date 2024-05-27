/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.proto;

import java.io.*;
import org.redkale.convert.ConvertException;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class ProtobufStreamWriter extends ProtobufByteBufferWriter {

	private OutputStream out;

	protected ProtobufStreamWriter(int features, boolean enumtostring, OutputStream out) {
		super(features, enumtostring, null);
		this.out = out;
	}

	@Override
	protected boolean recycle() {
		super.recycle();
		this.out = null;
		return false;
	}

	@Override
	public void writeTo(final byte[] chs, final int start, final int len) {
		try {
			out.write(chs, start, len);
		} catch (IOException e) {
			throw new ConvertException(e);
		}
	}

	@Override
	public void writeTo(final byte ch) {
		try {
			out.write((byte) ch);
		} catch (IOException e) {
			throw new ConvertException(e);
		}
	}
}
