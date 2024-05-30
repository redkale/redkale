/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.Map;

/** @author zhangjx */
public interface TestInterface {

    public int getId();

    public Map<String, String> getMap();

    public void setId(int id);

    public void setMap(Map<String, String> map);
}
