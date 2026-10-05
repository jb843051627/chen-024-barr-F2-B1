package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.Date;

/**
 * 定档结论：那一趟求值当下回的一整份话。
 * 档、线代号、起用那一日从同一条在位线上同一趟取出——
 * 钉进户卡的一对（线代号、起用那一日）只能从这里抄，抄邻条的、抄上一季的都算错。
 *
 * @author fuce
 * @date 2026-10-04
 */
public class TierVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 不给档：那一刻请不出在位线，或三条分界一条都够不上 */
    public static final int TIER_NONE = 0;
    /** 基础档 */
    public static final int TIER_BASIC = 1;
    /** 提升档 */
    public static final int TIER_LIFT = 2;
    /** 重点档 */
    public static final int TIER_KEY = 3;

    /** 不给档时结论栏落的固定话 */
    public static final String NO_LINE = "无可用线";

    /** 档 0无可用线 1基础 2提升 3重点 */
    private int tier;

    /** 结论栏落的话：档名；不给档时落"无可用线" */
    private String conclusion;

    /** 钉进户卡的线代号；无可用线时留空，不许就着隔壁那条的数硬凑 */
    private String ruleCode;

    /** 钉进户卡的起用那一日；无可用线时留空 */
    private Date effStart;

    /** 不给档的那一份话：结论栏落"无可用线"，线代号与起用那一日两栏一并留空 */
    public static TierVerdict noLine() {
        TierVerdict v = new TierVerdict();
        v.setTier(TIER_NONE);
        v.setConclusion(NO_LINE);
        return v;
    }

    /** 给档的那一份话：档名连同说话那条线的代号与起用那一日 */
    public static TierVerdict of(int tier, String ruleCode, Date effStart) {
        TierVerdict v = new TierVerdict();
        v.setTier(tier);
        v.setConclusion(tierName(tier));
        v.setRuleCode(ruleCode);
        v.setEffStart(effStart);
        return v;
    }

    /** 档的名：0 落"无可用线" */
    public static String tierName(int tier) {
        switch (tier) {
            case TIER_BASIC:
                return "基础";
            case TIER_LIFT:
                return "提升";
            case TIER_KEY:
                return "重点";
            default:
                return NO_LINE;
        }
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }
}
