# Kizuna

多租户的门店运营系统（CMS/CRM/HRM）：平台方（Central）管理多个租户，每个租户是一家门店，运营自己的站点、员工、客户和订单。

## Language

### 平台与租户

**Tenant（租户）**:
平台管理语境中被管理的签约对象，与门店一一对应。拥有独立域名和站点。
_Avoid_: Shop, Organization

**Store（门店）**:
与 Tenant 是同一个对象，在门店运营语境中的自称。代码中门店侧作用域的命名前缀（StoreUser、store-orders）。
_Avoid_: Branch

**Central（平台侧）**:
平台运营方的作用域。Central 与 Store 是同一系统的两个访问作用域（权限边界），不是两个独立系统。

### 账号

**CentralUser（平台管理员）**:
平台侧的管理员账号，以 username 登录，角色目录独立于门店侧。

**StoreUser（门店用户）**:
门店的员工账号，以 email 登录，数据按租户隔离。
_Avoid_: TenantUser（旧名）

**AuthSession（认证会话）**:
一枚已签发 JWT 所代表的认证状态。登出与修改密码都走唯一的失效通道（token 黑名单）作废当前会话。
_Avoid_: Token 裸用（token 是载体，session 是概念）

### 门店运营

**Cast**:
门店在册的接待人员，HRM 的管理对象。

**Customer（客户）**:
门店的客户，CRM 的管理对象。归属于单一门店。

**Order（订单）**:
客户在门店的一次预约/接单记录，关联 Customer、Cast 和接待员（StoreUser）。
_Avoid_: Reservation, Booking

**StoreProfile（门店站点配置）**:
门店站点的品牌与外观配置（模板、logo、banner、SNS 链接等）。
_Avoid_: TenantConfig（旧名，与 SystemConfig 假同名）

### 平台设置

**SystemConfig（系统设置）**:
平台级键值设置（如 SMTP、维护模式），仅 Central 可管理。
_Avoid_: Config（裸用，易与 StoreProfile 混淆）

**Menu（菜单）**:
后台界面的导航菜单树。Central 与 Store 两个作用域共用同一概念；代码中为 menu 模块内的双实体（CentralMenu / StoreMenu，2026-07 决定不合并表）。
_Avoid_: TenantMenu（旧名）

## Open questions

- **Order.storeName**：订单上的自由文本"店名"字段。Tenant 与门店已确认 1:1，此字段的真实含义（屋号/品牌/渠道名？）待澄清后改名或移除。
- **Customer.rank / classification**：均为自由文本，等级/区分的正式取值体系未定义（DB 默认 rank='SILVER'）。UI 以文本输入暂顶，待业务确定取值集后收敛为 enum + 下拉。
