package com.oficina.auth.dto;

/**
 * Corpo de erro da API de autenticação.
 *
 * <p>O código é específico e serve ao log e ao suporte; a mensagem é deliberadamente
 * genérica para 404 e 403, de modo que um chamador não autenticado não consiga descobrir
 * quais CPFs existem na base (SPEC-01 §2, §8).
 */
public record ErroResponse(String erro, String mensagem) {

    /** Mensagem única para "não existe" e "existe mas não pode autenticar". */
    public static final String FALHA_GENERICA = "Não foi possível autenticar com o CPF informado.";

    public static ErroResponse cpfInvalido(String detalhe) {
        return new ErroResponse("CPF_INVALIDO", detalhe);
    }

    public static ErroResponse clienteNaoEncontrado() {
        return new ErroResponse("CLIENTE_NAO_ENCONTRADO", FALHA_GENERICA);
    }

    public static ErroResponse clienteInativo() {
        return new ErroResponse("CLIENTE_INATIVO", FALHA_GENERICA);
    }

    public static ErroResponse funcionarioNaoEncontrado() {
        return new ErroResponse("FUNCIONARIO_NAO_ENCONTRADO", FALHA_GENERICA);
    }

    public static ErroResponse funcionarioInativo() {
        return new ErroResponse("FUNCIONARIO_INATIVO", FALHA_GENERICA);
    }

    public static ErroResponse interno() {
        return new ErroResponse("ERRO_INTERNO", "Erro ao processar a autenticação.");
    }
}
