/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 * 函数表达式， 均与SQL定义中的表达式相同
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public enum FilterExpress {
	EQ("="),
	IG_EQ("="), // 不区分大小写的 =
	NE("<>"), //
	IG_NE("="), // 不区分大小写的 <>
	GT(">"),
	LT("<"),
	GE(">="),
	LE("<="),
	LIKE("LIKE"),
	NOT_LIKE("NOT LIKE"),
	IG_LIKE("LIKE"), // 不区分大小写的 LIKE
	IG_NOT_LIKE("NOT LIKE"), // 不区分大小写的 NOT LIKE
	STARTS("LIKE"),
	ENDS("LIKE"),
	NOT_STARTS("NOT LIKE"),
	NOT_ENDS("NOT LIKE"),
	LEN_EQ("="), // 字符串值的长度
	LEN_GT(">"), // 字符串值的长度 >
	LEN_LT("<"), // 字符串值的长度 <
	LEN_GE(">="), // 字符串值的长度 >=
	LEN_LE("<="), // 字符串值的长度 <=

	CONTAIN("CONTAIN"), // 包含， 相当于反向LIKE
	NOT_CONTAIN("NOT CONTAIN"), // 不包含， 相当于反向LIKE
	IG_CONTAIN("CONTAIN"), // 不区分大小写的 CONTAIN
	IG_NOT_CONTAIN("NOT CONTAIN"), // 不区分大小写的 NOT CONTAIN

	BETWEEN("BETWEEN"),
	NOT_BETWEEN("NOT BETWEEN"),
	IN("IN"),
	NOT_IN("NOT IN"),
	IS_NULL("IS NULL"),
	NOT_NULL("IS NOT NULL"),
	IS_EMPTY("="), // 值为空
	NOT_EMPTY("<>"), // 值不为空
	OPAND("&"), // 与运算 > 0
	OPOR("|"), // 或运算 > 0
	NOT_OPAND("&"), // 与运算 == 0
	FV_MOD("%"), // 取模运算，需要与FilterValue配合使用
	FV_DIV("DIV"), // 整除运算，需要与FilterValue配合使用

	AND("AND"),
	OR("OR"),
	// ------------------------ 过期 ------------------------
	@Deprecated(since = "2.8.0")
	EQUAL("="),
	@Deprecated(since = "2.8.0")
	IGNORECASELIKE("LIKE"),
	@Deprecated(since = "2.8.0")
	IGNORECASENOTLIKE("NOT LIKE"),
	@Deprecated(since = "2.8.0")
	ENDSWITH("LIKE"),
	@Deprecated(since = "2.8.0")
	STARTSWITH("LIKE"),
	@Deprecated(since = "2.8.0")
	IGNORECASEEQUAL("="),
	@Deprecated(since = "2.8.0")
	NOTEQUAL("<>"),
	@Deprecated(since = "2.8.0")
	GREATERTHAN(">"),
	@Deprecated(since = "2.8.0")
	LESSTHAN("<"),
	@Deprecated(since = "2.8.0")
	GREATERTHANOREQUALTO(">="),
	@Deprecated(since = "2.8.0")
	LESSTHANOREQUALTO("<="),
	@Deprecated(since = "2.8.0")
	NOTLIKE("NOT LIKE"),
	@Deprecated(since = "2.8.0")
	IGNORECASENOTEQUAL("="),
	@Deprecated(since = "2.8.0")
	NOTENDSWITH("NOT LIKE"),
	@Deprecated(since = "2.8.0")
	NOTSTARTSWITH("NOT LIKE"),
	@Deprecated(since = "2.8.0")
	LENGTH_EQUAL("="),
	@Deprecated(since = "2.8.0")
	LENGTH_GREATERTHAN(">"),
	@Deprecated(since = "2.8.0")
	LENGTH_LESSTHAN("<"),
	@Deprecated(since = "2.8.0")
	LENGTH_GREATERTHANOREQUALTO(">="),
	@Deprecated(since = "2.8.0")
	IGNORECASENOTCONTAIN("NOT CONTAIN"),
	@Deprecated(since = "2.8.0")
	IGNORECASECONTAIN("CONTAIN"),
	@Deprecated(since = "2.8.0")
	LENGTH_LESSTHANOREQUALTO("<="),
	@Deprecated(since = "2.8.0")
	NOTCONTAIN("NOT CONTAIN"),
	@Deprecated(since = "2.8.0")
	ISEMPTY("="),
	@Deprecated(since = "2.8.0")
	ISNOTNULL("IS NOT NULL"),
	@Deprecated(since = "2.8.0")
	NOTBETWEEN("NOT BETWEEN"),
	@Deprecated(since = "2.8.0")
	NOTIN("NOT IN"),
	@Deprecated(since = "2.8.0")
	ISNULL("IS NULL"),
	@Deprecated(since = "2.8.0")
	ISNOTEMPTY("<>");

	private final String value;

	private FilterExpress(String v) {
		this.value = v;
	}

	public String value() {
		return value;
	}
}
