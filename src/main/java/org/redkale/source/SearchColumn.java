/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * 搜索引擎的数据Entity依附在setter、getter方法、字段进行简单的配置  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.4.0
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface SearchColumn {

    //高亮显示参数
    public static class HighLights {

        public static final String HIGHLIGHT_NAME_ID = "#[id]";

        public static final String HIGHLIGHT_NAME_INDEX = "#[index]";

    }

    /**
     * 是否全文搜索
     *
     * @return boolean
     */
    boolean text() default false;

    /**
     * 高亮对应的Column.name字段名，被标记的字段为虚拟字段，不会映射表中的字段 <br>
     * 被标记的字段必须是String类型 <br>
     * 有值时，ignore必须为true
     *
     * @return String
     */
    String highlight() default "";

    /**
     * 解析/存储时是否屏蔽该字段
     *
     * @return boolean
     */
    boolean ignore() default false;

    /**
     * 设置索引参数, 特殊值"false"表示不被索引
     *
     * @return String
     */
    String options() default "";

    /**
     * 内容是否html格式
     *
     * @return boolean
     */
    boolean html() default false;

    /**
     * 内容是否时间类型，只有数据类型为int、long、String才有效
     *
     * @return boolean
     */
    boolean date() default false;

    /**
     * 内容是否ip类型，只有数据类型为String才有效
     *
     * @return boolean
     */
    boolean ip() default false;

    /**
     * 设置索引分词器
     *
     * @return String
     */
    String analyzer() default "";

    /**
     * 设置搜索索引分词器
     *
     * @return String
     */
    String searchAnalyzer() default "";

}
