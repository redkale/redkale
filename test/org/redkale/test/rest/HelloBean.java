package org.redkale.test.rest;

import org.redkale.convert.json.JsonFactory;
import org.redkale.source.FilterBean;

public class HelloBean implements FilterBean {

    private int helloid;

    public int getHelloid() {
        return helloid;
    }

    public void setHelloid(int helloid) {
        this.helloid = helloid;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
