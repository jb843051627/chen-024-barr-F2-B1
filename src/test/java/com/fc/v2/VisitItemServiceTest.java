package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.service.impl.TBarrVisitItemServiceImpl;

/**
 * 质保与养护复检条目核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 * 窗口外不抢跑不补递、条件给不全落兜底、单条失败跳过继续。
 */
public class VisitItemServiceTest {

    private TBarrVisitItemServiceImpl service;
    private TBarrVisitItemMapper visitItemMapper;

    private final List<TBarrVisitItem> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrVisitItemServiceImpl();
        visitItemMapper = mock(TBarrVisitItemMapper.class);
        setField(service, "barrVisitItemMapper", visitItemMapper);

        // 捞出的是 detached 副本：就地改字段不落库，只有 updateById 成了才写回册子
        when(visitItemMapper.selectList(any())).thenAnswer(inv -> {
            List<TBarrVisitItem> copy = new ArrayList<>();
            for (TBarrVisitItem r : store) {
                TBarrVisitItem c = new TBarrVisitItem();
                c.setId(r.getId());
                c.setItemNo(r.getItemNo());
                c.setDueAt(r.getDueAt());
                c.setAmount(r.getAmount());
                c.setStatus(r.getStatus());
                c.setDelFlag(r.getDelFlag());
                copy.add(c);
            }
            return copy;
        });
        when(visitItemMapper.updateById(any())).thenAnswer(inv -> {
            TBarrVisitItem r = inv.getArgument(0);
            store.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            store.add(r);
            return 1;
        });
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

    private Date at(String ymd) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .parse(ymd.length() == 10 ? ymd + " 00:00:00" : ymd);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private TBarrVisitItem item(long id, String dueAt, String earlyDays, Integer status) {
        TBarrVisitItem r = new TBarrVisitItem();
        r.setId(id);
        r.setItemNo("FJ-" + id);
        r.setDueAt(dueAt == null ? null : at(dueAt));
        r.setAmount(earlyDays == null ? null : new BigDecimal(earlyDays));
        r.setStatus(status);
        r.setDelFlag(0);
        store.add(r);
        return r;
    }

    @Test
    public void 窗口里才算到期_早一天不抢跑过了止点不补递() {
        // 止点 6月10日正午，最多许提前 3 日：窗口 [6月7日正午, 6月10日正午]
        item(1L, "2026-06-10 12:00:00", "3", 0);

        assertEquals(0, service.listDue(at("2026-06-07 11:59:59")).size(), "提前量之外不抢跑");
        assertEquals(1, service.listDue(at("2026-06-07 12:00:00")).size(), "窗口起这一刻落进来");
        assertEquals(1, service.listDue(at("2026-06-08 12:00:00")).size());
        assertEquals(1, service.listDue(at("2026-06-10 12:00:00")).size(), "止点这一刻仍落进来");
        assertEquals(0, service.listDue(at("2026-06-10 12:00:01")).size(), "过了止点不补递");
    }

    @Test
    public void 已递出的撤下的不再回炉_缺栏的一律请不动() {
        item(1L, "2026-06-10 12:00:00", "3", 1);            // 已递出
        item(2L, "2026-06-10 12:00:00", "3", 2);            // 递不动
        item(3L, "2026-06-10 12:00:00", "3", null);         // 情形空着
        item(4L, null, "3", 0);                             // 止点空着
        TBarrVisitItem removed = item(5L, "2026-06-10 12:00:00", "3", 0);
        removed.setDelFlag(1);                              // 已撤下
        item(6L, "2026-06-10 12:00:00", "3", 0);            // 正落在窗口里

        List<TBarrVisitItem> due = service.listDue(at("2026-06-08 12:00:00"));
        assertEquals(1, due.size());
        assertEquals(Long.valueOf(6L), due.get(0).getId());
    }

    @Test
    public void 条件给不全_回空回零不抛异常() {
        item(1L, "2026-06-10 12:00:00", "3", 0);

        assertTrue(service.listDue(null).isEmpty(), "没问那一刻：回空名单");
        assertEquals(0, service.runOnce(null), "没问那一刻：回 0 不炸");

        // 提前量空着按 0 日兜底：只认止点那一刻
        TBarrVisitItem noEarly = item(2L, "2026-06-10 12:00:00", null, 0);
        List<TBarrVisitItem> dayBefore = service.listDue(at("2026-06-09 12:00:00"));
        assertEquals(1, dayBefore.size(), "提前量空着的那条不抢跑，窗口里只有 1 号");
        assertEquals(Long.valueOf(1L), dayBefore.get(0).getId());
        List<TBarrVisitItem> atEnd = service.listDue(at("2026-06-10 12:00:00"));
        assertEquals(2, atEnd.size(), "止点这一刻两条都落进来");
        assertTrue(noEarly.getStatus() == 0);
    }

    private TBarrVisitItem stored(long id) {
        for (TBarrVisitItem r : store) {
            if (r.getId() != null && r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    @Test
    public void 空账回零_递出去的改情形_单条失败跳过继续() {
        // 空账：一个待递的都没有，回 0 不炸（早先直接越界抛给调用方）
        assertEquals(0, service.runOnce(at("2026-06-08 12:00:00")));

        item(1L, "2026-06-10 12:00:00", "3", 0);
        item(2L, "2026-06-10 12:00:00", "3", 0);
        assertEquals(2, service.runOnce(at("2026-06-08 12:00:00")));
        assertEquals(1, stored(1L).getStatus(), "递出去的落已递出");
        assertEquals(1, stored(2L).getStatus());
        // 递过的不再回炉
        assertEquals(0, service.runOnce(at("2026-06-09 12:00:00")));

        // 单条失败跳过继续：4 号落库时炸，3 号照样递出去
        store.clear();
        item(3L, "2026-06-10 12:00:00", "3", 0);
        item(4L, "2026-06-10 12:00:00", "3", 0);
        // doAnswer().when() 重打桩：when(mock.x()) 写法会先引爆旧桩里的空参调用
        org.mockito.Mockito.doAnswer(inv -> {
            TBarrVisitItem r = inv.getArgument(0);
            if (r.getId() != null && r.getId().equals(4L)) {
                throw new RuntimeException("落库炸了");
            }
            store.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            store.add(r);
            return 1;
        }).when(visitItemMapper).updateById(any());
        assertEquals(1, service.runOnce(at("2026-06-08 12:00:00")));
        assertEquals(1, stored(3L).getStatus());
        assertEquals(0, stored(4L).getStatus(), "失败的那条留在原地等下趟");
    }
}
