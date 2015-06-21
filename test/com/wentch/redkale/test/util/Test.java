/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.util;

import java.util.*;

/**
 *
 * @author zhangjx
 */
public class Test {

    public static void main(String args[]) {
        List<String> list = new ArrayList<String>();
        test1(list);
        System.out.println(list.size()); // 1处
        test2(list);
        System.out.println(list.size()); // 2处
        test3(list);
        System.out.println(list.size()); // 3处
    }

    public static void test1(List list) {
        list = null;
    }

    public static void test2(List list) {
        list.add("3wyc"); 
    }
    
    public static void test3(List list) {
        list.add(new StringBuilder("3wyc"));
    }

}
