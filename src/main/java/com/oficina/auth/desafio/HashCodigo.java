package com.oficina.auth.desafio;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public class HashCodigo {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    public HashCodigo(String segredo) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Segredo do código precisa de no mínimo 32 bytes.");
        }
        this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    public String calcular(String desafioId, String codigo) {
        try {
            var mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            var assinatura = mac.doFinal((desafioId + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(assinatura);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível.", e);
        }
    }

    public boolean confere(String desafioId, String codigo, String hashEsperado) {
        return MessageDigest.isEqual(
            calcular(desafioId, codigo).getBytes(StandardCharsets.UTF_8),
            hashEsperado.getBytes(StandardCharsets.UTF_8));
    }
}
