package notificador.servico;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import notificador.canal.CanalNotificacao;
import notificador.db.ConexaoMySQL;
import notificador.ia.ProcessadorNotificacao;
import notificador.modelo.ItemAlerta;
import notificador.modelo.Notificacao;
import notificador.verificacao.Verificador;

/**
 * Orquestra um ciclo completo:
 *   1. abre a ligação ao MySQL;
 *   2. corre cada Verificador (consultas SQL);
 *   3. descarta itens já notificados hoje;
 *   4. passa a notificação pelos processadores (IA);
 *   5. entrega a cada canal (histórico, tray, log, WhatsApp...);
 *   6. actualiza a SituacaoAtual (tudo o que está em alerta, novo ou não).
 *
 * Uma falha num verificador ou num canal não impede os restantes.
 */
public final class NotificacaoService {

    private static final Logger LOG = Logger.getLogger(NotificacaoService.class.getName());

    private final ConexaoMySQL conexao;
    private final List<Verificador> verificadores;
    private final List<ProcessadorNotificacao> processadores;
    private final List<CanalNotificacao> canais;
    private final SituacaoAtual situacao;
    private final Runnable aoConcluirCiclo;
    private final ControleDuplicidade duplicidade = new ControleDuplicidade();

    /**
     * @param aoConcluirCiclo chamado no fim de cada ciclo (ex.: refrescar a
     *                        Central de Notificações). Pode ser null.
     */
    public NotificacaoService(ConexaoMySQL conexao,
                              List<Verificador> verificadores,
                              List<ProcessadorNotificacao> processadores,
                              List<CanalNotificacao> canais,
                              SituacaoAtual situacao,
                              Runnable aoConcluirCiclo) {
        this.conexao = conexao;
        this.verificadores = verificadores;
        this.processadores = processadores;
        this.canais = canais;
        this.situacao = situacao;
        this.aoConcluirCiclo = aoConcluirCiclo;
    }

    /** Executa todas as verificações uma vez. Chamado pelo agendador. */
    public void executarCiclo() {
        LOG.info("Iniciando ciclo de verificação...");
        try (Connection con = conexao.abrir()) {
            for (Verificador v : verificadores) {
                verificar(con, v);
            }
            situacao.marcarVerificacao(new Date());
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Sem ligação ao MySQL (" + e.getMessage() + "). Nova tentativa no próximo ciclo.");
        }
        LOG.info("Ciclo concluído.");
        if (aoConcluirCiclo != null) {
            aoConcluirCiclo.run();
        }
    }

    private void verificar(Connection con, Verificador v) {
        List<? extends ItemAlerta> encontrados;
        try {
            encontrados = v.buscar(con);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Erro na verificação " + v.tipo(), e);
            return;
        }
        situacao.atualizar(v.tipo(), encontrados);

        List<ItemAlerta> novos = new ArrayList<>();
        for (ItemAlerta item : encontrados) {
            if (!duplicidade.jaNotificado(chave(v, item))) {
                novos.add(item);
            }
        }
        LOG.info(v.tipo() + ": " + encontrados.size() + " encontrado(s), " + novos.size() + " novo(s) hoje.");
        if (novos.isEmpty()) {
            return;
        }

        Notificacao n = new Notificacao(v.tipo(), v.titulo(novos), novos);
        processar(n);
        if (entregar(n)) {
            for (ItemAlerta item : novos) {
                duplicidade.registrar(chave(v, item));
            }
        }
    }

    private void processar(Notificacao n) {
        for (ProcessadorNotificacao p : processadores) {
            try {
                p.processar(n);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Processador " + p.getClass().getSimpleName() + " falhou", e);
            }
        }
    }

    /** @return true se pelo menos um canal entregou a notificação. */
    private boolean entregar(Notificacao n) {
        boolean algumEntregou = false;
        for (CanalNotificacao canal : canais) {
            try {
                canal.enviar(n);
                algumEntregou = true;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Canal " + canal.nome() + " falhou", e);
            }
        }
        return algumEntregou;
    }

    private static String chave(Verificador v, ItemAlerta item) {
        return v.tipo() + "|" + item.chave();
    }
}
