package br.edu.bcc.autorizacao.service;

import br.edu.bcc.autorizacao.security.UsuarioAutenticado;
import br.edu.bcc.autorizacao.service.Modelos.Conta;
import br.edu.bcc.autorizacao.service.Modelos.Empresa;
import br.edu.bcc.autorizacao.service.Modelos.Membro;
import br.edu.bcc.autorizacao.service.Modelos.Noticia;
import br.edu.bcc.autorizacao.service.Modelos.Vinculo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LaboratorioService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder senhas;
    private final String senhaInexistente;

    private static final RowMapper<Empresa> EMPRESA_MAPPER = (result, row) -> new Empresa(
            result.getLong("id"), result.getString("nome"), result.getString("codigo"),
            result.getString("conflitos"));

    private static final RowMapper<Noticia> NOTICIA_MAPPER = (result, row) -> new Noticia(
            result.getLong("id"), result.getLong("empresa_id"), result.getString("empresa"),
            result.getString("titulo"), result.getString("conteudo"), result.getString("autor"),
            result.getTimestamp("criada_em").toLocalDateTime());

    private static final RowMapper<Conta> CONTA_MAPPER = (result, row) -> new Conta(
            result.getLong("id"), result.getString("descricao"), result.getBigDecimal("valor"),
            result.getString("tipo"), result.getDate("vencimento").toLocalDate(),
            result.getString("situacao"));

    private static final String SELECT_NOTICIAS = """
            SELECT n.*, e.nome AS empresa, u.nome AS autor FROM noticias n
            JOIN empresas e ON e.id = n.empresa_id JOIN usuarios u ON u.id = n.autor_id
            """;

    public LaboratorioService(JdbcTemplate jdbc, PasswordEncoder senhas) {
        this.jdbc = jdbc;
        this.senhas = senhas;
        senhaInexistente = senhas.encode("credencial-inexistente");
    }

    public List<Empresa> empresas() {
        return jdbc.query("SELECT * FROM empresas ORDER BY nome", EMPRESA_MAPPER);
    }

    public List<Noticia> noticiasPublicas() {
        return jdbc.query(SELECT_NOTICIAS + " ORDER BY n.criada_em DESC, n.id DESC", NOTICIA_MAPPER);
    }

    public UsuarioAutenticado autenticar(String codigo, String login, String senha) {
        List<Credencial> credenciais = jdbc.query(
                "SELECT id, senha_hash FROM usuarios WHERE login = ? FOR UPDATE",
                (result, row) -> new Credencial(result.getLong("id"), result.getString("senha_hash")),
                normalizar(login));
        String hash = credenciais.isEmpty() ? senhaInexistente : credenciais.get(0).hash();
        if (!senhas.matches(senha, hash) || credenciais.isEmpty()) {
            throw new BadCredentialsException("Curso, usuário ou senha inválidos.");
        }
        List<Empresa> empresas = jdbc.query("SELECT * FROM empresas WHERE codigo = ?",
                EMPRESA_MAPPER, normalizar(codigo));
        if (empresas.isEmpty()) {
            throw new BadCredentialsException("Curso, usuário ou senha inválidos.");
        }
        UsuarioAutenticado usuario = buscarVinculo(credenciais.get(0).id(), empresas.get(0).id());
        if (usuario == null) {
            throw new BadCredentialsException("Curso, usuário ou senha inválidos.");
        }
        try {
            validarAcesso(usuario);
        } catch (AccessDeniedException exception) {
            throw new BadCredentialsException("Acesso bloqueado pela política CWP.", exception);
        }
        return usuario;
    }

    public UsuarioAutenticado trocarEmpresa(UsuarioAutenticado atual, long empresaId) {
        validarAcesso(atual);
        UsuarioAutenticado destino = buscarVinculo(atual.id(), empresaId);
        if (destino == null) {
            throw new AccessDeniedException("Você não possui vínculo com este curso.");
        }
        validarAcesso(destino);
        return destino;
    }

    public List<Vinculo> vinculos(UsuarioAutenticado usuario) {
        validarAcesso(usuario);
        List<Empresa> historico = historico(usuario.id());
        return jdbc.query("""
                SELECT e.*, v.perfil FROM vinculos v JOIN empresas e ON e.id = v.empresa_id
                WHERE v.usuario_id = ? ORDER BY e.nome
                """, (result, row) -> {
                    Empresa empresa = EMPRESA_MAPPER.mapRow(result, row);
                    String perfil = result.getString("perfil");
                    boolean bloqueado = "ADMIN".equals(perfil) && temConflito(empresa, historico);
                    return new Vinculo(empresa.id(), empresa.nome(), perfil, bloqueado);
                }, usuario.id());
    }

    public List<Empresa> historicoAdministrativo(UsuarioAutenticado usuario) {
        validarAcesso(usuario);
        return historico(usuario.id());
    }

    public List<Noticia> noticias(UsuarioAutenticado usuario) {
        validarAcesso(usuario);
        return jdbc.query(SELECT_NOTICIAS + " WHERE n.empresa_id = ? ORDER BY n.id DESC",
                NOTICIA_MAPPER, usuario.empresaId());
    }

    public Noticia noticia(UsuarioAutenticado usuario, long noticiaId) {
        validarAcesso(usuario);
        return unico(jdbc.query(SELECT_NOTICIAS + " WHERE n.id = ? AND n.empresa_id = ?",
                NOTICIA_MAPPER, noticiaId, usuario.empresaId()));
    }

    public void salvarNoticia(UsuarioAutenticado usuario, Long noticiaId, String titulo, String conteudo) {
        validarAcesso(usuario);
        titulo = texto(titulo, "Título", 150);
        conteudo = texto(conteudo, "Conteúdo", 10000);
        if (noticiaId == null) {
            jdbc.update("INSERT INTO noticias (empresa_id, autor_id, titulo, conteudo) VALUES (?, ?, ?, ?)",
                    usuario.empresaId(), usuario.id(), titulo, conteudo);
        } else {
            exigirAlteracao(jdbc.update("UPDATE noticias SET titulo = ?, conteudo = ? WHERE id = ? AND empresa_id = ?",
                    titulo, conteudo, noticiaId, usuario.empresaId()));
        }
    }

    public void excluirNoticia(UsuarioAutenticado usuario, long noticiaId) {
        validarAcesso(usuario);
        exigirAlteracao(jdbc.update("DELETE FROM noticias WHERE id = ? AND empresa_id = ?",
                noticiaId, usuario.empresaId()));
    }

    public List<Conta> contas(UsuarioAutenticado usuario) {
        validarAcesso(usuario);
        return jdbc.query("SELECT * FROM contas WHERE empresa_id = ? ORDER BY vencimento, id",
                CONTA_MAPPER, usuario.empresaId());
    }

    public Conta conta(UsuarioAutenticado usuario, long contaId) {
        validarAcesso(usuario);
        return unico(jdbc.query("SELECT * FROM contas WHERE id = ? AND empresa_id = ?",
                CONTA_MAPPER, contaId, usuario.empresaId()));
    }

    public void salvarConta(UsuarioAutenticado usuario, Long contaId, String descricao,
            BigDecimal valor, String tipo, LocalDate vencimento, String situacao) {
        validarAcesso(usuario);
        descricao = texto(descricao, "Descrição", 150);
        if (valor == null || valor.signum() <= 0 || valor.scale() > 2
                || valor.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Informe um valor positivo com até duas casas decimais.");
        }
        if (!List.of("PAGAR", "RECEBER").contains(tipo)
                || !List.of("PENDENTE", "PAGA").contains(situacao) || vencimento == null) {
            throw new IllegalArgumentException("Confira o tipo, a situação e o vencimento da conta.");
        }
        if (contaId == null) {
            jdbc.update("""
                    INSERT INTO contas (empresa_id, descricao, valor, tipo, vencimento, situacao)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, usuario.empresaId(), descricao, valor, tipo, vencimento, situacao);
        } else {
            exigirAlteracao(jdbc.update("""
                    UPDATE contas SET descricao = ?, valor = ?, tipo = ?, vencimento = ?, situacao = ?
                    WHERE id = ? AND empresa_id = ?
                    """, descricao, valor, tipo, vencimento, situacao, contaId, usuario.empresaId()));
        }
    }

    public void excluirConta(UsuarioAutenticado usuario, long contaId) {
        validarAcesso(usuario);
        exigirAlteracao(jdbc.update("DELETE FROM contas WHERE id = ? AND empresa_id = ?",
                contaId, usuario.empresaId()));
    }

    public List<Membro> membros(UsuarioAutenticado usuario) {
        exigirAdministrador(usuario);
        return jdbc.query("""
                SELECT u.id, u.nome, u.login, v.perfil FROM usuarios u
                JOIN vinculos v ON v.usuario_id = u.id WHERE v.empresa_id = ? ORDER BY u.nome
                """, (result, row) -> new Membro(result.getLong("id"), result.getString("nome"),
                result.getString("login"), result.getString("perfil")), usuario.empresaId());
    }

    public void cadastrarUsuario(UsuarioAutenticado administrador, String nome, String login,
            String senha, String perfil) {
        exigirAdministrador(administrador);
        nome = texto(nome, "Nome", 100);
        login = normalizar(texto(login, "Usuário", 50));
        if (!login.matches("[a-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException("O usuário deve ter de 3 a 50 letras, números, pontos, traços ou sublinhados.");
        }
        if (senha == null || senha.length() < 8 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres e no máximo 72 bytes.");
        }
        if (!List.of("ADMIN", "COMUM").contains(perfil)) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
        try {
            jdbc.update("INSERT INTO usuarios (nome, login, senha_hash) VALUES (?, ?, ?)",
                    nome, login, senhas.encode(senha));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Esse nome de usuário já está em uso. Escolha outro.");
        }
        Long usuarioId = jdbc.queryForObject("SELECT id FROM usuarios WHERE login = ?", Long.class, login);
        jdbc.update("INSERT INTO vinculos (usuario_id, empresa_id, perfil) VALUES (?, ?, ?)",
                usuarioId, administrador.empresaId(), perfil);
    }

    private UsuarioAutenticado buscarVinculo(long usuarioId, long empresaId) {
        List<UsuarioAutenticado> usuarios = jdbc.query("""
                SELECT u.id, u.login, u.nome, e.id AS empresa_id, e.nome AS empresa, v.perfil
                FROM usuarios u JOIN vinculos v ON v.usuario_id = u.id
                JOIN empresas e ON e.id = v.empresa_id WHERE u.id = ? AND e.id = ?
                """, (result, row) -> new UsuarioAutenticado(result.getLong("id"),
                result.getString("login"), result.getString("nome"), result.getLong("empresa_id"),
                result.getString("empresa"), result.getString("perfil")), usuarioId, empresaId);
        return usuarios.isEmpty() ? null : usuarios.get(0);
    }

    private void validarAcesso(UsuarioAutenticado usuario) {
        jdbc.queryForList("SELECT id FROM usuarios WHERE id = ? FOR UPDATE", usuario.id());
        UsuarioAutenticado atual = buscarVinculo(usuario.id(), usuario.empresaId());
        if (atual == null || !atual.perfil().equals(usuario.perfil())) {
            throw new AccessDeniedException("Vínculo ou perfil alterado. Entre novamente no sistema.");
        }
        if (atual.administrador()) {
            Empresa empresa = unico(jdbc.query("SELECT * FROM empresas WHERE id = ?",
                    EMPRESA_MAPPER, atual.empresaId()));
            if (temConflito(empresa, historico(atual.id()))) {
                throw new AccessDeniedException("A política CWP impede administrar cursos com conflito de interesses.");
            }
            Integer acessos = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM acessos_administrativos WHERE usuario_id = ? AND empresa_id = ?
                    """, Integer.class, atual.id(), atual.empresaId());
            if (acessos == 0) {
                jdbc.update("INSERT INTO acessos_administrativos (usuario_id, empresa_id) VALUES (?, ?)",
                        atual.id(), atual.empresaId());
            }
        }
    }

    private void exigirAdministrador(UsuarioAutenticado usuario) {
        validarAcesso(usuario);
        if (!usuario.administrador()) {
            throw new AccessDeniedException("Somente administradores podem cadastrar usuários.");
        }
    }

    private List<Empresa> historico(long usuarioId) {
        return jdbc.query("""
                SELECT e.* FROM acessos_administrativos a JOIN empresas e ON e.id = a.empresa_id
                WHERE a.usuario_id = ? ORDER BY e.nome
                """, EMPRESA_MAPPER, usuarioId);
    }

    private boolean temConflito(Empresa empresa, List<Empresa> historico) {
        return historico.stream().anyMatch(anterior -> anterior.id() != empresa.id()
                && (declaraConflito(empresa, anterior) || declaraConflito(anterior, empresa)));
    }

    private boolean declaraConflito(Empresa origem, Empresa destino) {
        return Arrays.stream(origem.conflitos().split(","))
                .map(this::normalizar).anyMatch(destino.codigo()::equals);
    }

    private String normalizar(String valor) {
        return valor == null ? "" : valor.strip().toLowerCase(Locale.ROOT);
    }

    private String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.strip().length() > limite) {
            throw new IllegalArgumentException(campo + " é obrigatório e deve ter até " + limite + " caracteres.");
        }
        return valor.strip();
    }

    private <T> T unico(List<T> registros) {
        if (registros.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado neste curso.");
        }
        return registros.get(0);
    }

    private void exigirAlteracao(int quantidade) {
        if (quantidade != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado neste curso.");
        }
    }

    private record Credencial(long id, String hash) {
    }
}
