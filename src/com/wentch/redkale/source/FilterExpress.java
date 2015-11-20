/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

/**
 *
 * @author zhangjx
 */
public enum FilterExpress {

    EQUAL("="),
    NOTEQUAL("<>"),
    GREATERTHAN(">"),
    LESSTHAN("<"),
    GREATERTHANOREQUALTO(">="),
    LESSTHANOREQUALTO("<="),
    LIKE("LIKE"),
    NOTLIKE("NOT LIKE"),
    IGNORECASELIKE("LIKE"),  //不区分大小写的 LIKE
    IGNORECASENOTLIKE("NOT LIKE"), //不区分大小写的 NOT LIKE
    BETWEEN("BETWEEN"),
    NOTBETWEEN("NOT BETWEEN"),
    IN("IN"),
    NOTIN("NOT IN"),
    ISNULL("IS NULL"),
    ISNOTNULL("IS NOT NULL"),
    OPAND("&"), //与运算 > 0
    OPOR("|"), //或运算 > 0
    OPANDNO("&"), //与运算 == 0
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
