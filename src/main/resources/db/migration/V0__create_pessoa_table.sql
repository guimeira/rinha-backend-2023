CREATE EXTENSION pg_trgm;

CREATE TABLE pessoa(
    id UUID PRIMARY KEY,
    apelido TEXT NOT NULL UNIQUE,
    nome TEXT NOT NULL,
    nascimento TEXT NOT NULL,
    stack TEXT[],
    stack_search TEXT);

CREATE INDEX apelido_idx ON pessoa USING gist(apelido gist_trgm_ops);
CREATE INDEX nome_idx ON pessoa USING gist(nome gist_trgm_ops);
CREATE INDEX stack_idx ON pessoa USING gist(stack_search gist_trgm_ops);