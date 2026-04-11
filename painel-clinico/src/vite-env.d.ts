/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_WS_URL: string;
  readonly VITE_API_URL: string;
  readonly VITE_HOSPITAL_NOME: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.module.css' {
  const classes: Record<string, string>;
  export default classes;
}
