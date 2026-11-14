package com.oficina.auth.dto;

public record ErroResponse(String erro, String mensagem) {

    public static final String FALHA_CONFIRMACAO = "Código inválido ou expirado.";

    public static ErroResponse cpfInvalido(String detalhe) {
        return new ErroResponse("CPF_INVALIDO", detalhe);
    }

    public static ErroResponse requisicaoInvalida(String detalhe) {
        return new ErroResponse("REQUISICAO_INVALIDA", detalhe);
    }

    public static ErroResponse autenticacaoRecusada() {
        return new ErroResponse("AUTENTICACAO_RECUSADA", FALHA_CONFIRMACAO);
    }

    public static ErroResponse rotaInexistente() {
        return new ErroResponse("ROTA_INEXISTENTE", "Rota não atendida por esta função.");
    }

    public static ErroResponse servicoIndisponivel() {
        return new ErroResponse("SERVICO_INDISPONIVEL", "Serviço de autenticação indisponível.");
    }

    public static ErroResponse interno() {
        return new ErroResponse("ERRO_INTERNO", "Erro ao processar a autenticação.");
    }
}
