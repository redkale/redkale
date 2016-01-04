/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.watch;

import java.beans.*;
import java.util.concurrent.atomic.*;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
public final class WatchNumber extends AtomicLong implements WatchNode {

    private final boolean interval;

    private final String name;

    private final String description;

    @ConstructorProperties({"name", "description", "interval", "value"})
    protected WatchNumber(String name, String description, boolean interval, long value) {
        this.name = name;
        this.description = description;
        this.interval = interval;
        this.set(value);
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
