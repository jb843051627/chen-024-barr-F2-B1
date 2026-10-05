package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TBarrSignBillMapper;
import com.fc.v2.model.auto.TBarrSignBill;
import com.fc.v2.service.ITBarrSignBillService;

/**
 * 联户验收签认单 Service业务层处理（approval-chain 形状：多阶段签批）。
 * 一口一口往前挪：本口算齐办法写在 sign_mode 上——一名即签（OR）一口一签，
 * 两名须同判（AND）签满应签人数才算齐；本口点齐才许挪下一口，末口点齐整单才算过。
 * 已点齐、已压回的单子谁都不许再签再压。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrSignBillServiceImpl implements ITBarrSignBillService {

    /** 末口：两级残联（口序 0..2） */
    private static final int MAX_NODE = 2;
    /** 本口算齐办法：0一名即签 1两名须同判 */
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    /** 签认情形：0在签 1已点齐 2已压回 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TBarrSignBillMapper barrSignBillMapper;

    @Override
    public TBarrSignBill selectTBarrSignBillById(Long id) {
        TBarrSignBill r = this.barrSignBillMapper.selectById(id);
        return r == null ? null : normalize(r);
    }

    @Override
    public TBarrSignBill approve(Long id, String approver, String comment) {
        TBarrSignBill r = loadSignable(id, approver);
        if (r == null) {
            return null;
        }
        r.setSignCount(nz(r.getSignCount()) + 1);
        if (nz(r.getSignCount()) < requiredSignatures(r)) {
            // 同判口还没签满：留在本口继续签
            this.barrSignBillMapper.updateById(r);
            return normalize(r);
        }
        // 本口点齐：已签人数清空，挪下一口；末口点齐全单过
        r.setSignCount(0);
        if (nz(r.getNodeNo()) >= MAX_NODE) {
            r.setStatus(STATUS_PASS);
        } else {
            r.setNodeNo(nz(r.getNodeNo()) + 1);
            r.setStatus(STATUS_RUNNING);
        }
        this.barrSignBillMapper.updateById(r);
        return normalize(r);
    }

    @Override
    public TBarrSignBill reject(Long id, String approver, String comment) {
        TBarrSignBill r = loadSignable(id, approver);
        if (r == null) {
            return null;
        }
        // 压回就是压回：情形落"已压回"，不许什么都不改空走一趟
        r.setStatus(STATUS_VETO);
        this.barrSignBillMapper.updateById(r);
        return normalize(r);
    }

    @Override
    public TBarrSignBill rollback(Long id, String comment) {
        TBarrSignBill r = loadRunning(id);
        if (r == null) {
            return null;
        }
        if (nz(r.getNodeNo()) <= 0) {
            // 头一口前面没有口子可退
            return null;
        }
        r.setNodeNo(nz(r.getNodeNo()) - 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.barrSignBillMapper.updateById(r);
        return normalize(r);
    }

    // ============================ 内部 ============================

    /** 签批/压回共用的装载：单子要在、没撤、仍在签；签批还得有签字人 */
    private TBarrSignBill loadSignable(Long id, String approver) {
        if (approver == null || approver.trim().isEmpty()) {
            return null;
        }
        return loadRunning(id);
    }

    /** 每条改动路共用的装载：在册、没撤、仍在签；点齐/压回之后一律请不动 */
    private TBarrSignBill loadRunning(Long id) {
        TBarrSignBill r = loadMutable(id);
        if (r == null || nz(r.getStatus()) != STATUS_RUNNING) {
            return null;
        }
        return r;
    }

    private TBarrSignBill loadMutable(Long id) {
        if (id == null) {
            return null;
        }
        TBarrSignBill r = this.barrSignBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() == 1)) {
            return null;
        }
        return normalize(r);
    }

    /** 本口要签几笔才算齐：一名即签一笔即可；两名须同判按应签人数（空着按一笔接住） */
    private int requiredSignatures(TBarrSignBill r) {
        if (nz(r.getSignMode()) == MODE_AND) {
            int need = nz(r.getNeedCount());
            return need > 0 ? need : 1;
        }
        return 1;
    }

    /** 旧单接住：口序/已签数/情形空着按在签头一口，不抛异常 */
    private TBarrSignBill normalize(TBarrSignBill r) {
        if (r.getNodeNo() == null) {
            r.setNodeNo(0);
        }
        if (r.getSignCount() == null) {
            r.setSignCount(0);
        }
        if (r.getStatus() == null) {
            r.setStatus(STATUS_RUNNING);
        }
        if (r.getDelFlag() == null) {
            r.setDelFlag(0);
        }
        return r;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
