package notificador.modelo;

/**
 * Cada linha encontrada por uma verificação (um produto, um cliente...).
 * Os modelos concretos guardam os dados brutos para que módulos futuros
 * (IA, WhatsApp) possam usá-los sem refazer a consulta SQL.
 */
public interface ItemAlerta {

    /**
     * Identificador estável do item, usado para não repetir o mesmo alerta
     * várias vezes no mesmo dia. Ex.: "produto:14".
     */
    String chave();

    /** Linha de texto legível para o pop-up. */
    String descricao();
}
