/*

*/

package org.redkale.test.convert;

import java.util.function.BiFunction;
import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertColumnHandler;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class ConvertColumnHandlerTest {

    public static void main(String[] args) throws Throwable {
        ConvertColumnHandlerTest test = new ConvertColumnHandlerTest();
        test.run1();
    }

    @Test
    public void run1() throws Throwable {
        ParamRequest param = new ParamRequest();
        param.setName("haha");
        param.setPhone("1381234500");
        Assertions.assertEquals("{\"name\":\"haha\",\"phone\":\"138****00\"}", param.toString());
    }

    public static class ParamRequest {

        private String name;

        @ConvertColumnHandler(ParamColumnHandler.class)
        private String phone;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class ParamColumnHandler implements BiFunction<String, String, String> {

        @Override
        public String apply(String field, String value) {
            if (value == null || value.length() < 5) {
                return value;
            } else {
                return value.substring(0, 3) + "****" + value.substring(value.length() - 2);
            }
        }
    }
}
