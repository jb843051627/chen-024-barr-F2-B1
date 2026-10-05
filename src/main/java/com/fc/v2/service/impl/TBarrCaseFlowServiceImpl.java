package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrCaseFlowMapper;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.ITBarrCaseFlowService;

/**
 * 一户一档改造卷宗 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrCaseFlowServiceImpl implements ITBarrCaseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TBarrCaseFlowMapper barrCaseFlowMapper;

    @Override
    public TBarrCaseFlow selectTBarrCaseFlowById(Long id) {
        return this.barrCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TBarrCaseFlow> selectTBarrCaseFlowList(QueryWrapper<TBarrCaseFlow> queryWrapper) {
        return this.barrCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TBarrCaseFlow advance(Long id, String remark) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.barrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TBarrCaseFlow rollback(Long id, String remark) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.barrCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.barrCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.barrCaseFlowMapper.deleteById(id) > 0;
    }

}
