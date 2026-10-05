package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TBarrCaseFlowMapper;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.impl.TBarrCaseFlowServiceImpl;

/**
 * 一户一档改造卷宗流转核心规矩单测（不起 Spring、不连库；mapper 打桩）。
 * 盯四件事：格次一格一格走、情形只由格次一处派生、结清封住后四条改动路一律拦下、
 * 删除只打标记不硬删。
 */
public class CaseFlowServiceTest {

    private TBarrCaseFlowServiceImpl service;
    private TBarrCaseFlowMapper mapper;

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrCaseFlowServiceImpl();
        mapper = mock(TBarrCaseFlowMapper.class);
        setField(service, "barrCaseFlowMapper", mapper);
        when(mapper.updateById(any())).thenReturn(1);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private TBarrCaseFlow flow(int stage, int status) {
        TBarrCaseFlow r = new TBarrCaseFlow();
        r.setId(7L);
        r.setBizNo("JZ-7");
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        when(mapper.selectOne(any())).thenReturn(r);
        when(mapper.selectById(7L)).thenReturn(r);
        return r;
    }

    @Test
    public void 推进只走一格_情形跟着格次同出一处() {
        TBarrCaseFlow r = flow(0, 0);
        TBarrCaseFlow one = service.advance(7L, "送核卷");
        assertEquals(1, one.getStage());
        assertEquals(1, one.getStatus(), "走到中间格即「在办」，不许两处各记各的");
        assertTrue(one.getContent().contains("[1]送核卷"), "一格一记：" + one.getContent());

        service.advance(7L, "评估过");
        assertEquals(2, r.getStage());
        assertEquals(1, r.getStatus());

        TBarrCaseFlow terminal = service.advance(7L, "验收结清");
        assertEquals(3, terminal.getStage());
        assertEquals(2, terminal.getStatus(), "走到结清即封住，情形与格次对得住");
    }

    @Test
    public void 不准跨格推进也不准走过头() {
        TBarrCaseFlow r = flow(2, 1);
        service.advance(7L, "到结清");
        assertEquals(3, r.getStage());
        // 封住之后再推：请不动
        assertNull(service.advance(7L, "还想往前"));
        assertEquals(3, r.getStage());
    }

    @Test
    public void 结清封住后四条改动路一条都走不通() {
        flow(3, 2);
        assertNull(service.advance(7L, "推进"), "封住后推进要拦下");
        assertNull(service.rollback(7L, "回退"), "封住后回退要拦下");
        assertFalse(service.updateContent(7L, "再补一句"), "封住后改记要要拦下");
        assertFalse(service.remove(7L), "封住后撤单要拦下");
        verify(mapper, never()).deleteById(any());
    }

    @Test
    public void 回退只退一格_头一格无路可退() {
        TBarrCaseFlow r = flow(2, 1);
        TBarrCaseFlow back = service.rollback(7L, "退评估");
        assertEquals(1, back.getStage());
        assertEquals(1, back.getStatus());

        service.rollback(7L, "退核卷");
        assertEquals(0, r.getStage());
        assertEquals(0, r.getStatus(), "退回头一格即「未起」");
        assertNull(service.rollback(7L, "再退无路"), "头一格前面没有格");
        assertEquals(0, r.getStage());
    }

    @Test
    public void 撤单只打标记_册子上仍翻得着() {
        flow(1, 1);
        assertTrue(service.remove(7L));
        verify(mapper).updateById(any());
        verify(mapper, never()).deleteById(any());

        // 已撤下的单子：selectOne 翻不出来，动不得
        when(mapper.selectOne(any())).thenReturn(null);
        assertNull(service.advance(7L, "x"));
        assertFalse(service.remove(7L));
    }

    @Test
    public void 回查时情形也按格次当场接住() {
        TBarrCaseFlow legacy = new TBarrCaseFlow();
        legacy.setId(9L);
        legacy.setStage(3);
        legacy.setStatus(1); // 库里两处对不上：格次已结清、情形还记在办
        when(mapper.selectById(9L)).thenReturn(legacy);
        TBarrCaseFlow shown = service.selectTBarrCaseFlowById(9L);
        assertEquals(2, shown.getStatus(), "回查以格次为准，把两处拧回同一处");
    }

    @Test
    public void 台账只列在册卷宗_情形也按格次接住() {
        TBarrCaseFlow a = new TBarrCaseFlow();
        a.setId(1L);
        a.setStage(2);
        a.setStatus(2); // 两处对不上：格次在验收、情形却记封住
        a.setDelFlag(0);
        TBarrCaseFlow removed = new TBarrCaseFlow();
        removed.setId(2L);
        removed.setStage(1);
        removed.setDelFlag(1); // 桩里即便混入已撤行，服务也守一道（查询条件已带 del_flag=0）
        when(mapper.selectList(any())).thenReturn(java.util.Arrays.asList(a, removed));

        java.util.List<TBarrCaseFlow> list =
                service.selectTBarrCaseFlowList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TBarrCaseFlow>());
        assertEquals(2, list.size());
        // 回显的情形一律以格次当场派生，两处拧成一处
        assertEquals(1, list.get(0).getStatus());
        // 传给册子的查询条件必须钉死 del_flag=0
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TBarrCaseFlow>> cap =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(mapper).selectList(cap.capture());
        assertTrue(cap.getValue().getTargetSql().contains("del_flag")
                || cap.getValue().getSqlSegment().contains("del_flag"), "台账必须只查在册卷宗");
    }
}
