/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Bean;

/**
 * 翻页对象与过滤条件Bean的组合对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> Bean类
 *
 * @since 2.7.0
 */
@Bean
public class PageBean<T> {

    @ConvertColumn(index = 1)
    protected T bean;

    @ConvertColumn(index = 2)
    protected Flipper flipper;

    public PageBean() {
    }

    public PageBean(T bean, Flipper flipper) {
        this.bean = bean;
        this.flipper = flipper;
    }

    public PageBean<T> bean(T bean) {
        this.bean = bean;
        return this;
    }

    public PageBean<T> flipper(Flipper flipper) {
        this.flipper = flipper;
        return this;
    }

    public T getBean() {
        return bean;
    }

    public void setBean(T bean) {
        this.bean = bean;
    }

    public Flipper getFlipper() {
        return flipper;
    }

    public void setFlipper(Flipper flipper) {
        this.flipper = flipper;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
