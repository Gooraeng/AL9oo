import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";

export const API_VERSION = "v1";

const getApiBase = () => {
  const isDev = process.env.NODE_ENV === "development";

  const isLocalHost =
    typeof window !== "undefined" &&
    (window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1");

  if (isDev || isLocalHost) {
    return "http://localhost:3000";
  }

  if (process.env.NEXT_PUBLIC_API_BASE_URL) {
    return process.env.NEXT_PUBLIC_API_BASE_URL;
  }

  if (typeof window !== "undefined") {
    return `${window.location.protocol}//${window.location.host}`;
  }

  return "http://localhost:3000";
};

export const api = axios.create({
  baseURL: getApiBase(),
  withCredentials: true,
  timeout: 10000,
  //   headers: {
  //     "Content-Type": "application/json",
  //     accept: "application/json",
  //   },
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const response = error.response;

    if (!response) return Promise.reject(error);

    // error response exists
    // and its body refers problem details
    /**
     * {
     *    "type": "about:blank",
     *    "title": "ACCESS_DENIED",
     *    "status": 403,
     *    "detail": "Access Denied: ...",
     *    "instance": "api path",
     *    "timestamp": "yyyy-mm-dd HH:MM:SS",
     *    "code": "error code"
     * }
     */
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _is_retry?: boolean;
    };

    if (
      response.status === 401 &&
      originalRequest &&
      !originalRequest._is_retry
    ) {
      originalRequest._is_retry = true;

      try {
        const refreshTokenResponse = await api.post(
          `/api/${API_VERSION}/auth/session/refresh-token`,
        );

        if (refreshTokenResponse.status === 200) {
          return api(originalRequest);
        }
      } catch (refreshTokenIssueFailedError: unknown) {
        window.location.href = getApiBase() + "/login";
        return Promise.reject(refreshTokenIssueFailedError);
      }
    }

    // if (status === 401) {
    //   if (typeof window !== "undefined") {
    //     window.location.href = "/login";
    //   }
    // }

    return Promise.reject(error);
  },
);
