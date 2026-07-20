export * from './model/types';
export { platformAuthApi } from './api/platform';
export { platformStaffApi } from './api/platform-staff';
export { resolvePlatformDestination } from './model/platformRouting';
export type { PlatformDestination } from './model/platformRouting';
export { hasStoreConsoleCapability } from './model/storeConsoleCapability';
export { useAuthorizedStores } from './model/useAuthorizedStores';
export { AuthProvider, useAuth } from './model/AuthContext';
