package com.ht.robotvacuum.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ReqUpdateAlertDTO(@JsonProperty(value = "readAt", required = true) Instant readAt) {
}
