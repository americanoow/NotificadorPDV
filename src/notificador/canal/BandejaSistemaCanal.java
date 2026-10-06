package notificador.canal;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.logging.Logger;

import notificador.modelo.Notificacao;
import notificador.modelo.TipoNotificacao;

/**
 * Notificação nativa do sistema operativo.
 *
 *  - Windows (e Linux com área de notificação): ícone na bandeja + balão (SystemTray).
 *    Clicar no balão ou fazer duplo clique no ícone abre a Central de Notificações.
 *  - Sem suporte a SystemTray (alguns ambientes Linux): abre directamente a
 *    Central de Notificações (janela Swing, não modal).
 *  - Sem ecrã (headless): só regista no log.
 */
public final class BandejaSistemaCanal implements CanalNotificacao {

    private static final Logger LOG = Logger.getLogger(BandejaSistemaCanal.class.getName());
    private static final String NOME_APP = "Notificador PDV";

    private final int maxLinhasBalao;
    private final Consumer<TipoNotificacao> abrirCentral;
    private final Runnable acaoVerificarAgora;
    private final Runnable acaoSair;

    private TrayIcon trayIcon;
    /** Tipo da última notificação mostrada no balão; usado como filtro ao clicar. */
    private volatile TipoNotificacao ultimoTipo;

    /**
     * @param abrirCentral       abre a Central de Notificações filtrada por um tipo (null = todos)
     * @param acaoVerificarAgora executada pelo item de menu "Verificar agora"
     * @param acaoSair           executada pelo item de menu "Sair"
     */
    public BandejaSistemaCanal(int maxLinhasBalao, Consumer<TipoNotificacao> abrirCentral,
                               Runnable acaoVerificarAgora, Runnable acaoSair) {
        this.maxLinhasBalao = maxLinhasBalao;
        this.abrirCentral = abrirCentral;
        this.acaoVerificarAgora = acaoVerificarAgora;
        this.acaoSair = acaoSair;
    }

    /** Instala o ícone na bandeja. Deve ser chamado uma vez no arranque. */
    public void instalar() {
        if (GraphicsEnvironment.isHeadless()) {
            LOG.warning("Ambiente sem ecrã: as notificações serão apenas registadas no log.");
            return;
        }
        if (!SystemTray.isSupported()) {
            LOG.warning("SystemTray não suportado neste sistema: será aberta a Central de Notificações.");
            return;
        }

        PopupMenu menu = new PopupMenu();
        MenuItem central = new MenuItem("Central de notificações");
        central.setFont(new Font(Font.DIALOG, Font.BOLD, 12)); // acção principal
        central.addActionListener(e -> abrirCentral.accept(null));
        MenuItem verificar = new MenuItem("Verificar agora");
        verificar.addActionListener(e -> acaoVerificarAgora.run());
        MenuItem sair = new MenuItem("Sair");
        sair.addActionListener(e -> acaoSair.run());
        menu.add(central);
        menu.add(verificar);
        menu.addSeparator();
        menu.add(sair);

        trayIcon = new TrayIcon(criarIcone(), NOME_APP, menu);
        trayIcon.setImageAutoSize(true);
        // Disparado ao clicar no balão (Windows) ou duplo clique no ícone.
        trayIcon.addActionListener(e -> abrirCentral.accept(ultimoTipo));

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            LOG.warning("Falha ao adicionar ícone na bandeja: " + e.getMessage());
            trayIcon = null;
        }
    }

    /** Remove o ícone da bandeja (chamado ao sair). */
    public void remover() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    @Override
    public String nome() {
        return "Bandeja do sistema";
    }

    @Override
    public void enviar(Notificacao n) {
        ultimoTipo = n.getTipo();

        if (trayIcon != null) {
            TrayIcon.MessageType tipo = n.getTipo() == TipoNotificacao.COBRANCA
                    ? TrayIcon.MessageType.WARNING
                    : TrayIcon.MessageType.INFO;
            trayIcon.displayMessage(n.getTitulo(), n.resumo(maxLinhasBalao), tipo);
        } else if (!GraphicsEnvironment.isHeadless()) {
            abrirCentral.accept(n.getTipo());
        }
    }

    /** Ícone desenhado em código (círculo laranja com "!") para dispensar ficheiros de imagem. */
    private static Image criarIcone() {
        int t = 64;
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(230, 126, 34));
        g.fillOval(2, 2, t - 4, t - 4);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(9, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(t / 2, 14, t / 2, 36);
        g.fillOval(t / 2 - 5, 44, 10, 10);
        g.dispose();
        return img;
    }
}
