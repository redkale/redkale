/*

*/

package org.redkale.test.http;

import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class RestConvertBean {

    private int id;

    private boolean enable;

    private String name;

    private RestConvertItem content;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RestConvertItem getContent() {
        return content;
    }

    public void setContent(RestConvertItem content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
