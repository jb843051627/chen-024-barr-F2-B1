-- barr 无障碍环境建设与适老化改造补助监督 -- schema (chen-024)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_024

CREATE TABLE IF NOT EXISTS t_barr_arch (
  id bigint NOT NULL COMMENT '主键',
  arch_no varchar(64) DEFAULT NULL COMMENT '改造点位底册代号',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—区—街道)',
  status int DEFAULT NULL COMMENT '底册情形 0在册 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造点位底册';

CREATE TABLE IF NOT EXISTS t_barr_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '一户一档改造卷宗号',
  stage int DEFAULT NULL COMMENT '当前格次 0..3（核卷/评估/验收/结清）',
  status int DEFAULT NULL COMMENT '卷宗走到哪一格 0未起 1在办 2已封住',
  content varchar(255) DEFAULT NULL COMMENT '一格一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪格动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一户一档改造卷宗';

CREATE TABLE IF NOT EXISTS t_barr_pay_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '月度补助核销册码',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的改造进度卡代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行请领补助额',
  quali_num decimal(12,2) DEFAULT NULL COMMENT '本行验收合格处数',
  pay_kind varchar(32) DEFAULT NULL COMMENT '拨付口径（区级／市级两条来路）',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已送回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补助核销行';

CREATE TABLE IF NOT EXISTS t_barr_sign_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '联户验收签认单',
  node_no int DEFAULT NULL COMMENT '当前所在口 0..2（住户/到场两方/两级残联）',
  sign_mode int DEFAULT NULL COMMENT '本口算齐办法 0一名即签 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本口应签人数',
  sign_count int DEFAULT NULL COMMENT '本口已签人数',
  status int DEFAULT NULL COMMENT '签认情形 0在签 1已点齐 2已压回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联户验收签认单';

CREATE TABLE IF NOT EXISTS t_barr_site_card (
  id bigint NOT NULL COMMENT '主键',
  card_no varchar(64) DEFAULT NULL COMMENT '改造进度卡代号（只由系统排号，本号不认）',
  arch_id bigint DEFAULT NULL COMMENT '挂在哪一处底册名下',
  arch_no varchar(64) DEFAULT NULL COMMENT '底册代号（以底册为准，随卡冗余）',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称（以底册为准，随卡冗余；改卡不改底）',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别（随卡冗余，以底册为准）',
  street_code varchar(40) DEFAULT NULL COMMENT '卡所在街代号（由底册属地落到街道）',
  street_name varchar(64) DEFAULT NULL COMMENT '卡所在街名（随卡冗余）',
  season varchar(8) DEFAULT NULL COMMENT '本卡所属季（如 2026Q4，立卡时定死）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位或住户代号',
  need_num int DEFAULT NULL COMMENT '应改处数',
  done_num int DEFAULT NULL COMMENT '已改处数',
  lack_num int DEFAULT NULL COMMENT '还差几处（轧出来，不手填：need-done）',
  content varchar(255) DEFAULT NULL COMMENT '随卡交来的改造要件',
  source int DEFAULT 0 COMMENT '立卡来路 0亲手立 1当月验收带出（两条入口同一套数法）',
  stage int DEFAULT 0 COMMENT '已走完的级数 0新录 1已改齐(缺项) 2收口（旧卡空值按0接住）',
  status int DEFAULT 0 COMMENT '进展档 0新录 1已改齐 2缺项（按级数与处数由系统数出，不许手填）',
  closed int DEFAULT 0 COMMENT '是否收口 0在走 1收口到头（旧卡空值按在走接住）',
  verify_code varchar(64) DEFAULT NULL COMMENT '核验串（系统落；旧卡头一回被流程接住时补）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '谁立的（递卡人登录身份自动落，不接手填）',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  active_site varchar(64) GENERATED ALWAYS AS (CASE WHEN COALESCE(closed,0) = 0 AND del_flag = 0 THEN site_no ELSE NULL END) STORED COMMENT '活卡占位：同点位同时只容一张在走卡',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no),
  UNIQUE KEY uk_site_active (active_site),
  KEY idx_street_season (street_code, season),
  KEY idx_arch_kind (arch_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡';

-- 卡代号系统取号（一季一排，本子自带号一概不认）
CREATE TABLE IF NOT EXISTS t_barr_card_seq (
  season varchar(8) NOT NULL COMMENT '季码 如 2026Q4',
  next_val int NOT NULL DEFAULT 0 COMMENT '下一发号值',
  PRIMARY KEY (season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡取号表';

-- 卡流转留痕（推进/回退/收口一记一行；回退旧笔留底，不与后补之笔并排）
CREATE TABLE IF NOT EXISTS t_barr_card_log (
  id bigint NOT NULL COMMENT '主键',
  card_id bigint NOT NULL COMMENT '改造进度卡主键',
  card_no varchar(64) DEFAULT NULL COMMENT '当时卡代号',
  action varchar(32) NOT NULL COMMENT '动作 create/advance/revert/close/edit',
  from_stage int DEFAULT NULL COMMENT '动前级数',
  to_stage int DEFAULT NULL COMMENT '动后级数',
  step_note varchar(255) DEFAULT NULL COMMENT '这一步落下的一笔（一格一记）',
  operator varchar(64) DEFAULT NULL COMMENT '经办人（登录身份）',
  create_time datetime DEFAULT NULL COMMENT '动作时间',
  PRIMARY KEY (id),
  KEY idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡流转留痕';

-- 用户可见街道授权：街道岗一行；部岗可多行（只看得见划给它的那几条街）
CREATE TABLE IF NOT EXISTS t_barr_user_street (
  id bigint NOT NULL COMMENT '主键',
  user_id bigint NOT NULL COMMENT '登录用户',
  street_code varchar(40) NOT NULL COMMENT '授权可见街道代号',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_street (user_id, street_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户街道可见范围';

CREATE TABLE IF NOT EXISTS t_barr_tier_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '入户评估定档线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  scene_kind varchar(32) DEFAULT NULL COMMENT '这条线管公共点位还是居家适老',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算分',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线分',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶分',
  eff_start datetime DEFAULT NULL COMMENT '起用那一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一日(不含)',
  priority int DEFAULT NULL COMMENT '交班次序(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在位 1已卸下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入户评估定档线';

CREATE TABLE IF NOT EXISTS t_barr_visit_item (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '质保与养护复检条目代号',
  due_at datetime DEFAULT NULL COMMENT '该上门那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '最多许提前几日',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对住户的记要',
  status int DEFAULT NULL COMMENT '条目情形 0搁着 1已递出 2递不动',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质保与养护复检条目';

-- 初始底册档案数据：在册一条（DC00·小区楼组类）、已停用一条（DC01·公共点位类）
INSERT IGNORE INTO t_barr_arch (id, arch_no, arch_name, arch_kind, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'DC00', '浈县区白沙街道·祥和楼组片区（小区楼组类，在册）', '小区楼组', '岫岭省—柏州市—浈县区—白沙街道', 0, 0, 'seed', NOW()),
       (1, 'DC01', '梅坂区藕塘街道·沿河南岸公共通道（已并走停用）', '公共点位', '岫岭省—柏州市—梅坂区—藕塘街道', 1, 0, 'seed', NOW());

-- 两条街种子（底册属地最后一段与街名对齐，卡挂街由此落）
INSERT IGNORE INTO t_sys_street (id, street_code, area_code, street_name, short_name, sort, data_state, create_by, create_time)
VALUES (8024001001, 'BSJD', 'ZXQ', '白沙街道', '白沙', 1, 0, 'seed', NOW()),
       (8024001002, 'OTJD', 'MBQ', '藕塘街道', '藕塘', 2, 0, 'seed', NOW());

-- 菜单与按钮权限（挂在系统管理目录 411522822607867904 下）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001101, '改造进度卡', '改造进度卡展示', '/BarrSiteCardController/view', 0, 411522822607867904, 'barr:barrSiteCard:view', 1, 'fa fa-clipboard', 90, 0, 'seed', NOW()),
       (8024001102, '改造进度卡集合', '改造进度卡集合', '/BarrSiteCardController/list', 0, 8024001101, 'barr:barrSiteCard:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001103, '改造进度卡立卡', '一处一张亲手立/验收带出', '/BarrSiteCardController/add', 0, 8024001101, 'barr:barrSiteCard:add', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001104, '改造进度卡改口', '改造进度卡改口编辑', '/BarrSiteCardController/edit', 0, 8024001101, 'barr:barrSiteCard:edit', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001105, '改造进度卡删除', '改造进度卡删除', '/BarrSiteCardController/remove', 0, 8024001101, 'barr:barrSiteCard:remove', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001106, '改造进度卡推进', '走完一级', '/BarrSiteCardController/advance', 0, 8024001101, 'barr:barrSiteCard:advance', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001107, '改造进度卡回退', '已改齐退回一步重来', '/BarrSiteCardController/revert', 0, 8024001101, 'barr:barrSiteCard:revert', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001108, '改造进度卡收口', '走到收口到头', '/BarrSiteCardController/close', 0, 8024001101, 'barr:barrSiteCard:close', 2, NULL, NULL, 0, 'seed', NOW());

-- 街道岗 / 部岗：两岗都能看卡立卡改口；删除、推进、回退、收口归部岗
INSERT IGNORE INTO t_sys_role (id, name) VALUES (8024001201, '无障碍街道岗'), (8024001202, '无障碍部岗');
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
VALUES (8024001301, 8024001201, 8024001101),
       (8024001302, 8024001201, 8024001102),
       (8024001303, 8024001201, 8024001103),
       (8024001304, 8024001201, 8024001104),
       (8024001401, 8024001202, 8024001101),
       (8024001402, 8024001202, 8024001102),
       (8024001403, 8024001202, 8024001103),
       (8024001404, 8024001202, 8024001104),
       (8024001405, 8024001202, 8024001105),
       (8024001406, 8024001202, 8024001106),
       (8024001407, 8024001202, 8024001107),
       (8024001408, 8024001202, 8024001108);

-- 管理员（id=1）给全部按钮权限，并兼部岗、两条街都看得见
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
SELECT 8024001500 + n, 488243256161730560, 8024001101 + n
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) t;
INSERT IGNORE INTO t_sys_role_user (id, sys_user_id, sys_role_id) VALUES (8024001601, 1, 8024001202);
INSERT IGNORE INTO t_barr_user_street (id, user_id, street_code, create_by, create_time)
VALUES (8024001701, 1, 'BSJD', 'seed', NOW()),
       (8024001702, 1, 'OTJD', 'seed', NOW());

