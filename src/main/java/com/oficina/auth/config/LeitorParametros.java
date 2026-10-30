package com.oficina.auth.config;

import java.util.Collection;
import java.util.Map;

@FunctionalInterface
public interface LeitorParametros {

    Map<String, String> ler(Collection<String> nomes);
}
