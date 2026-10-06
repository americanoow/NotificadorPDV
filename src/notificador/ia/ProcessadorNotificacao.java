package notificador.ia;

import notificador.modelo.Notificacao;

/**
 * Ponto de extensão para a IA: corre DEPOIS das consultas SQL e ANTES
 * dos canais. Pode enriquecer a notificação gravando texto em
 * {@link Notificacao#getExtras()}, por exemplo:
 *
 *  - "sugestao_compra": lista de compras priorizada a partir do stock baixo;
 *  - "mensagem_cobranca": mensagem de cobrança educada para o WhatsApp.
 *
 * Os dados brutos estão em {@link Notificacao#getItens()} (faça cast para
 * ProdutoEstoqueBaixo, ClienteDevedor ou SugestaoCompra conforme getTipo()).
 *
 * Registe a implementação em App.criarProcessadores() quando ia.ativo=true.
 */
public interface ProcessadorNotificacao {

    void processar(Notificacao notificacao) throws Exception;
}
