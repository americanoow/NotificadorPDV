package notificador.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import notificador.historico.HistoricoNotificacoes;
import notificador.modelo.ClienteDevedor;
import notificador.modelo.FaixasAtraso;
import notificador.modelo.ItemAlerta;
import notificador.modelo.RegistroHistorico;
import notificador.modelo.SugestaoCompra;
import notificador.modelo.SugestaoCompra.Acao;
import notificador.modelo.TipoNotificacao;
import notificador.servico.SituacaoAtual;

/**
 * Janela "Central de Notificações", aberta pelo ícone da bandeja.
 *
 *  - Situação atual: tudo o que está em alerta na última verificação.
 *  - Cobrança: clientes com parcelas vencidas, por faixa de atraso.
 *  - Compras (IA): o que comprar mais e o que deixar de comprar, com o motivo.
 *  - Histórico: todas as notificações emitidas, gravadas em ficheiro.
 *
 * Fechar a janela só a esconde; o programa continua em segundo plano.
 * Os métodos públicos podem ser chamados de qualquer thread.
 */
public final class CentralNotificacoes {

    private static final int ABA_SITUACAO = 0;
    private static final int ABA_COBRANCA = 1;
    private static final int ABA_COMPRAS = 2;

    private static final SimpleDateFormat FMT_DATA_HORA = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    private static final SimpleDateFormat FMT_DATA = new SimpleDateFormat("dd/MM/yyyy");
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private static final DecimalFormat QTD = new DecimalFormat("#,##0.##");
    private static final DecimalFormat INTEIRO = new DecimalFormat("#,##0");
    private static final DecimalFormat PERCENT = new DecimalFormat("+0%;-0%");

    /** Índice sazonal: 2.1 -> "Alta 2,1×", 0.5 -> "Baixa 0,5×". */
    private static final Format EPOCA = new Format() {
        private final DecimalFormat fator = new DecimalFormat("0.0");

        @Override
        public StringBuffer format(Object v, StringBuffer sb, FieldPosition pos) {
            double i = ((Number) v).doubleValue();
            return sb.append(i >= 1 ? "Alta " : "Baixa ").append(fator.format(i)).append('×');
        }

        @Override
        public Object parseObject(String s, ParsePosition pos) {
            throw new UnsupportedOperationException();
        }
    };

    private static final Color AMARELO = new Color(255, 243, 205);
    private static final Color VERMELHO = new Color(246, 190, 190);
    private static final Color VERDE = new Color(214, 240, 214);
    private static final Color LARANJA = new Color(252, 226, 196);

    private final SituacaoAtual situacao;
    private final HistoricoNotificacoes historico;
    private final FaixasAtraso faixas;
    private final Map<String, Color> coresFaixa = new LinkedHashMap<>();
    private final Runnable acaoVerificarAgora;

    // Componentes Swing: criados e usados só na thread do Swing (EDT).
    private JFrame janela;
    private JTabbedPane abas;
    private PainelTabela painelSituacao;
    private PainelTabela painelCobranca;
    private PainelTabela painelCompras;
    private PainelTabela painelHistorico;
    private JPanel resumoCobranca;
    private JLabel resumoCompras;
    private JTextArea motivoCompra;
    private JLabel estado;
    private List<SugestaoCompra> sugestoes = Collections.emptyList();

    public CentralNotificacoes(SituacaoAtual situacao, HistoricoNotificacoes historico,
                               FaixasAtraso faixas, Runnable acaoVerificarAgora) {
        this.situacao = situacao;
        this.historico = historico;
        this.faixas = faixas;
        this.acaoVerificarAgora = acaoVerificarAgora;
        List<String> rotulos = faixas.rotulos();
        for (int i = 0; i < rotulos.size(); i++) {
            float t = rotulos.size() == 1 ? 1f : (float) i / (rotulos.size() - 1);
            coresFaixa.put(rotulos.get(i), misturar(AMARELO, VERMELHO, t));
        }
    }

    /** Abre (ou traz para a frente) a janela, no separador do tipo indicado (null = situação atual). */
    public void mostrar(TipoNotificacao foco) {
        SwingUtilities.invokeLater(() -> {
            if (janela == null) {
                construir();
            }
            recarregar();
            if (foco == TipoNotificacao.COBRANCA) {
                abas.setSelectedIndex(ABA_COBRANCA);
            } else if (foco == TipoNotificacao.SUGESTAO_COMPRA) {
                abas.setSelectedIndex(ABA_COMPRAS);
            } else {
                abas.setSelectedIndex(ABA_SITUACAO);
                painelSituacao.filtrar(foco == null ? null : foco.getDescricao());
            }
            janela.setVisible(true);
            janela.setExtendedState(janela.getExtendedState() & ~JFrame.ICONIFIED);
            janela.toFront();
            janela.requestFocus();
        });
    }

    /** Refresca os dados se a janela já estiver aberta (chamado no fim de cada ciclo). */
    public void atualizar() {
        SwingUtilities.invokeLater(() -> {
            if (janela != null && janela.isVisible()) {
                recarregar();
            }
        });
    }

    private void construir() {
        janela = new JFrame("Notificador PDV - Central de Notificações");
        janela.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        List<String> tipos = new ArrayList<>();
        for (TipoNotificacao t : TipoNotificacao.values()) {
            tipos.add(t.getDescricao());
        }
        painelSituacao = new PainelTabela(
                new String[] {"Tipo", "Código", "Descrição"},
                new int[] {130, 150, 800}, "Tipo", tipos, 0);
        painelHistorico = new PainelTabela(
                new String[] {"Data/hora", "Tipo", "Código", "Descrição"},
                new int[] {120, 130, 150, 680}, "Tipo", tipos, 1);

        abas = new JTabbedPane();
        abas.addTab("Situação atual", painelSituacao);
        abas.addTab("Cobrança", construirCobranca());
        abas.addTab("Compras (IA)", construirCompras());
        abas.addTab("Histórico", painelHistorico);

        estado = new JLabel();
        JButton verificar = new JButton("Verificar agora");
        verificar.addActionListener(e -> {
            estado.setText("Verificando...");
            acaoVerificarAgora.run(); // ao terminar, o serviço chama atualizar()
        });
        JButton recarregar = new JButton("Recarregar");
        recarregar.addActionListener(e -> recarregar());

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        botoes.add(recarregar);
        botoes.add(verificar);
        rodape.add(estado, BorderLayout.WEST);
        rodape.add(botoes, BorderLayout.EAST);

        janela.getContentPane().add(abas, BorderLayout.CENTER);
        janela.getContentPane().add(rodape, BorderLayout.SOUTH);
        janela.setSize(1150, 680);
        janela.setLocationRelativeTo(null);
    }

    private JPanel construirCobranca() {
        painelCobranca = new PainelTabela(
                new String[] {"Faixa", "Cliente", "Contato", "Parcelas", "Valor vencido", "Vencida desde", "Dias"},
                new int[] {80, 330, 130, 70, 120, 110, 60}, "Faixa", faixas.rotulos(), 0);
        painelCobranca.formatarColuna(3, INTEIRO);
        painelCobranca.formatarColuna(4, MOEDA);
        painelCobranca.formatarColuna(5, FMT_DATA);
        painelCobranca.formatarColuna(6, INTEIRO);
        painelCobranca.colorirPor(0, coresFaixa);

        resumoCobranca = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        resumoCobranca.setBorder(BorderFactory.createEmptyBorder(10, 4, 0, 4));

        JPanel p = new JPanel(new BorderLayout());
        p.add(resumoCobranca, BorderLayout.NORTH);
        p.add(painelCobranca, BorderLayout.CENTER);
        return p;
    }

    private JPanel construirCompras() {
        List<String> acoes = new ArrayList<>();
        Map<String, Color> cores = new LinkedHashMap<>();
        for (Acao a : Acao.values()) {
            acoes.add(a.getRotulo());
        }
        cores.put(Acao.AUMENTAR.getRotulo(), VERDE);
        cores.put(Acao.REDUZIR.getRotulo(), LARANJA);

        painelCompras = new PainelTabela(
                new String[] {"Ação", "Mais vendido", "Produto", "Un", "Estoque", "Vendido 30 dias",
                              "Média/mês antes", "Tendência", "Época", "Dura (dias)", "Quantidade", "Valor"},
                new int[] {110, 80, 270, 35, 65, 85, 90, 65, 75, 70, 75, 90}, "Ação", acoes, 0);
        painelCompras.formatarColuna(4, QTD);
        painelCompras.formatarColuna(5, QTD);
        painelCompras.formatarColuna(6, QTD);
        painelCompras.formatarColuna(7, PERCENT);
        painelCompras.formatarColuna(8, EPOCA);
        painelCompras.formatarColuna(9, INTEIRO);
        painelCompras.formatarColuna(10, QTD);
        painelCompras.formatarColuna(11, MOEDA);
        painelCompras.colorirPor(0, cores);

        motivoCompra = new JTextArea(3, 40);
        motivoCompra.setEditable(false);
        motivoCompra.setLineWrap(true);
        motivoCompra.setWrapStyleWord(true);
        motivoCompra.setFont(new JLabel().getFont().deriveFont(13f));
        motivoCompra.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        painelCompras.aoSelecionar(i -> motivoCompra.setText(i < 0 || i >= sugestoes.size()
                ? "Selecione um produto para ver o motivo da sugestão."
                : sugestoes.get(i).getNome() + "\n" + sugestoes.get(i).getMotivo()));
        JScrollPane rolagem = new JScrollPane(motivoCompra);
        rolagem.setBorder(BorderFactory.createTitledBorder("Por quê?"));

        JSplitPane divisao = new JSplitPane(JSplitPane.VERTICAL_SPLIT, painelCompras, rolagem);
        divisao.setResizeWeight(0.8);
        divisao.setBorder(null);

        resumoCompras = new JLabel();
        resumoCompras.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

        JPanel p = new JPanel(new BorderLayout());
        p.add(resumoCompras, BorderLayout.NORTH);
        p.add(divisao, BorderLayout.CENTER);
        return p;
    }

    /** Lê SituacaoAtual e histórico e preenche todas as tabelas. */
    private void recarregar() {
        Map<TipoNotificacao, List<ItemAlerta>> atuais = situacao.getItens();
        List<Object[]> linhas = new ArrayList<>();
        StringBuilder resumo = new StringBuilder();
        for (Map.Entry<TipoNotificacao, List<ItemAlerta>> e : atuais.entrySet()) {
            String tipo = e.getKey().getDescricao();
            for (ItemAlerta item : e.getValue()) {
                linhas.add(new Object[] {tipo, item.chave(), item.descricao()});
            }
            resumo.append(resumo.length() == 0 ? "" : "  |  ")
                  .append(tipo).append(": ").append(e.getValue().size());
        }
        painelSituacao.setLinhas(linhas);

        recarregarCobranca(atuais.getOrDefault(TipoNotificacao.COBRANCA, Collections.emptyList()));
        recarregarCompras(atuais.getOrDefault(TipoNotificacao.SUGESTAO_COMPRA, Collections.emptyList()));

        List<Object[]> hist = new ArrayList<>();
        for (RegistroHistorico r : historico.listar()) {
            hist.add(new Object[] {FMT_DATA_HORA.format(r.getDataHora()), r.getTipo().getDescricao(),
                    r.getChave(), r.getDescricao()});
        }
        painelHistorico.setLinhas(hist);

        Date ultima = situacao.getUltimaVerificacao();
        estado.setText(ultima == null
                ? "Ainda nenhuma verificação concluída."
                : "Última verificação: " + FMT_DATA_HORA.format(ultima) + "   —   " + resumo);
    }

    private void recarregarCobranca(List<ItemAlerta> itens) {
        List<Object[]> linhas = new ArrayList<>();
        Map<String, int[]> qtdPorFaixa = new LinkedHashMap<>();
        Map<String, Double> valorPorFaixa = new LinkedHashMap<>();
        for (String r : faixas.rotulos()) {
            qtdPorFaixa.put(r, new int[1]);
            valorPorFaixa.put(r, 0.0);
        }
        double total = 0;
        for (ItemAlerta item : itens) {
            ClienteDevedor c = (ClienteDevedor) item;
            linhas.add(new Object[] {c.getRotuloFaixa(), c.getNome(), c.contato(), c.getParcelas(),
                    c.getValorVencido(), c.getVencimentoMaisAntigo(), c.getDiasAtraso()});
            qtdPorFaixa.get(c.getRotuloFaixa())[0]++;
            valorPorFaixa.merge(c.getRotuloFaixa(), c.getValorVencido(), Double::sum);
            total += c.getValorVencido();
        }
        painelCobranca.setLinhas(linhas);

        resumoCobranca.removeAll();
        resumoCobranca.add(cartao("Todas as faixas", itens.size(), total, new Color(230, 230, 230), null));
        for (String r : faixas.rotulos()) {
            resumoCobranca.add(cartao(r, qtdPorFaixa.get(r)[0], valorPorFaixa.get(r), coresFaixa.get(r), r));
        }
        resumoCobranca.revalidate();
        resumoCobranca.repaint();
    }

    /** Cartão clicável com o total de uma faixa; clicar filtra a tabela por essa faixa. */
    private JLabel cartao(String titulo, int clientes, double valor, Color fundo, String filtro) {
        JLabel l = new JLabel("<html><center><b>" + titulo + "</b><br>"
                + clientes + (clientes == 1 ? " cliente" : " clientes") + "<br>"
                + MOEDA.format(valor) + "</center></html>");
        l.setOpaque(true);
        l.setBackground(fundo);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fundo.darker()),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.setToolTipText("Mostrar só " + titulo.toLowerCase());
        l.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                painelCobranca.filtrar(filtro);
            }
        });
        return l;
    }

    private void recarregarCompras(List<ItemAlerta> itens) {
        List<SugestaoCompra> lista = new ArrayList<>();
        List<Object[]> linhas = new ArrayList<>();
        int nAumentar = 0;
        int nMaisVendidos = 0;
        int nReduzir = 0;
        double custoCompras = 0;
        double parado = 0;
        Date referencia = null;
        for (ItemAlerta item : itens) {
            SugestaoCompra s = (SugestaoCompra) item;
            lista.add(s);
            referencia = s.getDataReferencia();
            if (s.isMaisVendido() && s.getAcao() == Acao.AUMENTAR) {
                nMaisVendidos++;
            }
            linhas.add(new Object[] {s.getAcao().getRotulo(), s.isMaisVendido() ? "★ Sim" : "",
                    s.getNome(), s.getUnidade(), s.getEstoque(),
                    s.getVendido30Dias(), s.getMediaMensalAnterior(), s.getTendencia(), s.getIndiceSazonal(),
                    s.getCoberturaDias(), s.getQuantidade(), s.getValor()});
            if (s.getAcao() == Acao.AUMENTAR) {
                nAumentar++;
                custoCompras += s.getValor();
            } else {
                nReduzir++;
                parado += s.getValor();
            }
        }
        sugestoes = lista;
        painelCompras.setLinhas(linhas);
        motivoCompra.setText("Selecione um produto para ver o motivo da sugestão.");

        resumoCompras.setText(referencia == null
                ? "Sem dados de vendas para analisar."
                : "<html>Análise das vendas dos 90 dias até <b>" + FMT_DATA.format(referencia) + "</b>"
                  + " &nbsp;—&nbsp; <span style='color:#c0392b'><b>" + nMaisVendidos
                  + "</b> mais vendidos para repor</span>"
                  + " &nbsp;—&nbsp; <span style='color:#1e7b1e'><b>" + nAumentar + "</b> para aumentar (~"
                  + MOEDA.format(custoCompras) + " em compras)</span>"
                  + " &nbsp;—&nbsp; <span style='color:#b35900'><b>" + nReduzir + "</b> para reduzir ("
                  + MOEDA.format(parado) + " parados em estoque)</span>"
                  + destaqueEpoca(lista) + "</html>");
    }

    /** Linha com os produtos de época de alta que precisam de compra, os de maior valor primeiro. */
    private static String destaqueEpoca(List<SugestaoCompra> lista) {
        List<SugestaoCompra> alta = new ArrayList<>();
        for (SugestaoCompra s : lista) {
            if (s.getAcao() == Acao.AUMENTAR && s.getIndiceSazonal() != null && s.getIndiceSazonal() >= 1) {
                alta.add(s);
            }
        }
        if (alta.isEmpty()) {
            return "";
        }
        alta.sort((a, b) -> Double.compare(b.getValor(), a.getValor()));
        StringBuilder sb = new StringBuilder("<br><span style='color:#1f5fa8'><b>Época de alta chegando:</b> ");
        int n = Math.min(5, alta.size());
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ", ").append(html(alta.get(i).getNome()));
        }
        if (alta.size() > n) {
            sb.append(" e mais ").append(alta.size() - n);
        }
        return sb.append(" (clique no título da coluna Época para ordenar)</span>").toString();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Color misturar(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
