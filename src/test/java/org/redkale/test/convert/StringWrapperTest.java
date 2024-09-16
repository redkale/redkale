/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.StringWrapper;

/** @author zhangjx */
public class StringWrapperTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        StringWrapperTest test = new StringWrapperTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        JsonConvert convert = JsonConvert.root();
        {
            String val = "{}";
            StringWrapper wrapper = new StringWrapper(val);
            if (!main) Assertions.assertEquals(val, convert.convertTo(wrapper));
            if (!main) Assertions.assertEquals(val, new String(convert.convertToBytes(wrapper)));
            System.out.println(convert.convertTo(wrapper));
            System.out.println(new String(convert.convertToBytes(wrapper)));
        }
        {
            String emoji =
                    new String(new byte[] {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x81}, StandardCharsets.UTF_8);
            String val = "{id:'z带中文" + emoji + "a'}";
            StringWrapper wrapper = new StringWrapper(val);
            if (!main) Assertions.assertEquals(val, convert.convertTo(wrapper));
            if (!main)
                Assertions.assertEquals(val, new String(convert.convertToBytes(wrapper), StandardCharsets.UTF_8));
            System.out.println(convert.convertTo(wrapper));
            System.out.println(new String(convert.convertToBytes(wrapper), StandardCharsets.UTF_8));
        }
    }
}
