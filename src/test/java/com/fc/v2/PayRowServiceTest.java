package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.service.impl.TBarrPayRowServiceImpl;

/**
 * 补助核销行核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 * 空批兜底、超限整批不入、好坏分行各落各的、同一册码并着递两回不再叠。
 */
public class PayRowServiceTest {

    private TBarrPayRowServiceImpl service;
    private TBarrPayRowMapper payRowMapper;

    private final List<TBarrPayRow> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrPayRowServiceImpl();
        payRowMapper = mock(TBarrPayRowMapper.class);
        setField(service, "barrPayRowMapper", payRowMapper);

        when(payRowMapper.insert(any())).thenAnswer(inv -> {
            TBarrPayRow r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId((long) (store.size() + 1));
            }
            store.add(r);
            return 1;
        });
        // 册码从查询条件里取：数的是这一册已在库的行
        when(payRowMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            String no = stringParam(w);
            return (int) store.stream()
                    .filter(r -> r.getBatchNo() != null && r.getBatchNo().equals(no))
                    .filter(r -> r.getDelFlag() == null || r.getDelFlag() == 0)
                    .count();
        });
        // 失败明细：按册码与情形捞出，按行号升序
        when(payRowMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            String no = stringParam(w);
            return store.stream()
                    .filter(r -> r.getBatchNo() != null && r.getBatchNo().equals(no))
                    .filter(r -> r.getStatus() != null && r.getStatus() == 2)
                    .sorted(Comparator.comparing(TBarrPayRow::getRowNo))
                    .collect(Collectors.toList());
        });
    }

    private String stringParam(QueryWrapper<?> w) {
        // MP 3.4.2 的查询参数是渲染 SQL 那一刻才落进 paramNameValuePairs 的，先催一下再取
        w.getSqlSegment();
        return w.getParamNameValuePairs().values().stream()
                .filter(v -> v instanceof String)
                .map(v -> (String) v)
                .findFirst().orElse(null);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = null;
        Class<?> c = target.getClass();
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        assertTrue(f != null, "字段 " + name + " 找不着");
        f.setAccessible(true);
        f.set(target, value);
    }

    private TBarrPayRow row(String itemCode, String qty) {
        TBarrPayRow r = new TBarrPayRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    public void 空批回零是兜底不是异常() {
        assertEquals(0, service.submitBatch("B202610", new ArrayList<>()));
        assertEquals(0, service.submitBatch("B202610", null));
        assertTrue(store.isEmpty());
    }

    @Test
    public void 冒了单批上限_整批一行不入() {
        List<TBarrPayRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("CARD-" + i, "10"));
        }
        assertEquals(-1, service.submitBatch("B202610", rows));
        assertTrue(store.isEmpty(), "超上限整批不入库");
    }

    @Test
    public void 好坏分行各落各的_回的是收下几行() {
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(row("CARD-1", "100"));
        rows.add(row(" ", "100"));      // 卡代号空着：送回
        rows.add(row("CARD-3", "0"));   // 请领额没起来：送回
        rows.add(row("CARD-4", null));  // 请领额空着：送回
        rows.add(row("CARD-5", "50"));

        assertEquals(2, service.submitBatch("B202610", rows));
        assertEquals(5, store.size(), "收下的、送回的都落册，互不相扰");
        assertEquals(1, store.get(0).getStatus());
        assertEquals(2, store.get(1).getStatus());
        assertEquals(2, store.get(2).getStatus());
        assertEquals(2, store.get(3).getStatus());
        assertEquals(1, store.get(4).getStatus());
        // 没报行次的按递来次序补
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, store.get(i).getRowNo());
            assertEquals("B202610", store.get(i).getBatchNo());
        }
    }

    @Test
    public void 原报行次留住_失败明细按行号升序() {
        TBarrPayRow a = row("CARD-1", "10");
        a.setRowNo(30);
        TBarrPayRow b = row(null, "10");
        b.setRowNo(10);
        TBarrPayRow c = row("CARD-3", "0");
        c.setRowNo(20);
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(a);
        rows.add(b);
        rows.add(c);

        assertEquals(1, service.submitBatch("B202610", rows));
        assertEquals(30, store.get(0).getRowNo(), "原报行次不另排");

        List<TBarrPayRow> errors = service.listErrors("B202610");
        assertEquals(2, errors.size());
        assertEquals(10, errors.get(0).getRowNo());
        assertEquals(20, errors.get(1).getRowNo());
    }

    @Test
    public void 同一册并着递两回_第二回一行都不再叠() {
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(row("CARD-1", "100"));
        rows.add(row("CARD-2", "50"));

        assertEquals(2, service.submitBatch("B202610", rows));
        assertEquals(0, service.submitBatch("B202610", rows), "重复递来一概不再入");
        assertEquals(2, store.size(), "幂等：册里有这一册就不再叠");

        // 换一册码另起一批：各入各的
        List<TBarrPayRow> next = new ArrayList<>();
        next.add(row("CARD-1", "100"));
        next.add(row("CARD-2", "50"));
        assertEquals(2, service.submitBatch("B202611", next));
        assertEquals(4, store.size());
    }

    @Test
    public void 册码以递进来的为准_行上带的不算数() {
        TBarrPayRow r = row("CARD-1", "10");
        r.setBatchNo("B旧的");
        List<TBarrPayRow> rows = new ArrayList<>();
        rows.add(r);

        assertEquals(1, service.submitBatch("B202610", rows));
        assertEquals("B202610", store.get(0).getBatchNo());
    }
}
