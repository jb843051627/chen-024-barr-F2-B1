package com.fc.v2.model.custom;

/**
 * 改造进度卡寻卡条件。三个头：卡代号、点位代号、底册类别；
 * 整段对上算找着，只对上开头那几笔也算（等值或前缀）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class SiteCardQuery {

    /** 卡代号 */
    private String cardNo;

    /** 点位或住户代号 */
    private String siteNo;

    /** 底册类别 */
    private String archKind;

    /** 限定哪一季（可空） */
    private String season;

    /** 进展情形 0新录 1已改齐 2缺项；另有 -1 表示只看在走（未收口），-2 只看已收口 */
    private Integer status;

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getArchKind() {
        return archKind;
    }

    public void setArchKind(String archKind) {
        this.archKind = archKind;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
