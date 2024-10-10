/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.util;

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Creator;

/**
 *
 * @author zhangjx
 */
public class CreatorTest {

    public static void main(String[] args) throws Throwable {
        CreatorTest test = new CreatorTest();
        test.run1();
    }

    @Test
    public void run1() throws Throwable {
        Creator<CreatorRecord> creator = Creator.create(CreatorRecord.class, 9);
        Creator.load(CreatorRecord.class);
        System.out.println(Arrays.toString(creator.paramTypes()));
        CreatorRecord record =
                creator.create(new Object[] {null, "ss", null, true, null, (short) 45, null, 4.3f, null});
        String json = record.toString();
        System.out.println(json);
        String json2 = JsonConvert.root().convertFrom(CreatorRecord.class, json).toString();
        System.out.println(json2);
        Assertions.assertEquals("ss", record.getName());
        Assertions.assertEquals(json, json2);
    }
}
