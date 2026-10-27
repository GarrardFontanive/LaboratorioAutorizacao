package br.edu.bcc.autorizacao.rmi;

import br.edu.bcc.autorizacao.service.LaboratorioService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rmi.enabled", havingValue = "true", matchIfMissing = true)
public class ServidorRmi implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ServidorRmi.class);
    private final LaboratorioService laboratorio;
    private final String host;
    private final int portaRegistro;
    private final int portaServico;
    private Registry registro;
    private ServicoMuralRmi servico;

    public ServidorRmi(LaboratorioService laboratorio,
            @Value("${app.rmi.host:127.0.0.1}") String host,
            @Value("${app.rmi.registry-port:1099}") int portaRegistro,
            @Value("${app.rmi.service-port:1100}") int portaServico) {
        this.laboratorio = laboratorio;
        this.host = host;
        this.portaRegistro = portaRegistro;
        this.portaServico = portaServico;
    }

    /** Publica o serviço depois da inicialização do banco e dos dados de exemplo. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void iniciar() {
        if (registro != null) {
            return;
        }
        try {
            InetAddress endereco = InetAddress.getByName(host);
            RMIServerSocketFactory sockets = new SocketsRmi(endereco);
            System.setProperty("java.rmi.server.hostname", host);
            registro = LocateRegistry.createRegistry(portaRegistro, null, sockets);
            servico = new ServicoMuralRmi(laboratorio);
            Remote stub = UnicastRemoteObject.exportObject(servico, portaServico, null, sockets);
            registro.rebind(MuralRemoto.NOME_SERVICO, stub);
            LOG.info("RMI disponível em {}:{}/{}; porta do serviço: {}",
                    host, portaRegistro, MuralRemoto.NOME_SERVICO, portaServico);
        } catch (IOException exception) {
            close();
            throw new IllegalStateException("Não foi possível iniciar o RMI. Confira RMI_HOST, "
                    + "RMI_PORT e RMI_SERVICE_PORT; as portas precisam estar livres.", exception);
        }
    }

    @Override
    @PreDestroy
    public synchronized void close() {
        desexportar(servico);
        desexportar(registro);
        servico = null;
        registro = null;
    }

    private void desexportar(Remote objeto) {
        if (objeto != null) {
            try {
                UnicastRemoteObject.unexportObject(objeto, true);
            } catch (NoSuchObjectException exception) {
                // Também permite fechar após uma inicialização que falhou antes da exportação.
            }
        }
    }

    // A igualdade por endereço permite reutilizar corretamente o transporte ao reiniciar.
    private record SocketsRmi(InetAddress endereco) implements RMIServerSocketFactory {
        @Override
        public ServerSocket createServerSocket(int porta) throws IOException {
            return new ServerSocket(porta, 0, endereco);
        }
    }
}
