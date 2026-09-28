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
    OR("OR");

    private final String value;

    private FilterExpress(String v) {
        this.value = v;
    }

    public String value() {
        return value;
    }
}
