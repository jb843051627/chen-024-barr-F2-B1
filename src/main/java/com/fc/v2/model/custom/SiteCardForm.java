package com.fc.v2.model.custom;

/**
 * 改造进度卡写入表单（立卡 / 改口编辑共用；亲手立与当月验收带出也共用）。
 * 处数一律用字符串接，为的是把"带零头/负数/空着"当栏级毛病讲清楚，而不是吃一个类型转换异常。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class SiteCardForm {

    /** 主键（改口编辑才有） */
    private String id;

    /** 挂在哪一处底册名下（底册主键） */
    private String archId;

    /** 点位或住户代号 */
    private String siteNo;

    /** 应改处数（原样字符串，服务里逐栏验） */
    private String needNum;

    /** 已改处数（原样字符串；空着按 0） */
    private String doneNum;

    /** 随卡交来的改造要件 */
    private String content;

    /** 立卡来路 0亲手立 1当月验收带出 */
    private String source;

    /** 备注 */
    private String remark;

    /** 推进/回退/收口时随步落下的一笔 */
    private String note;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getArchId() {
        return archId;
    }

    public void setArchId(String archId) {
        this.archId = archId;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getNeedNum() {
        return needNum;
    }

    public void setNeedNum(String needNum) {
        this.needNum = needNum;
    }

    public String getDoneNum() {
        return doneNum;
    }

    public void setDoneNum(String doneNum) {
        this.doneNum = doneNum;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
