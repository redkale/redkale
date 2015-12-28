/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.sql.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class DataConnection {

    private final Connection conn;

    protected DataConnection(Connection connection) {
        this.conn = connection;
    }

    protected Connection getConnection() {
        return this.conn;
    }

    public boolean close() {
        try {
            if (conn == null || conn.isClosed()) return true;
            conn.close();
            return true;
        } catch (Exception e) {
            //do nothing
            return false;
        }
    }

    public boolean commit() {
        try {
            conn.commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean rollback() {
        try {
            conn.rollback();
            return true;
        } catch (Exception e) {
            //do nothing
            return false;
        }
    }
}
