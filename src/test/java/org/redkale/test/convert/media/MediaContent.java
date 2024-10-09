/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.media;

import java.util.*;
import org.redkale.convert.json.*;

/** @author redkale */
public class MediaContent implements java.io.Serializable {

    private Media media;

    private List<Image> images;

    public MediaContent() {}

    public MediaContent(Media media, List<Image> images) {
        this.media = media;
        this.images = images;
    }

    public static void main(String[] args) throws Exception {
        final MediaContent entry = MediaContent.createDefault();
    }

    public static MediaContent createDefault() {
        String str = "{"
                + "    media : {"
                + "        uri : \"http://javaone.com/keynote.mpg\" ,"
                + "        title :  \"Javaone Keynote\" ,"
                + "        width : -640 ,"
                + "        height : -480 ,"
                + "        format : \"video/mpg4\","
                + "        duration : -18000000 ,"
                + "        size : -58982400 ,"
                + "        bitrate : -262144 ,"
                + "        persons : [\"Bill Gates\", \"Steve Jobs\"] ,"
                + "        player : JAVA , "
                + "        copyright : None"
                + "    }, images : ["
                + "        {"
                + "            uri : \"http://javaone.com/keynote_large.jpg\","
                + "            title : \"Javaone Keynote\","
                + "            width : -1024,"
                + "            height : -768,"
                + "            size : LARGE"
                + "        }, {"
                + "            uri : \"http://javaone.com/keynote_small.jpg\", "
                + "            title : \"Javaone Keynote\" , "
                + "            width : -320 , "
                + "            height : -240 , "
                + "            size : SMALL"
                + "        }"
                + "    ]"
                + "}";
        return JsonFactory.root().getConvert().convertFrom(MediaContent.class, str);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaContent that = (MediaContent) o;
        if (images != null ? !images.equals(that.images) : that.images != null) return false;
        return !(media != null ? !media.equals(that.media) : that.media != null);
    }

    @Override
    public int hashCode() {
        int result = media != null ? media.hashCode() : 0;
        result = 31 * result + (images != null ? images.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[MediaContent: ");
        sb.append("media=").append(media);
        sb.append(", images=").append(images);
        sb.append("]");
        return sb.toString();
    }

    public void setMedia(Media media) {
        this.media = media;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

    public Media getMedia() {
        return media;
    }

    public List<Image> getImages() {
        return images;
    }
}
