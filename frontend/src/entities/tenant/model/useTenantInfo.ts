import { useState, useEffect } from 'react';

export interface ResolvedTenantInfo {
  domain: string;
  tenant_id: string;
  tenant_name: string;
}

// 统一通过中心端点 /api/tenants/{domain} 解析租户
export function useTenantInfo() {
  const [tenant, setTenant] = useState<ResolvedTenantInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const run = async () => {
      try {
        setLoading(true);
        setError(null);
        const path = window.location.pathname;
        if (path.startsWith('/central')) {
          setTenant(null);
          return;
        }
        const hostname = window.location.hostname;
        const apiBase = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '');
        const res = await fetch(`${apiBase}/tenants/${hostname}`, {
          headers: { Accept: 'application/json' },
        });
        if (res.status === 404) {
          setTenant(null);
          setError('租户不存在');
          return;
        }
        if (!res.ok) {
          setTenant(null);
          setError('获取租户失败');
          return;
        }
        const data = await res.json();
        if (data.success) {
          setTenant({
            domain: data.domain,
            tenant_id: data.tenant_id,
            tenant_name: data.tenant_name,
          });
        } else {
          setTenant(null);
          setError('租户数据无效');
        }
      } catch (e) {
        setTenant(null);
        setError('网络错误');
      } finally {
        setLoading(false);
      }
    };
    run();
  }, []);

  return { tenant, loading, error };
}
