package com.riskplatform.risk;

public class QuantEngineException extends RuntimeException {

    private final Integer downstreamStatus; // null if no response was ever received (timeout, connection refused, etc.)

    public QuantEngineException(Integer downstreamStatus, Throwable cause) {
        super(downstreamStatus == null
                ? "Quant engine communication failed"
                : "Quant engine returned HTTP " + downstreamStatus, cause);
        this.downstreamStatus = downstreamStatus;
    }

    public Integer getDownstreamStatus() {
        return downstreamStatus;
    }
}