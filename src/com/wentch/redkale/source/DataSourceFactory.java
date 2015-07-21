/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public abstract class DataSourceFactory {

    private static final Logger logger = Logger.getLogger(DataSourceFactory.class.getSimpleName());

    public static DataSource create() {
        return create("");
    }

    public static DataSource create(final String unitName) {
//        boolean jpa = false;
//        if (!"jdbc".equalsIgnoreCase(System.getProperty("source.type", "jpa"))) {
//            try {
//                jpa = ServiceLoader.load(Class.forName("javax.persistence.spi.PersistenceProvider")).iterator().hasNext();
//            } catch (Exception e) {
//                jpa = false;
//            }
//        }
//        if (jpa) return new DataJPASource(unitName);
        try {
            return new DataDefaultSource(unitName);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "cannot create DataSource (" + unitName + ")", ex);
            return null;
        }
    }
}
