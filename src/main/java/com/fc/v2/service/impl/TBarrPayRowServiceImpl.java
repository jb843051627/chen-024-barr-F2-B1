package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.service.ITBarrPayRowService;

/**
 * 补助核销行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrPayRowServiceImpl implements ITBarrPayRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TBarrPayRowMapper barrPayRowMapper;

    @Override
    public TBarrPayRow selectTBarrPayRowById(Long id) {
        return this.barrPayRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TBarrPayRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TBarrPayRow> errors = new java.util.ArrayList<TBarrPayRow>();
        int seq = 0;
        for (TBarrPayRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.barrPayRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TBarrPayRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.barrPayRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TBarrPayRow> listErrors(String batchNo) {
        return this.barrPayRowMapper.selectList(new QueryWrapper<TBarrPayRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
