package com.guimeira.resource;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.guimeira.exception.PessoaAlreadyExistsException;
import com.guimeira.model.NewPessoaRequest;
import com.guimeira.model.Pessoa;
import com.guimeira.repository.PessoaRepository;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/pessoas")
public class PessoaResource {
    @Inject
    PessoaRepository repository;

    Map<UUID, Pessoa> cache = Collections.synchronizedMap(new HashMap<>());

    Set<String> apelidos = Collections.synchronizedSet(new HashSet<>());

    Pattern datePattern = Pattern.compile("^([0-9]{4})-([0-9]{2})-([0-9]{2})$");
    int[] maxDays = new int[] {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}; //faz de conta que todo ano é bissexto

    @POST
    public Uni<RestResponse<Void>> create(NewPessoaRequest request) {
        if(!requestIsValid(request)) {
            return Uni.createFrom().item(RestResponse.status(422));
        }

        UUID id = UUID.randomUUID();
        Pessoa pessoa = new Pessoa(id, request.apelido(), request.nome(), request.nascimento(), request.stack());

        return repository.insert(pessoa)
                .onItem().invoke(() -> {
                    cache.put(id, pessoa);
                    apelidos.add(pessoa.nome());
                })
                .onItem().transform(p -> RestResponse.<Void>created(URI.create("/pessoas/" + id)))
                .onFailure(e -> e instanceof PessoaAlreadyExistsException)
                .recoverWithItem(RestResponse.status(422));
    }

    @Path("/{id}")
    @GET
    public Uni<RestResponse<Pessoa>> get(@PathParam("id") UUID id) {
        Pessoa p = cache.get(id);

        if(p != null) {
            return Uni.createFrom().item(RestResponse.ok(p));
        }

        return repository.findById(id)
                .onItem().transform(pessoa -> {
                    if(pessoa == null) {
                        return RestResponse.notFound();
                    }
                    apelidos.add(pessoa.apelido());
                    cache.put(pessoa.id(), pessoa);
                    return RestResponse.ok(pessoa);
                });
    }

    @GET
    public Uni<RestResponse<List<Pessoa>>> search(@QueryParam("t") String searchTerm) {
        if(StringUtils.isBlank(searchTerm)) {
            return Uni.createFrom().item(RestResponse.status(400));
        }
        return repository.search(searchTerm)
                .collect().asList()
                .onItem().transform(RestResponse::ok);
    }

    @ServerExceptionMapper
    public RestResponse<Void> handleJsonParsingError(MismatchedInputException e) {
        return RestResponse.status(422);
    }

    @ServerExceptionMapper
    public RestResponse<Void> handleJsonParsingError2(InvalidDefinitionException e) {
        return RestResponse.status(422);
    }

    /**
     * A maneira mais elegante de fazer isso é com Hibernate Validator
     * mas esse jeito aqui também resolve e talvez seja um pouquinho mais rápido
     */
    private boolean requestIsValid(NewPessoaRequest pessoa) {
        //LocalDate.parse é bem mais lento do que eu pensava, então vou fazer só essa validação mais bobinha
        Matcher dateMatcher = datePattern.matcher(pessoa.nascimento());
        if(!dateMatcher.matches()) {
            return false;
        }
        int month = Integer.parseInt(dateMatcher.group(1));
        if(month < 1 || month > 12) {
            return false;
        }
        int day = Integer.parseInt(dateMatcher.group(2));
        if(day < 1 || day > maxDays[month]) {
            return false;
        }

        return StringUtils.isNoneBlank(pessoa.apelido(), pessoa.nome(), pessoa.nascimento()) &&
                pessoa.apelido().length() <= 32 &&
                pessoa.nome().length() <= 100 &&
                (pessoa.stack() == null || Arrays.stream(pessoa.stack()).noneMatch(StringUtils::isBlank)) &&
                !apelidos.contains(pessoa.apelido());
    }
}
