package com.oficina.auth.notificacao;

import com.oficina.auth.desafio.Desafio;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

public class EnviadorCodigoSes implements EnviadorCodigo {

    private static final String CHARSET = "UTF-8";

    private final SesV2Client ses;
    private final String remetente;

    public EnviadorCodigoSes(SesV2Client ses, String remetente) {
        this.ses = ses;
        this.remetente = remetente;
    }

    @Override
    public void enviar(String destinatario, String nome, String codigo) {
        var mensagem = MensagemCodigo.para(nome, codigo, Desafio.VALIDADE);
        try {
            ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(remetente)
                .destination(Destination.builder().toAddresses(destinatario).build())
                .content(EmailContent.builder()
                    .simple(Message.builder()
                        .subject(texto(mensagem.assunto()))
                        .body(Body.builder().text(texto(mensagem.corpo())).build())
                        .build())
                    .build())
                .build());
        } catch (SdkException e) {
            throw new EnvioCodigoException("Falha ao enviar o código por e-mail.", e);
        }
    }

    private static Content texto(String valor) {
        return Content.builder().data(valor).charset(CHARSET).build();
    }
}
