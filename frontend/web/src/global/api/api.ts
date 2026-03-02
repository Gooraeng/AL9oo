import axios, { AxiosInstance } from "axios";

const STABLE_API_VERSION = "v1";

const getApiBase = () => {
  const isDev = process.env.NODE_ENV === "development";

  const isLocalHost =
    typeof window !== "undefined" &&
    (window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1");

  if (isDev || isLocalHost) {
    return "http://localhost:8080";
  }

  if (process.env.NEXT_PUBLIC_API_BASE_URL) {
    return process.env.NEXT_PUBLIC_API_BASE_URL;
  }

  if (typeof window !== "undefined") {
    return `${window.location.protocol}//${window.location.host}`;
  }
  return "http://localhost:8080";
};

const api = axios.create({
  baseURL: getApiBase(),
  withCredentials: true,
  timeout: 10000,
}) as AxiosInstance & { STABLE_VERSION: string };

api.STABLE_VERSION = STABLE_API_VERSION;

export default api;