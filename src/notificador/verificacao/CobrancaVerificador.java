package notificador.verificacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import notificador.modelo.ClienteDevedor;
import notificador.modelo.FaixasAtraso;
import notificador.modelo.TipoNotificacao;

/**
 * Clientes com parcelas vencidas e não confirmadas no contas a receber do
 * TagComercio (cad_lancamentos.status_lancamento = 0), agrupados por cliente
 * e classificados pela parcela vencida mais antiga.
 *
 * Cartões (débito/crédito) também ficam com status 0 até serem conciliados,
 * mas é dinheiro a receber da operadora, não do cliente: por isso os modos de
 * lançamento configurados em cobranca.modos_ignorados ficam de fora.
 */
public final class CobrancaVerificador implements Verificador {

    private final FaixasAtraso faixas;
    private final List<Integer> modosIgnorados;
    private final List<Integer> clientesIgnorados;

    public CobrancaVerificador(FaixasAtraso faixas, List<Integer> modosIgnorados, List<Integer> clientesIgnorados) {
        this.faixas = faixas;
        this.modosIgnorados = new ArrayList<>(modosIgnorados);
        this.clientesIgnorados = new ArrayList<>(clientesIgnorados);
    }

    @Override
    public TipoNotificacao tipo() {
        return TipoNotificacao.COBRANCA;
    }

    @Override
    public List<ClienteDevedor> buscar(Connection con) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.nome_cliente, c.telefone, c.celular, COUNT(*) AS parcelas,"
              + "       SUM(IFNULL(l.vr_parcela, 0) - IFNULL(l.vr_abatimentos, 0)) AS valor,"
              + "       MIN(l.data_vencimento) AS vencimento,"
              + "       DATEDIFF(CURDATE(), MIN(l.data_vencimento)) AS dias"
              + "  FROM cad_lancamentos l"
              + " INNER JOIN cad_clientes c ON c.id = l.id_cliente"
              + " WHERE l.status_lancamento = 0"
              + "   AND l.id_cliente > 0"
              + "   AND l.data_vencimento < CURDATE()");
        if (!modosIgnorados.isEmpty()) {
            sql.append(" AND IFNULL(l.id_modo_lancamento, 0) NOT IN (").append(marcadores(modosIgnorados.size())).append(')');
        }
        if (!clientesIgnorados.isEmpty()) {
            sql.append(" AND l.id_cliente NOT IN (").append(marcadores(clientesIgnorados.size())).append(')');
        }
        sql.append(" GROUP BY c.id, c.nome_cliente, c.telefone, c.celular"
                 + " HAVING dias >= ? AND valor > 0.009"
                 + " ORDER BY dias DESC, valor DESC");

        List<ClienteDevedor> itens = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int p = 1;
            for (Integer m : modosIgnorados) {
                ps.setInt(p++, m);
            }
            for (Integer c : clientesIgnorados) {
                ps.setInt(p++, c);
            }
            ps.setInt(p, faixas.primeira());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int dias = rs.getInt("dias");
                    int faixa = faixas.classificar(dias);
                    itens.add(new ClienteDevedor(
                            rs.getInt("id"),
                            rs.getString("nome_cliente"),
                            rs.getString("telefone"),
                            rs.getString("celular"),
                            rs.getInt("parcelas"),
                            rs.getDouble("valor"),
                            rs.getDate("vencimento"),
                            dias,
                            faixa,
                            faixas.rotulo(faixa)));
                }
            }
        }
        return itens;
    }

    @Override
    public String titulo(int quantidade) {
        return quantidade == 1
                ? "1 cliente para cobrar"
                : quantidade + " clientes para cobrar";
    }

    private static String marcadores(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.toString();
    }
}
