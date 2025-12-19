package org.architech.launcher.utils;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Jsons {
    public static final ObjectMapper MAPPER;
    public static final ObjectWriter PRETTY;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES включен по умолчанию с Jackson 2.12+
        // Если требуется явное включение для старых версий, используйте:
        // MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

        PRETTY = MAPPER.writerWithDefaultPrettyPrinter();
    }

    private Jsons() {}
}
