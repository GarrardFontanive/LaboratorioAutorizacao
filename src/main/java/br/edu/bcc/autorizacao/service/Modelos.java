package br.edu.bcc.autorizacao.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Modelos {
    private Modelos() {
    }

    public record Empresa(long id, String nome, String codigo, String conflitos) {
    }

    public record Noticia(long id, long empresaId, String empresa, String titulo,
            String conteudo, String autor, LocalDateTime criadaEm) {
    }

    public record Conta(long id, String descricao, BigDecimal valor, String tipo,
            LocalDate vencimento, String situacao) {
    }

    public record Membro(long id, String nome, String login, String perfil) {
    }

    public record Vinculo(long empresaId, String empresa, String perfil, boolean bloqueado) {
    }
}
