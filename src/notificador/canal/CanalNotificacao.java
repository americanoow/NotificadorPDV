package notificador.canal;

import notificador.modelo.Notificacao;

/**
 * Destino de uma notificação: pop-up no ecrã, WhatsApp, e-mail, etc.
 * Todos os canais registados recebem todas as notificações; cada canal
 * decide o que fazer com cada tipo (ex.: o WhatsApp pode ignorar stock baixo).
 */
public interface CanalNotificacao {

    String nome();

    void enviar(Notificacao notificacao) throws Exception;
}
