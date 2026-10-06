package notificador.verificacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import notificador.modelo.ProdutoEstoqueBaixo;
import notificador.modelo.TipoNotificacao;

/**
 * Produtos de cad_produtos com estoque <= min_estoque.
 * Produtos marcados como inactivos no TagComercio (inativo = 1) NUNCA entram.
 */
public final class EstoqueBaixoVerificador implements Verificador {

    private static final String SQL =
            "SELECT p.id, p.nome_produto, p.cod_barra, p.unidade, p.estoque, p.min_estoque"
          + "  FROM cad_produtos p"
          + " WHERE p.estoque <= p.min_estoque"
          + "   AND p.inativo = 0";

    private static final String FILTRO_MINIMO_ZERO = "   AND p.min_estoque > 0";

    /** Só produtos com pelo menos uma venda nos últimos ? dias (usa o índice id_produto). */
    private static final String FILTRO_SEM_VENDA =
            "   AND EXISTS (SELECT 1 FROM mv_vendas_movimento m"
          + "                WHERE m.id_produto = p.id"
          + "                  AND m.data_venda >= DATE_SUB(CURDATE(), INTERVAL ? DAY))";

    private static final String ORDEM = " ORDER BY (p.estoque - p.min_estoque), p.nome_produto";

    private final boolean ignorarMinimoZero;
    private final int ignorarSemVendaDias;

    /**
     * @param ignorarMinimoZero   se true, descarta produtos com min_estoque = 0
     *                            (sem mínimo definido), que de outra forma
     *                            apareceriam sempre que o estoque chegasse a 0.
     * @param ignorarSemVendaDias se > 0, descarta produtos sem nenhuma venda
     *                            nesse número de dias (produtos "parados",
     *                            mesmo que não estejam marcados como inactivos).
     */
    public EstoqueBaixoVerificador(boolean ignorarMinimoZero, int ignorarSemVendaDias) {
        this.ignorarMinimoZero = ignorarMinimoZero;
        this.ignorarSemVendaDias = ignorarSemVendaDias;
    }

    @Override
    public TipoNotificacao tipo() {
        return TipoNotificacao.ESTOQUE_BAIXO;
    }

    @Override
    public List<ProdutoEstoqueBaixo> buscar(Connection con) throws SQLException {
        String sql = SQL
                + (ignorarMinimoZero ? FILTRO_MINIMO_ZERO : "")
                + (ignorarSemVendaDias > 0 ? FILTRO_SEM_VENDA : "")
                + ORDEM;
        List<ProdutoEstoqueBaixo> itens = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (ignorarSemVendaDias > 0) {
                ps.setInt(1, ignorarSemVendaDias);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itens.add(new ProdutoEstoqueBaixo(
                            rs.getInt("id"),
                            rs.getString("nome_produto"),
                            rs.getString("cod_barra"),
                            rs.getString("unidade"),
                            rs.getDouble("estoque"),
                            rs.getDouble("min_estoque")));
                }
            }
        }
        return itens;
    }

    @Override
    public String titulo(int quantidade) {
        return quantidade == 1
                ? "1 produto com estoque baixo"
                : quantidade + " produtos com estoque baixo";
    }
}
