package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.service.impl.TBarrVisitItemServiceImpl;

/**
 * 质保与养护复检核心规矩单测（不起 Spring、不连库；mapper 打桩）。
 * 盯：执行窗口与到期边界都压线收进、只递搁着且在册的、提前量夹在 2~5 日、
 * 空表/缺字段回兜底值不甩异常、并列按止点与主键定先后、单条递不动跳过继续。
 */
public class VisitItemServiceTest {

    private TBarrVisitItemServiceImpl service;
    private TBarrVisitItemMapper mapper;
    private final List<TBarrVisitItem> store = new ArrayList<>();

    private static final long DAY = 24L * 60 * 60 * 1000;

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrVisitItemServiceImpl();
        mapper = mock(TBarrVisitItemMapper.class);
        setField(service, "barrVisitItemMapper", mapper);
        // 用 doXxx().when() 打桩，重打桩时不会先以 null 参数触发旧 Answer。
        // selectList 回的是册子上的副本：服务改手里的对象，落不了库就不许污染册子。
        doAnswer(inv -> store.stream().map(this::copy).collect(java.util.stream.Collectors.toList()))
                .when(mapper).selectList(any());
        doAnswer(inv -> {
            TBarrVisitItem r = inv.getArgument(0);
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(r.getId())) {
                    store.set(i, r);
                }
            }
            return 1;
        }).when(mapper).updateById(any());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Date at(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private TBarrVisitItem copy(TBarrVisitItem s) {
        return item(s.getId(), s.getDueAt(),
                s.getAmount() == null ? null : s.getAmount().toPlainString(),
                s.getStatus(), s.getDelFlag());
    }

    private TBarrVisitItem item(long id, Date dueAt, String amount, Integer status, Integer delFlag) {
        TBarrVisitItem r = new TBarrVisitItem();
        r.setId(id);
        r.setItemNo("V-" + id);
        r.setDueAt(dueAt);
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setStatus(status);
        r.setDelFlag(delFlag);
        return r;
    }

    private TBarrVisitItem waiting(long id, Date dueAt, String amount) {
        return item(id, dueAt, amount, 0, 0);
    }

    @Test
    public void 时刻给不全或册子空着_回兜底值不甩异常() {
        assertTrue(service.listDue(null).isEmpty());
        assertEquals(0, service.runOnce(at("2026-10-05 00:00:00")), "空册子跑一趟回 0");
        assertEquals(0, service.runOnce(null));
    }

    @Test
    public void 窗口压线收进_开窗当日与止点当日都算() {
        Date due = at("2026-10-10 12:00:00");
        store.add(waiting(1, due, "3"));
        Date windowStart = new Date(due.getTime() - 3 * DAY);

        // 开窗前一刻：不递
        assertTrue(service.listDue(new Date(windowStart.getTime() - 1)).isEmpty());
        // 恰好开窗（压线收进）：递
        assertEquals(1, service.listDue(windowStart).size());
        // 窗口中段：递
        assertEquals(1, service.listDue(at("2026-10-09 09:00:00")).size());
        // 恰好止点：递（旧跑法用严格小于，把这一户漏在窗外）
        assertEquals(1, service.listDue(due).size());
        // 走过止点一刻：不递
        assertTrue(service.listDue(new Date(due.getTime() + 1)).isEmpty());
    }

    @Test
    public void 提前量夹在二到五日() {
        Date due = at("2026-10-10 12:00:00");
        store.add(waiting(1, due, "1"));   // 夹到 2 日
        store.add(waiting(2, due, "9"));   // 夹到 5 日

        // 提前 2 日：1 号（夹到 2）刚好开窗
        Date twoBefore = new Date(due.getTime() - 2 * DAY);
        List<TBarrVisitItem> atTwo = service.listDue(twoBefore);
        assertEquals(2, atTwo.size());

        // 提前 4 日：夹到 2 日的 1 号已过开窗（未到止点仍在窗内），夹到 5 日的 2 号已开窗
        Date fourBefore = new Date(due.getTime() - 4 * DAY);
        List<TBarrVisitItem> atFour = service.listDue(fourBefore);
        assertEquals(1, atFour.size());
        assertEquals(2L, atFour.get(0).getId());

        // 提前 6 日：夹到 5 日也没开窗
        Date sixBefore = new Date(due.getTime() - 6 * DAY);
        assertTrue(service.listDue(sixBefore).isEmpty());
    }

    @Test
    public void 已递出已撤下缺止点缺提前量的_都不进册() {
        Date due = at("2026-10-10 12:00:00");
        store.add(item(1, due, "3", 1, 0));     // 已递出
        store.add(item(2, due, "3", 2, 0));     // 递不动
        store.add(item(3, due, "3", 0, 1));     // 已撤下
        store.add(item(4, null, "3", 0, 0));    // 止点空
        store.add(item(5, due, null, 0, 0));    // 提前量空
        store.add(item(6, due, "0", 0, 0));     // 提前量不是正数
        assertTrue(service.listDue(at("2026-10-09 00:00:00")).isEmpty());
    }

    @Test
    public void 跑一趟落已递出_单条递不动跳过继续() {
        Date due = at("2026-10-10 12:00:00");
        store.add(waiting(1, due, "3"));
        store.add(waiting(2, due, "3"));
        store.add(waiting(3, due, "3"));
        // 第 2 条落库失败（递不动）：返回 0 且不写进册子
        doAnswer(inv -> {
            TBarrVisitItem r = inv.getArgument(0);
            if (r.getId() == 2L) {
                return 0;
            }
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(r.getId())) {
                    store.set(i, r);
                }
            }
            return 1;
        }).when(mapper).updateById(any());

        int ok = service.runOnce(at("2026-10-09 00:00:00"));
        assertEquals(2, ok, "一条递不动不拖垮整趟");
        assertEquals(1, store.get(0).getStatus(), "成功的落「已递出」");
        assertEquals(0, store.get(1).getStatus(), "递不动的仍是「搁着」，下趟还能再递");
        assertEquals(1, store.get(2).getStatus());
    }

    @Test
    public void 并列按止点早晚再按主键_同一刻先后确定() {
        Date early = at("2026-10-08 12:00:00");
        Date later = at("2026-10-09 12:00:00");
        // 乱序入册：止点相同的两条，主键小的在前
        store.add(waiting(30, later, "3"));
        store.add(waiting(20, early, "3"));
        store.add(waiting(10, early, "3"));

        // 取在三条窗口的公共重叠处（恰好是较早止点当日，压线收进）
        List<TBarrVisitItem> due = service.listDue(at("2026-10-08 12:00:00"));
        assertEquals(Arrays.asList(10L, 20L, 30L),
                Arrays.asList(due.get(0).getId(), due.get(1).getId(), due.get(2).getId()));
    }
}
