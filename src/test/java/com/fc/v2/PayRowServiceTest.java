package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.service.impl.TBarrPayRowServiceImpl;

/**
 * 补助核销整批提交核心规矩单测（不起 Spring、不连库；mapper 打桩）。
 * 盯：空批回 0 不甩异常、超 500 行回 -1 整批不入、合法非法一趟落且行次按送报次第、
 * 同一批次并着递两回只落一回。
 */
public class PayRowServiceTest {

    private TBarrPayRowServiceImpl service;
    private TBarrPayRowMapper mapper;
    private final List<TBarrPayRow> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrPayRowServiceImpl();
        mapper = mock(TBarrPayRowMapper.class);
        setField(service, "barrPayRowMapper", mapper);

        // 模拟库：只数在册行、只列送回行、撞 (batch_no,row_no) 唯一键要炸。
        // 统一用 doXxx().when() 打桩，避免用例里重打桩时旧 Answer 先被 null 参数调一遍。
        doAnswer(inv -> (int) store.stream()
                .filter(r -> r.getDelFlag() == null || r.getDelFlag() == 0)
                .count()).when(mapper).selectCount(any());
        doAnswer(inv -> store.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 2)
                .sorted((a, b) -> a.getRowNo() - b.getRowNo())
                .collect(Collectors.toList())).when(mapper).selectList(any());
        doAnswer(inv -> {
            TBarrPayRow r = inv.getArgument(0);
            boolean dup = store.stream().anyMatch(x -> sameBatchRow(x, r));
            if (dup) {
                throw new DuplicateKeyException("uk_batch_row");
            }
            if (r.getId() == null) {
                r.setId((long) (store.size() + 1));
            }
            store.add(r);
            return 1;
        }).when(mapper).insert(any());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private boolean sameBatchRow(TBarrPayRow a, TBarrPayRow b) {
        return java.util.Objects.equals(a.getBatchNo(), b.getBatchNo())
                && java.util.Objects.equals(a.getRowNo(), b.getRowNo());
    }

    private TBarrPayRow row(String itemCode, String qty) {
        TBarrPayRow r = new TBarrPayRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    public void 空批回零_不把异常甩给调用方() {
        assertEquals(0, service.submitBatch("B1", new ArrayList<>()));
        assertEquals(0, service.submitBatch("B1", null));
        verify(mapper, never()).insert(any());
    }

    @Test
    public void 冒过五百行整批不收_一行不入() {
        List<TBarrPayRow> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(row("K-1", "10"));
        }
        assertEquals(-1, service.submitBatch("BIG", tooMany));
        verify(mapper, never()).insert(any());
    }

    @Test
    public void 合法非法一趟落_行次按送报次第_回收下行数() {
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(row("K-1", "100"));        // 行1 收
        rows.add(row("  ", "100"));        // 行2 卡代号空：送回
        rows.add(row("K-3", "0"));         // 行3 额不是正数：送回
        rows.add(row("K-4", null));        // 行4 额空：送回
        rows.add(row("K-5", "88.5"));      // 行5 收

        int ok = service.submitBatch("B2", rows);
        assertEquals(2, ok);
        assertEquals(5, store.size(), "合法非法同一趟都落库");
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, store.get(i).getRowNo(), "行次按次第");
            assertEquals("B2", store.get(i).getBatchNo());
        }
        assertEquals(1, store.get(0).getStatus());
        assertEquals(2, store.get(1).getStatus());

        List<TBarrPayRow> errors = service.listErrors("B2");
        assertEquals(3, errors.size());
        assertEquals(2, errors.get(0).getRowNo(), "失败明细按行号升序");
        assertEquals(4, errors.get(2).getRowNo());
    }

    @Test
    public void 同一批并着递两回_只落一回() {
        List<TBarrPayRow> first = new ArrayList<>();
        first.add(row("K-1", "10"));
        first.add(row("K-2", "20"));
        assertEquals(2, service.submitBatch("B3", first));
        assertEquals(2, store.size());

        // 第二回整批原样再递：册里已落过，不重复入库，回的还是已收下两行
        List<TBarrPayRow> second = new ArrayList<>();
        second.add(row("K-1", "10"));
        second.add(row("K-2", "20"));
        int again = service.submitBatch("B3", second);
        assertEquals(2, again);
        assertEquals(2, store.size(), "并发/重复提交幂等，库里还是两行");
    }

    @Test
    public void 并发撞上唯一键_后到一趟当已落过不炸() {
        // 查的时候册里空着（两回都查空），落的时候键已被另一回占住
        doAnswer(inv -> 0).when(mapper).selectCount(any());
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(row("K-9", "10"));
        // insert 首行即撞键
        doThrow(new DuplicateKeyException("uk_batch_row")).when(mapper).insert(any());
        int ok = service.submitBatch("B4", rows);
        assertEquals(1, ok, "撞唯一键按幂等认作已落过，不把异常甩出去");
        assertTrue(store.isEmpty());
    }
}
