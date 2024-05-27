/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source.parser;

import java.io.*;
import org.redkale.convert.json.*;
import org.redkale.persistence.*;

/** @author zhangjx */
@Entity
public abstract class BaseEntity implements Serializable {

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
