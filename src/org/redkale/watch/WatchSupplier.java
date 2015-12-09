/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.watch;

import java.util.function.LongSupplier;

/**
 *
 * @author zhangjx
 */
public final class WatchSupplier implements WatchNode {

    private final String name;

    private final String description;

    private final LongSupplier supplier;

    WatchSupplier(String name, String description, LongSupplier supplier) {
        this.name = name;
        this.description = description;
        this.supplier = supplier;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public long getValue() {
        return supplier == null ? Long.MIN_VALUE : supplier.getAsLong();
    }

    @Override
    public boolean isInterval() {
        return false;
    }
}
