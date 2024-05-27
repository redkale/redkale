/*
 *
 */
package org.redkale.source;

import java.sql.Connection;

/**
 * 用于获取jdbc的物理连接对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class DataJdbcConnection {

    final boolean readFlag;

    public abstract Connection getConnection();

    DataJdbcConnection(boolean readFlag) {
        this.readFlag = readFlag;
    }
}
