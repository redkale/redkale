/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.watch;

/**
 *
 * @author zhangjx
 */
public interface WatchBean {

    public String getName();

    public String getDescription();

    public long getValue();

    public boolean isInterval();
}
