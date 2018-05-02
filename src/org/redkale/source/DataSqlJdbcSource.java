/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.net.URL;
import java.sql.Connection;
import java.util.Properties;
import org.redkale.service.Local;
import org.redkale.util.*;

/**
 * DataSource的JDBC实现类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(DataSource.class)
public abstract class DataSqlJdbcSource extends DataSqlSource<Connection> {

    public DataSqlJdbcSource(String unitName, URL persistxml, Properties readprop, Properties writeprop) {
        super(unitName, persistxml, readprop, writeprop);
    }

    @Override
    protected final String getPrepareParamSign(int index) {
        return "?";
    }

    @Override
    protected final boolean isAysnc() {
        return false;
    }
}
