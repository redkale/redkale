/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Id;

/** @author zhangjx */
public class World implements Comparable<World> {

    @Id
    private int id;

    private int randomNumber;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRandomNumber() {
        return randomNumber;
    }

    public void setRandomNumber(int randomNumber) {
        this.randomNumber = randomNumber;
    }

    @Override
    public int compareTo(World o) {
        return Integer.compare(id, o.id);
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public static void main(String[] args) throws Throwable {
        World[] worlds = new World[20];
        int index = 8866;
        for (int i = 0; i < worlds.length; i++) {
            worlds[i] = new World();
            worlds[i].setId(8866 + i);
            worlds[i].setRandomNumber(9966 + i);
        }
        System.out.println(JsonConvert.root().convertTo(worlds));
    }
}
