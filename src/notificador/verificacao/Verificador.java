package notificador.verificacao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import notificador.modelo.ItemAlerta;
import notificador.modelo.TipoNotificacao;

/**
 * Uma regra de negócio que consulta o PDV e devolve os itens a alertar.
 * Para criar um alerta novo: implemente esta interface e registe-a em App.
 */
public interface Verificador {

    TipoNotificacao tipo();

    /** Executa a consulta SQL. A ligação é aberta e fechada por quem chama. */
    List<? extends ItemAlerta> buscar(Connection con) throws SQLException;

    /** Título do pop-up, ex.: "12 produtos com estoque baixo". */
    String titulo(int quantidade);

    /** Título a partir dos itens novos; sobrescreva quando o conteúdo deve mudar o título. */
    default String titulo(List<? extends ItemAlerta> novos) {
        return titulo(novos.size());
    }
}
