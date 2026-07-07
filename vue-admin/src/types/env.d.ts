/// <reference types="vite/client" />

declare module "*.vue" {
  import { DefineComponent } from "vue";
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

interface ImportMetaEnv {
  /** 应用端口 */
  VITE_APP_PORT: string;
  /** API 基础路径 */
  VITE_APP_BASE_API: string;
  VITE_APP_API_URL: string;
  VITE_MOCK_DEV_SERVER: boolean;
  VITE_OAUTH_ISSUER: string;
  VITE_OAUTH_BASE_API: string;
  VITE_OAUTH_CLIENT_ID: string;
  VITE_OAUTH_CLIENT_SECRET: string;
  VITE_OAUTH_PASSWORD_CLIENT_ID: string;
  VITE_OAUTH_PASSWORD_CLIENT_SECRET: string;
  VITE_OAUTH_REDIRECT_URI: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
