package br.edu.bcc.autorizacao.rmi;

import br.edu.bcc.autorizacao.rmi.DadosMural.Curso;
import br.edu.bcc.autorizacao.rmi.DadosMural.Noticia;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/** Contrato compartilhado pelo servidor e pelo cliente Java. */
public interface MuralRemoto extends Remote {
    String NOME_SERVICO = "MuralCursos";

    List<Curso> listarCursos() throws RemoteException;

    /** Código vazio ou nulo consulta o mural inteiro; os demais filtram um curso. */
    List<Noticia> listarNoticias(String codigoCurso) throws RemoteException;
}
