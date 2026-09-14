CREATE TABLE clientes (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cpf_cnpj VARCHAR(14) NOT NULL UNIQUE,
    nome     VARCHAR(255) NOT NULL,
    status   VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
);

CREATE TABLE funcionarios (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cpf    VARCHAR(11) NOT NULL UNIQUE,
    nome   VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ATIVO'
);

INSERT INTO clientes (id, cpf_cnpj, nome, status) VALUES
    ('11111111-1111-1111-1111-111111111111', '52998224725', 'João Ativo', 'ATIVO'),
    ('22222222-2222-2222-2222-222222222222', '11144477735', 'Ana Bloqueada', 'BLOQUEADO');

INSERT INTO funcionarios (id, cpf, nome, status) VALUES
    ('33333333-3333-3333-3333-333333333333', '39053344705', 'Maria Ativa', 'ATIVO'),
    ('44444444-4444-4444-4444-444444444444', '15350946056', 'Carlos Inativo', 'INATIVO');
