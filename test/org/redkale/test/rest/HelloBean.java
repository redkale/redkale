package org.redkale.test.rest;

import org.redkale.convert.json.JsonFactory;
import org.redkale.net.http.*;
import org.redkale.source.FilterBean;

public class HelloBean implements FilterBean {

    private int helloid;

    @RestHeader(name = "hello-res")
    private String res;

    public int getHelloid() {
        return helloid;
    }

    public void setHelloid(int helloid) {
        this.helloid = helloid;
    }

    public String getRes() {
        return res;
    }

    public void setRes(String res) {
        this.res = res;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }

}
