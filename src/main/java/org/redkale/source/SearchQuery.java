/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.*;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;

/**
 * SearchQuery用于构建搜索过滤条件<br>
 * 不被标记为&#64;org.redkale.persistence.Transient 的字段均视为过滤条件 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
@ConvertImpl(SearchQuery.SearchSimpleQuery.class)
public interface SearchQuery extends java.io.Serializable {

    public static final String SEARCH_FILTER_NAME = "#search";

    public static SearchSimpleQuery create() {
        return new SearchSimpleQuery();
    }

    public static SearchSimpleQuery create(String keyword, String... fields) {
        return new SearchSimpleQuery(keyword, fields);
    }

    public static SearchSimpleHighlight createHighlight() {
        return new SearchSimpleHighlight();
    }

    /**
     * 需要搜索的index集合，无值则使用当前entity类
     *
     * @return Class[]
     */
    public Class[] searchClasses();

    /**
     * 搜索字段集合， 必须字段值
     *
     * @return String[]
     */
    public String[] searchFields();

    /**
     * 搜索关键字， 必须字段值
     *
     * @return String
     */
    public String searchKeyword();

    /**
     * 搜索分词器，可以为空
     *
     * @return String
     */
    public String searchAnalyzer();

    /**
     * 扩展的信息
     *
     * @return Map
     */
    default Map<String, Object> extras() {
        return null;
    }

    /**
     * 高亮显示
     *
     * @return SearchHighlight
     */
    public SearchHighlight highlight();

    @ConvertImpl(SearchQuery.SearchSimpleHighlight.class)
    public static interface SearchHighlight {

        public static SearchSimpleHighlight create() {
            return new SearchSimpleHighlight();
        }

        public static SearchSimpleHighlight createTag(String preTag, String postTag) {
            return new SearchSimpleHighlight().tag(preTag, postTag);
        }

        public String preTag();

        public String postTag();

        public String boundaryLocale();

        public int fragmentSize();

        default int fragmentCount() {
            return 1;
        }

        default Map<String, Object> extras() {
            return null;
        }
    }

    public static class SearchSimpleQuery implements SearchQuery {

        @ConvertColumn(index = 1)
        @FilterColumn(ignore = true)
        private Class[] classes;

        @ConvertColumn(index = 2)
        @FilterColumn(ignore = true)
        private String[] fields;

        @ConvertColumn(index = 3)
        @FilterColumn(ignore = true)
        private String keyword;

        @ConvertColumn(index = 4)
        @FilterColumn(ignore = true)
        private String analyzer;

        @ConvertColumn(index = 5)
        @FilterColumn(ignore = true)
        private SearchHighlight highlight;

        @ConvertColumn(index = 6)
        @FilterColumn(ignore = true)
        private Map<String, Object> extras;

        public SearchSimpleQuery() {}

        public SearchSimpleQuery(String keyword, String... fields) {
            this.keyword = keyword;
            this.fields = fields;
            if (fields == null || fields.length < 1) {
                throw new IllegalArgumentException("fields is empty");
            }
        }

        public SearchSimpleQuery keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public SearchSimpleQuery analyzer(String analyzer) {
            this.analyzer = analyzer;
            return this;
        }

        public SearchSimpleQuery fields(String... fields) {
            if (fields == null || fields.length < 1) {
                throw new IllegalArgumentException("fields is empty");
            }
            this.fields = fields;
            return this;
        }

        public SearchSimpleQuery classes(Class[] classes) {
            this.classes = classes;
            return this;
        }

        public SearchSimpleQuery highlight(SearchHighlight highlight) {
            this.highlight = highlight;
            return this;
        }

        public SearchSimpleQuery extras(Map<String, Object> map) {
            this.extras = map;
            return this;
        }

        public SearchSimpleQuery extras(String key, Object value) {
            if (this.extras == null) {
                this.extras = new LinkedHashMap<>();
            }
            this.extras.put(key, value);
            return this;
        }

        @Override
        public String searchKeyword() {
            return keyword;
        }

        @Override
        public Class[] searchClasses() {
            return classes;
        }

        @Override
        public String[] searchFields() {
            return fields;
        }

        @Override
        public Map<String, Object> extras() {
            return extras;
        }

        @Override
        public String searchAnalyzer() {
            return analyzer;
        }

        @Override
        public SearchHighlight highlight() {
            return highlight;
        }

        public Class[] getClasses() {
            return classes;
        }

        public void setClasses(Class[] classes) {
            this.classes = classes;
        }

        public String[] getFields() {
            return fields;
        }

        public void setFields(String[] fields) {
            this.fields = fields;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }

        public String getAnalyzer() {
            return analyzer;
        }

        public void setAnalyzer(String analyzer) {
            this.analyzer = analyzer;
        }

        public SearchHighlight getHighlight() {
            return highlight;
        }

        public void setHighlight(SearchHighlight highlight) {
            this.highlight = highlight;
        }

        public Map<String, Object> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, Object> extras) {
            this.extras = extras;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class SearchSimpleHighlight implements SearchHighlight {

        @ConvertColumn(index = 1)
        private String preTag;

        @ConvertColumn(index = 2)
        private String postTag;

        @ConvertColumn(index = 3)
        private String boundaryLocale;

        @ConvertColumn(index = 4)
        private int fragmentSize = 100;

        @ConvertColumn(index = 5)
        private int fragmentCount = 1;

        @ConvertColumn(index = 6)
        @FilterColumn(ignore = true)
        private Map<String, Object> extras;

        public SearchSimpleHighlight tag(String preTag, String postTag) {
            this.preTag = preTag;
            this.postTag = postTag;
            return this;
        }

        public SearchSimpleHighlight boundaryLocale(String boundaryLocale) {
            this.boundaryLocale = boundaryLocale;
            return this;
        }

        public SearchSimpleHighlight fragmentSize(int fragmentSize) {
            this.fragmentSize = fragmentSize;
            return this;
        }

        public SearchSimpleHighlight fragmentCount(int fragmentCount) {
            this.fragmentCount = fragmentCount;
            return this;
        }

        public SearchSimpleHighlight extras(Map<String, Object> map) {
            this.extras = map;
            return this;
        }

        public SearchSimpleHighlight extras(String key, Object value) {
            if (this.extras == null) {
                this.extras = new LinkedHashMap<>();
            }
            this.extras.put(key, value);
            return this;
        }

        @Override
        public Map<String, Object> extras() {
            return extras;
        }

        @Override
        public String preTag() {
            return preTag;
        }

        @Override
        public String postTag() {
            return postTag;
        }

        @Override
        public String boundaryLocale() {
            return boundaryLocale;
        }

        @Override
        public int fragmentSize() {
            return fragmentSize;
        }

        @Override
        public int fragmentCount() {
            return fragmentCount;
        }

        public String getPreTag() {
            return preTag;
        }

        public void setPreTag(String preTag) {
            this.preTag = preTag;
        }

        public String getPostTag() {
            return postTag;
        }

        public void setPostTag(String postTag) {
            this.postTag = postTag;
        }

        public String getBoundaryLocale() {
            return boundaryLocale;
        }

        public void setBoundaryLocale(String boundaryLocale) {
            this.boundaryLocale = boundaryLocale;
        }

        public int getFragmentSize() {
            return fragmentSize;
        }

        public void setFragmentSize(int fragmentSize) {
            this.fragmentSize = fragmentSize;
        }

        public int getFragmentCount() {
            return fragmentCount;
        }

        public void setFragmentCount(int fragmentCount) {
            this.fragmentCount = fragmentCount;
        }

        public Map<String, Object> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, Object> extras) {
            this.extras = extras;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
