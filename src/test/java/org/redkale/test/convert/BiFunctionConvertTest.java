/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.ConvertField;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Attribute;

/** @author zhangjx */
public class BiFunctionConvertTest {

    public static class GamePlayer {

        public int userid;

        public String username;

        public int[] cards;
    }

    public static class GameTable {

        public int tableid;

        public GamePlayer[] players;
    }

    @Test
    public void run() throws Throwable {
        GamePlayer player1 = new GamePlayer();
        player1.userid = 1;
        player1.username = "玩家1";
        player1.cards = new int[] {11, 12, 13, 14, 15};
        GamePlayer player2 = new GamePlayer();
        player2.userid = 2;
        player2.username = "玩家2";
        player2.cards = new int[] {21, 22, 23, 24, 25};
        GamePlayer player3 = new GamePlayer();
        player3.userid = 3;
        player3.username = "玩家3";
        player3.cards = new int[] {31, 32, 33, 34, 35};
        GameTable table = new GameTable();
        table.tableid = 100;
        table.players = new GamePlayer[] {player1, player2, player3};
        JsonConvert convert1 = JsonConvert.root();
        System.out.println(convert1.convertTo(table));
        JsonConvert convert2 = convert1.newConvert(
                (Attribute t, Object u) -> {
                    if (t.field().equals("cards") && u instanceof GamePlayer) {
                        int userid = ((GamePlayer) u).userid;
                        if (userid == 3) return null; // 玩家3的cards不输出
                        return t.get(u);
                    }
                    return t.get(u);
                },
                (Object u) -> {
                    if (table != u) return null;
                    // return new ConvertField[]{new ConvertField("extcol1", 30), new ConvertField("extcol2", "扩展字段值")};
                    return ConvertField.ofArray("extcol1", 30, "extcol2", "扩展字段值");
                });
        System.out.println(convert2.convertTo(table));
        Assertions.assertEquals(
                "{\"players\":[{\"cards\":[11,12,13,14,15],\"userid\":1,\"username\":\"玩家1\"},{\"cards\":[21,22,23,24,25],\"userid\":2,\"username\":\"玩家2\"},{\"userid\":3,\"username\":\"玩家3\"}],\"tableid\":100,\"extcol1\":30,\"extcol2\":\"扩展字段值\"}",
                convert2.convertTo(table));
        // {"players":[{"cards":[11,12,13,14,15],"userid":1,"username":"玩家1"},{"cards":[21,22,23,24,25],"userid":2,"username":"玩家2"},{"cards":[31,32,33,34,35],"userid":3,"username":"玩家3"}],"tableid":100}
        // {"players":[{"cards":[11,12,13,14,15],"userid":1,"username":"玩家1"},{"cards":[21,22,23,24,25],"userid":2,"username":"玩家2"},{"userid":3,"username":"玩家3"}],"tableid":100,"extcol1":30,"extcol2":"扩展字段值"}
    }
}
