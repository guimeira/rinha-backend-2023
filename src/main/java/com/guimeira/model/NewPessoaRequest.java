package com.guimeira.model;

public record NewPessoaRequest(
        String apelido,
        String nome,
        String nascimento,
        String[] stack
) {}
