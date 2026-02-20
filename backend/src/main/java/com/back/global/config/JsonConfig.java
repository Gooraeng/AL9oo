package com.back.global.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.net.URI;
import java.util.Map;

@Configuration
public class JsonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        // ProblemDetail 커스텀 직렬화 모듈
        SimpleModule module = new SimpleModule();
        module.addSerializer(ProblemDetail.class, new ProblemDetailSerializer());

        return builder -> builder
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModule(module);
    }

    /**
     * ProblemDetail을 flat한 구조로 직렬화하는 커스텀 Serializer
     * properties 중첩 없이 최상위 레벨에 모든 필드 출력
     */
    private static class ProblemDetailSerializer extends StdSerializer<ProblemDetail> {

        public ProblemDetailSerializer() {
            super(ProblemDetail.class);
        }

        @Override
        public void serialize(ProblemDetail value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
            gen.writeStartObject();

            // RFC 7807 표준 필드
            URI type = value.getType();
            if (type != null) {
                gen.writeStringProperty("type", type.toString());
            }

            String title = value.getTitle();
            if (title != null) {
                gen.writeStringProperty("title", title);
            }

            gen.writeNumberProperty("status", value.getStatus());

            String detail = value.getDetail();
            if (detail != null) {
                gen.writeStringProperty("detail", detail);
            }

            URI instance = value.getInstance();
            if (instance != null) {
                gen.writeStringProperty("instance", instance.toString());
            }

            // properties를 최상위 레벨로 flatten
            Map<String, Object> properties = value.getProperties();
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    gen.writePOJOProperty(entry.getKey(), entry.getValue());
                }
            }

            gen.writeEndObject();
        }
    }
}
