-- Garante que o schema 'REGISTOS' existe, caso ainda não tenha sido criado pelo Flyway/Hibernate
CREATE SCHEMA IF NOT EXISTS REGISTOS;

-- 1. Cria a função que será executada pelo trigger
CREATE OR REPLACE FUNCTION REGISTOS.update_audit_history_flag()
RETURNS TRIGGER AS $$
BEGIN
    -- Atualiza a tabela principal (registos_horas)
UPDATE REGISTOS.registos_horas rh
SET has_audit_history = TRUE
-- O 'NEW' refere-se ao registo que acabou de ser INSERIDO na tabela REGISTO_HISTORICO
WHERE rh.public_id::text = NEW.registo_public_id;

-- Retorna NEW (necessário para triggers AFTER INSERT)
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Cria o trigger que executa a função após uma inserção na tabela REGISTO_HISTORICO
CREATE TRIGGER trg_after_insert_registo_historico
    AFTER INSERT ON REGISTOS.REGISTO_HISTORICO
    FOR EACH ROW
    EXECUTE FUNCTION REGISTOS.update_audit_history_flag();