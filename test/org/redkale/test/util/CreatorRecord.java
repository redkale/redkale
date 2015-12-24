/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class CreatorRecord {

    private final int id;

    private String name;

    public CreatorRecord(int id, String name) {
        this.id = id;
        this.name = name;
    }

    private static Creator createCreator() {
        return new Creator() {
            @Override
            public Object create(Object... params) {
                return new CreatorRecord((Integer) params[0], (String) params[1]);
            }
            
            @Override
            public String toString(){
                return "CreatorRecord_Creator" + Objects.hashCode(this);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        System.out.println(Creator.create(CreatorRecord.class));
    }
}
