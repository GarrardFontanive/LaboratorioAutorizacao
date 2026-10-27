package br.edu.bcc.autorizacao.rmi;

import br.edu.bcc.autorizacao.rmi.DadosMural.Curso;
import br.edu.bcc.autorizacao.rmi.DadosMural.Noticia;
import br.edu.bcc.autorizacao.service.LaboratorioService;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ServicoMuralRmi implements MuralRemoto {
    private static final Logger LOG = LoggerFactory.getLogger(ServicoMuralRmi.class);
    private final LaboratorioService laboratorio;

    ServicoMuralRmi(LaboratorioService laboratorio) {
        this.laboratorio = laboratorio;
    }

    @Override
    public List<Curso> listarCursos() throws RemoteException {
        return consultar(() -> new ArrayList<>(laboratorio.empresas().stream()
                .map(curso -> new Curso(curso.id(), curso.codigo(), curso.nome())).toList()));
    }

    @Override
    public List<Noticia> listarNoticias(String codigoCurso) throws RemoteException {
        String codigo = codigoCurso == null ? "" : codigoCurso.strip().toLowerCase(Locale.ROOT);
        if (codigo.length() > 40) {
            throw new IllegalArgumentException("Código de curso inválido.");
        }
        return consultar(() -> {
            Long cursoId = codigo.isEmpty() ? null : laboratorio.empresas().stream()
                    .filter(curso -> curso.codigo().equals(codigo))
                    .map(curso -> curso.id()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Curso não encontrado: " + codigo));
            return new ArrayList<>(laboratorio.noticiasPublicas().stream()
                    .filter(noticia -> cursoId == null || noticia.empresaId() == cursoId)
                    .map(noticia -> new Noticia(noticia.id(), noticia.empresaId(), noticia.empresa(),
                            noticia.titulo(), noticia.conteudo(), noticia.autor(), noticia.criadaEm()))
                    .toList());
        });
    }

    private <T> T consultar(Supplier<T> consulta) throws RemoteException {
        try {
            return consulta.get();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.error("Falha ao consultar o mural por RMI", exception);
            // O cliente depende somente do contrato Java, não das exceções internas do Spring/JDBC.
            throw new RemoteException("Não foi possível consultar o mural. Confira o log do servidor.");
        }
    }
}
