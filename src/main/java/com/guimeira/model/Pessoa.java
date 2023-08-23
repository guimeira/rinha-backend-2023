package com.guimeira.model;

import java.util.UUID;

public record Pessoa(
        UUID id,
        String apelido,
        String nome,
        String nascimento,
        String[] stack
) {}
