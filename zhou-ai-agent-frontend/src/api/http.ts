import axios from 'axios'

/** 与后端 `http://localhost:8123/api` 对齐；开发环境默认走 Vite 代理 `/api` */
export const apiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8123/api'

export const http = axios.create({
  baseURL: apiBase.replace(/\/$/, ''),
  timeout: 0,
})
