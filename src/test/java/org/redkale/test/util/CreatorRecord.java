/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Creator;

/** @author zhangjx */
public class CreatorRecord {

	private int id = -1;

	private String name;

	private long lval;

	private boolean tval;

	private byte bval;

	private short sval;

	private char cval;

	private float fval;

	private double dval;

	@ConstructorParameters({"id", "name", "lval", "tval", "bval", "sval", "cval", "fval", "dval"})
	public CreatorRecord(
			int id, String name, long lval, boolean tval, byte bval, short sval, char cval, float fval, double dval) {
		this.id = id;
		this.name = name;
		this.lval = lval;
		this.tval = tval;
		this.bval = bval;
		this.sval = sval;
		this.cval = cval;
		this.fval = fval;
		this.dval = dval;
	}

	@Test
	public void run1() {
		Creator<CreatorRecord> creator = Creator.create(CreatorRecord.class);
		System.out.println(Arrays.toString(creator.paramTypes()));
		CreatorRecord record =
				creator.create(new Object[] {null, "ss", null, true, null, (short) 45, null, 4.3f, null});
		String json = record.toString();
		System.out.println(json);
		String json2 = JsonConvert.root().convertFrom(CreatorRecord.class, json).toString();
		System.out.println(json2);
		Assertions.assertEquals(json, json2);
	}

	@Override
	public String toString() {
		return JsonConvert.root().convertTo(this);
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public long getLval() {
		return lval;
	}

	public boolean isTval() {
		return tval;
	}

	public byte getBval() {
		return bval;
	}

	public short getSval() {
		return sval;
	}

	public char getCval() {
		return cval;
	}

	public float getFval() {
		return fval;
	}

	public double getDval() {
		return dval;
	}
}
