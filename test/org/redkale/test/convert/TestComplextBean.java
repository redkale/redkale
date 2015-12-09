/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.List;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 */
public class TestComplextBean extends BasedEntity{
    
    @Id
    private int userid;

    private String chname = "";

    private int organid;

    private String photos = "";

    private String introvideourl = "";

    private String introduction = "";

    private String linkemail = "";

    private String telephone = "";

    private String skype = "";

    private String weixin = "";

    private String jego = "";

    private String city = "";   //'导师所在城市',

    private String states = ""; //导师所在洲名

    private String country = ""; //导师所在国家

    private int zones;// 导师所在时区

    private int lac;// 1东北部，2西部，4中西部，8南部

    private short hyproficient;

    private long createtime;

    private long updatetime;

    private int edutype;

    private int major;

    private short iecalevel;  //IECA会员或者认证：1、IECA会员一级 ；2、IECA会员二级 ；3、IECA会员三级 ；4、IECA认证一级；5、IECA认证二级；6、IECA认证三级；7、非会员非认证导师'

    private int workyear;

    private int rateservice;//'服务态度评分(总分)',

    private int ratemajor;//'咨询专业评分(总分)',

    private int ratenum;//'导师评分人次数(总评价人次)',

    private int successnum;//'服务过多少名申请学生',

    private int successrate; //'申请的成功率: 9500,表示成功率为95%',

    private int successhotrate; //'常青藤学校比例： 6000，表示比例为60%',

    private long hots;

    private int starlevel;

    private String degreestr = "";

    private String searchflag="";

    private String searchkey = "";

    @Transient
    private boolean baseinfoAll = true;//基本信息是否完整
    
    @Transient
    private List<UserMentorPrize> prizes;
    
    @Transient
    private UserInfo user;

    public List<UserMentorPrize> getPrizes() {
        return prizes;
    }

    public void setPrizes(List<UserMentorPrize> prizes) {
        this.prizes = prizes;
    }

    public boolean isBaseinfoAll() {
        return baseinfoAll;
    }

    public void setBaseinfoAll(boolean baseinfoAll) {
        this.baseinfoAll = baseinfoAll;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }
    
    public int getUserid() {
        return userid;
    }

    public void setUserid(int userid) {
        this.userid = userid;
    }

    public String getChname() {
        return chname;
    }

    public void setChname(String chname) {
        this.chname = chname;
    }

    public int getOrganid() {
        return organid;
    }

    public void setOrganid(int organid) {
        this.organid = organid;
    }

    public String getPhotos() {
        return photos;
    }

    public void setPhotos(String photos) {
        this.photos = photos;
    }

    public String getIntrovideourl() {
        return introvideourl;
    }

    public void setIntrovideourl(String introvideourl) {
        this.introvideourl = introvideourl;
    }

    public String getIntroduction() {
        return introduction;
    }

    public void setIntroduction(String introduction) {
        this.introduction = introduction;
    }

    public String getLinkemail() {
        return linkemail;
    }

    public void setLinkemail(String linkemail) {
        this.linkemail = linkemail;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getSkype() {
        return skype;
    }

    public void setSkype(String skype) {
        this.skype = skype;
    }

    public String getWeixin() {
        return weixin;
    }

    public void setWeixin(String weixin) {
        this.weixin = weixin;
    }

    public String getJego() {
        return jego;
    }

    public void setJego(String jego) {
        this.jego = jego;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStates() {
        return states;
    }

    public void setStates(String states) {
        this.states = states;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public int getZones() {
        return zones;
    }

    public void setZones(int zones) {
        this.zones = zones;
    }

    public int getLac() {
        return lac;
    }

    public void setLac(int lac) {
        this.lac = lac;
    }

    public short getHyproficient() {
        return hyproficient;
    }

    public void setHyproficient(short hyproficient) {
        this.hyproficient = hyproficient;
    }

    public long getCreatetime() {
        return createtime;
    }

    public void setCreatetime(long createtime) {
        this.createtime = createtime;
    }

    public long getUpdatetime() {
        return updatetime;
    }

    public void setUpdatetime(long updatetime) {
        this.updatetime = updatetime;
    }

    public int getEdutype() {
        return edutype;
    }

    public void setEdutype(int edutype) {
        this.edutype = edutype;
    }

    public int getMajor() {
        return major;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public short getIecalevel() {
        return iecalevel;
    }

    public void setIecalevel(short iecalevel) {
        this.iecalevel = iecalevel;
    }

    public int getWorkyear() {
        return workyear;
    }

    public void setWorkyear(int workyear) {
        this.workyear = workyear;
    }

    public int getRateservice() {
        return rateservice;
    }

    public void setRateservice(int rateservice) {
        this.rateservice = rateservice;
    }

    public int getRatemajor() {
        return ratemajor;
    }

    public void setRatemajor(int ratemajor) {
        this.ratemajor = ratemajor;
    }

    public int getRatenum() {
        return ratenum;
    }

    public void setRatenum(int ratenum) {
        this.ratenum = ratenum;
    }

    public int getSuccessnum() {
        return successnum;
    }

    public void setSuccessnum(int successnum) {
        this.successnum = successnum;
    }

    public int getSuccessrate() {
        return successrate;
    }

    public void setSuccessrate(int successrate) {
        this.successrate = successrate;
    }

    public int getSuccesshotrate() {
        return successhotrate;
    }

    public void setSuccesshotrate(int successhotrate) {
        this.successhotrate = successhotrate;
    }

    public long getHots() {
        return hots;
    }

    public void setHots(long hots) {
        this.hots = hots;
    }

    public int getStarlevel() {
        return starlevel;
    }

    public void setStarlevel(int starlevel) {
        this.starlevel = starlevel;
    }

    public String getDegreestr() {
        return degreestr;
    }

    public void setDegreestr(String degreestr) {
        this.degreestr = degreestr;
    }

    public String getSearchflag() {
        return searchflag;
    }

    public void setSearchflag(String searchflag) {
        this.searchflag = searchflag;
    }

    public String getSearchkey() {
        return searchkey;
    }

    public void setSearchkey(String searchkey) {
        this.searchkey = searchkey;
    }
    
}
