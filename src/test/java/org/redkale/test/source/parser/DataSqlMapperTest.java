/*
 *
 */
package org.redkale.test.source.parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redkale.source.DataJdbcSource;
import org.redkale.source.DataSqlSource;

/**
 *
 * @author zhangjx
 */
public class DataSqlMapperTest {

    private static DataSqlSource source = new DataJdbcSource();

    public static void main(String[] args) throws Throwable {
        DataSqlMapperTest test = new DataSqlMapperTest();
        test.init();
        test.run();
    }

    @BeforeAll
    public static void init() throws Exception {
        //do
    }

    @Test
    public void run() throws Exception {
        //do
    }
}
