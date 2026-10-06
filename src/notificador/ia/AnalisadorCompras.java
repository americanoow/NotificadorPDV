package notificador.ia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import notificador.modelo.ItemAlerta;
import notificador.modelo.SugestaoCompra;
import notificador.modelo.SugestaoCompra.Acao;
import notificador.modelo.TipoNotificacao;
import notificador.verificacao.Verificador;

/**
 * Analisa o histórico de vendas (mv_vendas_movimento) e sugere em que
 * produtos aumentar ou reduzir as compras, explicando porquê.
 *
 * 1. Ritmo atual: compara os últimos 30 dias com os 60 anteriores, estima a
 *    procura diária (60% recente + 40% anterior) e para quantos dias o
 *    estoque chega (cobertura).
 * 2. Época do ano: para os próximos 2 meses, compara a fatia que o produto
 *    costuma ter nas vendas da loja nesses meses com a fatia média do ano,
 *    usando todos os anos de histórico. Medir pela fatia (e não pela
 *    quantidade) evita confundir o crescimento da loja com "época de alta".
 *
 * A data de referência é o último dia com movimento real (>= 3 itens), não
 * CURDATE(), salvo se compras.data_referencia estiver preenchida.
 */
public final class AnalisadorCompras implements Verificador {

    private static final String SQL_REFERENCIA =
            "SELECT data_venda FROM mv_vendas_movimento"
          + " WHERE data_venda <= CURDATE()"
          + " GROUP BY data_venda HAVING COUNT(*) >= 3"
          + " ORDER BY data_venda DESC LIMIT 1";

    // O JOIN usa o índice id_produto de mv_vendas_movimento.
    private static final String SQL_VENDAS =
            "SELECT p.id, p.nome_produto, p.unidade, p.estoque, p.min_estoque,"
          + "       IFNULL(p.vr_compra, 0) AS vr_compra,"
          + "       SUM(CASE WHEN m.data_venda > DATE_SUB(?, INTERVAL 30 DAY) THEN m.quant ELSE 0 END) AS q_recente,"
          + "       SUM(CASE WHEN m.data_venda <= DATE_SUB(?, INTERVAL 30 DAY) THEN m.quant ELSE 0 END) AS q_anterior,"
          + "       COUNT(m.id) AS n_vendas,"
          + "       IFNULL(SUM(m.vr_total), 0) AS faturamento"
          + "  FROM cad_produtos p"
          + "  LEFT JOIN mv_vendas_movimento m ON m.id_produto = p.id"
          + "        AND m.data_venda > DATE_SUB(?, INTERVAL 90 DAY) AND m.data_venda <= ?"
          + " WHERE p.inativo = 0"
          + " GROUP BY p.id, p.nome_produto, p.unidade, p.estoque, p.min_estoque, p.vr_compra";

    /** Movimento da loja (itens vendidos): total, nos meses da janela e nos últimos 12 meses. */
    private static final String SQL_LOJA =
            "SELECT COUNT(*) AS total,"
          + "       SUM(MONTH(data_venda) IN (?, ?)) AS janela,"
          + "       SUM(data_venda >= DATE_SUB(?, INTERVAL 12 MONTH)) AS ult12"
          + "  FROM mv_vendas_movimento WHERE data_venda < ?";

    // Conta vendas (linhas), não quantidades: uma compra grande isolada não cria uma "época".
    private static final String SQL_HISTORICO =
            "SELECT id_produto, SUM(quant) AS qtd, COUNT(*) AS vendas,"
          + "       SUM(MONTH(data_venda) IN (?, ?)) AS vendas_janela,"
          + "       COUNT(DISTINCT CASE WHEN MONTH(data_venda) IN (?, ?) THEN YEAR(data_venda) END) AS anos_janela,"
          + "       COUNT(DISTINCT YEAR(data_venda)) AS anos"
          + "  FROM mv_vendas_movimento WHERE data_venda < ?"
          + " GROUP BY id_produto"
          + " HAVING vendas >= ? AND qtd > 0";

    private static final String[] MESES =
            {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    private static final DecimalFormat QTD = new DecimalFormat("#,##0.#");
    private static final DecimalFormat PCT = new DecimalFormat("0");
    private static final DecimalFormat FATOR = new DecimalFormat("0.0");
    private static final DecimalFormat FATIA = new DecimalFormat("0.#");

    /** Estoques fora disto são lixo no cadastro (já vimos -7 biliões). */
    private static final double ESTOQUE_ABSURDO = 1_000_000;

    private final ParametrosCompras p;

    public AnalisadorCompras(ParametrosCompras parametros) {
        this.p = parametros;
    }

    @Override
    public TipoNotificacao tipo() {
        return TipoNotificacao.SUGESTAO_COMPRA;
    }

    @Override
    public List<SugestaoCompra> buscar(Connection con) throws SQLException {
        Date referencia = p.dataReferencia != null ? p.dataReferencia : dataReferencia(con);
        List<SugestaoCompra> aumentar = new ArrayList<>();
        List<SugestaoCompra> reduzir = new ArrayList<>();
        if (referencia == null) {
            return aumentar;
        }
        Janela janela = new Janela(referencia);
        Map<Integer, Sazonalidade> epocas = sazonalidade(con, janela);

        List<Produto> produtos = new ArrayList<>();
        java.sql.Date ref = new java.sql.Date(referencia.getTime());
        try (PreparedStatement ps = con.prepareStatement(SQL_VENDAS)) {
            for (int i = 1; i <= 4; i++) {
                ps.setDate(i, ref);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    produtos.add(new Produto(rs));
                }
            }
        }
        Map<Integer, Double> curvaA = curvaA(produtos);

        for (Produto pr : produtos) {
            SugestaoCompra s = avaliar(pr.id, pr.nome, pr.unidade, pr.estoque, pr.minimo, pr.custo,
                    pr.qRecente, pr.qAnterior, pr.nVendas, curvaA.get(pr.id),
                    epocas.get(pr.id), janela, referencia);
            if (s != null) {
                (s.getAcao() == Acao.AUMENTAR ? aumentar : reduzir).add(s);
            }
        }

        // Mais vendidos primeiro, depois o mais urgente; reduzir: mais dinheiro parado primeiro.
        aumentar.sort(Comparator.comparing((SugestaoCompra s) -> !s.isMaisVendido())
                .thenComparingDouble(SugestaoCompra::getCoberturaDias)
                .thenComparing(Comparator.comparingDouble(SugestaoCompra::getValor).reversed()));
        reduzir.sort(Comparator.comparingDouble(SugestaoCompra::getValor).reversed());
        aumentar.addAll(reduzir);
        return aumentar;
    }

    @Override
    public String titulo(int quantidade) {
        return quantidade == 1 ? "1 sugestão de compra" : quantidade + " sugestões de compra";
    }

    @Override
    public String titulo(List<? extends ItemAlerta> novos) {
        int maisVendidos = 0;
        for (ItemAlerta i : novos) {
            SugestaoCompra s = (SugestaoCompra) i;
            if (s.isMaisVendido() && s.getAcao() == Acao.AUMENTAR) {
                maisVendidos++;
            }
        }
        if (maisVendidos == 0) {
            return titulo(novos.size());
        }
        return (maisVendidos == 1 ? "1 mais vendido para repor" : maisVendidos + " mais vendidos para repor")
                + " - " + titulo(novos.size());
    }

    /**
     * Mais vendidos: curva A (os produtos que, somados do maior para o menor, fazem
     * os primeiros X% do faturamento dos últimos 90 dias) E com venda frequente, para
     * que um produto caro vendido uma ou duas vezes não conte como "vende bastante".
     *
     * @return id -> fatia do faturamento (0.03 = 3%) só para os mais vendidos
     */
    private Map<Integer, Double> curvaA(List<Produto> produtos) {
        List<Produto> ordenados = new ArrayList<>();
        double total = 0;
        for (Produto pr : produtos) {
            if (pr.faturamento > 0) {
                ordenados.add(pr);
                total += pr.faturamento;
            }
        }
        Map<Integer, Double> mapa = new HashMap<>();
        if (total <= 0) {
            return mapa;
        }
        ordenados.sort(Comparator.comparingDouble((Produto pr) -> pr.faturamento).reversed());
        double acumulado = 0;
        for (Produto pr : ordenados) {
            if (acumulado >= p.maisVendidosPct / 100.0 * total) {
                break;
            }
            if (pr.nVendas >= p.maisVendidosMinVendas) {
                mapa.put(pr.id, pr.faturamento / total);
            }
            acumulado += pr.faturamento;
        }
        return mapa;
    }

    private static Date dataReferencia(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_REFERENCIA);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDate(1) : null;
        }
    }

    /** Só devolve produtos com época de alta ou de baixa confiável nos meses da janela. */
    private Map<Integer, Sazonalidade> sazonalidade(Connection con, Janela j) throws SQLException {
        Map<Integer, Sazonalidade> mapa = new HashMap<>();
        double lojaTotal;
        double lojaJanela;
        double lojaMes;
        try (PreparedStatement ps = con.prepareStatement(SQL_LOJA)) {
            ps.setInt(1, j.mes1);
            ps.setInt(2, j.mes2);
            ps.setDate(3, j.inicioMesReferencia);
            ps.setDate(4, j.inicioMesReferencia);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return mapa;
                }
                lojaTotal = rs.getDouble("total");
                lojaJanela = rs.getDouble("janela");
                lojaMes = rs.getDouble("ult12") / 12.0;
            }
        }
        if (lojaTotal <= 0 || lojaJanela <= 0) {
            return mapa;
        }

        try (PreparedStatement ps = con.prepareStatement(SQL_HISTORICO)) {
            ps.setInt(1, j.mes1);
            ps.setInt(2, j.mes2);
            ps.setInt(3, j.mes1);
            ps.setInt(4, j.mes2);
            ps.setDate(5, j.inicioMesReferencia);
            ps.setInt(6, p.sazonalMinVendas);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double vendas = rs.getDouble("vendas");
                    double vendasJanela = rs.getDouble("vendas_janela");
                    double fatiaJanela = vendasJanela / lojaJanela;
                    double indice = fatiaJanela / (vendas / lojaTotal);
                    boolean alta = indice >= p.sazonalFator
                            && vendasJanela >= p.sazonalMinVendas
                            && rs.getInt("anos_janela") >= p.sazonalMinAnos;
                    // Na baixa exige-se o volume que seria de esperar nesses meses se não houvesse época.
                    boolean baixa = indice <= 1 / p.sazonalFator
                            && vendas * lojaJanela / lojaTotal >= p.sazonalMinVendas
                            && rs.getInt("anos") >= p.sazonalMinAnos;
                    if (alta || baixa) {
                        double qtdPorVenda = rs.getDouble("qtd") / vendas;
                        mapa.put(rs.getInt("id_produto"), new Sazonalidade(
                                indice, fatiaJanela * lojaMes * qtdPorVenda, rs.getInt("anos_janela")));
                    }
                }
            }
        }
        return mapa;
    }

    /** @return a sugestão para o produto, ou null se não há nada a fazer. */
    SugestaoCompra avaliar(int id, String nome, String unidade, double estoque, double minimo,
                           double custo, double qRecente, double qAnterior, int nVendas,
                           Double fatiaFaturamento, Sazonalidade epoca, Janela janela, Date referencia) {
        if (Math.abs(estoque) > ESTOQUE_ABSURDO) {
            return null;
        }
        double ritmoRecente = Math.max(qRecente, 0) / 30.0;
        double ritmoAnterior = Math.max(qAnterior, 0) / 60.0;
        double procuraAtual = 0.6 * ritmoRecente + 0.4 * ritmoAnterior;
        Double tendencia = ritmoAnterior > 0 ? ritmoRecente / ritmoAnterior - 1 : null;
        double emStock = Math.max(estoque, 0);
        String un = unidade == null || unidade.trim().isEmpty() ? "" : " " + unidade.trim();
        double mediaMensalAnterior = Math.max(qAnterior, 0) / 2.0;
        double limiteTendencia = p.tendenciaPct / 100.0;

        boolean alta = epoca != null && epoca.indice >= 1;
        boolean baixa = epoca != null && epoca.indice < 1;
        double esperadoDia = epoca == null ? 0 : epoca.esperadoMes / 30.0;
        // Na alta prepara-se para o que costuma vender; na baixa não se compra pelo ritmo de agora.
        double procura = alta ? Math.max(procuraAtual, esperadoDia)
                       : baixa ? Math.min(procuraAtual, esperadoDia)
                       : procuraAtual;
        Double cobertura = procura > 0 ? emStock / procura : null;
        Double indice = epoca == null ? null : epoca.indice;
        String textoEpoca = epoca == null ? null : textoEpoca(epoca, janela, un);
        boolean maisVendido = fatiaFaturamento != null;

        // ---- MAIS VENDIDO A CHEGAR AO MÍNIMO CADASTRADO ---------------------
        // Avisa antes de zerar: quando já está no mínimo ou vai chegar nele em poucos dias.
        if (maisVendido && minimo > 0 && procura > 0) {
            double diasAteMinimo = (emStock - minimo) / procura;
            double comprar = Math.ceil(procura * p.coberturaAlvoDias + minimo - emStock);
            if (diasAteMinimo <= p.avisoAntesMinimoDias && comprar > 0) {
                List<String> m = new ArrayList<>();
                m.add("Mais vendido: " + nVendas + " vendas e " + FATIA.format(fatiaFaturamento * 100)
                        + "% do faturamento nos últimos 90 dias");
                m.add("vende ~" + QTD.format(procura * 7) + un + " por semana");
                if (emStock == 0) {
                    m.add("está sem estoque");
                } else if (estoque <= minimo) {
                    m.add("já está no mínimo cadastrado (" + QTD.format(minimo) + un + "): os "
                            + QTD.format(emStock) + un + " que restam duram ~" + QTD.format(Math.floor(cobertura)) + " dias");
                } else {
                    m.add("chega ao mínimo cadastrado (" + QTD.format(minimo) + un + ") em ~"
                            + QTD.format(Math.max(1, Math.floor(diasAteMinimo))) + " dias e zera em ~"
                            + QTD.format(Math.floor(cobertura)) + " dias");
                }
                if (alta || baixa) {
                    m.add(textoEpoca);
                }
                if (estoque < 0) {
                    m.add("ATENÇÃO: estoque no sistema negativo (" + QTD.format(estoque) + un
                            + "), entradas de compra não lançadas; confira o estoque físico");
                }
                m.add("comprar ~" + QTD.format(comprar) + un + " cobre " + p.coberturaAlvoDias
                        + " dias sem descer do mínimo");
                return new SugestaoCompra(Acao.AUMENTAR, id, nome, unidade, estoque, qRecente,
                        mediaMensalAnterior, tendencia, cobertura, comprar, comprar * custo,
                        juntar(m), indice, true, referencia);
            }
        }

        // ---- AUMENTAR -----------------------------------------------------
        if ((nVendas >= p.minVendas90Dias || alta) && cobertura != null) {
            boolean acabando = cobertura < p.coberturaMinimaDias;
            boolean subindo = !baixa && tendencia != null && tendencia >= limiteTendencia
                    && cobertura < p.coberturaAlvoDias;
            boolean abaixoMinimo = minimo > 0 && estoque < minimo && cobertura < p.coberturaAlvoDias;
            boolean epocaAlta = alta && cobertura < p.coberturaAlvoDias;
            double comprar = Math.ceil(procura * p.coberturaAlvoDias - emStock);
            if ((acabando || subindo || abaixoMinimo || epocaAlta) && comprar > 0) {
                List<String> m = new ArrayList<>();
                if (epocaAlta) {
                    m.add(textoEpoca);
                }
                m.add(procuraAtual * 7 >= 0.05
                        ? "Hoje vende ~" + QTD.format(procuraAtual * 7) + un + " por semana"
                        : procuraAtual > 0 ? "Quase não vendeu nos últimos 90 dias"
                                           : "Não vendeu nos últimos 90 dias (fora de época)");
                if (emStock == 0) {
                    m.add("está sem estoque");
                } else if (acabando || abaixoMinimo || epocaAlta) {
                    m.add("o estoque atual dá para só ~" + QTD.format(Math.floor(cobertura)) + " dias");
                }
                if (subindo) {
                    m.add("as vendas subiram " + PCT.format(tendencia * 100) + "% (últimos 30 dias: "
                            + QTD.format(qRecente) + un + "; antes: ~" + QTD.format(mediaMensalAnterior) + un + "/mês)");
                } else if (tendencia == null && qRecente > 0) {
                    m.add("começou a vender agora (" + QTD.format(qRecente) + un + " nos últimos 30 dias)");
                }
                if (abaixoMinimo && emStock > 0) {
                    m.add("está abaixo do mínimo cadastrado (" + QTD.format(minimo) + un + ")");
                }
                if (baixa) {
                    m.add(textoEpoca + ": compre só o necessário");
                }
                if (estoque < 0) {
                    m.add("ATENÇÃO: estoque no sistema negativo (" + QTD.format(estoque) + un
                            + "), entradas de compra não lançadas; confira o estoque físico");
                }
                m.add("comprar ~" + QTD.format(comprar) + un + " cobre " + p.coberturaAlvoDias + " dias de venda");
                return new SugestaoCompra(Acao.AUMENTAR, id, nome, unidade, estoque, qRecente,
                        mediaMensalAnterior, tendencia, cobertura, comprar, comprar * custo,
                        juntar(m), indice, maisVendido, referencia);
            }
        }

        // ---- REDUZIR ------------------------------------------------------
        double parado = emStock * custo;
        if (alta || emStock <= 0 || parado < p.valorMinimoParado) {
            return null;
        }
        boolean semVenda = procuraAtual <= 0;
        boolean excesso = cobertura != null && cobertura > p.coberturaExcessoDias;
        boolean caindo = !semVenda && tendencia != null && tendencia <= -limiteTendencia
                && cobertura != null && cobertura > 2.0 * p.coberturaAlvoDias;
        boolean epocaBaixa = baixa && (cobertura == null || cobertura > 2.0 * p.coberturaAlvoDias);
        if (!semVenda && !excesso && !caindo && !epocaBaixa) {
            return null;
        }
        List<String> m = new ArrayList<>();
        if (epocaBaixa) {
            m.add(textoEpoca);
        }
        if (semVenda) {
            m.add("Nenhuma venda nos últimos 90 dias");
        } else if (excesso) {
            m.add(cobertura > 3 * 365
                    ? "Vende muito pouco (~" + QTD.format(procura * 30) + un + "/mês): o estoque dá para mais de 3 anos"
                    : "O estoque dá para ~" + QTD.format(Math.floor(cobertura / 30)) + " meses de venda");
        } else if (caindo) {
            m.add("As vendas caíram " + PCT.format(-tendencia * 100) + "% (últimos 30 dias: "
                    + QTD.format(qRecente) + un + "; antes: ~" + QTD.format(mediaMensalAnterior) + un + "/mês)");
        }
        m.add(moeda(parado) + " parados em estoque (" + QTD.format(estoque) + un + ")");
        m.add(semVenda && !baixa ? "não reponha; considere promoção ou kit para girar"
                                 : "suspenda novas compras até o estoque baixar");
        double excessoQtd = semVenda ? emStock : Math.max(0, emStock - procura * p.coberturaAlvoDias);
        return new SugestaoCompra(Acao.REDUZIR, id, nome, unidade, estoque, qRecente,
                mediaMensalAnterior, tendencia, cobertura, Math.floor(excessoQtd), parado,
                juntar(m), indice, maisVendido, referencia);
    }

    private static String textoEpoca(Sazonalidade e, Janela j, String un) {
        String anos = e.anosJanela == 1 ? "1 ano" : e.anosJanela + " anos";
        if (e.indice >= 1) {
            return "Época de alta: em " + j.rotulo + " costuma vender ~" + QTD.format(e.esperadoMes) + un
                    + "/mês, " + FATOR.format(e.indice) + "× o normal (visto em " + anos + " de histórico)";
        }
        if (e.indice < 0.05) {
            return "Época de baixa: em " + j.rotulo + " quase não vende";
        }
        return "Época de baixa: em " + j.rotulo + " costuma vender " + PCT.format((1 - e.indice) * 100)
                + "% menos que no resto do ano";
    }

    private static String juntar(List<String> partes) {
        return String.join("; ", partes) + ".";
    }

    private static String moeda(double v) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(v);
    }

    /** Uma linha de SQL_VENDAS. */
    private static final class Produto {
        final int id;
        final String nome;
        final String unidade;
        final double estoque;
        final double minimo;
        final double custo;
        final double qRecente;
        final double qAnterior;
        final int nVendas;
        final double faturamento;

        Produto(ResultSet rs) throws SQLException {
            id = rs.getInt("id");
            nome = rs.getString("nome_produto");
            unidade = rs.getString("unidade");
            estoque = rs.getDouble("estoque");
            minimo = rs.getDouble("min_estoque");
            custo = rs.getDouble("vr_compra");
            qRecente = rs.getDouble("q_recente");
            qAnterior = rs.getDouble("q_anterior");
            nVendas = rs.getInt("n_vendas");
            faturamento = rs.getDouble("faturamento");
        }
    }

    /** Os meses de daqui a 30 e 60 dias: o que a compra feita agora vai abastecer. */
    static final class Janela {
        final int mes1;
        final int mes2;
        final String rotulo;
        final java.sql.Date inicioMesReferencia;

        Janela(Date referencia) {
            Calendar c = Calendar.getInstance();
            c.setTime(referencia);
            c.add(Calendar.DAY_OF_MONTH, 30);
            mes1 = c.get(Calendar.MONTH) + 1;
            c.add(Calendar.DAY_OF_MONTH, 30);
            mes2 = c.get(Calendar.MONTH) + 1;
            rotulo = mes1 == mes2 ? MESES[mes1 - 1] : MESES[mes1 - 1] + "–" + MESES[mes2 - 1];

            c.setTime(referencia);
            c.set(Calendar.DAY_OF_MONTH, 1);
            inicioMesReferencia = new java.sql.Date(c.getTimeInMillis());
        }
    }

    /** Comportamento do produto nos meses da janela face ao resto do ano. */
    static final class Sazonalidade {
        /** 2.0 = nesses meses o produto tem o dobro da sua fatia habitual nas vendas da loja. */
        final double indice;
        /** Quantidade que costuma vender por mês nesses meses, ao volume atual da loja. */
        final double esperadoMes;
        final int anosJanela;

        Sazonalidade(double indice, double esperadoMes, int anosJanela) {
            this.indice = indice;
            this.esperadoMes = esperadoMes;
            this.anosJanela = anosJanela;
        }
    }

    /** Limiares da análise, lidos do config.properties (secção compras.*). */
    public static final class ParametrosCompras {
        final int coberturaAlvoDias;
        final int coberturaMinimaDias;
        final int coberturaExcessoDias;
        final int tendenciaPct;
        final int minVendas90Dias;
        final double valorMinimoParado;
        final double sazonalFator;
        final int sazonalMinAnos;
        final int sazonalMinVendas;
        final int maisVendidosPct;
        final int maisVendidosMinVendas;
        final int avisoAntesMinimoDias;
        /** null = automático (último dia com vendas). */
        final Date dataReferencia;

        public ParametrosCompras(int coberturaAlvoDias, int coberturaMinimaDias, int coberturaExcessoDias,
                                 int tendenciaPct, int minVendas90Dias, double valorMinimoParado,
                                 double sazonalFator, int sazonalMinAnos, int sazonalMinVendas,
                                 int maisVendidosPct, int maisVendidosMinVendas, int avisoAntesMinimoDias,
                                 Date dataReferencia) {
            this.maisVendidosPct = maisVendidosPct;
            this.maisVendidosMinVendas = maisVendidosMinVendas;
            this.avisoAntesMinimoDias = avisoAntesMinimoDias;
            this.coberturaAlvoDias = coberturaAlvoDias;
            this.coberturaMinimaDias = coberturaMinimaDias;
            this.coberturaExcessoDias = coberturaExcessoDias;
            this.tendenciaPct = tendenciaPct;
            this.minVendas90Dias = minVendas90Dias;
            this.valorMinimoParado = valorMinimoParado;
            this.sazonalFator = sazonalFator;
            this.sazonalMinAnos = sazonalMinAnos;
            this.sazonalMinVendas = sazonalMinVendas;
            this.dataReferencia = dataReferencia;
        }
    }
}
