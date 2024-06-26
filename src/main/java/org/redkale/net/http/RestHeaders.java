/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 用于RestService类的方法的参数获取HttpHeaders
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface RestHeaders {

    public String firstValue(String name);

    public String firstValue(String name, String defaultValue);

    public List<String> listValue(String name);

    public void forEach(BiConsumer<String, String> consumer);

    public void forEach(Predicate<String> filter, BiConsumer<String, String> consumer);

    public String[] names();

    public boolean contains(String name);

    public Map<String, Serializable> map();
}
