package com.oficina.auth.identidade;

import java.util.UUID;

public interface Identidade {

    UUID id();

    String nome();

    Papel papel();

    boolean podeAutenticar();
}
