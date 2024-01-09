/*
 *
 */
package org.redkale.test.source.parser;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.SourceException;
import org.redkale.util.TypeToken;

/**
 *
 * @author zhangjx
 */
public class DataSqlMapperTest {

    public static void main(String[] args) throws Throwable {
        DataSqlMapperTest test = new DataSqlMapperTest();
        test.run();

        System.out.println(entityType(ForumInfoMapper.class));
    }

    private static Class entityType(Class mapperType) {
        for (Type t : mapperType.getGenericInterfaces()) {
            if (DataSqlMapper.class.isAssignableFrom(TypeToken.typeToClass(t))) {
                return TypeToken.typeToClass(((ParameterizedType) t).getActualTypeArguments()[0]);
            }
        }
        throw new SourceException("Not found entity class from " + mapperType.getName());
    }

    @Test
    public void run() throws Exception {

    }
}
