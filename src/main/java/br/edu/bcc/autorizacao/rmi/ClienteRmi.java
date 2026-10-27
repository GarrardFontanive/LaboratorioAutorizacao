package br.edu.bcc.autorizacao.rmi;

import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

/** Cliente independente: executa em outra JVM e não abre conexão com o banco. */
public final class ClienteRmi {
    private ClienteRmi() {
    }

    public static void main(String[] args) {
        int resultado = executar(args, System.out, System.err);
        if (resultado != 0) {
            System.exit(resultado);
        }
    }

    static int executar(String[] args, PrintStream saida, PrintStream erros) {
        if (args.length == 1 && args[0].equals("--help")) {
            uso(saida);
            return 0;
        }
        if (args.length > 3) {
            uso(erros);
            return 2;
        }
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        try {
            int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
            if (porta < 1 || porta > 65535) {
                throw new IllegalArgumentException("A porta deve estar entre 1 e 65535.");
            }
            String codigoCurso = args.length > 2 ? args[2] : "";
            var registro = LocateRegistry.getRegistry(host, porta);
            var mural = (MuralRemoto) registro.lookup(MuralRemoto.NOME_SERVICO);
            saida.printf("Conectado por RMI a %s:%d/%s%n", host, porta, MuralRemoto.NOME_SERVICO);
            saida.println("\nCursos:");
            for (var curso : mural.listarCursos()) {
                saida.printf("- %s (%s)%n", curso.nome(), curso.codigo());
            }
            var noticias = mural.listarNoticias(codigoCurso);
            saida.println("\nNotícias públicas:");
            if (noticias.isEmpty()) {
                saida.println("Nenhuma notícia publicada.");
            }
            for (var noticia : noticias) {
                saida.printf("%n[%s] %s%nPor %s em %s%n%s%n", noticia.curso(), noticia.titulo(),
                        noticia.autor(), noticia.criadaEm(), noticia.conteudo());
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            erros.println("Consulta inválida: " + exception.getMessage());
            uso(erros);
            return 2;
        } catch (RemoteException | NotBoundException | ClassCastException exception) {
            erros.println("Não foi possível consultar o RMI: " + exception.getMessage());
            erros.println("Confira se o servidor está iniciado e se host e porta estão corretos.");
            return 1;
        }
    }

    private static void uso(PrintStream saida) {
        saida.println("Uso: java -jar laboratorio-autorizacao-1.0.0-cliente-rmi.jar [host] [porta] [codigoCurso]");
        saida.println("Padrão: 127.0.0.1 1099, todos os cursos. Códigos iniciais: aurora, horizonte, verde.");
    }
}
