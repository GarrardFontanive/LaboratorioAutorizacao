package br.edu.bcc.autorizacao.rmi;

import br.edu.bcc.autorizacao.service.LaboratorioService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:rmi-test;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "app.rmi.enabled=false"
})
class MuralRmiIntegrationTest {
    @Autowired
    private LaboratorioService laboratorio;
    @Autowired
    private JdbcTemplate jdbc;
    private ServidorRmi servidor;
    private MuralRemoto remoto;
    private int porta;

    @BeforeEach
    void iniciarServidor() throws Exception {
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            porta = socket.getLocalPort();
        }
        servidor = new ServidorRmi(laboratorio, "127.0.0.1", porta, 0);
        servidor.iniciar();
        remoto = (MuralRemoto) LocateRegistry.getRegistry("127.0.0.1", porta)
                .lookup(MuralRemoto.NOME_SERVICO);
    }

    @AfterEach
    void encerrarServidor() {
        if (servidor != null) {
            servidor.close();
        }
    }

    @Test
    void consultaCursosENoticiasPeloStubComObjetosSerializados() throws Exception {
        assertThat(remoto).isNotInstanceOf(ServicoMuralRmi.class);
        assertThat(remoto.listarCursos()).extracting(DadosMural.Curso::nome)
                .containsExactly("Ciência da Computação", "Física", "Matemática");
        assertThat(remoto.listarNoticias(null)).hasSize(3)
                .allSatisfy(noticia -> {
                    assertThat(noticia.conteudo()).isNotBlank();
                    assertThat(noticia.criadaEm()).isNotNull();
                    assertThat(noticia.autor()).isNotBlank();
                });
    }

    @Test
    void filtraPorCursoERejeitaCodigoDesconhecido() throws Exception {
        assertThat(remoto.listarNoticias(" AURORA ")).hasSize(1)
                .allMatch(noticia -> noticia.curso().equals("Ciência da Computação"));
        assertThat(remoto.listarNoticias("verde")).hasSize(1)
                .allMatch(noticia -> noticia.curso().equals("Física"));
        assertThat(remoto.listarNoticias("horizonte")).hasSize(1)
                .allMatch(noticia -> noticia.curso().equals("Matemática"));
        assertThatThrownBy(() -> remoto.listarNoticias("inexistente"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Curso não encontrado");
    }

    @Test
    void alteracaoNoMesmoBancoApareceNaConsultaRemota() throws Exception {
        var noticia = remoto.listarNoticias("aurora").get(0);
        try {
            jdbc.update("UPDATE noticias SET titulo = ? WHERE id = ?", "Publicação atualizada para RMI", noticia.id());
            assertThat(remoto.listarNoticias("aurora")).extracting(DadosMural.Noticia::titulo)
                    .containsExactly("Publicação atualizada para RMI");
        } finally {
            jdbc.update("UPDATE noticias SET titulo = ? WHERE id = ?", noticia.titulo(), noticia.id());
        }
    }

    @Test
    void clienteConsultaServidorEInformaCursoInvalido() {
        var saida = new ByteArrayOutputStream();
        var erros = new ByteArrayOutputStream();
        int resultado = ClienteRmi.executar(new String[]{"127.0.0.1", String.valueOf(porta), "verde"},
                new PrintStream(saida, true, StandardCharsets.UTF_8),
                new PrintStream(erros, true, StandardCharsets.UTF_8));
        assertThat(resultado).isZero();
        assertThat(saida.toString(StandardCharsets.UTF_8)).contains("Conectado por RMI", "[Física]")
                .doesNotContain("[Matemática]", "[Ciência da Computação]");
        assertThat(erros.toString(StandardCharsets.UTF_8)).isEmpty();

        resultado = ClienteRmi.executar(new String[]{"127.0.0.1", String.valueOf(porta), "inexistente"},
                new PrintStream(saida), new PrintStream(erros));
        assertThat(resultado).isEqualTo(2);
        assertThat(erros.toString(StandardCharsets.UTF_8)).contains("Curso não encontrado");
    }

    @Test
    void fechaObjetosRemotosELiberaRegistroParaReiniciar() throws Exception {
        servidor.close();
        assertThatThrownBy(() -> remoto.listarCursos()).isInstanceOf(RemoteException.class);
        servidor.iniciar();
        var novoStub = (MuralRemoto) LocateRegistry.getRegistry("127.0.0.1", porta)
                .lookup(MuralRemoto.NOME_SERVICO);
        assertThat(novoStub.listarCursos()).hasSize(3);
    }
}
