package notificador.canal;

import java.util.logging.Logger;

import notificador.modelo.Notificacao;

/** Regista cada notificação no log (útil para depuração e histórico). */
public final class LogCanal implements CanalNotificacao {

    private static final Logger LOG = Logger.getLogger(LogCanal.class.getName());

    @Override
    public String nome() {
        return "Log";
    }

    @Override
    public void enviar(Notificacao n) {
        LOG.info("[" + n.getTipo() + "] " + n.getTitulo() + "\n" + n.textoCompleto());
    }
}
