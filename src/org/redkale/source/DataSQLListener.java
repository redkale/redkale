/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 * @Resource(name = "property.datasource.nodeid")
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
public interface DataSQLListener {

    public void insert(String... sqls);

    public void update(String... sqls);

    public void delete(String... sqls);
}
