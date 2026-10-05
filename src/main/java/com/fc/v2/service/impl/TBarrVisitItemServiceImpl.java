package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.service.ITBarrVisitItemService;

/**
 * 质保与养护复检条目 Service业务层处理（scheduling-job 形状：周期执行）。
 * 时段比较全在 Java 里走（册子小，整册捞出再逐条问）：
 * JDBC 的 serverTimezone 与本机时区不对称，把 Date 绑进 SQL 或转墙钟字符串都会整体偏移，
 * 边界判定在"恰好到期/开窗当日"这类用例上随机错档（实测踩到），干脆不让 SQL 碰时段。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrVisitItemServiceImpl implements ITBarrVisitItemService {

    /** 提前量统一夹在 2~5 日之间：册子上的"最多许提前几日"只认这一带 */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;
    /** 条目情形 0搁着 1已递出 2递不动 */
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;

    /** 同刻并列只许一个先后：止点早的先递，止点齐平比主键 */
    private static final Comparator<TBarrVisitItem> DUE_THEN_ID = new Comparator<TBarrVisitItem>() {
        @Override
        public int compare(TBarrVisitItem a, TBarrVisitItem b) {
            int byDue = a.getDueAt().compareTo(b.getDueAt());
            if (byDue != 0) {
                return byDue;
            }
            long ia = a.getId() == null ? 0L : a.getId();
            long ib = b.getId() == null ? 0L : b.getId();
            return Long.compare(ia, ib);
        }
    };

    @javax.annotation.Resource
    private TBarrVisitItemMapper barrVisitItemMapper;

    @Override
    public TBarrVisitItem selectTBarrVisitItemById(Long id) {
        return this.barrVisitItemMapper.selectById(id);
    }

    @Override
    public List<TBarrVisitItem> listDue(Date at) {
        List<TBarrVisitItem> due = new ArrayList<TBarrVisitItem>();
        // 时刻给不全：回空册这条兜底值，不甩异常
        if (at == null) {
            return due;
        }
        for (TBarrVisitItem r : registry()) {
            if (isDue(r, at)) {
                due.add(r);
            }
        }
        Collections.sort(due, DUE_THEN_ID);
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TBarrVisitItem> due = listDue(at);
        int ok = 0;
        for (TBarrVisitItem r : due) {
            try {
                r.setStatus(STATUS_DONE);
                int rows = this.barrVisitItemMapper.updateById(r);
                // 落了库才算递出；单条递不动跳过继续，不拖垮整趟
                if (rows > 0) {
                    ok++;
                }
            } catch (RuntimeException e) {
                // 这一条递不动，接着递下一条
            }
        }
        return ok;
    }

    // ============================ 内部 ============================

    /** 整册捞出在册条目（已撤下的不进执行册） */
    private List<TBarrVisitItem> registry() {
        return this.barrVisitItemMapper.selectList(new QueryWrapper<TBarrVisitItem>()
                .eq("del_flag", 0));
    }

    /**
     * 那一刻该不该上门：搁着没递过的才算；止点时刻、提前量给不全的不硬凑。
     * 已到最早可上门日（止点往前推提前量，压线收进）、且止点还没走过（压线也收进）。
     */
    private boolean isDue(TBarrVisitItem r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() == 1) {
            // 已撤下的不进执行册（与册子查询同一口径，再守一道）
            return false;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_WAIT) {
            // 已递出、递不动的都不再进册
            return false;
        }
        Date dueAt = r.getDueAt();
        if (dueAt == null) {
            return false;
        }
        Integer earlyDays = earlyDays(r.getAmount());
        if (earlyDays == null) {
            return false;
        }
        Date windowStart = new Date(dueAt.getTime() - earlyDays.longValue() * MILLIS_PER_DAY);
        // 压线收进窗内：开窗当日、止点当日都算；早于开窗或走过止点都不递
        return at.compareTo(windowStart) >= 0 && at.compareTo(dueAt) <= 0;
    }

    /** 提前量夹到 2~5 日；空着或不是正数回 null（这条请不动，但不抛异常） */
    private Integer earlyDays(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        int days = amount.setScale(0, java.math.RoundingMode.DOWN).intValue();
        if (days < WIN_FROM) {
            return WIN_FROM;
        }
        if (days > WIN_TO) {
            return WIN_TO;
        }
        return days;
    }
}
