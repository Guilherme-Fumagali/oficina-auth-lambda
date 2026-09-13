package com.oficina.auth.cpf;

public final class Cpf {

    private final String valor;

    private Cpf(String valor) {
        this.valor = valor;
    }

    public static Cpf de(String bruto) {
        var limpo = limpar(bruto);

        if (limpo.length() != 11) {
            throw new CpfInvalidoException("CPF deve ter 11 dígitos.");
        }
        if (limpo.chars().distinct().count() == 1) {
            throw new CpfInvalidoException("CPF com todos os dígitos iguais.");
        }
        if (limpo.charAt(9) - '0' != digito(limpo, 10) || limpo.charAt(10) - '0' != digito(limpo, 11)) {
            throw new CpfInvalidoException("Dígito verificador inválido.");
        }
        return new Cpf(limpo);
    }

    public String valor() {
        return valor;
    }

    public String mascarado() {
        return "***" + valor.substring(8);
    }

    private static String limpar(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static int digito(String cpf, int peso) {
        int soma = 0;
        for (int i = 0; i < peso - 1; i++) {
            soma += (cpf.charAt(i) - '0') * (peso - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    @Override
    public String toString() {
        return mascarado();
    }
}
