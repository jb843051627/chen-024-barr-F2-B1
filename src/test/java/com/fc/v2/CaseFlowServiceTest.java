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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrCaseFlowMapper;
import com.fc.v2.model.auto.TBarrCaseFlow;
import com.fc.v2.service.impl.TBarrCaseFlowServiceImpl;

/**
 * 一户一档改造卷宗核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 * 挪格一次一格、情形由格次一处数出、封住后四条路同一处拦——逐一过堂。
 */
public class CaseFlowServiceTest {

    private TBarrCaseFlowServiceImpl service;
    private TBarrCaseFlowMapper caseFlowMapper;

    private final List<TBarrCaseFlow> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrCaseFlowServiceImpl();
        caseFlowMapper = mock(TBarrCaseFlowMapper.class);
        setField(service, "barrCaseFlowMapper", caseFlowMapper);

        when(caseFlowMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TBarrCaseFlow r : store) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(caseFlowMapper.updateById(any())).thenAnswer(inv -> {
            TBarrCaseFlow r = inv.getArgument(0);
            store.removeIf(x -> x.getId() != null && x.getId().equals(r.getId()));
            store.add(r);
            return 1;
        });
        when(caseFlowMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
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

    private TBarrCaseFlow flow(long id, Integer stage, Integer status) {
        TBarrCaseFlow r = new TBarrCaseFlow();
        r.setId(id);
        r.setBizNo("JZ-" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        store.add(r);
        return r;
    }

    @Test
    public void 挪格一次只挪一格_情形跟着格次走() {
        TBarrCaseFlow r = flow(1L, 0, 0);

        service.advance(1L, "核卷过");
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus(), "走到评估：在办");
        assertEquals("核卷过", r.getLastAction());

        service.advance(1L, "评估过");
        assertEquals(2, r.getStage());
        assertEquals(1, r.getStatus());

        service.advance(1L, "验收过");
        assertEquals(3, r.getStage());
        assertEquals(2, r.getStatus(), "走到结清：已封住");
    }

    @Test
    public void 回退一次只退一格_不一竿子清回零() {
        TBarrCaseFlow r = flow(1L, 2, 1);

        service.rollback(1L, "验收打回");
        assertEquals(1, r.getStage());
        assertEquals(1, r.getStatus());

        service.rollback(1L, "再打回");
        assertEquals(0, r.getStage());
        assertEquals(0, r.getStatus(), "退回头一格：未起");

        // 头一格前面没有可退的步
        assertNull(service.rollback(1L, "没处退"));
        assertEquals(0, r.getStage());
    }

    @Test
    public void 封住之后四条路同一处拦() {
        flow(1L, 3, 2);

        assertNull(service.advance(1L, "再挪"));
        assertNull(service.rollback(1L, "再退"));
        assertFalse(service.updateContent(1L, "再改"));
        assertFalse(service.remove(1L));

        TBarrCaseFlow r = store.get(0);
        assertEquals(3, r.getStage());
        assertEquals(2, r.getStatus());
        assertNull(r.getContent(), "已办结改不动");
        assertEquals(0, r.getDelFlag(), "已办结撤不下");
    }

    @Test
    public void 在走的卷宗改得动撤得下_撤卷只销标记() {
        TBarrCaseFlow r = flow(1L, 1, 1);

        assertTrue(service.updateContent(1L, "评估一格一记"));
        assertEquals("评估一格一记", r.getContent());

        assertTrue(service.remove(1L));
        assertEquals(1, r.getDelFlag(), "撤卷只销标记不抹行");
        // 撤下后按主键请不动、台账不再列
        assertNull(service.selectTBarrCaseFlowById(1L));
        assertTrue(service.selectTBarrCaseFlowList(new QueryWrapper<TBarrCaseFlow>()).isEmpty());
    }

    @Test
    public void 状态与格次两处记岔_以格次为唯一出处() {
        // 旧卷宗：格次在评估，情形栏却记成未起——两处不是一回事
        TBarrCaseFlow r = flow(1L, 2, 0);

        TBarrCaseFlow shown = service.selectTBarrCaseFlowById(1L);
        assertEquals(1, shown.getStatus(), "情形栏一律按格次重数：评估即在办");

        // 台账里那份与按主键翻出来的那份同一出处
        List<TBarrCaseFlow> list = service.selectTBarrCaseFlowList(new QueryWrapper<TBarrCaseFlow>());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getStatus());

        // 格次空着的旧卷按未起接住，不炸
        TBarrCaseFlow legacy = flow(2L, null, null);
        assertEquals(0, service.selectTBarrCaseFlowById(2L).getStatus());
        assertNotNull(service.advance(2L, "旧卷头一回挪"));
        assertEquals(1, legacy.getStage());
        assertEquals(1, legacy.getStatus());
    }

    @Test
    public void 找不着的单子四个口子都回绝() {
        assertNull(service.advance(99L, "x"));
        assertNull(service.rollback(99L, "x"));
        assertFalse(service.updateContent(99L, "x"));
        assertFalse(service.remove(99L));
        assertNull(service.selectTBarrCaseFlowById(99L));
    }
}
