package notificador;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.UIManager;

import notificador.agendador.AgendadorVerificacoes;
import notificador.canal.BandejaSistemaCanal;
import notificador.canal.CanalNotificacao;
import notificador.canal.HistoricoCanal;
import notificador.canal.LogCanal;
import notificador.canal.WhatsAppCanal;
import notificador.config.AppConfig;
import notificador.db.ConexaoMySQL;
import notificador.historico.HistoricoNotificacoes;
import notificador.ia.ProcessadorNotificacao;
import notificador.servico.NotificacaoService;
import notificador.servico.SituacaoAtual;
import notificador.ui.CentralNotificacoes;
import notificador.ia.AnalisadorCompras;
import notificador.modelo.FaixasAtraso;
import notificador.verificacao.CobrancaVerificador;
import notificador.verificacao.EstoqueBaixoVerificador;
import notificador.verificacao.Verificador;

/**
 * Ponto de entrada. Monta as peças e deixa o programa a correr em segundo plano.
 *
 * Uso: java -jar NotificadorPDV.jar [caminho/para/config.properties]
 */
public final class App {

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %3$s - %5$s%6$s%n");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // fica com o visual padrão do Swing
        }

        AppConfig cfg = AppConfig.carregar(new File(args.length > 0 ? args[0] : "config.properties"));
        ConexaoMySQL conexao = new ConexaoMySQL(cfg);
        if (conexao.testar()) {
            LOG.info("Ligado ao MySQL: " + conexao.getUrl());
        }

        // O agendador e a bandeja dependem um do outro (menu "Verificar agora"),
        // por isso o agendador é criado primeiro com um ciclo que delega no serviço.
        final NotificacaoService[] servico = new NotificacaoService[1];
        AgendadorVerificacoes agendador = new AgendadorVerificacoes(() -> servico[0].executarCiclo());

        // Central de Notificações: situação actual + histórico gravado em ficheiro.
        HistoricoNotificacoes historico = new HistoricoNotificacoes(
                cfg.historicoArquivo(), cfg.historicoRetencaoDias());
        SituacaoAtual situacao = new SituacaoAtual();
        CentralNotificacoes central = new CentralNotificacoes(situacao, historico,
                new FaixasAtraso(cfg.cobrancaFaixas()), agendador::executarAgora);

        BandejaSistemaCanal bandeja = new BandejaSistemaCanal(
                cfg.maxLinhasBalao(),
                central::mostrar,
                agendador::executarAgora,
                () -> System.exit(0));
        bandeja.instalar();

        servico[0] = new NotificacaoService(
                conexao,
                criarVerificadores(cfg),
                criarProcessadores(cfg),
                criarCanais(cfg, historico, bandeja),
                situacao,
                central::atualizar);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agendador.parar();
            bandeja.remover();
            LOG.info("Notificador encerrado.");
        }));

        agendador.iniciar(cfg.atrasoInicialSegundos(), cfg.intervaloMinutos());
    }

    /** Regras de negócio activas. Novos alertas entram aqui. */
    private static List<Verificador> criarVerificadores(AppConfig cfg) {
        List<Verificador> lista = new ArrayList<>();
        if (cfg.estoqueAtivo()) {
            lista.add(new EstoqueBaixoVerificador(cfg.estoqueIgnorarMinimoZero(), cfg.estoqueIgnorarSemVendaDias()));
        }
        if (cfg.cobrancaAtivo()) {
            lista.add(new CobrancaVerificador(new FaixasAtraso(cfg.cobrancaFaixas()),
                    cfg.cobrancaModosIgnorados(), cfg.cobrancaClientesIgnorados()));
        }
        if (cfg.comprasAtivo()) {
            lista.add(new AnalisadorCompras(new AnalisadorCompras.ParametrosCompras(
                    cfg.comprasCoberturaAlvoDias(), cfg.comprasCoberturaMinimaDias(),
                    cfg.comprasCoberturaExcessoDias(), cfg.comprasTendenciaPct(),
                    cfg.comprasMinVendas90Dias(), cfg.comprasValorMinimoParado(),
                    cfg.comprasSazonalFator(), cfg.comprasSazonalMinAnos(), cfg.comprasSazonalMinVendas(),
                    cfg.comprasMaisVendidosPct(), cfg.comprasMaisVendidosMinVendas(),
                    cfg.comprasAvisoAntesMinimoDias(),
                    cfg.comprasDataReferencia())));
        }
        return lista;
    }

    /** Processadores de IA (vazio até existir uma implementação). */
    private static List<ProcessadorNotificacao> criarProcessadores(AppConfig cfg) {
        List<ProcessadorNotificacao> lista = new ArrayList<>();
        if (cfg.iaAtivo()) {
            // TODO: lista.add(new MeuProcessadorIA(cfg));
            LOG.warning("ia.ativo=true, mas nenhum ProcessadorNotificacao foi registado ainda.");
        }
        return lista;
    }

    /** Destinos das notificações. */
    private static List<CanalNotificacao> criarCanais(AppConfig cfg, HistoricoNotificacoes historico,
                                                      BandejaSistemaCanal bandeja) {
        List<CanalNotificacao> lista = new ArrayList<>();
        lista.add(new HistoricoCanal(historico)); // antes da bandeja: o clique no balão já mostra o registo
        lista.add(new LogCanal());
        lista.add(bandeja);
        if (cfg.whatsappAtivo()) {
            lista.add(new WhatsAppCanal());
        }
        return lista;
    }
}
