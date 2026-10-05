package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fc.v2.mapper.auto.TBarrSignBillMapper;
import com.fc.v2.model.auto.TBarrSignBill;
import com.fc.v2.service.impl.TBarrSignBillServiceImpl;

/**
 * 联户验收签认单核心规矩单测（不起 Spring、不连库；mapper 打桩）。
 * 盯：一名即签一口一签、两名须同判签满才齐、末口点齐才算过、
 * 否决必须落「已压回」、点齐/压回后谁都动不得、头一口退不回去。
 */
public class SignBillServiceTest {

    private TBarrSignBillServiceImpl service;
    private TBarrSignBillMapper mapper;

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrSignBillServiceImpl();
        mapper = mock(TBarrSignBillMapper.class);
        setField(service, "barrSignBillMapper", mapper);
        when(mapper.updateById(any())).thenReturn(1);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private TBarrSignBill bill(int node, int mode, Integer need, int signed, int status) {
        TBarrSignBill r = new TBarrSignBill();
        r.setId(5L);
        r.setBillNo("QR-5");
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        r.setDelFlag(0);
        when(mapper.selectById(5L)).thenReturn(r);
        return r;
    }

    @Test
    public void 一名即签_一口一签挪到末口点齐() {
        TBarrSignBill r = bill(0, 0, 1, 0, 0);

        TBarrSignBill n1 = service.approve(5L, "wang", "");
        assertEquals(1, n1.getNodeNo(), "一笔签齐即挪下一口");
        assertEquals(0, n1.getSignCount());
        assertEquals(0, n1.getStatus());

        service.approve(5L, "wang", "");
        assertEquals(2, r.getNodeNo());

        TBarrSignBill passed = service.approve(5L, "wang", "");
        assertEquals(2, passed.getNodeNo(), "末口不再往前挪");
        assertEquals(1, passed.getStatus(), "末口点齐全单过");
    }

    @Test
    public void 两名须同判_签满应签人数本口才算齐() {
        TBarrSignBill r = bill(0, 1, 2, 0, 0);

        TBarrSignBill first = service.approve(5L, "wang", "");
        assertEquals(0, first.getNodeNo(), "只签一笔，两名同判口不算齐，留在本口");
        assertEquals(1, first.getSignCount());
        assertEquals(0, first.getStatus());

        TBarrSignBill second = service.approve(5L, "li", "");
        assertEquals(1, second.getNodeNo(), "第二笔签满才挪口");
        assertEquals(0, second.getSignCount());
    }

    @Test
    public void 同判口应签人数空着按一笔接住() {
        bill(0, 1, null, 0, 0);
        TBarrSignBill once = service.approve(5L, "wang", "");
        assertEquals(1, once.getNodeNo(), "应签人数空着不当异常，按一笔签齐挪口");
    }

    @Test
    public void 否决必须落下已压回_不许空走一趟() {
        TBarrSignBill r = bill(1, 0, 1, 0, 0);
        TBarrSignBill vetoed = service.reject(5L, "zhao", "不符");
        assertEquals(2, vetoed.getStatus(), "压回就是压回");

        // 已点齐、已压回的单子谁都不许再签再压
        assertNull(service.approve(5L, "wang", ""), "已压回不许再签");
        assertNull(service.reject(5L, "zhao", ""), "已压回不许再压");
        assertEquals(2, r.getStatus());
    }

    @Test
    public void 点齐之后签批压回一律拦下() {
        bill(2, 0, 1, 0, 1);
        assertNull(service.approve(5L, "wang", ""));
        assertNull(service.reject(5L, "wang", ""));
    }

    @Test
    public void 没有签字人不受理() {
        bill(0, 0, 1, 0, 0);
        assertNull(service.approve(5L, "  ", ""));
        assertNull(service.reject(5L, null, ""));
    }

    @Test
    public void 退回上一口_头一口退不回() {
        TBarrSignBill r = bill(2, 1, 2, 1, 0);
        TBarrSignBill back = service.rollback(5L, "补签");
        assertEquals(1, back.getNodeNo());
        assertEquals(0, back.getSignCount(), "退回后本口已签数清零重来");
        assertEquals(0, back.getStatus());

        service.rollback(5L, "");
        assertEquals(0, r.getNodeNo());
        assertNull(service.rollback(5L, ""), "头一口前面没有口子");
        assertEquals(0, r.getNodeNo());
    }

    @Test
    public void 点齐压回的单子退不回来() {
        // 已点齐：终态不可退
        bill(2, 0, 1, 0, 1);
        assertNull(service.rollback(5L, ""), "已点齐的单子退不回来");
    }
}
