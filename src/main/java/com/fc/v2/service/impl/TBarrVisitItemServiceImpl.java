package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.service.ITBarrVisitItemService;

/**
 * 质保与养护复检条目 Service业务层处理（scheduling-job 形状：周期执行）。
 * 可处理三件套：还没递出的、没撤下的、正落在窗口里的——
 * 窗口是「该上门那一日的止点时刻」往前推「最多许提前几日」到止点这一刻，
 * 早一天不抢跑，过了止点不补递。
 * 条件给不全（没问时刻、条目缺止点、提前量空着）一律落兜底：回空名单、回 0，
 * 调用方拿到的永远是一句话，不是一个异常。
 * 时段比较全在 Java 里走（与定档线同一把尺）：Date 不绑进 SQL，
 * 免得 JDBC 的 serverTimezone 与本机时区不对称把边界时刻整体带偏。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrVisitItemServiceImpl implements ITBarrVisitItemService {

    /** 一日的毫秒数：提前量按日折算 */
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TBarrVisitItemMapper barrVisitItemMapper;

    @Override
    public TBarrVisitItem selectTBarrVisitItemById(Long id) {
        return this.barrVisitItemMapper.selectById(id);
    }

    @Override
    public List<TBarrVisitItem> listDue(Date at) {
        List<TBarrVisitItem> due = new ArrayList<TBarrVisitItem>();
        if (at == null) {
            // 没问那一刻：回空名单，不抛异常
            return due;
        }
        List<TBarrVisitItem> all = this.barrVisitItemMapper.selectList(
                new QueryWrapper<TBarrVisitItem>().eq("del_flag", 0));
        if (all == null) {
            return due;
        }
        for (TBarrVisitItem r : all) {
            if (r != null && inWindow(r, at)) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        int ok = 0;
        for (TBarrVisitItem r : listDue(at)) {
            try {
                r.setStatus(STATUS_DONE);
                if (this.barrVisitItemMapper.updateById(r) > 0) {
                    ok++;
                }
            } catch (RuntimeException e) {
                // 单条失败跳过继续，不拖垮这一趟
            }
        }
        return ok;
    }

    /**
     * 这一刻落不落在窗口里：只认还搁着的条目；
     * 止点空着的条目请不动；提前量空着按 0 日兜底（只认止点那一刻）；
     * 窗口＝[止点 − 提前量, 止点]，两端都含。
     */
    private boolean inWindow(TBarrVisitItem r, Date at) {
        if (r.getDelFlag() != null && r.getDelFlag() == 1) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_WAIT) {
            return false;
        }
        if (r.getDueAt() == null) {
            return false;
        }
        BigDecimal early = r.getAmount() == null ? BigDecimal.ZERO : r.getAmount();
        long from = r.getDueAt().getTime() - early.multiply(BigDecimal.valueOf(DAY_MS)).longValue();
        long t = at.getTime();
        return t >= from && t <= r.getDueAt().getTime();
    }
}
