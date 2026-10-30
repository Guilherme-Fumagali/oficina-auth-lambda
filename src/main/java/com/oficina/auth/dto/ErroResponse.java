package com.oficina.auth.dto;

public record ErroResponse(String erro, String mensagem) {

    public static final String FALHA_GENERICA = "Não foi possível autenticar com o CPF informado.";

    public static ErroResponse cpfInvalido(String detalhe) {
        return new ErroResponse("CPF_INVALIDO", detalhe);
    }

    public static ErroResponse autenticacaoRecusada() {
        return new ErroResponse("AUTENTICACAO_RECUSADA", FALHA_GENERICA);
    }

    public static ErroResponse servicoIndisponivel() {
        return new ErroResponse("SERVICO_INDISPONIVEL", "Serviço de autenticação indisponível.");
    }

    public static ErroResponse interno() {
        return new ErroResponse("ERRO_INTERNO", "Erro ao processar a autenticação.");
    }
}
