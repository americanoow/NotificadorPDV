package notificador.modelo;

/** Tipos de alerta. O nome da constante é gravado no histórico: não renomeie sem migrar o ficheiro. */
public enum TipoNotificacao {
    ESTOQUE_BAIXO("Estoque baixo"),
    COBRANCA("Cobrança"),
    SUGESTAO_COMPRA("Sugestão de compra");

    private final String descricao;

    TipoNotificacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
