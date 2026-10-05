package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrCaseFlowMapper;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.ITBarrCaseFlowService;

/**
 * 一户一档改造卷宗 Service业务层处理（state-machine 形状：单据流转）。
 * 挪格一次只挪一格，不许跳着迈；卷宗情形（未起/在办/已封住）不由人手填，
 * 全由当前格次一处数出——单据上的状态与它走到哪个环节，两处不许各记各的。
 * 已封住的卷宗：挪不动、退不动、字改不动、也撤不下，四条路同一处拦。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrCaseFlowServiceImpl implements ITBarrCaseFlowService {

    /** 格次上限：0核卷 1评估 2验收 3结清 */
    private static final int MAX_STAGE = 3;
    private static final int STATUS_NEW = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TBarrCaseFlowMapper barrCaseFlowMapper;

    @Override
    public TBarrCaseFlow selectTBarrCaseFlowById(Long id) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return null;
        }
        return normalize(r);
    }

    @Override
    public List<TBarrCaseFlow> selectTBarrCaseFlowList(QueryWrapper<TBarrCaseFlow> queryWrapper) {
        QueryWrapper<TBarrCaseFlow> wrapper = queryWrapper == null
                ? new QueryWrapper<TBarrCaseFlow>() : queryWrapper;
        wrapper.eq("del_flag", 0);
        List<TBarrCaseFlow> list = this.barrCaseFlowMapper.selectList(wrapper);
        List<TBarrCaseFlow> out = new java.util.ArrayList<TBarrCaseFlow>();
        for (TBarrCaseFlow r : list) {
            // 已撤的不进台账（SQL 挡一道，册子这边再挡一道，两道同一口径）
            if (r != null && (r.getDelFlag() == null || r.getDelFlag() != 1)) {
                out.add(normalize(r));
            }
        }
        return out;
    }

    @Override
    public TBarrCaseFlow advance(Long id, String remark) {
        TBarrCaseFlow r = loadAlive(id);
        if (r == null || isTerminal(r)) {
            // 已封住的卷宗挪不动
            return null;
        }
        int stage = stageOf(r);
        // 一次只挪一格，逐级走不许跳
        r.setStage(Math.min(stage + 1, MAX_STAGE));
        r.setStatus(deriveStatus(r.getStage()));
        r.setLastAction(remark);
        this.barrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TBarrCaseFlow rollback(Long id, String remark) {
        TBarrCaseFlow r = loadAlive(id);
        if (r == null || isTerminal(r)) {
            // 已封住的卷宗退不动
            return null;
        }
        int stage = stageOf(r);
        if (stage <= 0) {
            // 还在头一格，前面没有可退回的步
            return null;
        }
        // 一次只退一格，不一竿子清回零
        r.setStage(stage - 1);
        r.setStatus(deriveStatus(r.getStage()));
        r.setLastAction(remark);
        this.barrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TBarrCaseFlow r = loadAlive(id);
        if (r == null || isTerminal(r)) {
            // 已办结不得再改：与挪格/回退/撤单同一处拦，不留漏口
            return false;
        }
        r.setContent(remark);
        return this.barrCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TBarrCaseFlow r = loadAlive(id);
        if (r == null || isTerminal(r)) {
            // 已办结不得删除
            return false;
        }
        // 撤卷只销标记不抹行：册里翻得着，屏上不再列
        r.setDelFlag(1);
        return this.barrCaseFlowMapper.updateById(r) > 0;
    }

    // ============================ 内部：状态只从格次数出 ============================

    private TBarrCaseFlow loadAlive(Long id) {
        if (id == null) {
            return null;
        }
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return null;
        }
        return r;
    }

    private int stageOf(TBarrCaseFlow r) {
        return r.getStage() == null ? 0 : r.getStage();
    }

    /** 封没封住只看格次：走到结清即已封住，与情形栏同一出处 */
    private boolean isTerminal(TBarrCaseFlow r) {
        return deriveStatus(stageOf(r)) == STATUS_TERMINAL;
    }

    /** 情形由格次一处数出：0格未起、中间在办、走到结清即封住——全系统只此一把尺 */
    private int deriveStatus(int stage) {
        if (stage >= MAX_STAGE) {
            return STATUS_TERMINAL;
        }
        return stage <= 0 ? STATUS_NEW : STATUS_ACTIVE;
    }

    /** 回显对齐：情形栏一律按格次重数一遍，旧卷宗两处记岔了的以格次为准 */
    private TBarrCaseFlow normalize(TBarrCaseFlow r) {
        r.setStatus(deriveStatus(stageOf(r)));
        return r;
    }
}
