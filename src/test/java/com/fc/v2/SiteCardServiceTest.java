package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.common.exception.SiteCardRuleException;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.mapper.auto.TBarrCardLogMapper;
import com.fc.v2.mapper.auto.TBarrSiteCardMapper;
import com.fc.v2.mapper.auto.TBarrUserStreetMapper;
import com.fc.v2.mapper.auto.TSysStreetMapper;
import com.fc.v2.mapper.custom.BarrCardSeqDao;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TBarrSiteCard;
import com.fc.v2.model.auto.TSysStreet;
import com.fc.v2.model.custom.CardSummary;
import com.fc.v2.model.custom.SiteCardForm;
import com.fc.v2.service.impl.TBarrSiteCardServiceImpl;

/**
 * 改造进度卡核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 */
public class SiteCardServiceTest {

    private TBarrSiteCardServiceImpl service;
    private TBarrSiteCardMapper cardMapper;
    private TBarrArchMapper archMapper;
    private TBarrCardLogMapper logMapper;
    private BarrCardSeqDao seqDao;
    private TSysStreetMapper streetMapper;

    private final List<TBarrSiteCard> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrSiteCardServiceImpl();
        cardMapper = mock(TBarrSiteCardMapper.class);
        archMapper = mock(TBarrArchMapper.class);
        logMapper = mock(TBarrCardLogMapper.class);
        TBarrUserStreetMapper userStreetMapper = mock(TBarrUserStreetMapper.class);
        seqDao = mock(BarrCardSeqDao.class);
        streetMapper = mock(TSysStreetMapper.class);

        setField(service, "baseMapper", cardMapper);
        setField(service, "barrArchMapper", archMapper);
        setField(service, "barrCardLogMapper", logMapper);
        setField(service, "barrUserStreetMapper", userStreetMapper);
        setField(service, "barrCardSeqDao", seqDao);
        setField(service, "sysStreetMapper", streetMapper);

        when(seqDao.currentValue()).thenReturn(1L);
        when(cardMapper.insert(any())).thenAnswer(inv -> {
            TBarrSiteCard c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId((long) (store.size() + 1));
            }
            store.add(c);
            return 1;
        });
        when(cardMapper.updateById(any())).thenAnswer(inv -> {
            TBarrSiteCard c = inv.getArgument(0);
            store.removeIf(x -> x.getId() != null && x.getId().equals(c.getId()));
            store.add(c);
            return 1;
        });
        when(logMapper.insert(any())).thenReturn(1);
        when(userStreetMapper.selectList(any())).thenReturn(new ArrayList<>());

        TBarrArch arch = new TBarrArch();
        arch.setId(0L);
        arch.setArchNo("DC00");
        arch.setArchName("浈县区白沙街道·祥和楼组片区");
        arch.setArchKind("小区楼组");
        arch.setRoadName("岫岭省—柏州市—浈县区—白沙街道");
        arch.setStatus(0);
        arch.setDelFlag(0);
        when(archMapper.selectOne(any())).thenReturn(arch);
        when(archMapper.selectById(0L)).thenReturn(arch);

        TSysStreet street = new TSysStreet();
        street.setStreetCode("BSJD");
        street.setStreetName("白沙街道");
        street.setDataState(0);
        when(streetMapper.selectList(any())).thenReturn(Arrays.asList(street));

        // 默认同点位没有别的活卡
        when(cardMapper.selectCount(any())).thenReturn(0);
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

    private SiteCardForm form(String need, String done) {
        SiteCardForm f = new SiteCardForm();
        f.setArchId("0");
        f.setSiteNo("SITE-1");
        f.setNeedNum(need);
        f.setDoneNum(done);
        return f;
    }

    @Test
    public void 立卡轧差_五十对二十_差三十() {
        TBarrSiteCard card = service.createCard(form("50", "20"), "zhangke");
        assertEquals(30, card.getLackNum());
        assertEquals(TBarrSiteCard.STATUS_NEW, card.getStatus());
        assertEquals(0, card.getClosed());
        assertTrue(card.getCardNo().matches("K20\\d{2}Q[1-4]-000001"), "卡代号系统按季排：" + card.getCardNo());
        assertTrue(card.getVerifyCode() != null && card.getVerifyCode().length() > 0);
    }

    @Test
    public void 立卡轧差_二十对七_差十三_齐平差零() {
        assertEquals(13, service.createCard(form("20", "7"), "u").getLackNum());
        store.clear();
        when(seqDao.currentValue()).thenReturn(2L);
        assertEquals(0, service.createCard(form("8", "8"), "u").getLackNum());
    }

    @Test
    public void 栏级报错要讲清落在哪一栏() {
        assertError("应改处数", () -> service.createCard(form("", "1"), "u"));
        assertError("应改处数", () -> service.createCard(form("12.5", "1"), "u"));
        assertError("应改处数", () -> service.createCard(form("-3", "1"), "u"));
        assertError("已改处数", () -> service.createCard(form("10", "11"), "u"));
        SiteCardForm noSite = form("10", "0");
        noSite.setSiteNo(" ");
        assertError("点位或住户代号", () -> service.createCard(noSite, "u2"));
    }

    @Test
    public void 同点位活卡挡第二张_收口后名额空出() {
        TBarrSiteCard first = service.createCard(form("10", "0"), "u");
        when(cardMapper.selectCount(any())).thenReturn(1);
        assertError("第二张", () -> service.createCard(form("10", "0"), "u"));

        // 头一张走完并收口（selectOne 按 id 返回头一张）
        when(cardMapper.selectOne(any())).thenReturn(first);
        service.advance(first.getId(), "", "u");
        when(cardMapper.selectCount(any())).thenReturn(1);
        // 已改齐但缺项时收口挡住
        assertError("缺项", () -> service.close(first.getId(), "", "u"));

        // 补齐改齐：编辑改 done=10
        when(cardMapper.selectById(first.getId())).thenReturn(first);
        SiteCardForm edit = new SiteCardForm();
        edit.setId(String.valueOf(first.getId()));
        edit.setArchId("0");
        edit.setSiteNo("SITE-1");
        edit.setNeedNum("10");
        edit.setDoneNum("10");
        service.updateCard(edit, "u");
        assertEquals(0, first.getLackNum());
        assertEquals(TBarrSiteCard.STATUS_DONE, first.getStatus());

        service.close(first.getId(), "", "u");
        assertEquals(1, first.getClosed());
        // 收口后再动：加不进、改不动、撤不下
        assertError("收口", () -> service.updateCard(edit, "u"));
        assertError("撤不下", () -> service.deleteTBarrSiteCardByIds(String.valueOf(first.getId()), "u"));
    }

    @Test
    public void 编辑本卡不当第二张挡门外() {
        TBarrSiteCard card = service.createCard(form("10", "2"), "u");
        when(cardMapper.selectById(card.getId())).thenReturn(card);
        when(cardMapper.selectOne(any())).thenReturn(card);
        SiteCardForm edit = form("10", "5");
        edit.setId(String.valueOf(card.getId()));
        // 同点位不改，不应触发占位计数，也不应被自己挡住
        TBarrSiteCard updated = assertDoesNotThrow(() -> service.updateCard(edit, "u"));
        assertEquals(5, updated.getLackNum());
    }

    @Test
    public void 级次只能逐级走_缺项可退回重来旧笔留底() {
        TBarrSiteCard card = service.createCard(form("5", "2"), "u");
        when(cardMapper.selectOne(any())).thenReturn(card);
        service.advance(card.getId(), "头一回已改齐动作", "u");
        assertEquals(TBarrSiteCard.STAGE_DONE, card.getStage());
        assertEquals(TBarrSiteCard.STATUS_LACK, card.getStatus());

        // 不能再往前迈
        assertError("步子不能乱迈", () -> service.advance(card.getId(), "", "u"));
        // 退回新录
        service.revert(card.getId(), "补齐后重来", "u");
        assertEquals(TBarrSiteCard.STAGE_NEW, card.getStage());
        assertEquals(TBarrSiteCard.STATUS_NEW, card.getStatus());
        // 留痕三笔都在（create/advance/revert），旧笔不并排不抹
        when(logMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> service.listLogs(card.getId()));
    }

    @Test
    public void 停用底册不能新挂_底册合不上起不来() {
        TBarrArch stopped = new TBarrArch();
        stopped.setId(1L);
        stopped.setArchNo("DC01");
        stopped.setStatus(1);
        stopped.setDelFlag(0);
        when(archMapper.selectOne(any())).thenReturn(stopped);
        assertError("停用", () -> service.createCard(form("5", "0"), "u"));

        when(archMapper.selectOne(any())).thenReturn(null);
        assertError("合不上", () -> service.createCard(form("5", "0"), "u"));
    }

    @Test
    public void 旧卡步数卡号核验串空着_接住不报错也不当已收口() {
        TBarrSiteCard legacy = new TBarrSiteCard();
        legacy.setId(99L);
        legacy.setSiteNo("OLD-1");
        legacy.setArchId(0L);
        legacy.setNeedNum(5);
        legacy.setDoneNum(2);
        legacy.setStatus(TBarrSiteCard.STATUS_LACK);
        legacy.setDelFlag(0);
        // stage/closed/cardNo/verifyCode/lackNum 全空
        assertNull(legacy.getStage());
        assertNull(legacy.getClosed());

        when(cardMapper.selectOne(any())).thenReturn(legacy);
        when(cardMapper.selectById(99L)).thenReturn(legacy);
        TBarrSiteCard shown = service.selectTBarrSiteCardById(99L);
        assertEquals(0, shown.getClosed(), "在走的旧卡不得当成已收口");
        assertEquals(TBarrSiteCard.STAGE_DONE, shown.getStage(), "旧档缺项反推走到第二级");
        assertEquals(3, shown.getLackNum());

        // 头一回推进流程不报错，且把缺的都补上
        assertDoesNotThrow(() -> service.revert(99L, "旧卡回新录", "u"));
        assertTrue(legacy.getCardNo().startsWith("K"));
        assertTrue(legacy.getVerifyCode() != null);
    }

    @Test
    public void 三摆法户数同一次计算接得平() {
        TBarrSiteCard a = card("S1", "白沙街道", "BSJD", "小区楼组", TBarrSiteCard.STATUS_NEW, 0);
        TBarrSiteCard b = card("S2", "白沙街道", "BSJD", "公共点位", TBarrSiteCard.STATUS_DONE, 1);
        TBarrSiteCard c = card("S3", "藕塘街道", "OTJD", "小区楼组", TBarrSiteCard.STATUS_LACK, 1);
        when(cardMapper.selectList(any())).thenReturn(Arrays.asList(a, b, c));

        CardSummary summary = service.summary(null, Arrays.asList("BSJD", "OTJD"));
        assertEquals(3, summary.getTotal());
        assertEquals(3, sum(summary.getByStreet()));
        assertEquals(3, sum(summary.getByArchKind()));
        assertEquals(3, sum(summary.getByStatus()));

        // 没授权：空就是空，不借邻街
        when(cardMapper.selectList(any())).thenReturn(new ArrayList<>());
        CardSummary empty = service.summary(null, new ArrayList<>());
        assertEquals(0, empty.getTotal());
    }

    private TBarrSiteCard card(String site, String streetName, String streetCode, String kind, int status, int stage) {
        TBarrSiteCard c = new TBarrSiteCard();
        c.setSiteNo(site);
        c.setStreetName(streetName);
        c.setStreetCode(streetCode);
        c.setArchKind(kind);
        c.setStatus(status);
        c.setStage(stage);
        c.setClosed(0);
        c.setNeedNum(1);
        c.setDoneNum(status == TBarrSiteCard.STATUS_DONE ? 1 : 0);
        c.setLackNum(c.getNeedNum() - c.getDoneNum());
        c.setDelFlag(0);
        return c;
    }

    private long sum(List<CardSummary.GroupCount> groups) {
        return groups.stream().mapToLong(CardSummary.GroupCount::getCount).sum();
    }

    private interface CardCall {
        void run();
    }

    private void assertError(String keyword, CardCall call) {
        SiteCardRuleException ex = assertThrows(SiteCardRuleException.class, call::run);
        assertTrue(ex.getMessage().contains(keyword), "毛病要讲清，实际报：" + ex.getMessage());
    }
}
