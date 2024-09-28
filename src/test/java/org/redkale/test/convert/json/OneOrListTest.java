/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.OneOrList;

/** @author zhangjx */
public class OneOrListTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        OneOrListTest test = new OneOrListTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        JsonConvert convert = JsonConvert.root();
        String json = "[\"aaaaa\"]";
        {
            StringOneList sol = convert.convertFrom(StringOneList.class, json);
            System.out.println("sol.list = " + convert.convertTo(sol.getList()));
            if (!main) Assertions.assertEquals(json, convert.convertTo(sol));
            System.out.println(convert.convertTo(sol));
        }
        {
            String2OneList sol2 = convert.convertFrom(String2OneList.class, json);
            System.out.println("sol2.list = " + convert.convertTo(sol2.getList()));
            if (!main) Assertions.assertEquals(json, convert.convertTo(sol2));
            System.out.println(convert.convertTo(sol2));
        }
        {
            OneOrList<String> sol3 = convert.convertFrom(OneOrList.TYPE_OL_STRING, json);
            System.out.println("sol3.list = " + convert.convertTo(sol3.getList()));
            if (!main) Assertions.assertEquals(json, convert.convertTo(OneOrList.TYPE_OL_STRING, sol3));
            System.out.println(convert.convertTo(OneOrList.TYPE_OL_STRING, sol3));
        }
    }

    public static class StringOneList extends OneOrList<String> {}

    public static class String2OneList extends StringOneList {}
}
