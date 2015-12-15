/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.apns;

import org.redkale.convert.json.JsonFactory;
import java.util.*;
import java.util.regex.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class ApnsPayload {

    private static final Pattern regex = Pattern.compile("\"");

    //----------------------- alert ---------------------------------
    private String alertTitle;

    private String alertBody;

    private String alertTitleLocKey;

    private String[] alertTitleLocArgs;

    private String alertActionLocKey;

    private String alertLocKey;

    private String[] alertLocArgs;

    private String alertLaunchImage;

    //--------------------------------------------------------
    private int contentAvailable;

    private String alert;

    private int badge;

    private String sound;

    private final Map<String, Object> attributes = new HashMap<>();

    public ApnsPayload() {
    }

    public ApnsPayload(String alert, int badge) {
        this.alert = alert;
        this.badge = badge;
    }

    public ApnsPayload(String title, String body, int badge) {
        this.alertTitle = title;
        this.alertBody = body;
        this.badge = badge;
    }

    public void putAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> map) {
        if (map != null) attributes.putAll(map);
    }

    @Override
    public String toString() {
        StringBuilder alertsb = new StringBuilder();
        if (alert != null) {
            alertsb.append('"').append(regex.matcher(alert).replaceAll("\\\"")).append('"');
        } else {
            alertsb.append('{');
            if (alertTitle != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"title\":\"").append(regex.matcher(alertTitle).replaceAll("\\\"")).append('"');
            }
            if (alertBody != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"body\":\"").append(regex.matcher(alertBody).replaceAll("\\\"")).append('"');
            }
            if (alertTitleLocKey != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"title-loc-key\":\"").append(regex.matcher(alertTitleLocKey).replaceAll("\\\"")).append('"');
            }
            if (alertTitleLocArgs != null && alertTitleLocArgs.length > 0) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"title-loc-args\":[");
                boolean first = true;
                for (String str : alertTitleLocArgs) {
                    if (!first) alertsb.append(',');
                    alertsb.append('"').append(regex.matcher(str).replaceAll("\\\"")).append('"');
                    first = false;
                }
                alertsb.append(']');
            }
            if (alertActionLocKey != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"action-loc-key\":\"").append(regex.matcher(alertActionLocKey).replaceAll("\\\"")).append('"');
            }
            if (alertLocKey != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"loc-key\":\"").append(regex.matcher(alertLocKey).replaceAll("\\\"")).append('"');
            }
            if (alertLocArgs != null && alertLocArgs.length > 0) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"loc-args\":[");
                boolean first = true;
                for (String str : alertLocArgs) {
                    if (!first) alertsb.append(',');
                    alertsb.append('"').append(regex.matcher(str).replaceAll("\\\"")).append('"');
                    first = false;
                }
                alertsb.append(']');
            }
            if (alertLaunchImage != null) {
                if (alertsb.length() > 1) alertsb.append(',');
                alertsb.append("\"launch-image\":\"").append(regex.matcher(alertLaunchImage).replaceAll("\\\"")).append('"');
            }
            alertsb.append('}');
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"aps\":{\"alert\":").append(alertsb);
        if (badge > 0) sb.append(",\"badge\":").append(badge);
        if (contentAvailable > 0) sb.append(",\"content-available\":").append(contentAvailable);
        if (sound != null) sb.append(",\"sound\":\"").append(sound).append('"');
        sb.append("}");
        if (attributes.isEmpty()) {
            sb.append('}');
        } else {
            sb.append(',').append(JsonFactory.root().getConvert().convertTo(attributes).substring(1));
        }
        return sb.toString();
    }

    public String getAlertTitle() {
        return alertTitle;
    }

    public void setAlertTitle(String alertTitle) {
        this.alertTitle = alertTitle;
    }

    public String getAlertBody() {
        return alertBody;
    }

    public void setAlertBody(String alertBody) {
        this.alertBody = alertBody;
    }

    public String getAlertTitleLocKey() {
        return alertTitleLocKey;
    }

    public void setAlertTitleLocKey(String alertTitleLocKey) {
        this.alertTitleLocKey = alertTitleLocKey;
    }

    public String[] getAlertTitleLocArgs() {
        return alertTitleLocArgs;
    }

    public void setAlertTitleLocArgs(String[] alertTitleLocArgs) {
        this.alertTitleLocArgs = alertTitleLocArgs;
    }

    public String getAlertActionLocKey() {
        return alertActionLocKey;
    }

    public void setAlertActionLocKey(String alertActionLocKey) {
        this.alertActionLocKey = alertActionLocKey;
    }

    public String getAlertLocKey() {
        return alertLocKey;
    }

    public void setAlertLocKey(String alertLocKey) {
        this.alertLocKey = alertLocKey;
    }

    public String[] getAlertLocArgs() {
        return alertLocArgs;
    }

    public void setAlertLocArgs(String[] alertLocArgs) {
        this.alertLocArgs = alertLocArgs;
    }

    public String getAlertLaunchImage() {
        return alertLaunchImage;
    }

    public void setAlertLaunchImage(String alertLaunchImage) {
        this.alertLaunchImage = alertLaunchImage;
    }

    public int getContentAvailable() {
        return contentAvailable;
    }

    public void setContentAvailable(int contentAvailable) {
        this.contentAvailable = contentAvailable;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }

    public int getBadge() {
        return badge;
    }

    public void setBadge(int badge) {
        this.badge = badge;
    }

    public String getSound() {
        return sound;
    }

    public void setSound(String sound) {
        this.sound = sound;
    }

}
