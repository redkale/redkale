/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import org.redkale.convert.json.JsonFactory;

/**
 *
 * @author zhangjx
 */
public class LoginBean {

    private String account = "";

    private String password = "";

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
