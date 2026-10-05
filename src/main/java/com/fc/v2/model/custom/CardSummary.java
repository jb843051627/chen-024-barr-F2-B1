package com.fc.v2.model.custom;

import java.util.List;

/**
 * 卡面三种摆法（按街道 / 按底册类别 / 按进展情形）一次回库算齐的汇总。
 * 三组户数与总数同一次计算得出，页面只回显，不许拿当前页显示的那份来顶。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CardSummary {

    /** 分组里的一格：名字 + 户数 */
    public static class GroupCount {
        private String name;
        private long count;

        public GroupCount() {
        }

        public GroupCount(String name, long count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    /** 总户数（= 三组各组户数之和） */
    private long total;

    /** 按街道摆 */
    private List<GroupCount> byStreet;

    /** 按底册类别摆 */
    private List<GroupCount> byArchKind;

    /** 按进展情形摆（新录/已改齐/缺项） */
    private List<GroupCount> byStatus;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<GroupCount> getByStreet() {
        return byStreet;
    }

    public void setByStreet(List<GroupCount> byStreet) {
        this.byStreet = byStreet;
    }

    public List<GroupCount> getByArchKind() {
        return byArchKind;
    }

    public void setByArchKind(List<GroupCount> byArchKind) {
        this.byArchKind = byArchKind;
    }

    public List<GroupCount> getByStatus() {
        return byStatus;
    }

    public void setByStatus(List<GroupCount> byStatus) {
        this.byStatus = byStatus;
    }
}
