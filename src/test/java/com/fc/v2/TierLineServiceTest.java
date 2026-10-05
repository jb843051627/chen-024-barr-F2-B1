package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.fc.v2.mapper.auto.TBarrTierLineMapper;
import com.fc.v2.model.auto.TBarrTierLine;
import com.fc.v2.model.custom.TierVerdict;
import com.fc.v2.service.impl.TBarrTierLineServiceImpl;

/**
 * 入户评估定档线核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 * 四问——那一刻哪条线说话、请不出线给不给说法、并列两条听谁的、压线往哪头落——逐一过堂。
 */
public class TierLineServiceTest {

    private TBarrTierLineServiceImpl service;
    private TBarrTierLineMapper tierLineMapper;

    private final List<TBarrTierLine> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrTierLineServiceImpl();
        tierLineMapper = mock(TBarrTierLineMapper.class);
        setField(service, "barrTierLineMapper", tierLineMapper);

        when(tierLineMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(tierLineMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TBarrTierLine r : store) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
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

    private TBarrTierLine line(long id, String code, String th1, String th2, String th3,
                               String effStart, String effEnd, Integer priority, Integer status) {
        TBarrTierLine r = new TBarrTierLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        r.setEffStart(at(effStart));
        r.setEffEnd(at(effEnd));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private Date at(String ymd) {
        if (ymd == null) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .parse(ymd.length() == 10 ? ymd + " 00:00:00" : ymd);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // ============================ 一、三个分界数 ============================

    @Test
    public void 分界数缺一格或没压过前一格_回话讲清哪一格() {
        assertNull(service.boundaryProblem(line(1, "L-OK", "60", "80", "90", "2026-01-01", null, 1, 0)));

        assertTrue(service.boundaryProblem(line(2, "L2", null, "80", "90", "2026-01-01", null, 1, 0))
                .contains("起算分空着"));
        assertTrue(service.boundaryProblem(line(3, "L3", "60", null, "90", "2026-01-01", null, 1, 0))
                .contains("够线分空着"));
        assertTrue(service.boundaryProblem(line(4, "L4", "60", "80", null, "2026-01-01", null, 1, 0))
                .contains("封顶分空着"));

        String m1 = service.boundaryProblem(line(5, "L5", "80", "60", "90", "2026-01-01", null, 1, 0));
        assertTrue(m1.contains("够线分") && m1.contains("起算分") && m1.contains("没能压过"), "实际报：" + m1);
        String m2 = service.boundaryProblem(line(6, "L6", "60", "90", "90", "2026-01-01", null, 1, 0));
        assertTrue(m2.contains("封顶分") && m2.contains("够线分") && m2.contains("没能压过"), "实际报：" + m2);
    }

    @Test
    public void 数立不住的线请不动_不给档两栏留空() {
        store.add(line(1, "L-BAD", "60", null, "90", "2020-01-01", null, 9, 0));
        Date at = at("2026-06-01");

        TierVerdict v = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals(TierVerdict.TIER_NONE, v.getTier());
        assertEquals("无可用线", v.getConclusion());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());
        assertEquals(0, service.evaluateTop(new BigDecimal("85"), at));
        assertEquals(0, service.evaluate("L-BAD", new BigDecimal("85"), at));
        assertFalse(service.usable(1L, at));
        // 屏上挂着代号、册里请不出在位线：一条条指得出
        assertEquals(Arrays.asList("L-BAD"), service.listDanglingRuleCodes(at));
    }

    // ============================ 二、问话问的是那一刻 ============================

    @Test
    public void 补判前年用前年那一刻的线_不套今年新调的() {
        // 同一代号两版：前年秋的版够线 60，今年新调的版够线 80
        store.add(line(1, "L-A", "40", "60", "90", "2024-01-01", "2025-01-01", 1, 0));
        store.add(line(2, "L-A", "50", "80", "95", "2026-01-01", null, 1, 0));

        // 前年秋天评估 70 分：照前年那一刻的线够上提升；套今年新线才会凭空低成基础
        assertEquals(TierVerdict.TIER_LIFT, service.evaluate("L-A", new BigDecimal("70"), at("2024-10-15")));
        // 今年此刻 70 分：照今年的线只够基础
        assertEquals(TierVerdict.TIER_BASIC, service.evaluate("L-A", new BigDecimal("70"), at("2026-06-01")));
    }

    @Test
    public void 新旧交替那一日_旧管到新管起_同一刻两回一字不差() {
        store.add(line(1, "L-OLD", "40", "60", "90", "2025-01-01", "2026-01-01", 1, 0));
        store.add(line(2, "L-NEW", "50", "80", "95", "2026-01-01", null, 1, 0));

        // 交替日前一刻：旧线说话
        TierVerdict before = service.evaluateVerdict(new BigDecimal("70"), at("2025-12-31 23:59:59"));
        assertEquals(TierVerdict.TIER_LIFT, before.getTier());
        assertEquals("L-OLD", before.getRuleCode());

        // 交替当日零点：新线说话，旧线已交班（交班那一日不含）
        TierVerdict dayOf = service.evaluateVerdict(new BigDecimal("70"), at("2026-01-01 00:00:00"));
        assertEquals(TierVerdict.TIER_BASIC, dayOf.getTier());
        assertEquals("L-NEW", dayOf.getRuleCode());
        assertEquals(at("2026-01-01"), dayOf.getEffStart());

        // 同一刻再问一回：回话一字不差
        TierVerdict again = service.evaluateVerdict(new BigDecimal("70"), at("2026-01-01 00:00:00"));
        assertEquals(dayOf.getTier(), again.getTier());
        assertEquals(dayOf.getRuleCode(), again.getRuleCode());
        assertEquals(dayOf.getEffStart(), again.getEffStart());
    }

    // ============================ 三、两条并立与请不出线 ============================

    @Test
    public void 两条同时在位_交班次序高的先说() {
        store.add(line(1, "L-LO", "10", "20", "30", "2026-01-01", null, 1, 0));
        store.add(line(2, "L-HI", "70", "80", "90", "2026-01-01", null, 5, 0));
        Date at = at("2026-06-01");

        // 次序高的 L-HI 说话；50 分够不上它的起算，不给档——不许就着隔壁 L-LO 的数硬凑
        TierVerdict v = service.evaluateVerdict(new BigDecimal("50"), at);
        assertEquals(TierVerdict.TIER_NONE, v.getTier());
        assertEquals("无可用线", v.getConclusion());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());

        // 85 分：L-HI 够上提升
        TierVerdict hi = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals(TierVerdict.TIER_LIFT, hi.getTier());
        assertEquals("L-HI", hi.getRuleCode());
    }

    @Test
    public void 交班次序一样高_线代号排在后面的赢() {
        store.add(line(1, "L-A", "10", "20", "30", "2026-01-01", null, 3, 0));
        store.add(line(2, "L-B", "70", "80", "90", "2026-01-01", null, 3, 0));
        Date at = at("2026-06-01");

        // 85 分：L-A 手里是重点、L-B 手里是提升——回话说提升，排在后面的 L-B 赢了
        TierVerdict v = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals(TierVerdict.TIER_LIFT, v.getTier());
        assertEquals("L-B", v.getRuleCode());
        // 同一刻问不出两个结论：再问问出的还是它
        assertEquals("L-B", service.evaluateVerdict(new BigDecimal("85"), at).getRuleCode());
        // 屏上名单头一条也是它
        assertEquals("L-B", service.listAvailable(at).get(0).getRuleCode());
    }

    @Test
    public void 空册与缺参_一律无可用线不硬凑() {
        Date at = at("2026-06-01");
        TierVerdict v = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals(TierVerdict.TIER_NONE, v.getTier());
        assertEquals("无可用线", v.getConclusion());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());

        assertEquals(0, service.evaluate("L-A", new BigDecimal("85"), at));
        assertEquals(0, service.evaluate(" ", new BigDecimal("85"), at));
        assertEquals(0, service.evaluate("L-A", null, at));
        assertEquals(0, service.evaluate("L-A", new BigDecimal("85"), null));
        assertEquals(0, service.evaluateTop(null, at));
        assertEquals(0, service.evaluateTop(new BigDecimal("85"), null));
        assertTrue(service.listAvailable(null).isEmpty());
        assertTrue(service.listDanglingRuleCodes(null).isEmpty());
        assertEquals(0, service.countAvailable(null));
        assertFalse(service.usable(null, at));
        assertFalse(service.usable(1L, null));
    }

    @Test
    public void 已卸下不参与现行比数_册里翻得着屏上指得出() {
        store.add(line(1, "L-GONE", "60", "80", "90", "2020-01-01", null, 9, 1));
        Date at = at("2026-06-01");

        // 不许捞它定档，也不许让它缀在新结论后头
        assertEquals(0, service.evaluateTop(new BigDecimal("85"), at));
        TierVerdict v = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals("无可用线", v.getConclusion());
        assertNull(v.getRuleCode());
        assertFalse(service.usable(1L, at));
        assertEquals(0, service.countAvailable(at));

        // 册里翻得着、屏上列得出
        assertNotNull(service.selectTBarrTierLineById(1L));
        assertEquals(Arrays.asList("L-GONE"), service.listDanglingRuleCodes(at));
    }

    // ============================ 四、压线往哪头落 ============================

    @Test
    public void 压线往高档走_几个口子答同一档() {
        store.add(line(1, "L-A", "60", "80", "90", "2026-01-01", null, 1, 0));
        Date at = at("2026-06-01");

        // 够不上起算：不给档
        assertTier(at, "59.999", TierVerdict.TIER_NONE);
        // 压起算：够上即基础
        assertTier(at, "60", TierVerdict.TIER_BASIC);
        assertTier(at, "79.999", TierVerdict.TIER_BASIC);
        // 压够线：往高档走，提升
        assertTier(at, "80", TierVerdict.TIER_LIFT);
        assertTier(at, "89.999", TierVerdict.TIER_LIFT);
        // 压封顶：往高档走，重点
        assertTier(at, "90", TierVerdict.TIER_KEY);
        assertTier(at, "100", TierVerdict.TIER_KEY);
        // 满分一百之外：越界不给档
        assertTier(at, "100.001", TierVerdict.TIER_NONE);
        assertTier(at, "-0.001", TierVerdict.TIER_NONE);
    }

    private void assertTier(Date at, String score, int tier) {
        // 单线口子、多线口子、整份结论口子：答出来的必须是同一档
        assertEquals(tier, service.evaluate("L-A", new BigDecimal(score), at), "单线口子 " + score);
        assertEquals(tier, service.evaluateTop(new BigDecimal(score), at), "多线口子 " + score);
        assertEquals(tier, service.evaluateVerdict(new BigDecimal(score), at).getTier(), "结论口子 " + score);
    }

    @Test
    public void 先取齐再比线_同一把舍法只此一处() {
        store.add(line(1, "L-A", "60", "80", "90", "2026-01-01", null, 1, 0));
        Date at = at("2026-06-01");

        // 79.9995 取齐到 80.000，压够线往高档走：提升；不取齐直接比才会冤成基础
        assertEquals(TierVerdict.TIER_LIFT, service.evaluateTop(new BigDecimal("79.9995"), at));
        // 59.9995 取齐 60.000：够上起算；59.9994 取齐 59.999：够不上
        assertEquals(TierVerdict.TIER_BASIC, service.evaluateTop(new BigDecimal("59.9995"), at));
        assertEquals(TierVerdict.TIER_NONE, service.evaluateTop(new BigDecimal("59.9994"), at));
    }

    // ============================ 五、存量兼容与钉户卡 ============================

    @Test
    public void 交班空着按一直在位_钉进户卡的一对当下回() {
        // 早年手抄进册的线：只填起用那一日，交班空着
        store.add(line(7, "L-OLD-COPY", "60", "80", "90", "2020-01-01", null, 1, 0));
        Date at = at("2026-06-01");

        assertTrue(service.usable(7L, at));
        TierVerdict v = service.evaluateVerdict(new BigDecimal("85"), at);
        assertEquals(TierVerdict.TIER_LIFT, v.getTier());
        assertEquals("提升", v.getConclusion());
        // 钉进户卡的线代号与起用那一日，与这一趟求值当下回的那一对完全对得住
        assertEquals("L-OLD-COPY", v.getRuleCode());
        assertEquals(at("2020-01-01"), v.getEffStart());
    }

    @Test
    public void 名单计数与求值同一出处() {
        store.add(line(1, "L-A", "60", "80", "90", "2026-01-01", null, 2, 0));
        store.add(line(2, "L-B", "50", "70", "95", "2026-01-01", null, 1, 0));
        store.add(line(3, "L-FUTURE", "1", "2", "3", "2027-01-01", null, 9, 0));
        Date at = at("2026-06-01");

        List<TBarrTierLine> avail = service.listAvailable(at);
        assertEquals(2, avail.size());
        // 交班次序降序，未起用的不在列
        assertEquals("L-A", avail.get(0).getRuleCode());
        assertEquals("L-B", avail.get(1).getRuleCode());
        assertEquals(avail.size(), service.countAvailable(at));
        for (TBarrTierLine r : avail) {
            assertTrue(service.usable(r.getId(), at));
        }
        assertFalse(service.usable(3L, at));

        // 屏上那份名单与逐条翻册凑出来的那份同一出处：求值挑的就是名单头一条
        assertEquals(avail.get(0).getRuleCode(),
                service.evaluateVerdict(new BigDecimal("85"), at).getRuleCode());
        // 屏上挂着代号、那一刻请不出在位线的：L-FUTURE 指得出
        assertEquals(Arrays.asList("L-FUTURE"), service.listDanglingRuleCodes(at));
    }
}
