package com.oficina.auth.token;

import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.funcionario.Funcionario;
import com.oficina.auth.cpf.Cpf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenTest {

    private static final String SEGREDO = "chave-de-teste-super-longa-para-hmac-sha-256-minimo-32-chars";
    private static final String OUTRO   = "outra-chave-completamente-diferente-com-32-chars-no-minimo!!";

    private final TokenIssuer emissor = new TokenIssuer(SEGREDO);
    private final TokenValidator validador = new TokenValidator(SEGREDO);

    @Test
    void tokenEmitidoDeveSerAceitoPeloValidador() {
        var id = UUID.randomUUID();
        var token = emissor.emitir(
            new Cliente(id, "João da Silva", StatusCliente.ATIVO), Cpf.de("52998224725"));

        var claims = validador.validar(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.getIssuer()).isEqualTo("oficina-auth");
        assertThat(claims.get("cpf")).isEqualTo("52998224725");
        assertThat(claims.get("nome")).isEqualTo("João da Silva");
        assertThat(claims.get("role")).isEqualTo("CLIENTE");
    }

    @Test
    void papelDoTokenVemDaIdentidadeAutenticada() {
        var token = emissor.emitir(
            new Funcionario(UUID.randomUUID(), "Maria", true), Cpf.de("52998224725"));

        assertThat(validador.validar(token).orElseThrow().get("role")).isEqualTo("FUNCIONARIO");
    }

    @Test
    void subDeveSerOUuidDoClienteENaoOCpf() {
        var id = UUID.randomUUID();
        var token = emissor.emitir(
            new Cliente(id, "Maria", StatusCliente.ATIVO), Cpf.de("52998224725"));

        var claims = validador.validar(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.getSubject()).isNotEqualTo("52998224725");
    }

    @Test
    void deveRecusarTokenAssinadoComOutraChave() {
        var token = new TokenIssuer(OUTRO).emitir(
            new Cliente(UUID.randomUUID(), "Ana", StatusCliente.ATIVO), Cpf.de("52998224725"));

        assertThat(validador.validar(token)).isEmpty();
    }

    @Test
    void deveRecusarTokenComPayloadAdulterado() {
        var token = emissor.emitir(
            new Cliente(UUID.randomUUID(), "Ana", StatusCliente.ATIVO), Cpf.de("52998224725"));

        // Altera o payload mantendo header e assinatura: a verificação HMAC precisa recusar.
        var partes = token.split("\\.");
        var payloadOriginal = new String(
            java.util.Base64.getUrlDecoder().decode(partes[1]), java.nio.charset.StandardCharsets.UTF_8);
        var payloadForjado = payloadOriginal.replace("\"role\":\"CLIENTE\"", "\"role\":\"ADMIN\"");
        var adulterado = partes[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadForjado.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            + "." + partes[2];

        assertThat(payloadForjado).isNotEqualTo(payloadOriginal);
        assertThat(validador.validar(adulterado)).isEmpty();
    }

    @Test
    void deveRecusarTokenSemAssinatura() {
        var token = emissor.emitir(
            new Cliente(UUID.randomUUID(), "Ana", StatusCliente.ATIVO), Cpf.de("52998224725"));
        var partes = token.split("\\.");

        assertThat(validador.validar(partes[0] + "." + partes[1] + ".")).isEmpty();
    }

    @Test
    void deveRecusarEntradaVaziaOuNula() {
        assertThat(validador.validar(null)).isEmpty();
        assertThat(validador.validar("  ")).isEmpty();
        assertThat(validador.validar("nao-e-um-jwt")).isEmpty();
    }

    @Test
    void deveExigirSegredoComPeloMenos32Bytes() {
        assertThatThrownBy(() -> new TokenIssuer("curto-demais"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    void deveExtrairTokenDoHeaderBearerIgnorandoCapitalizacao() {
        assertThat(TokenValidator.extrairBearer("Bearer abc123")).isEqualTo("abc123");
        assertThat(TokenValidator.extrairBearer("bearer abc123")).isEqualTo("abc123");
        assertThat(TokenValidator.extrairBearer("Basic abc123")).isNull();
        assertThat(TokenValidator.extrairBearer(null)).isNull();
    }
}
