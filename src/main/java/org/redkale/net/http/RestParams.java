/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 用于RestService类的方法的参数获取HttpParams
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public interface RestParams {

    public String get(String name);

    public String get(String name, String defaultValue);

    public void forEach(BiConsumer<String, String> consumer);

    public String[] names();

    public boolean contains(String name);

    public Map<String, String> map();
}
