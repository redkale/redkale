/*
 */
package org.redkale.test.convert;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.StringWrapper;

/**
 *
 * @author zhangjx
 */
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
            String val = "{id:'带中文'}";
            StringWrapper wrapper = new StringWrapper(val);
            if (!main) Assertions.assertEquals(val, convert.convertTo(wrapper));
            if (!main) Assertions.assertEquals(val, new String(convert.convertToBytes(wrapper), StandardCharsets.UTF_8));
            System.out.println(convert.convertTo(wrapper));
            System.out.println(new String(convert.convertToBytes(wrapper), StandardCharsets.UTF_8));
        }
    }
}
