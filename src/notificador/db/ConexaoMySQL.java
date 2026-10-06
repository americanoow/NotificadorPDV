package notificador.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import notificador.config.AppConfig;

/**
 * Fábrica de ligações ao MySQL do PDV TagComercio.
 *
 * Notas sobre o servidor do TagComercio:
 *  - MySQL 5.5, por isso usamos o Connector/J 5.1.x (o 8.x não suporta 5.5 oficialmente);
 *  - tabelas MyISAM em latin1 -> characterEncoding=Cp1252 para os acentos virem certos;
 *  - podem existir datas "0000-00-00" -> zeroDateTimeBehavior=convertToNull.
 *
 * As verificações rodam de hora a hora, por isso abre-se uma ligação por ciclo
 * em vez de manter um pool. A ligação é marcada só-leitura: este programa
 * nunca altera dados do PDV.
 */
public final class ConexaoMySQL {

    private static final Logger LOG = Logger.getLogger(ConexaoMySQL.class.getName());
    private static final String DRIVER = "com.mysql.jdbc.Driver";

    private final String url;
    private final String usuario;
    private final String senha;

    public ConexaoMySQL(AppConfig cfg) {
        this.url = "jdbc:mysql://" + cfg.dbHost() + ":" + cfg.dbPorta() + "/" + cfg.dbBase()
                + "?useSSL=false"
                + "&characterEncoding=Cp1252"
                + "&zeroDateTimeBehavior=convertToNull"
                + "&connectTimeout=5000"
                + "&socketTimeout=60000";
        this.usuario = cfg.dbUsuario();
        this.senha = cfg.dbSenha();
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Driver MySQL não encontrado. Confirme lib/mysql-connector-java-*.jar", e);
        }
    }

    /** Abre uma ligação nova. Quem chama deve fechá-la (try-with-resources). */
    public Connection abrir() throws SQLException {
        Connection con = DriverManager.getConnection(url, usuario, senha);
        con.setReadOnly(true);
        return con;
    }

    /** Teste rápido usado no arranque. */
    public boolean testar() {
        try (Connection con = abrir()) {
            return con.isValid(5);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Não foi possível ligar ao MySQL em " + url + ": " + e.getMessage());
            return false;
        }
    }

    public String getUrl() {
        return url;
    }
}
