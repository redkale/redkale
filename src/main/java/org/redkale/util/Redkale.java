/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

/**
 * 版本
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Redkale {

    private static final String ROOT_PACKAGE = "org.redkale";

    private Redkale() {}

    public static String getRootPackage() {
        return ROOT_PACKAGE;
    }

    public static String getDotedVersion() {
        return "2.8.1";
    }

    public static int getMajorVersion() {
        return 2;
    }

    public static int getMinorVersion() {
        return 8;
    }
}
