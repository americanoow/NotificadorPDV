package notificador.modelo;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Cliente com parcelas vencidas e não pagas, classificado pela parcela mais antiga. */
public final class ClienteDevedor implements ItemAlerta {

    private final int idCliente;
    private final String nome;
    private final String telefone;
    private final String celular;
    private final int parcelas;
    private final double valorVencido;
    private final Date vencimentoMaisAntigo;
    private final int diasAtraso;
    private final int faixa;
    private final String rotuloFaixa;

    public ClienteDevedor(int idCliente, String nome, String telefone, String celular, int parcelas,
                          double valorVencido, Date vencimentoMaisAntigo, int diasAtraso,
                          int faixa, String rotuloFaixa) {
        this.idCliente = idCliente;
        this.nome = nome;
        this.telefone = telefone;
        this.celular = celular;
        this.parcelas = parcelas;
        this.valorVencido = valorVencido;
        this.vencimentoMaisAntigo = vencimentoMaisAntigo;
        this.diasAtraso = diasAtraso;
        this.faixa = faixa;
        this.rotuloFaixa = rotuloFaixa;
    }

    /** Inclui a faixa: o cliente volta a ser notificado quando muda de faixa. */
    @Override
    public String chave() {
        return "cliente:" + idCliente + ":" + faixa;
    }

    @Override
    public String descricao() {
        String moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valorVencido);
        String data = new SimpleDateFormat("dd/MM/yyyy").format(vencimentoMaisAntigo);
        String qtd = parcelas == 1 ? "1 parcela" : parcelas + " parcelas";
        return "[" + rotuloFaixa + "] " + nome + " - " + moeda + " (" + qtd + ", vencida desde " + data + ")";
    }

    /** Celular se houver, senão telefone. */
    public String contato() {
        if (celular != null && !celular.trim().isEmpty()) {
            return celular.trim();
        }
        return telefone == null ? "" : telefone.trim();
    }

    public int getIdCliente()             { return idCliente; }
    public String getNome()               { return nome; }
    public String getTelefone()           { return telefone; }
    public String getCelular()            { return celular; }
    public int getParcelas()              { return parcelas; }
    public double getValorVencido()       { return valorVencido; }
    public Date getVencimentoMaisAntigo() { return vencimentoMaisAntigo; }
    public int getDiasAtraso()            { return diasAtraso; }
    public int getFaixa()                 { return faixa; }
    public String getRotuloFaixa()        { return rotuloFaixa; }
}
