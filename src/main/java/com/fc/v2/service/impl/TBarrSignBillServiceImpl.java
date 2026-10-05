package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TBarrSignBillMapper;
import com.fc.v2.model.auto.TBarrSignBill;
import com.fc.v2.service.ITBarrSignBillService;

/**
 * 联户验收签认单 Service业务层处理（approval-chain 形状：多阶段签批）。
 * 一口一口签：本口算齐办法是"一名即签"还是"两名须同判"，看本口应签人数点齐才挪口；
 * 末口点齐整单落"已点齐"。点齐、压回都是到了头的情形——
 * 到了头的单子：签不动、否不动、退不动，三条路同一处拦，不留漏口。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrSignBillServiceImpl implements ITBarrSignBillService {

    /** 末口：0住户 1到场两方 2两级残联 */
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TBarrSignBillMapper barrSignBillMapper;

    @Override
    public TBarrSignBill selectTBarrSignBillById(Long id) {
        return this.barrSignBillMapper.selectById(id);
    }

    @Override
    public TBarrSignBill approve(Long id, String approver, String comment) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TBarrSignBill r = loadAlive(id);
        if (r == null || statusOf(r) != STATUS_RUNNING) {
            // 已点齐/已压回的单子签不动
            return null;
        }
        int signed = signCountOf(r) + 1;
        if (signed >= requiredSigns(r)) {
            // 本口点齐：已签清零挪下一口；末口点齐整单落已点齐
            r.setSignCount(0);
            int node = nodeOf(r);
            if (node >= MAX_NODE) {
                r.setStatus(STATUS_PASS);
            } else {
                r.setNodeNo(node + 1);
                r.setStatus(STATUS_RUNNING);
            }
        } else {
            r.setSignCount(signed);
            r.setStatus(STATUS_RUNNING);
        }
        this.barrSignBillMapper.updateById(r);
        return r;
    }

    @Override
    public TBarrSignBill reject(Long id, String approver, String comment) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TBarrSignBill r = loadAlive(id);
        if (r == null || statusOf(r) != STATUS_RUNNING) {
            // 已点齐/已压回的单子否不动
            return null;
        }
        // 否决即压回：整单落定，不再在签
        r.setStatus(STATUS_VETO);
        this.barrSignBillMapper.updateById(r);
        return r;
    }

    @Override
    public TBarrSignBill rollback(Long id, String comment) {
        TBarrSignBill r = loadAlive(id);
        if (r == null || statusOf(r) != STATUS_RUNNING) {
            // 已点齐/已压回的单子退不动
            return null;
        }
        int node = nodeOf(r);
        if (node <= 0) {
            // 还在头一口，前面没有可退回的环节
            return null;
        }
        // 退回上一环节：本口已签的票清零，回上一口重签
        r.setNodeNo(node - 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.barrSignBillMapper.updateById(r);
        return r;
    }

    // ============================ 内部 ============================

    private TBarrSignBill loadAlive(Long id) {
        if (id == null) {
            return null;
        }
        TBarrSignBill r = this.barrSignBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return null;
        }
        return r;
    }

    /** 本口要点几票才算齐：两名须同判看应签人数（空着按两名兜底）；一名即签一票就够 */
    private int requiredSigns(TBarrSignBill r) {
        if (r.getSignMode() != null && r.getSignMode() == MODE_AND) {
            return r.getNeedCount() != null && r.getNeedCount() > 0 ? r.getNeedCount() : 2;
        }
        return 1;
    }

    private int nodeOf(TBarrSignBill r) {
        return r.getNodeNo() == null ? 0 : r.getNodeNo();
    }

    private int signCountOf(TBarrSignBill r) {
        return r.getSignCount() == null ? 0 : r.getSignCount();
    }

    private int statusOf(TBarrSignBill r) {
        return r.getStatus() == null ? STATUS_RUNNING : r.getStatus();
    }
}
