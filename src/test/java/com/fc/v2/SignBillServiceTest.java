package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.fc.v2.mapper.auto.TBarrSignBillMapper;
import com.fc.v2.model.auto.TBarrSignBill;
import com.fc.v2.service.impl.TBarrSignBillServiceImpl;

/**
 * 联户验收签认单核心规矩单测（不起 Spring、不连库；mapper 全打桩）。
 * 一名即签与两名须同判两种算齐办法、点齐挪口、末口落定、到了头的单子三条路同一处拦。
 */
public class SignBillServiceTest {

    private TBarrSignBillServiceImpl service;
    private TBarrSignBillMapper signBillMapper;

    private final List<TBarrSignBill> store = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrSignBillServiceImpl();
        signBillMapper = mock(TBarrSignBillMapper.class);
        setField(service, "barrSignBillMapper", signBillMapper);

        when(signBillMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TBarrSignBill r : store) {
                if (r.getId() != null && r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
        when(signBillMapper.updateById(any())).thenAnswer(inv -> {
            TBarrSignBill r = inv.getArgument(0);
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

    private TBarrSignBill bill(long id, Integer node, Integer mode, Integer need, Integer signed, Integer status) {
        TBarrSignBill r = new TBarrSignBill();
        r.setId(id);
        r.setBillNo("QD-" + id);
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        r.setDelFlag(0);
        store.add(r);
        return r;
    }

    @Test
    public void 一名即签_一票挪一口_末口点齐落定() {
        TBarrSignBill r = bill(1L, 0, 0, 1, 0, 0);

        service.approve(1L, "住户张", null);
        assertEquals(1, r.getNodeNo());
        assertEquals(0, r.getSignCount(), "挪口后已签清零");
        assertEquals(0, r.getStatus());

        service.approve(1L, "到场甲", null);
        assertEquals(2, r.getNodeNo());
        assertEquals(0, r.getStatus());

        service.approve(1L, "残联乙", null);
        assertEquals(1, r.getStatus(), "末口点齐整单落已点齐");
        assertEquals(2, r.getNodeNo(), "落定后口不再挪");
    }

    @Test
    public void 两名须同判_点齐两票才挪口() {
        TBarrSignBill r = bill(1L, 0, 1, 2, 0, 0);

        service.approve(1L, "到场甲", null);
        assertEquals(0, r.getNodeNo(), "头一票不够，口不挪");
        assertEquals(1, r.getSignCount());
        assertEquals(0, r.getStatus());

        service.approve(1L, "到场乙", null);
        assertEquals(1, r.getNodeNo(), "两票点齐才挪口");
        assertEquals(0, r.getSignCount(), "挪口后已签清零重起");
    }

    @Test
    public void 应签人数空着_同判按两名兜底() {
        TBarrSignBill r = bill(1L, 0, 1, null, 0, 0);

        service.approve(1L, "到场甲", null);
        assertEquals(0, r.getNodeNo());
        service.approve(1L, "到场乙", null);
        assertEquals(1, r.getNodeNo(), "应签空着按两名：两票点齐挪口");
    }

    @Test
    public void 否决落压回_压回后签不动退不动() {
        TBarrSignBill r = bill(1L, 1, 0, 1, 0, 0);

        service.reject(1L, "残联丙", "要件不齐");
        assertEquals(2, r.getStatus(), "否决即压回");

        assertNull(service.approve(1L, "住户张", null));
        assertNull(service.reject(1L, "残联丙", null));
        assertNull(service.rollback(1L, "重来"));
        assertEquals(2, r.getStatus());
        assertEquals(1, r.getNodeNo());
    }

    @Test
    public void 点齐之后三条路同一处拦() {
        TBarrSignBill r = bill(1L, 2, 0, 1, 0, 1);

        assertNull(service.approve(1L, "再来一票", null));
        assertNull(service.reject(1L, "想压回", null));
        assertNull(service.rollback(1L, "想退回"));
        assertEquals(1, r.getStatus());
        assertEquals(2, r.getNodeNo());
    }

    @Test
    public void 退回上一环节_本口已签清零_头一口没处退() {
        TBarrSignBill r = bill(1L, 1, 1, 2, 1, 0);

        service.rollback(1L, "要件重核");
        assertEquals(0, r.getNodeNo());
        assertEquals(0, r.getSignCount(), "退回一口，已签的票清零重签");
        assertEquals(0, r.getStatus());

        assertNull(service.rollback(1L, "再退"));
        assertEquals(0, r.getNodeNo());
    }

    @Test
    public void 签批人空着或单子找不着_一概回绝() {
        bill(1L, 0, 0, 1, 0, 0);

        assertNull(service.approve(1L, " ", null));
        assertNull(service.approve(1L, null, null));
        assertNull(service.reject(1L, "", null));
        assertNull(service.approve(99L, "住户张", null));
        assertNull(service.reject(99L, "残联丙", null));
        assertNull(service.rollback(99L, null));

        TBarrSignBill r = store.get(0);
        assertEquals(0, r.getNodeNo());
        assertEquals(0, r.getStatus());
        assertEquals(0, r.getSignCount());
    }
}
