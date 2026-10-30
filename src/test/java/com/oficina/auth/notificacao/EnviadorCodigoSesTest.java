package com.oficina.auth.notificacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnviadorCodigoSesTest {

    private static final String REMETENTE = "nao-responda@oficina.example.com";
    private static final String ENVIO = "/v2/email/outbound-emails";
    private static final ObjectMapper JSON = new ObjectMapper();

    @RegisterExtension
    static final WireMockExtension SES = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private SesV2Client ses;

    @BeforeEach
    void setUp() {
        ses = cliente(URI.create(SES.baseUrl()));
    }

    @AfterEach
    void tearDown() {
        ses.close();
    }

    @Test
    void enviaOCodigoDoRemetenteConfiguradoParaODestinatario() throws Exception {
        SES.stubFor(post(ENVIO).willReturn(okJson("{\"MessageId\":\"0100019a-teste\"}")));

        new EnviadorCodigoSes(ses, REMETENTE).enviar("joao@example.com", "João da Silva", "042137");

        var requisicoes = SES.findAll(postRequestedFor(urlEqualTo(ENVIO)));
        assertThat(requisicoes).hasSize(1);
        assertThat(requisicoes.get(0).getHeader("Authorization")).contains("/ses/aws4_request");
        var corpo = JSON.readTree(requisicoes.get(0).getBodyAsString());
        assertThat(corpo.at("/FromEmailAddress").asText()).isEqualTo(REMETENTE);
        assertThat(corpo.at("/Destination/ToAddresses").size()).isEqualTo(1);
        assertThat(corpo.at("/Destination/ToAddresses/0").asText()).isEqualTo("joao@example.com");
        assertThat(corpo.at("/Content/Simple/Subject/Data").asText()).isEqualTo("Código de acesso — Oficina");
        assertThat(corpo.at("/Content/Simple/Subject/Charset").asText()).isEqualTo("UTF-8");
        assertThat(corpo.at("/Content/Simple/Body/Text/Data").asText())
            .contains("Olá, João.", "042137", "5 minutos")
            .doesNotContain("Silva");
        assertThat(corpo.at("/Content/Simple/Body/Html").isMissingNode()).isTrue();
    }

    @Test
    void mensagemRecusadaPeloSesViraFalhaDeEnvio() {
        SES.stubFor(post(ENVIO).willReturn(aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withHeader("x-amzn-ErrorType", "MessageRejected")
            .withBody("{\"message\":\"Email address is not verified.\"}")));

        assertThatThrownBy(() -> new EnviadorCodigoSes(ses, REMETENTE).enviar("joao@example.com", "João", "123456"))
            .isInstanceOf(EnvioCodigoException.class)
            .hasCauseInstanceOf(MessageRejectedException.class);
    }

    @Test
    void sesInacessivelViraFalhaDeEnvio() {
        try (var inacessivel = cliente(URI.create("http://127.0.0.1:1"))) {
            assertThatThrownBy(() -> new EnviadorCodigoSes(inacessivel, REMETENTE)
                    .enviar("joao@example.com", "João", "123456"))
                .isInstanceOf(EnvioCodigoException.class);
        }
    }

    private static SesV2Client cliente(URI endpoint) {
        return SesV2Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("teste", "teste")))
            .build();
    }
}
