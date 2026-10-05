package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrCaseFlowMapper;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.ITBarrCaseFlowService;

/**
 * 一户一档改造卷宗 Service业务层处理（state-machine 形状：单据流转）。
 * 卷宗走到哪一格只有 stage 一个出处：status 永远由 stage 当场派生，不许两处各记各的；
 * 已封住（结清）之后的每条改动路——推进、回退、改记要、删除——共用同一道拦截，
 * 不允许哪条口子漏拦。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrCaseFlowServiceImpl implements ITBarrCaseFlowService {

    /** 头一格：核卷（未起卷宗也落在此格） */
    private static final int STAGE_FIRST = 0;
    /** 末一格：结清。走到此格即封住 */
    private static final int STAGE_TERMINAL = 3;
    /** 卷宗情形：0未起 1在办 2已封住——只由 stage 派生 */
    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TBarrCaseFlowMapper barrCaseFlowMapper;

    @Override
    public TBarrCaseFlow selectTBarrCaseFlowById(Long id) {
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectById(id);
        return r == null ? null : normalize(r);
    }

    @Override
    public List<TBarrCaseFlow> selectTBarrCaseFlowList(QueryWrapper<TBarrCaseFlow> queryWrapper) {
        // 已撤下的卷宗不上台账：码层传空条件来也守住这一道，与软删口径一致
        QueryWrapper<TBarrCaseFlow> w = queryWrapper == null ? new QueryWrapper<TBarrCaseFlow>() : queryWrapper;
        w.eq("del_flag", 0);
        List<TBarrCaseFlow> list = this.barrCaseFlowMapper.selectList(w);
        for (TBarrCaseFlow r : list) {
            normalize(r);
        }
        return list;
    }

    @Override
    public TBarrCaseFlow advance(Long id, String remark) {
        TBarrCaseFlow r = loadMutable(id);
        if (r == null) {
            return null;
        }
        int stage = nz(r.getStage());
        if (stage >= STAGE_TERMINAL) {
            // 走到头没有下一格；封住的卷宗在 loadMutable 已挡，这道是给末格再推兜底
            return null;
        }
        r.setStage(stage + 1);
        r.setStatus(deriveStatus(r.getStage()));
        r.setLastAction(remark);
        appendRecord(r, r.getStage(), remark);
        this.barrCaseFlowMapper.updateById(r);
        return normalize(r);
    }

    @Override
    public TBarrCaseFlow rollback(Long id, String remark) {
        TBarrCaseFlow r = loadMutable(id);
        if (r == null) {
            return null;
        }
        int stage = nz(r.getStage());
        if (stage <= STAGE_FIRST) {
            // 头一格前面没有路；不许一脚跨回零格抹掉中间走过的格
            return null;
        }
        r.setStage(stage - 1);
        r.setStatus(deriveStatus(r.getStage()));
        r.setLastAction(remark);
        appendRecord(r, r.getStage(), remark);
        this.barrCaseFlowMapper.updateById(r);
        return normalize(r);
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TBarrCaseFlow r = loadMutable(id);
        if (r == null) {
            return false;
        }
        if (remark == null) {
            return false;
        }
        appendRecord(r, nz(r.getStage()), remark);
        r.setLastAction(remark);
        return this.barrCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        // 已封住的卷宗在 loadMutable 挡死：结清的单子删不得；删除只打标记，册子上翻得着
        TBarrCaseFlow r = loadMutable(id);
        if (r == null) {
            return false;
        }
        r.setDelFlag(1);
        return this.barrCaseFlowMapper.updateById(r) > 0;
    }

    // ============================ 内部：单一出处 ============================

    /**
     * 每条改动路共用的装载：册子里翻不着的、已撤下的、已封住结清的，一律请不动。
     * 归档拦截只此一处，推进/回退/改记要/删除谁都绕不过。
     */
    private TBarrCaseFlow loadMutable(Long id) {
        if (id == null) {
            return null;
        }
        TBarrCaseFlow r = this.barrCaseFlowMapper.selectOne(new QueryWrapper<TBarrCaseFlow>()
                .eq("id", id)
                .eq("del_flag", 0));
        if (r == null) {
            return null;
        }
        normalize(r);
        if (nz(r.getStage()) >= STAGE_TERMINAL) {
            return null;
        }
        return r;
    }

    /** 情形只由格次派生：头一格未起、中间在办、结清封住——status 与 stage 永远对得上 */
    private int deriveStatus(int stage) {
        if (stage >= STAGE_TERMINAL) {
            return STATUS_TERMINAL;
        }
        if (stage > STAGE_FIRST) {
            return STATUS_ACTIVE;
        }
        return STATUS_NOT_STARTED;
    }

    /** 回显/落库前同一把接住：格次空着按头一格、删除标记空着按在册，情形当场重数 */
    private TBarrCaseFlow normalize(TBarrCaseFlow r) {
        if (r.getStage() == null) {
            r.setStage(STAGE_FIRST);
        }
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        r.setStatus(deriveStatus(r.getStage()));
        return r;
    }

    /** 一格一记：新记要接在旧记要后头，不抹前面各格落下的话 */
    private void appendRecord(TBarrCaseFlow r, int stage, String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            return;
        }
        String line = "[" + stage + "]" + remark.trim();
        String old = r.getContent();
        r.setContent(old == null || old.trim().isEmpty() ? line : old + "\n" + line);
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
