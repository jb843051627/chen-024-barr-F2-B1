package com.fc.v2.model.auto;

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
 * 改造进度卡流转留痕对象 t_barr_card_log
 * 一记一行：推进/回退/收口都在此落笔；回退不抹旧痕，后补之笔另起一行，不与旧笔并排。
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_barr_card_log")
@ApiModel(value = "TBarrCardLog", description = "改造进度卡流转留痕")
public class TBarrCardLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 立卡 */
    public static final String ACTION_CREATE = "create";
    /** 推进一级 */
    public static final String ACTION_ADVANCE = "advance";
    /** 退回一步重来（旧笔留底） */
    public static final String ACTION_REVERT = "revert";
    /** 收口到头 */
    public static final String ACTION_CLOSE = "close";
    /** 改口编辑（已改处数/挂靠街等） */
    public static final String ACTION_EDIT = "edit";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 改造进度卡主键 */
    @TableField("card_id")
    private Long cardId;

    /** 当时卡代号 */
    @TableField("card_no")
    private String cardNo;

    /** 动作 create/advance/revert/close/edit */
    @TableField("action")
    private String action;

    /** 动前级数 */
    @TableField("from_stage")
    private Integer fromStage;

    /** 动后级数 */
    @TableField("to_stage")
    private Integer toStage;

    /** 这一步落下的一笔（一格一记） */
    @TableField("step_note")
    private String stepNote;

    /** 经办人（登录身份） */
    @TableField("operator")
    private String operator;

    /** 动作时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getFromStage() {
        return fromStage;
    }

    public void setFromStage(Integer fromStage) {
        this.fromStage = fromStage;
    }

    public Integer getToStage() {
        return toStage;
    }

    public void setToStage(Integer toStage) {
        this.toStage = toStage;
    }

    public String getStepNote() {
        return stepNote;
    }

    public void setStepNote(String stepNote) {
        this.stepNote = stepNote;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
