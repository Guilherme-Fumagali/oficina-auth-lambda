package com.oficina.auth.handler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class RelogioAjustavel extends Clock {

    private Instant agora;

    RelogioAjustavel(Instant agora) {
        this.agora = agora;
    }

    void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
