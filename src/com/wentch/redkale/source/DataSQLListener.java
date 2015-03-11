/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

/**
 * @Resource(name = "property.datasource.nodeid")
 *
 * @author zhangjx
 */
public interface DataSQLListener {

    public void insert(String sourceName, String... sqls);

    public void update(String sourceName, String... sqls);

    public void delete(String sourceName, String... sqls);
}
