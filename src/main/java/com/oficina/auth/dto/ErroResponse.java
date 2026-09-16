package com.oficina.auth.dto;

/**
 * Corpo de erro da API de autenticação.
 *
 * <p>CPF sem cadastro e cadastro sem permissão recebem a mesma resposta, com status 401,
 * para que um chamador não consiga descobrir quais CPFs existem na base. O motivo
 * específico fica apenas no log.
 */
public record ErroResponse(String erro, String mensagem) {

    public static final String FALHA_GENERICA = "Não foi possível autenticar com o CPF informado.";

    public static ErroResponse cpfInvalido(String detalhe) {
        return new ErroResponse("CPF_INVALIDO", detalhe);
    }

    public static ErroResponse autenticacaoRecusada() {
        return new ErroResponse("AUTENTICACAO_RECUSADA", FALHA_GENERICA);
    }

    public static ErroResponse interno() {
        return new ErroResponse("ERRO_INTERNO", "Erro ao processar a autenticação.");
    }
}
