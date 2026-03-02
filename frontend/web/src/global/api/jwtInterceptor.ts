import api from "@/src/global/api/api";
import { AxiosError, InternalAxiosRequestConfig } from "axios";

/**
 * Error Response Format
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
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const response = error.response;

    if (!response) return Promise.reject(error);

    const statusCode = response.status;

    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _is_retry?: boolean;
    };

    if (
      statusCode === 401 &&
      originalRequest &&
      !originalRequest._is_retry
    ) {
      originalRequest._is_retry = true;

      try {
        const refreshTokenResponse = await api.post(
          `/api/${api.STABLE_VERSION}/auth/session/refresh-token`,
        );

        if (refreshTokenResponse.status === 200) {
          return api(originalRequest);
        }
      } catch (refreshTokenIssueFailedError: unknown) {
        window.location.href = "/login";
        return Promise.reject(refreshTokenIssueFailedError);
      }
    }

    if (statusCode === 401) {
      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
    }

    return Promise.reject(error);
  },
);