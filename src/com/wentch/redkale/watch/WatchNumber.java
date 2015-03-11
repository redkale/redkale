/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.watch;

import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class WatchNumber extends AtomicLong implements WatchBean {

    private final boolean interval;

    private final String name;

    private final String description;

    WatchNumber(String name, String description, boolean interval, long v) {
        this.name = name;
        this.description = description;
        this.interval = interval;
        this.set(v);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public long getValue() {
        return super.longValue();
    }

    @Override
    public boolean isInterval() {
        return interval;
    }

}
