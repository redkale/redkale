/*

*/

package org.redkale.test.mq;

import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class TestBean {
    
    private int userid;
    
    private String message;

    public TestBean() {}

    public TestBean(int userid, String message) {
        this.userid = userid;
        this.message = message;
    }

    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
