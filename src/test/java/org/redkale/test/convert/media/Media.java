/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.media;

import java.util.*;

/** @author redkale */
public class Media implements java.io.Serializable {

    public enum Player {
        JAVA,
        FLASH;
    }

    private String uri;

    private String title; // Can be unset.

    private int width;

    private int height;

    private String format;

    private long duration;

    private long size;

    private int bitrate; // Can be unset.

    private List<String> persons;

    private Player player;

    private String copyright; // Can be unset.

    public Media() {}

    public Media(
            String uri,
            String title,
            int width,
            int height,
            String format,
            long duration,
            long size,
            int bitrate,
            boolean hasBitrate,
            List<String> persons,
            Player player,
            String copyright) {
        this.uri = uri;
        this.title = title;
        this.width = width;
        this.height = height;
        this.format = format;
        this.duration = duration;
        this.size = size;
        this.bitrate = bitrate;

        this.persons = persons;
        this.player = player;
        this.copyright = copyright;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Media media = (Media) o;
        if (bitrate != media.bitrate) return false;
        if (duration != media.duration) return false;
        if (height != media.height) return false;
        if (size != media.size) return false;
        if (width != media.width) return false;
        if (copyright != null ? !copyright.equals(media.copyright) : media.copyright != null) return false;
        if (format != null ? !format.equals(media.format) : media.format != null) return false;
        if (persons != null ? !persons.equals(media.persons) : media.persons != null) return false;
        if (player != media.player) return false;
        if (title != null ? !title.equals(media.title) : media.title != null) return false;
        if (uri != null ? !uri.equals(media.uri) : media.uri != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = uri != null ? uri.hashCode() : 0;
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + (format != null ? format.hashCode() : 0);
        result = 31 * result + (int) (duration ^ (duration >>> 32));
        result = 31 * result + (int) (size ^ (size >>> 32));
        result = 31 * result + bitrate;
        result = 31 * result + (persons != null ? persons.hashCode() : 0);
        result = 31 * result + (player != null ? player.hashCode() : 0);
        result = 31 * result + (copyright != null ? copyright.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Media ");
        sb.append("uri=").append((uri));
        sb.append(", title=").append((title));
        sb.append(", width=").append(width);
        sb.append(", height=").append(height);
        sb.append(", format=").append((format));
        sb.append(", duration=").append(duration);
        sb.append(", size=").append(size);
        sb.append(", bitrate=").append(String.valueOf(bitrate));
        sb.append(", persons=").append((persons));
        sb.append(", player=").append(player);
        sb.append(", copyright=").append((copyright));
        sb.append("]");
        return sb.toString();
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public void setPersons(List<String> persons) {
        this.persons = persons;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    public String getUri() {
        return uri;
    }

    public String getTitle() {
        return title;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getFormat() {
        return format;
    }

    public long getDuration() {
        return duration;
    }

    public long getSize() {
        return size;
    }

    public int getBitrate() {
        return bitrate;
    }

    public List<String> getPersons() {
        return persons;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCopyright() {
        return copyright;
    }
}
