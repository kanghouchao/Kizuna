import axios from 'axios';
import Cookies from 'js-cookie';
import { redirectToLogin } from '@/shared/lib';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
});

apiClient.interceptors.request.use(
  config => {
    const token = Cookies.get('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    const csrfToken = Cookies.get('XSRF-TOKEN') || Cookies.get('X-CSRF-TOKEN');
    if (csrfToken) {
      (config.headers as any)['X-XSRF-TOKEN'] = csrfToken;
    }
    // Attach role and tenant context from middleware cookies
    try {
      const role = Cookies.get('x-mw-role');
      if (role) {
        (config.headers as any)['X-Role'] = role;
        if (role.toLowerCase() === 'tenant') {
          const tenantId = Cookies.get('x-mw-tenant-id');
          if (tenantId) {
            (config.headers as any)['X-Tenant-ID'] = tenantId;
          }
        }
      }
    } catch {
      // noop
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  response => {
    return response;
  },
  error => {
    if (error.response?.status === 401) {
      Cookies.remove('token');
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        redirectToLogin();
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;
