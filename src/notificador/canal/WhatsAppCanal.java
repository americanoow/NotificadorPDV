package notificador.canal;

import java.util.logging.Logger;

import notificador.modelo.ClienteDevedor;
import notificador.modelo.ItemAlerta;
import notificador.modelo.Notificacao;
import notificador.modelo.TipoNotificacao;

/**
 * ESQUELETO - canal WhatsApp ainda não implementado.
 * Activado com whatsapp.ativo=true no config.properties.
 *
 * Sugestão de implementação:
 *  - ESTOQUE BAIXO / SUGESTÃO DE COMPRA: enviar o resumo para o número do dono/gerente.
 *  - COBRANÇA: enviar uma mensagem de cobrança a cada cliente,
 *    usando ClienteDevedor.contato() (normalizar para +55DDDNÚMERO).
 *  - API: WhatsApp Cloud API (Meta), Twilio, Z-API, Evolution API, etc.
 *    Guarde token/URL no config.properties e leia via AppConfig.texto(...).
 */
public final class WhatsAppCanal implements CanalNotificacao {

    private static final Logger LOG = Logger.getLogger(WhatsAppCanal.class.getName());

    @Override
    public String nome() {
        return "WhatsApp";
    }

    @Override
    public void enviar(Notificacao n) {
        if (n.getTipo() == TipoNotificacao.COBRANCA) {
            for (ItemAlerta item : n.getItens()) {
                ClienteDevedor c = (ClienteDevedor) item;
                String mensagem = n.getExtras().getOrDefault("mensagem_cobranca", item.descricao());
                // TODO: chamar a API do WhatsApp com c.contato() e mensagem.
                LOG.info("[WhatsApp - não implementado] para " + c.contato() + ": " + mensagem);
            }
        } else {
            // TODO: enviar n.resumo(...) para o número do responsável.
            LOG.info("[WhatsApp - não implementado] " + n.getTitulo());
        }
    }
}
