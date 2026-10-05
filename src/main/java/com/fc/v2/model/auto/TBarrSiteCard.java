package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 改造进度卡对象 t_barr_site_card
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName(value = "t_barr_site_card", autoResultMap = true)
@ApiModel(value = "TBarrSiteCard", description = "改造进度卡")
public class TBarrSiteCard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 进展档：新录 */
    public static final int STATUS_NEW = 0;
    /** 进展档：已改齐 */
    public static final int STATUS_DONE = 1;
    /** 进展档：缺项 */
    public static final int STATUS_LACK = 2;

    /** 级数：新录（一级未走） */
    public static final int STAGE_NEW = 0;
    /** 级数：已改齐这一级已走完（档按处数自行分为已改齐/缺项） */
    public static final int STAGE_DONE = 1;
    /** 级数：收口到头 */
    public static final int STAGE_CLOSED = 2;

    /** 立卡来路：亲手立 */
    public static final int SOURCE_MANUAL = 0;
    /** 立卡来路：当月验收带出 */
    public static final int SOURCE_ACCEPT = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 改造进度卡代号（只由系统排号，本子自带号一概不认） */
    @TableField("card_no")
    @ApiModelProperty(value = "改造进度卡代号")
    private String cardNo;

    /** 挂在哪一处底册名下 */
    @TableField("arch_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "挂在哪一处底册名下")
    private Long archId;

    /** 底册代号（冗余自底册） */
    @TableField("arch_no")
    @ApiModelProperty(value = "底册代号（冗余自底册）")
    private String archNo;

    /** 底册全称（以底册为准，随卡冗余；改卡不改底） */
    @TableField("arch_name")
    @ApiModelProperty(value = "底册全称（以底册为准）")
    private String archName;

    /** 底册类别（小区楼组/村组院落/公共点位，以底册为准） */
    @TableField("arch_kind")
    @ApiModelProperty(value = "底册类别")
    private String archKind;

    /** 卡所在街代号（由底册属地落到街道） */
    @TableField("street_code")
    @ApiModelProperty(value = "卡所在街代号")
    private String streetCode;

    /** 卡所在街名 */
    @TableField("street_name")
    @ApiModelProperty(value = "卡所在街名")
    private String streetName;

    /** 本卡所属季（立卡时定死） */
    @TableField("season")
    @ApiModelProperty(value = "所属季")
    private String season;

    /** 点位或住户代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "点位或住户代号")
    private String siteNo;

    /** 应改处数 */
    @TableField("need_num")
    @ApiModelProperty(value = "应改处数")
    private Integer needNum;

    /** 已改处数 */
    @TableField("done_num")
    @ApiModelProperty(value = "已改处数")
    private Integer doneNum;

    /** 还差几处（轧出来，不手填） */
    @TableField("lack_num")
    @ApiModelProperty(value = "还差几处（轧出来，不手填）")
    private Integer lackNum;

    /** 随卡交来的改造要件 */
    @TableField("content")
    @ApiModelProperty(value = "随卡交来的改造要件")
    private String content;

    /** 立卡来路 0亲手立 1当月验收带出 */
    @TableField("source")
    @ApiModelProperty(value = "立卡来路 0亲手立 1当月验收带出")
    private Integer source;

    /** 已走完的级数 0新录 1已改齐(缺项) 2收口 */
    @TableField("stage")
    @ApiModelProperty(value = "已走完的级数")
    private Integer stage;

    /** 进展档 0新录 1已改齐 2缺项（按级数与处数系统数出） */
    @TableField("status")
    @ApiModelProperty(value = "进展情形 0新录 1已改齐 2缺项")
    private Integer status;

    /** 是否收口 0在走 1收口到头 */
    @TableField("closed")
    @ApiModelProperty(value = "是否收口 0在走 1收口")
    private Integer closed;

    /** 核验串（系统落；旧卡头一回被流程接住时补） */
    @TableField("verify_code")
    @ApiModelProperty(value = "核验串")
    private String verifyCode;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者（谁立的：递卡人登录身份自动落） */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "谁立的")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public Long getArchId() {
        return archId;
    }

    public void setArchId(Long archId) {
        this.archId = archId;
    }

    public String getArchNo() {
        return archNo;
    }

    public void setArchNo(String archNo) {
        this.archNo = archNo;
    }

    public String getArchName() {
        return archName;
    }

    public void setArchName(String archName) {
        this.archName = archName;
    }

    public String getArchKind() {
        return archKind;
    }

    public void setArchKind(String archKind) {
        this.archKind = archKind;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public void setStreetCode(String streetCode) {
        this.streetCode = streetCode;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getNeedNum() {
        return needNum;
    }

    public void setNeedNum(Integer needNum) {
        this.needNum = needNum;
    }

    public Integer getDoneNum() {
        return doneNum;
    }

    public void setDoneNum(Integer doneNum) {
        this.doneNum = doneNum;
    }

    public Integer getLackNum() {
        return lackNum;
    }

    public void setLackNum(Integer lackNum) {
        this.lackNum = lackNum;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getClosed() {
        return closed;
    }

    public void setClosed(Integer closed) {
        this.closed = closed;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
