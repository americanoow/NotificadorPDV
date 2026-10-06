package notificador.modelo;

import java.text.DecimalFormat;
import java.util.Date;

/** Recomendação de aumentar ou reduzir a compra de um produto, com o motivo por extenso. */
public final class SugestaoCompra implements ItemAlerta {

    public enum Acao {
        AUMENTAR("Aumentar compra"),
        REDUZIR("Reduzir compra");

        private final String rotulo;

        Acao(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    private static final DecimalFormat QTD = new DecimalFormat("#,##0.##");

    private final Acao acao;
    private final int idProduto;
    private final String nome;
    private final String unidade;
    private final double estoque;
    private final double vendido30Dias;
    private final double mediaMensalAnterior;
    private final Double tendencia;
    private final Double coberturaDias;
    private final double quantidade;
    private final double valor;
    private final String motivo;
    private final Double indiceSazonal;
    private final boolean maisVendido;
    private final Date dataReferencia;

    /**
     * @param tendencia     variação das vendas (0.45 = +45%); null se não havia vendas antes
     * @param coberturaDias quantos dias o estoque atual dura; null se o produto não vende
     * @param quantidade    AUMENTAR: quanto comprar; REDUZIR: excesso em estoque
     * @param valor         AUMENTAR: custo estimado da compra; REDUZIR: dinheiro parado em estoque
     * @param indiceSazonal vendas nos próximos meses face ao normal (2.0 = dobro); null se não há época marcada
     * @param maisVendido   curva A: está entre os produtos que fazem 80% do faturamento
     */
    public SugestaoCompra(Acao acao, int idProduto, String nome, String unidade, double estoque,
                          double vendido30Dias, double mediaMensalAnterior, Double tendencia,
                          Double coberturaDias, double quantidade, double valor, String motivo,
                          Double indiceSazonal, boolean maisVendido, Date dataReferencia) {
        this.indiceSazonal = indiceSazonal;
        this.maisVendido = maisVendido;
        this.acao = acao;
        this.idProduto = idProduto;
        this.nome = nome;
        this.unidade = unidade == null ? "" : unidade.trim();
        this.estoque = estoque;
        this.vendido30Dias = vendido30Dias;
        this.mediaMensalAnterior = mediaMensalAnterior;
        this.tendencia = tendencia;
        this.coberturaDias = coberturaDias;
        this.quantidade = quantidade;
        this.valor = valor;
        this.motivo = motivo;
        this.dataReferencia = dataReferencia;
    }

    @Override
    public String chave() {
        return "compra:" + acao.name() + ":" + idProduto;
    }

    @Override
    public String descricao() {
        String qtd = QTD.format(quantidade) + (unidade.isEmpty() ? "" : " " + unidade);
        return acao == Acao.AUMENTAR
                ? nome + " - comprar ~" + qtd + ". " + motivo
                : nome + " - excesso de ~" + qtd + ". " + motivo;
    }

    public Acao getAcao()                  { return acao; }
    public int getIdProduto()              { return idProduto; }
    public String getNome()                { return nome; }
    public String getUnidade()             { return unidade; }
    public double getEstoque()             { return estoque; }
    public double getVendido30Dias()       { return vendido30Dias; }
    public double getMediaMensalAnterior() { return mediaMensalAnterior; }
    public Double getTendencia()           { return tendencia; }
    public Double getCoberturaDias()       { return coberturaDias; }
    public double getQuantidade()          { return quantidade; }
    public double getValor()               { return valor; }
    public String getMotivo()              { return motivo; }
    public Double getIndiceSazonal()       { return indiceSazonal; }
    public boolean isMaisVendido()         { return maisVendido; }
    public Date getDataReferencia()        { return dataReferencia; }
}
