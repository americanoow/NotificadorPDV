package notificador.modelo;

import java.util.Date;

/** Uma linha do histórico: um item que foi notificado num dado momento. */
public final class RegistroHistorico {

    private final Date dataHora;
    private final TipoNotificacao tipo;
    private final String chave;
    private final String descricao;

    public RegistroHistorico(Date dataHora, TipoNotificacao tipo, String chave, String descricao) {
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.chave = chave;
        this.descricao = descricao;
    }

    public Date getDataHora()       { return dataHora; }
    public TipoNotificacao getTipo() { return tipo; }
    public String getChave()        { return chave; }
    public String getDescricao()    { return descricao; }
}
