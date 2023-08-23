package com.guimeira.repository;

import com.guimeira.exception.PessoaAlreadyExistsException;
import com.guimeira.model.Pessoa;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.pgclient.PgException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class PessoaRepository {
  @Inject
  PgPool db;

  public Uni<Void> insert(Pessoa pessoa) {
    String query = """
          INSERT INTO pessoa(id, apelido, nome, nascimento, stack, stack_search)
          VALUES ($1,$2,$3,$4,$5,$6)""";

    String stackSearch = pessoa.stack() != null ? String.join(" ", pessoa.stack()) : "";

    return db.preparedQuery(query)
            .execute(Tuple.of(pessoa.id(), pessoa.apelido(), pessoa.nome(), pessoa.nascimento(), pessoa.stack(), stackSearch))
            .replaceWithVoid()
            .onFailure(t -> t instanceof PgException && ((PgException) t).getSqlState().equals("23505"))
            .transform(t -> new PessoaAlreadyExistsException());
  }

  public Uni<Pessoa> findById(UUID id) {
    String query = """
          SELECT id, apelido, nome, nascimento, stack
          FROM pessoa
          WHERE id=$1""";

    return db.preparedQuery(query)
            .execute(Tuple.of(id))
            .onItem().transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .onItem().transform(this::mapPessoa)
            .collect().first();
  }

  public Multi<Pessoa> search(String searchTerm) {
    //Trocando o caractere de escape do LIKE de \ para $ pra evitar confusão quanto a o que é escape do SQL e o que é
    //escape do Java
    String query = """
          SELECT id, apelido, nome, nascimento, stack
          FROM pessoa
          WHERE apelido ILIKE $1 ESCAPE '$' OR
            nome ILIKE $1 ESCAPE '$' OR
            stack_search ILIKE $1 ESCAPE '$'
          LIMIT 50""";

    String escapedSearchTerm = searchTerm.replace("$", "$$")
            .replace("%", "$%")
            .replace("_", "$_")
            .toLowerCase();

    String searchPattern = "%" + escapedSearchTerm + "%";
    return db.preparedQuery(query)
            .execute(Tuple.of(searchPattern))
            .onItem().transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .onItem().transform(this::mapPessoa);
  }

  public Uni<Long> count() {
    String query = "SELECT COUNT(*) AS count FROM pessoa";

    return db.preparedQuery(query)
            .execute()
            .onItem().transformToMulti(rowSet -> Multi.createFrom().iterable(rowSet))
            .onItem().transform(row -> row.getLong("count"))
            .collect().first();
  }

  private Pessoa mapPessoa(Row row) {
    return new Pessoa(
            row.getUUID("id"),
            row.getString("apelido"),
            row.getString("nome"),
            row.getString("nascimento"),
            row.getArrayOfStrings("stack")
    );
  }
}
