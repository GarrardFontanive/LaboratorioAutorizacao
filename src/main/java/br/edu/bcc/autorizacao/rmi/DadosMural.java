package br.edu.bcc.autorizacao.rmi;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Objetos públicos enviados por valor entre as JVMs, sem dependências do Spring. */
public final class DadosMural {
    private DadosMural() {
    }

    public record Curso(long id, String codigo, String nome) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record Noticia(long id, long cursoId, String curso, String titulo,
            String conteudo, String autor, LocalDateTime criadaEm) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
