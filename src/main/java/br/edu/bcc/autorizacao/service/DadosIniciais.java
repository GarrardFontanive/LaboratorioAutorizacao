package br.edu.bcc.autorizacao.service;

import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DadosIniciais implements CommandLineRunner {
    private record ExemploCurso(String nome, String titulo, String conteudo, String conta) {}

    private record ExemploEmpresa(long id, String codigo, String conflitos, String nomeAnterior,
            String nome, String titulo, String conteudo, String conta, ExemploCurso curso) {}

    private static final List<ExemploEmpresa> EXEMPLOS = List.of(
            new ExemploEmpresa(1, "aurora", "horizonte", "Aurora Tecnologia", "Aurora Tecnologia Júnior",
                    "Grupo de estudos: Java e Desenvolvimento Web II",
                    "A equipe Aurora está organizando um grupo de estudos entre colegas de BCC do IFC Rio do Sul. "
                    + "A proposta é revisar Java, trocar ideias sobre os trabalhos de Desenvolvimento Web II "
                    + "e praticar autenticação e autorização com projetos da turma.",
                    "Impressão de materiais para o grupo de estudos",
                    new ExemploCurso("Ciência da Computação", "Grupo de estudos: Java e Desenvolvimento Web II",
                            "A turma de Ciência da Computação do IFC Rio do Sul está organizando um grupo de estudos. "
                            + "A proposta é revisar Java, trocar ideias sobre os trabalhos de Desenvolvimento Web II "
                            + "e praticar autenticação e autorização com projetos da turma.",
                            "Impressão de materiais para o grupo de estudos de Computação")),
            new ExemploEmpresa(2, "horizonte", "aurora", "Horizonte Digital", "Horizonte Digital Júnior",
                    "Do laboratório para o portfólio: projetos de BCC",
                    "A equipe Horizonte está preparando uma mostra de projetos desenvolvidos durante o semestre. "
                    + "A ideia é reunir aplicações web, compartilhar o que cada grupo aprendeu "
                    + "e conversar sobre tecnologia com os colegas do IFC Rio do Sul.",
                    "Materiais para a apresentação dos projetos de BCC",
                    new ExemploCurso("Matemática", "Matemática em grupo: resolução de problemas",
                            "A turma de Matemática está preparando encontros para resolver problemas e compartilhar "
                            + "estratégias de estudo. A ideia é reunir colegas do IFC Rio do Sul para conversar "
                            + "sobre cálculo, geometria e propostas de atividades para a sala de aula.",
                            "Materiais para a oficina de Matemática")),
            new ExemploEmpresa(3, "verde", "", "Verde Serviços", "Verde Projetos Estudantis",
                    "Tecnologia e sustentabilidade em um projeto de extensão",
                    "A equipe Verde propõe um projeto estudantil sobre reaproveitamento de equipamentos eletrônicos. "
                    + "A proposta une programação, consumo consciente e troca de conhecimentos "
                    + "entre estudantes e a comunidade de Rio do Sul.",
                    "Componentes para a oficina de reaproveitamento eletrônico",
                    new ExemploCurso("Física", "Física na prática: experimentos de baixo custo",
                            "A turma de Física propõe uma oficina de experimentos com materiais acessíveis. "
                            + "A proposta é explorar conceitos de mecânica, óptica e eletricidade "
                            + "e compartilhar os resultados com a comunidade do IFC Rio do Sul.",
                            "Componentes para a oficina de experimentos de Física")));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder senhas;

    public DadosIniciais(JdbcTemplate jdbc, PasswordEncoder senhas) {
        this.jdbc = jdbc;
        this.senhas = senhas;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM empresas", Integer.class) != 0) {
            adaptarExemplosExistentes();
            return;
        }
        for (ExemploEmpresa empresa : EXEMPLOS) {
            jdbc.update("INSERT INTO empresas (id, nome, codigo, conflitos) VALUES (?, ?, ?, ?)",
                    empresa.id(), empresa.curso().nome(), empresa.codigo(), empresa.conflitos());
        }
        criarUsuario("Administrador da turma", "admin", "Admin@123", "ADMIN", 1, 2, 3);
        criarUsuario("Carlos", "carlos", "Usuario@123", "COMUM", 1);
        criarUsuario("Luisinho", "luisinho", "Usuario@123", "COMUM", 2);
        criarUsuario("Pedro", "pedro", "Usuario@123", "COMUM", 3);
        Long admin = jdbc.queryForObject("SELECT id FROM usuarios WHERE login = 'admin'", Long.class);
        for (ExemploEmpresa empresa : EXEMPLOS) {
            jdbc.update("INSERT INTO noticias (empresa_id, autor_id, titulo, conteudo) VALUES (?, ?, ?, ?)",
                    empresa.id(), admin, empresa.curso().titulo(), empresa.curso().conteudo());
            jdbc.update("""
                    INSERT INTO contas (empresa_id, descricao, valor, tipo, vencimento, situacao)
                    VALUES (?, ?, 199.90, 'PAGAR', CURRENT_DATE, 'PENDENTE')
                    """, empresa.id(), empresa.curso().conta());
        }
    }

    private void adaptarExemplosExistentes() {
        // Reconhece as duas versões anteriores sem alterar códigos, vínculos ou o histórico CWP.
        for (ExemploEmpresa empresa : EXEMPLOS) {
            int alteradas = jdbc.update("UPDATE empresas SET nome = ? WHERE id = ? AND codigo = ? AND nome IN (?, ?)",
                    empresa.curso().nome(), empresa.id(), empresa.codigo(), empresa.nomeAnterior(), empresa.nome());
            if (alteradas == 0) {
                continue;
            }
            jdbc.update("""
                    UPDATE noticias SET titulo = ?, conteudo = ?
                    WHERE empresa_id = ? AND (
                        (titulo = 'Boas-vindas à nossa empresa'
                         AND conteudo = 'Este é o mural público de notícias. Cada publicação identifica sua empresa de origem.')
                        OR (titulo = ? AND conteudo = ?))
                    AND autor_id = (SELECT id FROM usuarios WHERE login = 'admin')
                    """, empresa.curso().titulo(), empresa.curso().conteudo(), empresa.id(),
                    empresa.titulo(), empresa.conteudo());
            jdbc.update("""
                    UPDATE contas SET descricao = ? WHERE empresa_id = ?
                    AND descricao IN ('Serviços de internet', ?) AND valor = 199.90
                    AND tipo = 'PAGAR' AND situacao = 'PENDENTE'
                    """, empresa.curso().conta(), empresa.id(), empresa.conta());
        }
        jdbc.update("""
                UPDATE usuarios SET nome = 'Administrador da turma'
                WHERE login = 'admin' AND nome = 'Administrador de demonstração'
                """);
        renomearUsuarioExemplo("ana", "Ana da Aurora", "Ana da Computação", "carlos", "Carlos");
        renomearUsuarioExemplo("bruno", "Bruno da Horizonte", "Bruno da Matemática", "luisinho", "Luisinho");
        renomearUsuarioExemplo("clara", "Clara da Verde", "Clara da Física", "pedro", "Pedro");
    }

    private void renomearUsuarioExemplo(String loginAnterior, String nomeOriginal, String nomeAnterior,
            String login, String nome) {
        jdbc.update("""
                UPDATE usuarios SET nome = ?, login = ?
                WHERE login = ? AND nome IN (?, ?)
                AND NOT EXISTS (SELECT 1 FROM usuarios WHERE login = ?)
                """, nome, login, loginAnterior, nomeOriginal, nomeAnterior, login);
    }

    private void criarUsuario(String nome, String login, String senha, String perfil, long... empresas) {
        jdbc.update("INSERT INTO usuarios (nome, login, senha_hash) VALUES (?, ?, ?)",
                nome, login, senhas.encode(senha));
        Long usuarioId = jdbc.queryForObject("SELECT id FROM usuarios WHERE login = ?", Long.class, login);
        for (long empresaId : empresas) {
            jdbc.update("INSERT INTO vinculos (usuario_id, empresa_id, perfil) VALUES (?, ?, ?)",
                    usuarioId, empresaId, perfil);
        }
    }
}
