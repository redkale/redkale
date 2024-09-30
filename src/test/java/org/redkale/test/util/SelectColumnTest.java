/*

*/

package org.redkale.test.util;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.SelectColumn;

/**
 *
 * @author zhangjx
 */
public class SelectColumnTest {

    public static void main(String[] args) throws Throwable {
        SelectColumnTest test = new SelectColumnTest();
        test.run1();
    }

    @Test
    public void run1() throws Exception {
        SelectColumn sel = SelectColumn.includes(User::getUserId, User::getUserName);
        SelectColumn sel2 = SelectColumn.includes("userId", "userName");
        Assertions.assertTrue(sel.equals(sel2));
        sel.setPatterns(new Pattern[] {Pattern.compile("aaa")});
        System.out.println(JsonConvert.root().convertTo(sel));
        String json = "aaa";
        Pattern pattern = JsonConvert.root().convertFrom(Pattern.class, json);
        Assertions.assertEquals(0, pattern.flags());
        Assertions.assertEquals("aaa", pattern.pattern());
    }

    public static class User {

        private long userId;

        private String userName;

        private int age;

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
