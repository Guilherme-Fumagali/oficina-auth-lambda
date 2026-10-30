package com.oficina.auth.identidade;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.oficina.auth.cliente.Cliente;
import com.oficina.auth.cliente.StatusCliente;
import com.oficina.auth.cpf.Cpf;
import com.oficina.auth.funcionario.Funcionario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpTimeoutException;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaIdentidadeHttpTest {

    private static final String CHAVE = "chave-interna-de-teste";
    private static final String CPF = "52998224725";
    private static final String CAMINHO_CLIENTE = "/internal/identidades/clientes/" + CPF;
    private static final String CAMINHO_FUNCIONARIO = "/internal/identidades/funcionarios/" + CPF;
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @RegisterExtension
    static final WireMockExtension OS = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private ConsultaIdentidadeHttp consulta;

    @BeforeEach
    void setUp() {
        consulta = new ConsultaIdentidadeHttp(OS.baseUrl(), CHAVE);
    }

    @Test
    void clienteEncontradoViraCadastroComEmail() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson(identidade("ATIVO"))));

        var cadastro = consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)).orElseThrow();

        assertThat(cadastro.identidade()).isEqualTo(new Cliente(ID, "João Ativo", StatusCliente.ATIVO));
        assertThat(cadastro.identidade().podeAutenticar()).isTrue();
        assertThat(cadastro.email()).isEqualTo("joao@example.com");
    }

    @Test
    void enviaAChaveInternaNoCabecalho() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson(identidade("ATIVO"))));

        consulta.buscar(Papel.CLIENTE, Cpf.de(CPF));

        OS.verify(1, getRequestedFor(urlEqualTo(CAMINHO_CLIENTE))
            .withHeader(ConsultaIdentidadeHttp.CABECALHO_CHAVE, equalTo(CHAVE)));
    }

    @Test
    void clienteBloqueadoNaoPodeAutenticar() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson(identidade("BLOQUEADO"))));

        var identidade = consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)).orElseThrow().identidade();

        assertThat(identidade.podeAutenticar()).isFalse();
    }

    @Test
    void funcionarioUsaORecursoDeFuncionarios() {
        OS.stubFor(get(CAMINHO_FUNCIONARIO).willReturn(okJson(identidade("ATIVO"))));

        var cadastro = consulta.buscar(Papel.FUNCIONARIO, Cpf.de(CPF)).orElseThrow();

        assertThat(cadastro.identidade()).isEqualTo(new Funcionario(ID, "João Ativo", true));
        assertThat(cadastro.identidade().papel()).isEqualTo(Papel.FUNCIONARIO);
    }

    @Test
    void funcionarioInativoNaoPodeAutenticar() {
        OS.stubFor(get(CAMINHO_FUNCIONARIO).willReturn(okJson(identidade("INATIVO"))));

        assertThat(consulta.buscar(Papel.FUNCIONARIO, Cpf.de(CPF)).orElseThrow().identidade().podeAutenticar())
            .isFalse();
    }

    @Test
    void cpfSemCadastroDevolveVazio() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(aResponse().withStatus(404)));

        assertThat(consulta.buscar(Papel.CLIENTE, Cpf.de(CPF))).isEmpty();
    }

    @Test
    void chaveRecusadaPeloOsServiceViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class)
            .hasMessageContaining("401");
    }

    @Test
    void erroDoOsServiceViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class)
            .hasMessageContaining("500");
    }

    @Test
    void respostaAcimaDeDoisSegundosViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson(identidade("ATIVO")).withFixedDelay(3_000)));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class)
            .hasCauseInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void osServiceForaDoArViraIndisponibilidade() {
        var semServidor = new ConsultaIdentidadeHttp("http://127.0.0.1:1", CHAVE);

        assertThatThrownBy(() -> semServidor.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class);
    }

    @Test
    void respostaMalformadaViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson("{\"id\":")));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class);
    }

    @Test
    void respostaSemIdentificadorViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson("{\"nome\":\"João\",\"status\":\"ATIVO\"}")));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class);
    }

    @Test
    void identificadorForaDoFormatoUuidViraIndisponibilidade() {
        OS.stubFor(get(CAMINHO_CLIENTE).willReturn(okJson(
            "{\"id\":\"42\",\"nome\":\"João\",\"email\":\"joao@example.com\",\"status\":\"ATIVO\"}")));

        assertThatThrownBy(() -> consulta.buscar(Papel.CLIENTE, Cpf.de(CPF)))
            .isInstanceOf(ConsultaIdentidadeException.class);
    }

    private static String identidade(String status) {
        return """
            {"id":"%s","nome":"João Ativo","email":"joao@example.com","status":"%s","cpfCnpj":"%s"}
            """.formatted(ID, status, CPF);
    }
}
