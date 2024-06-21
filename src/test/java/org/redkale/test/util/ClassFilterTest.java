/*

*/

package org.redkale.test.util;

import org.junit.jupiter.api.*;
import org.redkale.boot.ClassFilter;

/**
 *
 * @author zhangjx
 */
public class ClassFilterTest {

    public static void main(String[] args) throws Throwable {
        ClassFilterTest test = new ClassFilterTest();
        test.run1();
        test.run2();
        test.run3();
    }

    @Test
    public void run1() {
        String regx = ClassFilter.formatPackageRegx("*.platf.**");
        Assertions.assertEquals("^(\\w+)\\.platf\\.(.*)$", regx);
    }

    @Test
    public void run2() {
        String regx = ClassFilter.formatPackageRegx("com.platf.**.api");
        Assertions.assertEquals("^com\\.platf\\.(.*)\\.api$", regx);
    }

    @Test
    public void run3() {
        String regx = ClassFilter.formatPackageRegx("**.platf.api");
        Assertions.assertEquals("^(.*)\\.platf\\.api$", regx);
    }
}
