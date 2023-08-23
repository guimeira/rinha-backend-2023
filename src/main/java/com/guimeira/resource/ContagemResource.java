package com.guimeira.resource;

import com.guimeira.repository.PessoaRepository;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/contagem-pessoas")
public class ContagemResource {
  @Inject
  PessoaRepository repository;

  @GET
  public Uni<Long> count() {
    return repository.count();
  }
}
