/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public abstract class DataConnection {

    private final Object connection;

    protected DataConnection(Object connection) {
        this.connection = connection;
    }

    protected <T> T getConnection() {
        return (T) this.connection;
    }

    public abstract boolean commit();

    public abstract void rollback();

    public abstract void close();
}
