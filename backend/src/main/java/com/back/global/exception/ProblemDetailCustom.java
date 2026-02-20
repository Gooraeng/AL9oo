package com.back.global.exception;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;

public class ProblemDetailCustom {

    // Force to use static class
    private ProblemDetailCustom() {}

    public static ProblemDetail of(ErrorCode code, HttpServletRequest request, Object... args) {
        String errorMessage = (args == null || args.length == 0)
                ? code.getMessage()
                : String.format(code.getMessage() + ": %s", args);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.getHttpStatus(), errorMessage);

        pd.setTitle(code.name());
        pd.setType(URI.create("about:blank"));
        setCommonProperties(pd, request, code.getCode());

        return pd;
    }

    public static ProblemDetail of(
            HttpStatus status,
            HttpServletRequest request,
            String title,
            String detail,
            String code,
            Map<String, Object> properties
    ) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);

        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));
        setCommonProperties(pd, request, code);
        if (properties != null) properties.forEach(pd::setProperty);

        return pd;
    }

    private static void setCommonProperties(ProblemDetail pd, HttpServletRequest request, String code) {
        pd.setProperty("timestamp", OffsetDateTime.now().toString());

        if (code != null) pd.setProperty("code", code);

        if (request != null) {
            pd.setInstance(URI.create(request.getRequestURI()));
        }

        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);
    }
}
