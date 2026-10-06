package notificador.modelo;

import java.text.DecimalFormat;

/** Linha de cad_produtos com estoque <= min_estoque. */
public final class ProdutoEstoqueBaixo implements ItemAlerta {

    private static final DecimalFormat QTD = new DecimalFormat("#,##0.###");

    private final int id;
    private final String nome;
    private final String codBarra;
    private final String unidade;
    private final double estoque;
    private final double minEstoque;

    public ProdutoEstoqueBaixo(int id, String nome, String codBarra, String unidade,
                               double estoque, double minEstoque) {
        this.id = id;
        this.nome = nome;
        this.codBarra = codBarra;
        this.unidade = unidade;
        this.estoque = estoque;
        this.minEstoque = minEstoque;
    }

    @Override
    public String chave() {
        return "produto:" + id;
    }

    @Override
    public String descricao() {
        String un = unidade == null ? "" : " " + unidade.trim();
        return nome + " - estoque " + QTD.format(estoque) + un + " (mín. " + QTD.format(minEstoque) + ")";
    }

    public int getId()            { return id; }
    public String getNome()       { return nome; }
    public String getCodBarra()   { return codBarra; }
    public String getUnidade()    { return unidade; }
    public double getEstoque()    { return estoque; }
    public double getMinEstoque() { return minEstoque; }
}
