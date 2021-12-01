/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

/**
 * 版本
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Redkale {

    private static final String rootPackage = "org.redkale";

    private Redkale() { 
    }

    public static String getRootPackage() {
        return rootPackage;
    }

    public static String getDotedVersion() {
        return "2.6.0";
    }

    public static int getMajorVersion() {
        return 2;
    }

    public static int getMinorVersion() {
        return 6;
    }
}
