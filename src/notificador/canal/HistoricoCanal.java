package notificador.canal;

import notificador.historico.HistoricoNotificacoes;
import notificador.modelo.Notificacao;

/** Grava cada notificação emitida no histórico (visível na Central de Notificações). */
public final class HistoricoCanal implements CanalNotificacao {

    private final HistoricoNotificacoes historico;

    public HistoricoCanal(HistoricoNotificacoes historico) {
        this.historico = historico;
    }

    @Override
    public String nome() {
        return "Histórico";
    }

    @Override
    public void enviar(Notificacao n) throws Exception {
        historico.registrar(n);
    }
}
