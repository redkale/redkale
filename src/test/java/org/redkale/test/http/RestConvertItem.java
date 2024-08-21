/*

*/

package org.redkale.test.http;

import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class RestConvertItem {

    private long createTime;

    @ConvertColumn(ignore = true)
    private String aesKey;

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
