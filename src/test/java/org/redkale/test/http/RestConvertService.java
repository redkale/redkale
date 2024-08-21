/*

*/

package org.redkale.test.http;

import org.redkale.net.http.RestConvert;
import org.redkale.net.http.RestConvertCoder;
import org.redkale.net.http.RestService;
import org.redkale.service.AbstractService;

/**
 *
 * @author zhangjx
 */
@RestService(name = "test", autoMapping = true)
public class RestConvertService extends AbstractService {

    public RestConvertBean load1() {
        return createBean();
    }

    @RestConvert(type = RestConvertItem.class, skipIgnore = true)
    public RestConvertBean load2() {
        return createBean();
    }

    @RestConvert(type = RestConvertBean.class, onlyColumns = "id")
    public RestConvertBean load3() {
        return createBean();
    }

    @RestConvertCoder(type = RestConvertBean.class, field = "enable", coder = RestConvertBoolCoder.class)
    public RestConvertBean load4() {
        return createBean();
    }

    private RestConvertBean createBean() {
        RestConvertBean bean = new RestConvertBean();
        bean.setId(123);
        bean.setName("haha");
        bean.setEnable(true);
        RestConvertItem item = new RestConvertItem();
        item.setCreateTime(100);
        item.setAesKey("keykey");
        bean.setContent(item);
        return bean;
    }
}
