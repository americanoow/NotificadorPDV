package notificador.agendador;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rotina agendada que executa as verificações a cada N minutos (padrão 60).
 *
 * Usa uma única thread, por isso "Verificar agora" e o ciclo agendado nunca
 * correm ao mesmo tempo - entram na mesma fila.
 */
public final class AgendadorVerificacoes {

    private static final Logger LOG = Logger.getLogger(AgendadorVerificacoes.class.getName());

    private final ScheduledExecutorService executor;
    private final Runnable tarefa;

    public AgendadorVerificacoes(Runnable ciclo) {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agendador-verificacoes");
            t.setDaemon(false); // mantém o programa vivo em segundo plano
            return t;
        });
        // Uma exceção não tratada cancelaria silenciosamente as execuções
        // seguintes do scheduleAtFixedRate; por isso capturamos tudo aqui.
        this.tarefa = () -> {
            try {
                ciclo.run();
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Erro inesperado no ciclo de verificação", t);
            }
        };
    }

    public void iniciar(long atrasoInicialSegundos, long intervaloMinutos) {
        executor.scheduleAtFixedRate(tarefa, atrasoInicialSegundos,
                TimeUnit.MINUTES.toSeconds(intervaloMinutos), TimeUnit.SECONDS);
        LOG.info("Agendador iniciado: primeira verificação em " + atrasoInicialSegundos
                + " s, depois a cada " + intervaloMinutos + " min.");
    }

    /** Coloca uma verificação extra na fila (menu "Verificar agora"). */
    public void executarAgora() {
        executor.execute(tarefa);
    }

    public void parar() {
        executor.shutdownNow();
    }
}
