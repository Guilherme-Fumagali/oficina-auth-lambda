package com.oficina.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmacaoRequest(String desafioId, String codigo) {}
