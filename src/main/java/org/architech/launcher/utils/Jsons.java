// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

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
        PRETTY = MAPPER.writerWithDefaultPrettyPrinter();
    }

    private Jsons() {}
}
