package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.service.ITBarrPayRowService;

/**
 * 补助核销行 Service业务层处理（batch-process 形状：整批提交）。
 * 同一批次并着递两回也只落一回：先查册子，已落过的批次原样回成功行数、不重复入库；
 * 并发的两回由库上 (batch_no,row_no) 唯一索引兜底，撞键按"已落过"认。
 * 条件给不全回兜底值（空批 0、超限 -1），不把异常甩给调用方。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrPayRowServiceImpl implements ITBarrPayRowService {

    /** 单批最多这么多行，冒过整批不收、一行不入库 */
    private static final int MAX_ROWS = 500;
    /** 行落地情形 0未勾 1已收下 2已送回（校验不过的行） */
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TBarrPayRowMapper barrPayRowMapper;

    @Override
    public TBarrPayRow selectTBarrPayRowById(Long id) {
        return this.barrPayRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TBarrPayRow> rows) {
        // 空批：回 0 这条兜底值，绝不 rows.get(0) 把异常甩出去
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 冒过单批上限：回 -1，整批不入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }
        String no = normalizeBatchNo(batchNo, rows.get(0));

        // 幂等头一道：册子里这个批次已落过（收下或送回的行都算），不重复入库，
        // 原样回已收下的行数
        int existing = this.barrPayRowMapper.selectCount(new QueryWrapper<TBarrPayRow>()
                .eq("batch_no", no)
                .eq("del_flag", 0));
        if (existing > 0) {
            return this.barrPayRowMapper.selectCount(new QueryWrapper<TBarrPayRow>()
                    .eq("batch_no", no)
                    .eq("status", STATUS_OK)
                    .eq("del_flag", 0));
        }

        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TBarrPayRow r = rows.get(i);
            // 原报行次：按送进来的次第落定，合法非法都认这一行号
            r.setRowNo(i + 1);
            r.setBatchNo(no);
            r.setDelFlag(0);
            if (invalid(r)) {
                r.setStatus(STATUS_FAIL);
            } else {
                r.setStatus(STATUS_OK);
                ok++;
            }
            try {
                this.barrPayRowMapper.insert(r);
            } catch (DuplicateKeyException e) {
                // 幂等兜底：并发两回撞 (batch_no,row_no) 唯一键，后到这趟当已落过，不重不炸
                if (r.getStatus() == STATUS_FAIL) {
                    throw e;
                }
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
                .orderByAsc("row_no"));
    }

    // ============================ 内部 ============================

    /** 册码以批次参数为准；空着才回头认头一行自带的，两个都空就归一到空串 */
    private String normalizeBatchNo(String batchNo, TBarrPayRow first) {
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            return batchNo.trim();
        }
        return first == null || first.getBatchNo() == null ? "" : first.getBatchNo().trim();
    }

    /** 行校验：对上的卡代号空着、请领额空着或不是正数，都算送回行 */
    private boolean invalid(TBarrPayRow r) {
        return r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                || r.getQty() == null
                || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0;
    }
}
