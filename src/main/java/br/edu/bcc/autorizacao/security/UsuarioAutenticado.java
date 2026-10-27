package br.edu.bcc.autorizacao.security;

import java.io.Serializable;
import java.security.Principal;

public record UsuarioAutenticado(long id, String login, String nome, long empresaId,
        String empresaNome, String perfil) implements Principal, Serializable {
    @Override
    public String getName() {
        return login;
    }

    public boolean administrador() {
        return "ADMIN".equals(perfil);
    }
}
