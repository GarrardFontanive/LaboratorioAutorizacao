package br.edu.bcc.autorizacao;

import br.edu.bcc.autorizacao.security.UsuarioAutenticado;
import br.edu.bcc.autorizacao.service.DadosIniciais;
import br.edu.bcc.autorizacao.service.LaboratorioService;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:autorizacao-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
class AutorizacaoIntegrationTest {
    @Autowired
    private LaboratorioService laboratorio;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DadosIniciais dados;
    @Autowired
    private PasswordEncoder senhas;
    @LocalServerPort
    private int porta;

    @BeforeEach
    void prepararBanco() {
        for (String tabela : new String[]{"contas", "noticias", "acessos_administrativos", "vinculos", "usuarios", "empresas"}) {
            jdbc.update("DELETE FROM " + tabela);
        }
        dados.run();
    }

    @Test
    void muralPublicoIdentificaTodasAsEmpresasSemExporContas() throws Exception {
        HttpResponse<String> pagina = get(cliente(), "/");
        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).contains("IFC Rio do Sul", "Mural estudantil", "Desenvolvimento Web II",
                "Ciência da Computação", "Matemática", "Física")
                .doesNotContain("Impressão de materiais para o grupo de estudos de Computação");
    }

    @Test
    void adaptacaoDoTemaPreservaEdicoesVinculosEHistorico() {
        UsuarioAutenticado admin = laboratorio.autenticar("aurora", "admin", "Admin@123");
        jdbc.update("UPDATE empresas SET nome = 'Aurora Tecnologia', conflitos = 'horizonte,verde' WHERE id = 1");
        jdbc.update("UPDATE empresas SET nome = 'Horizonte Digital' WHERE id = 2");
        jdbc.update("""
                UPDATE noticias SET titulo = 'Boas-vindas à nossa empresa',
                conteudo = 'Este é o mural público de notícias. Cada publicação identifica sua empresa de origem.'
                WHERE empresa_id = 1
                """);
        jdbc.update("UPDATE noticias SET titulo = 'Projeto personalizado da turma' WHERE empresa_id = 2");
        jdbc.update("UPDATE contas SET descricao = 'Serviços de internet' WHERE empresa_id = 1");
        jdbc.update("UPDATE contas SET descricao = 'Materiais personalizados', valor = 50 WHERE empresa_id = 2");

        dados.run();
        dados.run();

        assertThat(laboratorio.empresas()).extracting(empresa -> empresa.nome())
                .contains("Ciência da Computação", "Matemática");
        assertThat(jdbc.queryForObject("SELECT conflitos FROM empresas WHERE id = 1", String.class))
                .isEqualTo("horizonte,verde");
        assertThat(jdbc.queryForObject("SELECT titulo FROM noticias WHERE empresa_id = 1", String.class))
                .isEqualTo("Grupo de estudos: Java e Desenvolvimento Web II");
        assertThat(jdbc.queryForObject("SELECT titulo FROM noticias WHERE empresa_id = 2", String.class))
                .isEqualTo("Projeto personalizado da turma");
        assertThat(jdbc.queryForObject("SELECT descricao FROM contas WHERE empresa_id = 1", String.class))
                .isEqualTo("Impressão de materiais para o grupo de estudos de Computação");
        assertThat(jdbc.queryForObject("SELECT descricao FROM contas WHERE empresa_id = 2", String.class))
                .isEqualTo("Materiais personalizados");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculos", Integer.class)).isEqualTo(6);
        assertThat(laboratorio.historicoAdministrativo(admin)).hasSize(1);
        assertThatThrownBy(() -> laboratorio.autenticar("horizonte", "admin", "Admin@123"))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("CWP");
    }

    @Test
    void loginApresentaTresCursosEAutenticaSeusUsuarios() throws Exception {
        HttpResponse<String> pagina = get(cliente(), "/login");
        assertThat(pagina.statusCode()).isEqualTo(200);
        assertThat(pagina.body()).contains("<label for=\"empresa\">Curso</label>",
                "<option value=\"aurora\">Ciência da Computação</option>",
                "<option value=\"horizonte\">Matemática</option>",
                "<option value=\"verde\">Física</option>")
                .doesNotContain("Aurora", "Horizonte", "Verde Projetos");
        for (var curso : Map.of("aurora", "carlos", "horizonte", "luisinho", "verde", "pedro").entrySet()) {
            HttpClient sessao = cliente();
            HttpResponse<String> resposta = login(sessao, curso.getKey(), curso.getValue(), "Usuario@123");
            assertThat(resposta.headers().firstValue("location").orElseThrow()).endsWith("/painel");
            assertThat(get(sessao, "/painel").body()).contains("Curso ativo <strong>");
        }
    }

    @Test
    void adaptacaoDosExemplosEstudantisParaCursosPreservaConteudoEditado() {
        laboratorio.autenticar("aurora", "admin", "Admin@123");
        jdbc.update("UPDATE empresas SET nome = 'Aurora Tecnologia Júnior' WHERE id = 1");
        jdbc.update("UPDATE empresas SET nome = 'Horizonte Digital Júnior' WHERE id = 2");
        jdbc.update("UPDATE empresas SET nome = 'Verde Projetos Estudantis' WHERE id = 3");
        jdbc.update("UPDATE usuarios SET nome = 'Clara da Verde', login = 'clara' WHERE login = 'pedro'");
        jdbc.update("""
                UPDATE noticias SET titulo = ?, conteudo = ? WHERE empresa_id = 2
                """, "Do laboratório para o portfólio: projetos de BCC",
                "A equipe Horizonte está preparando uma mostra de projetos desenvolvidos durante o semestre. "
                + "A ideia é reunir aplicações web, compartilhar o que cada grupo aprendeu "
                + "e conversar sobre tecnologia com os colegas do IFC Rio do Sul.");
        jdbc.update("UPDATE noticias SET titulo = 'Oficina personalizada', conteudo = 'Texto da turma' WHERE empresa_id = 3");
        jdbc.update("UPDATE contas SET descricao = 'Materiais para a apresentação dos projetos de BCC' WHERE empresa_id = 2");

        dados.run();
        dados.run();

        assertThat(laboratorio.empresas()).extracting(empresa -> empresa.nome())
                .containsExactlyInAnyOrder("Ciência da Computação", "Matemática", "Física");
        assertThat(jdbc.queryForObject("SELECT titulo FROM noticias WHERE empresa_id = 2", String.class))
                .isEqualTo("Matemática em grupo: resolução de problemas");
        assertThat(jdbc.queryForObject("SELECT conteudo FROM noticias WHERE empresa_id = 3", String.class))
                .isEqualTo("Texto da turma");
        assertThat(jdbc.queryForObject("SELECT descricao FROM contas WHERE empresa_id = 2", String.class))
                .isEqualTo("Materiais para a oficina de Matemática");
        assertThat(laboratorio.autenticar("verde", "pedro", "Usuario@123").nome()).isEqualTo("Pedro");
        assertThatThrownBy(() -> laboratorio.autenticar("horizonte", "admin", "Admin@123"))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("CWP");
    }

    @Test
    void renomearUsuariosDeExemploPreservaIdentidadeSenhaEVinculos() {
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        laboratorio.salvarNoticia(carlos, null, "Projeto de Carlos", "Publicação do estudante");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE id = ?", String.class, carlos.id());
        jdbc.update("UPDATE usuarios SET login = 'ana', nome = 'Ana da Computação' WHERE login = 'carlos'");
        jdbc.update("UPDATE usuarios SET login = 'bruno', nome = 'Bruno da Horizonte' WHERE login = 'luisinho'");
        jdbc.update("UPDATE usuarios SET login = 'clara', nome = 'Clara da Física' WHERE login = 'pedro'");

        dados.run();
        dados.run();

        UsuarioAutenticado atualizado = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        assertThat(atualizado.id()).isEqualTo(carlos.id());
        assertThat(atualizado.nome()).isEqualTo("Carlos");
        assertThat(atualizado.empresaId()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE id = ?", String.class, carlos.id()))
                .isEqualTo(hash);
        assertThat(laboratorio.autenticar("horizonte", "luisinho", "Usuario@123").nome()).isEqualTo("Luisinho");
        assertThat(laboratorio.autenticar("verde", "pedro", "Usuario@123").nome()).isEqualTo("Pedro");
        assertThat(jdbc.queryForObject("SELECT autor_id FROM noticias WHERE titulo = 'Projeto de Carlos'", Long.class))
                .isEqualTo(carlos.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculos", Integer.class)).isEqualTo(6);
        assertThatThrownBy(() -> laboratorio.autenticar("aurora", "ana", "Usuario@123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void paginasPrivadasExigemLogin() throws Exception {
        HttpResponse<String> resposta = get(cliente(), "/contas");
        assertThat(resposta.statusCode()).isEqualTo(302);
        assertThat(resposta.headers().firstValue("location").orElseThrow()).endsWith("/login");
    }

    @Test
    void loginExigeEmpresaUsuarioESenhaCorretos() throws Exception {
        assertThat(login(cliente(), "aurora", "carlos", "Usuario@123").statusCode()).isEqualTo(302);
        assertThatThrownBy(() -> laboratorio.autenticar("horizonte", "carlos", "Usuario@123"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> laboratorio.autenticar("aurora", "carlos", "errada"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> laboratorio.autenticar("", "carlos", "Usuario@123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void telasDoUsuarioComumRenderizamSemCadastroDeUsuarios() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "carlos", "Usuario@123");
        for (String rota : new String[]{"/painel", "/noticias", "/noticias/nova", "/contas", "/contas/nova"}) {
            HttpResponse<String> resposta = get(cliente, rota);
            assertThat(resposta.statusCode()).as(rota).isEqualTo(200);
            assertThat(resposta.body()).doesNotContain("href=\"/usuarios\"");
        }
        assertThat(get(cliente, "/usuarios").statusCode()).isEqualTo(403);
        assertThat(post(cliente, "/usuarios", csrf(get(cliente, "/painel")), Map.of(
                "nome", "Intruso", "login", "intruso", "senha", "Usuario@123", "perfil", "ADMIN"))
                .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE login = 'intruso'", Integer.class)).isZero();
    }

    @Test
    void usuarioComumPodeCriarEditarEExcluirNoticiasEContas() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "carlos", "Usuario@123");
        String token = csrf(get(cliente, "/painel"));
        assertThat(post(cliente, "/noticias/salvar", token, Map.of("titulo", "Notícia de teste", "conteudo", "Texto público"))
                .statusCode()).isEqualTo(302);
        Long noticia = jdbc.queryForObject("SELECT id FROM noticias WHERE titulo = 'Notícia de teste'", Long.class);
        assertThat(get(cliente, "/noticias/" + noticia + "/editar").statusCode()).isEqualTo(200);
        assertThat(post(cliente, "/noticias/salvar", token, Map.of("id", noticia.toString(), "titulo", "Notícia atualizada", "conteudo", "Novo texto"))
                .statusCode()).isEqualTo(302);
        assertThat(get(cliente(), "/").body()).contains("Notícia atualizada");
        assertThat(post(cliente, "/noticias/" + noticia + "/excluir", token, Map.of()).statusCode()).isEqualTo(302);

        Map<String, String> campos = Map.of("descricao", "Conta de teste", "valor", "42.50", "tipo", "PAGAR",
                "vencimento", "2026-12-10", "situacao", "PENDENTE");
        assertThat(post(cliente, "/contas/salvar", token, campos).statusCode()).isEqualTo(302);
        Long conta = jdbc.queryForObject("SELECT id FROM contas WHERE descricao = 'Conta de teste'", Long.class);
        assertThat(get(cliente, "/contas/" + conta + "/editar").statusCode()).isEqualTo(200);
        assertThat(post(cliente, "/contas/salvar", token, Map.of("id", conta.toString(), "descricao", "Conta atualizada",
                "valor", "45.00", "tipo", "RECEBER", "vencimento", "2026-12-11", "situacao", "PAGA")).statusCode()).isEqualTo(302);
        assertThat(post(cliente, "/contas/" + conta + "/excluir", token, Map.of()).statusCode()).isEqualTo(302);
    }

    @Test
    void urlsEFormulariosNaoPermitemAlterarRegistrosDeOutraEmpresa() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "carlos", "Usuario@123");
        String token = csrf(get(cliente, "/painel"));
        Long noticia = jdbc.queryForObject("SELECT id FROM noticias WHERE empresa_id = 2", Long.class);
        Long conta = jdbc.queryForObject("SELECT id FROM contas WHERE empresa_id = 2", Long.class);
        assertThat(get(cliente, "/noticias/" + noticia + "/editar").statusCode()).isEqualTo(404);
        assertThat(get(cliente, "/contas/" + conta + "/editar").statusCode()).isEqualTo(404);
        assertThat(post(cliente, "/noticias/salvar", token, Map.of("id", noticia.toString(), "titulo", "Ataque", "conteudo", "Ataque"))
                .statusCode()).isEqualTo(404);
        assertThat(post(cliente, "/contas/salvar", token, Map.of("id", conta.toString(), "descricao", "Ataque",
                "valor", "1.00", "tipo", "PAGAR", "vencimento", "2026-12-11", "situacao", "PAGA")).statusCode()).isEqualTo(404);
        assertThat(post(cliente, "/noticias/" + noticia + "/excluir", token, Map.of()).statusCode()).isEqualTo(404);
        assertThat(post(cliente, "/contas/" + conta + "/excluir", token, Map.of()).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT titulo FROM noticias WHERE id = ?", String.class, noticia)).isNotEqualTo("Ataque");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contas WHERE id = ?", Integer.class, conta)).isEqualTo(1);
    }

    @Test
    void empresaForjadaNoFormularioNaoMudaTenantDeCriacao() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "carlos", "Usuario@123");
        assertThat(post(cliente, "/noticias/salvar", csrf(get(cliente, "/painel")), Map.of("empresaId", "2",
                "titulo", "Empresa forjada", "conteudo", "Teste")).statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM noticias WHERE titulo = 'Empresa forjada'", Long.class)).isEqualTo(1L);
    }

    @Test
    void administradorCadastraUsuariosSomenteNaEmpresaAtual() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "admin", "Admin@123");
        assertThat(get(cliente, "/usuarios").statusCode()).isEqualTo(200);
        assertThat(post(cliente, "/usuarios", csrf(get(cliente, "/usuarios")), Map.of("nome", "Nova pessoa",
                "login", "nova.pessoa", "senha", "NovaSenha@123", "perfil", "ADMIN", "empresaId", "2"))
                .statusCode()).isEqualTo(302);
        assertThat(jdbc.queryForList("""
                SELECT v.empresa_id FROM vinculos v JOIN usuarios u ON u.id = v.usuario_id
                WHERE u.login = 'nova.pessoa'
                """, Long.class)).containsExactly(1L);
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE login = 'nova.pessoa'", String.class);
        assertThat(hash).isNotEqualTo("NovaSenha@123");
        assertThat(senhas.matches("NovaSenha@123", hash)).isTrue();
        assertThat(laboratorio.autenticar("aurora", "nova.pessoa", "NovaSenha@123").administrador()).isTrue();
        assertThatThrownBy(() -> laboratorio.autenticar("horizonte", "nova.pessoa", "NovaSenha@123"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void cwpContinuaValendoAposLogoutENovoLogin() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "admin", "Admin@123");
        assertThat(post(cliente, "/logout", csrf(get(cliente, "/painel")), Map.of()).statusCode()).isEqualTo(302);
        HttpClient outraSessao = cliente();
        HttpResponse<String> bloqueio = login(outraSessao, "horizonte", "admin", "Admin@123");
        assertThat(bloqueio.headers().firstValue("location").orElseThrow()).endsWith("/login?erro");
        assertThat(get(outraSessao, "/login?erro").body()).contains("CWP");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acessos_administrativos", Integer.class)).isEqualTo(1);
        assertThat(login(cliente(), "verde", "admin", "Admin@123").headers().firstValue("location").orElseThrow())
                .endsWith("/painel");
    }

    @Test
    void trocaDeEmpresaAtualizaSessaoERespeitaCwp() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "admin", "Admin@123");
        String token = csrf(get(cliente, "/painel"));
        assertThat(post(cliente, "/trocar-empresa", token, Map.of("empresaId", "2")).statusCode()).isEqualTo(403);
        assertThat(post(cliente, "/trocar-empresa", token, Map.of("empresaId", "3")).statusCode()).isEqualTo(302);
        assertThat(get(cliente, "/painel").body()).contains("Curso ativo <strong>Física</strong>");
        assertThat(get(cliente, "/usuarios").body()).contains("Pedro").doesNotContain("Carlos");
    }

    @Test
    void usuarioSemVinculoNaoPodeTrocarDeEmpresa() {
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        assertThatThrownBy(() -> laboratorio.trocarEmpresa(carlos, 2)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void conflitoDeclaradoEmApenasUmLadoTambemBloqueia() {
        jdbc.update("UPDATE empresas SET conflitos = '' WHERE id = 1");
        laboratorio.autenticar("aurora", "admin", "Admin@123");
        assertThatThrownBy(() -> laboratorio.autenticar("horizonte", "admin", "Admin@123"))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("CWP");
    }

    @Test
    void primeiroAcessoAHorizonteTambemBloqueiaAurora() {
        laboratorio.autenticar("horizonte", "admin", "Admin@123");
        assertThatThrownBy(() -> laboratorio.autenticar("aurora", "admin", "Admin@123"))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("CWP");
    }

    @Test
    void alteracoesDeConflitosNoBancoSaoRevalidadasDuranteASessao() {
        UsuarioAutenticado admin = laboratorio.autenticar("aurora", "admin", "Admin@123");
        UsuarioAutenticado verde = laboratorio.trocarEmpresa(admin, 3);
        jdbc.update("UPDATE empresas SET conflitos = 'verde' WHERE id = 1");
        assertThatThrownBy(() -> laboratorio.contas(verde)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> laboratorio.salvarNoticia(verde, null, "Bloqueada", "Não publicar"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revogacaoDeVinculoEPerfilNoBancoInvalidaAcessoExistente() {
        UsuarioAutenticado admin = laboratorio.autenticar("aurora", "admin", "Admin@123");
        jdbc.update("UPDATE vinculos SET perfil = 'COMUM' WHERE usuario_id = ? AND empresa_id = 1", admin.id());
        assertThatThrownBy(() -> laboratorio.membros(admin)).isInstanceOf(AccessDeniedException.class);
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        jdbc.update("DELETE FROM vinculos WHERE usuario_id = ?", carlos.id());
        assertThatThrownBy(() -> laboratorio.contas(carlos)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void loginsConcorrentesNaoConseguemRomperBarreiraCwp() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            var aurora = executor.submit(() -> tentarLogin(inicio, "aurora"));
            var horizonte = executor.submit(() -> tentarLogin(inicio, "horizonte"));
            inicio.countDown();
            assertThat(aurora.get(10, TimeUnit.SECONDS) ^ horizonte.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acessos_administrativos", Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void csrfImpedeAlteracaoSemTokenMesmoComLoginValido() throws Exception {
        HttpClient cliente = cliente();
        login(cliente, "aurora", "carlos", "Usuario@123");
        assertThat(post(cliente, "/noticias/salvar", null, Map.of("titulo", "Sem token", "conteudo", "Bloquear"))
                .statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM noticias WHERE titulo = 'Sem token'", Integer.class)).isZero();
    }

    @Test
    void conteudoPublicoEExibidoComoTextoSemExecutarHtml() throws Exception {
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        laboratorio.salvarNoticia(carlos, null, "<script>alert(1)</script>", "<img src=x onerror=alert(1)>");
        assertThat(get(cliente(), "/").body()).contains("&lt;script&gt;", "&lt;img")
                .doesNotContain("<script>alert(1)</script>", "<img src=x");
    }

    @Test
    void validacoesRecusamContasInvalidasESenhasCurtasSemGravar() {
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        assertThatThrownBy(() -> laboratorio.salvarConta(carlos, null, "Inválida", BigDecimal.ZERO,
                "PAGAR", LocalDate.now(), "PENDENTE")).isInstanceOf(IllegalArgumentException.class);
        UsuarioAutenticado admin = laboratorio.autenticar("aurora", "admin", "Admin@123");
        assertThatThrownBy(() -> laboratorio.cadastrarUsuario(admin, "Teste", "teste", "123", "COMUM"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE login = 'teste'", Integer.class)).isZero();
    }

    @Test
    void identificadorInexistenteRetornaNaoEncontrado() {
        UsuarioAutenticado carlos = laboratorio.autenticar("aurora", "carlos", "Usuario@123");
        assertThatThrownBy(() -> laboratorio.noticia(carlos, -1)).isInstanceOf(ResponseStatusException.class);
    }

    private boolean tentarLogin(CountDownLatch inicio, String empresa) throws InterruptedException {
        inicio.await();
        try {
            laboratorio.autenticar(empresa, "admin", "Admin@123");
            return true;
        } catch (BadCredentialsException exception) {
            return false;
        }
    }

    private HttpClient cliente() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private HttpResponse<String> get(HttpClient cliente, String caminho) throws Exception {
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient cliente, String caminho, String csrf, Map<String, String> campos) throws Exception {
        String corpo = campos.entrySet().stream().map(campo -> codificar(campo.getKey()) + "=" + codificar(campo.getValue()))
                .collect(Collectors.joining("&"));
        if (csrf != null) {
            corpo += "&_csrf=" + codificar(csrf);
        }
        return cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(HttpClient cliente, String empresa, String usuario, String senha) throws Exception {
        return post(cliente, "/login", csrf(get(cliente, "/login")),
                Map.of("empresa", empresa, "username", usuario, "password", senha));
    }

    private String csrf(HttpResponse<String> pagina) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(pagina.body());
        assertThat(matcher.find()).as("Token CSRF presente no formulário").isTrue();
        return matcher.group(1);
    }

    private String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
