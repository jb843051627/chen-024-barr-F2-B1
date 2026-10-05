package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.service.ITBarrPayRowService;

/**
 * 补助核销行 Service业务层处理（batch-process 形状：整批提交）。
 * 逐行校验：合法的收下、非法的记入失败明细（原报行次留住，不另排号），互不相扰；
 * 空批次回 0 不炸调用方；超上限整批不入；同一册码并着递两回，第二回一行都不再落——
 * 并发提交要幂等，库里有这一册就不再叠。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrPayRowServiceImpl implements ITBarrPayRowService {

    /** 单批行数上限：冒了整批不入 */
    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TBarrPayRowMapper barrPayRowMapper;

    @Override
    public TBarrPayRow selectTBarrPayRowById(Long id) {
        return this.barrPayRowMapper.selectById(id);
    }

    /**
     * 整批提交。synchronized：同一册码并着递两回时，先落库的那回说话，
     * 后到的这回看见册里已有，一行都不再叠。
     */
    @Override
    public synchronized int submitBatch(String batchNo, List<TBarrPayRow> rows) {
        if (rows == null || rows.isEmpty()) {
            // 空批次是兜底值不是异常
            return 0;
        }
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = resolveBatchNo(batchNo, rows);
        // 幂等：这一册已有落库行（含失败明细），重复递来一概不再入
        Integer exists = this.barrPayRowMapper.selectCount(new QueryWrapper<TBarrPayRow>()
                .eq("batch_no", no)
                .eq("del_flag", 0));
        if (exists != null && exists > 0) {
            return 0;
        }
        int ok = 0;
        int seq = 0;
        for (TBarrPayRow r : rows) {
            seq++;
            if (r == null) {
                continue;
            }
            if (r.getRowNo() == null) {
                // 原报行次留住：报来的不另排，没报的按递来次序补
                r.setRowNo(seq);
            }
            r.setBatchNo(no);
            boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                    && r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) > 0;
            r.setStatus(valid ? STATUS_OK : STATUS_FAIL);
            if (r.getDelFlag() == null) {
                r.setDelFlag(0);
            }
            this.barrPayRowMapper.insert(r);
            if (valid) {
                ok++;
            }
        }
        return ok;
    }

    @Override
    public List<TBarrPayRow> listErrors(String batchNo) {
        return this.barrPayRowMapper.selectList(new QueryWrapper<TBarrPayRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no")
                .orderByAsc("id"));
    }

    /** 册码以递进来的为准；没递就看行上带的，两栏都空着也不炸 */
    private String resolveBatchNo(String batchNo, List<TBarrPayRow> rows) {
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            return batchNo.trim();
        }
        for (TBarrPayRow r : rows) {
            if (r != null && r.getBatchNo() != null && !r.getBatchNo().trim().isEmpty()) {
                return r.getBatchNo().trim();
            }
        }
        return batchNo;
    }
}
