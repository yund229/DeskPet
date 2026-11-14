import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * 桌宠 —— 一只会在屏幕底部散步的小橘猫
 *   左键点它  : 开心 + 冒爱心
 *   按住拖动  : 搬家
 *   右键      : 菜单(喂小鱼干 / 去散步 / 退出)
 *   45秒不理它: 自己睡着(Zzz)
 *
 * 运行: javac -encoding UTF-8 DeskPet.java && java DeskPet
 */
public class DeskPet extends JPanel {

    enum State { IDLE, WALK, HAPPY, SLEEP }

    static final int WIN_W = 240, WIN_H = 270;   // 窗口尺寸(含特效留白)
    static final int CX = WIN_W / 2;             // 宠物中心 x
    static final int GROUND_Y = 228;             // 脚底所在 y

    // 配色(橘猫)
    static final Color BODY       = new Color(0xF2, 0xA6, 0x5A);
    static final Color BODY_LIGHT = new Color(0xFF, 0xD9, 0x9E);
    static final Color BODY_DARK  = new Color(0xDE, 0x8A, 0x3C);
    static final Color BELLY      = new Color(0xFF, 0xF4, 0xDE);
    static final Color PINK       = new Color(0xF2, 0x7B, 0x88);
    static final Color LINE       = new Color(0x7A, 0x4A, 0x26);

    State state = State.IDLE;
    int tick, stateTimer, stateDuration = 200;
    boolean faceRight = true;
    boolean blink;  int blinkLeft;  int nextBlink = 90;
    long lastInteract;
    boolean dragging;  Point dragOffset;
    int pupilDx, pupilDy;

    Rectangle screen;                            // 可活动区域(不含任务栏)
    final List<Particle> particles = new ArrayList<>();
    final Random rand = new Random();
    JFrame frame;
    JPopupMenu menu;

    public DeskPet() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buildMenu();

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastInteract = System.currentTimeMillis();
                if (e.isPopupTrigger()) { menu.show(DeskPet.this, e.getX(), e.getY()); return; }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragging = true;
                    Point p = frame.getLocation();
                    dragOffset = new Point(e.getXOnScreen() - p.x, e.getYOnScreen() - p.y);
                    if (state == State.SLEEP) setState(State.IDLE, 240);
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                lastInteract = System.currentTimeMillis();
                if (e.isPopupTrigger()) menu.show(DeskPet.this, e.getX(), e.getY());
                dragging = false;
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) petMe();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                lastInteract = System.currentTimeMillis();
                frame.setLocation(e.getXOnScreen() - dragOffset.x, e.getYOnScreen() - dragOffset.y);
                clampToScreen();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        new javax.swing.Timer(33, e -> step()).start();   // ~30fps
    }

    void buildMenu() {
        menu = new JPopupMenu();
        JMenuItem feed = new JMenuItem("喂小鱼干");
        feed.addActionListener(e -> petMe());
        JMenuItem walk = new JMenuItem("去散步");
        walk.addActionListener(e -> {
            lastInteract = System.currentTimeMillis();
            setState(State.WALK, 240 + rand.nextInt(120));
        });
        JMenuItem exit = new JMenuItem("退出");
        exit.addActionListener(e -> System.exit(0));
        menu.add(feed); menu.add(walk); menu.addSeparator(); menu.add(exit);
    }

    void setState(State s, int duration) {
        state = s; stateTimer = 0; stateDuration = duration;
        if (s == State.WALK) faceRight = rand.nextBoolean();
    }

    void petMe() {
        lastInteract = System.currentTimeMillis();
        setState(State.HAPPY, 55);
        for (int i = 0; i < 6; i++) spawnHeart(CX + randInt(-36, 36), 96 + randInt(-12, 14));
    }

    void spawnHeart(double x, double y) { particles.add(new Particle(x, y, true, rand)); }

    int randInt(int a, int b) { return a + rand.nextInt(b - a + 1); }

    void clampToScreen() {
        Point p = frame.getLocation();
        int x = Math.max(screen.x, Math.min(p.x, screen.x + screen.width  - WIN_W));
        int y = Math.max(screen.y, Math.min(p.y, screen.y + screen.height - WIN_H));
        frame.setLocation(x, y);
    }

    // ---------------- 逻辑 ----------------

    void step() {
        tick++; stateTimer++;
        long now = System.currentTimeMillis();

        if (state != State.SLEEP && !dragging && now - lastInteract > 45_000)
            setState(State.SLEEP, Integer.MAX_VALUE);

        switch (state) {
            case IDLE:
                if (stateTimer >= stateDuration) setState(State.WALK, 120 + rand.nextInt(240));
                break;
            case WALK: {
                if (!dragging) {
                    int dir = faceRight ? 1 : -1;
                    Point p = frame.getLocation();
                    int nx = p.x + dir * 2;
                    if (nx < screen.x || nx + WIN_W > screen.x + screen.width)
                        setState(State.IDLE, 120 + rand.nextInt(300));   // 碰壁休息
                    else
                        frame.setLocation(nx, p.y);
                }
                if (state == State.WALK && stateTimer >= stateDuration)
                    setState(State.IDLE, 120 + rand.nextInt(300));
                break;
            }
            case HAPPY:
                if (stateTimer % 8 == 0) spawnHeart(CX + randInt(-38, 38), 92 + randInt(-10, 12));
                if (stateTimer >= stateDuration) setState(State.IDLE, 120 + rand.nextInt(300));
                break;
            case SLEEP:
                if (stateTimer % 55 == 0) particles.add(new Particle(CX + 22, 88, false, rand));
                break;
        }

        // 眨眼
        if (state != State.SLEEP) {
            if (blink) { if (--blinkLeft <= 0) blink = false; }
            else if (--nextBlink <= 0) { blink = true; blinkLeft = 4; nextBlink = 90 + rand.nextInt(170); }
        }

        // 粒子
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy;
            if (--p.life <= 0) it.remove();
        }
        repaint();
    }

    void computePupil() {
        pupilDx = pupilDy = 0;
        if (state == State.SLEEP || state == State.HAPPY) return;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return;
        Point m = pi.getLocation();
        Point f = frame.getLocation();
        int dx = m.x - (f.x + CX);
        int dy = m.y - (f.y + GROUND_Y - 105);
        pupilDx = Math.max(-4, Math.min(4, dx / 40));
        pupilDy = Math.max(-3, Math.min(3, dy / 40));
        if (!faceRight) pupilDx = -pupilDx;      // 镜像空间里反转
    }

    // ---------------- 绘制 ----------------

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        computePupil();

        int hop = (state == State.WALK && !dragging) ? (int) (Math.abs(Math.sin(tick * 0.35)) * 9) : 0;
        double squash = (state == State.WALK) ? 1 - 0.05 * Math.sin(tick * 0.7)
                                              : 1 + 0.015 * Math.sin(tick * 0.06);   // 呼吸

        // 影子
        g.setColor(new Color(0, 0, 0, 40));
        int sw = (int) (58 * Math.max(0.6, 1 - hop / 30.0));
        g.fillOval(CX - sw, GROUND_Y - 6, sw * 2, 12);

        Graphics2D pg = (Graphics2D) g.create();
        pg.translate(CX, GROUND_Y);
        pg.scale(1, squash);
        pg.translate(-CX, -GROUND_Y);
        pg.translate(0, -hop);
        if (!faceRight) { pg.translate(2 * CX, 0); pg.scale(-1, 1); }
        drawCat(pg);
        pg.dispose();

        for (Particle p : particles) drawParticle(g, p);
        g.dispose();
    }

    void drawCat(Graphics2D g) {
        int bx = CX, by = 155, brx = 70, bry = 62;   // 身体

        // 尾巴
        double wag = Math.sin(tick * (state == State.WALK ? 0.28 : 0.09)) * 12;
        Path2D tail = new Path2D.Double();
        tail.moveTo(bx + 55, 195);
        tail.quadTo(bx + 108, 190, bx + 98 + wag, 138 - wag * 0.5);
        g.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(BODY_DARK);
        g.draw(tail);

        // 耳朵
        g.setColor(BODY);
        Path2D earL = new Path2D.Double();
        earL.moveTo(CX - 54, 112); earL.lineTo(CX - 32, 70); earL.lineTo(CX - 6, 102); earL.closePath();
        Path2D earR = new Path2D.Double();
        earR.moveTo(CX + 54, 112); earR.lineTo(CX + 32, 70); earR.lineTo(CX + 6, 102); earR.closePath();
        g.fill(earL); g.fill(earR);
        g.setColor(PINK);
        Path2D inL = new Path2D.Double();
        inL.moveTo(CX - 44, 104); inL.lineTo(CX - 32, 82); inL.lineTo(CX - 18, 99); inL.closePath();
        Path2D inR = new Path2D.Double();
        inR.moveTo(CX + 44, 104); inR.lineTo(CX + 32, 82); inR.lineTo(CX + 18, 99); inR.closePath();
        g.fill(inL); g.fill(inR);

        // 身体(渐变)
        g.setPaint(new GradientPaint(CX, 88, BODY_LIGHT, CX, 214, BODY));
        g.fillOval(bx - brx, by - bry, brx * 2, bry * 2);

        // 额头花纹
        g.setColor(new Color(0xDE, 0x8A, 0x3C, 150));
        g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(CX - 13, 98, CX - 13, 110);
        g.drawLine(CX,      95, CX,      109);
        g.drawLine(CX + 13, 98, CX + 13, 110);

        // 肚皮
        g.setColor(BELLY);
        g.fillOval(CX - 42, 158, 84, 62);

        // 前爪
        int lift = (state == State.WALK) ? (int) (Math.sin(tick * 0.7) * 2.5) : 0;
        g.setColor(BODY_LIGHT);
        g.fillOval(CX - 46, 210 - Math.max(0, lift),  34, 18);
        g.fillOval(CX + 12, 210 - Math.max(0, -lift), 34, 18);

        drawFace(g);
    }

    void drawFace(Graphics2D g) {
        int ey = 120;

        // 眼睛
        for (int ex : new int[]{CX - 27, CX + 27}) {
            if (state == State.HAPPY) {
                g.setColor(LINE);
                g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(ex - 12, ey - 6, 24, 16, 0, 180);          // ^ 开心眯眼
            } else if (state == State.SLEEP || blink) {
                g.setColor(LINE);
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(ex - 12, ey - 2, 24, 13, 180, 180);        // ‿ 睡觉/眨眼
            } else {
                g.setColor(Color.WHITE);
                g.fillOval(ex - 11, ey - 13, 22, 26);
                g.setColor(new Color(0x33, 0x2B, 0x24));
                g.fillOval(ex - 6 + pupilDx, ey - 9 + pupilDy, 12, 16);
                g.setColor(Color.WHITE);
                g.fillOval(ex - 1 + pupilDx, ey - 7 + pupilDy, 5, 6);
            }
        }

        // 鼻子
        g.setColor(PINK);
        Path2D nose = new Path2D.Double();
        nose.moveTo(CX - 5, 134); nose.lineTo(CX + 5, 134); nose.lineTo(CX, 140); nose.closePath();
        g.fill(nose);

        // 嘴 ω
        g.setColor(LINE);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(CX - 11, 139, 11, 9, 180, 180);
        g.drawArc(CX,      139, 11, 9, 180, 180);

        // 胡须
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(0xDE, 0x8A, 0x3C, 200));
        g.drawLine(CX - 38, 136, CX - 64, 130);
        g.drawLine(CX - 38, 142, CX - 64, 144);
        g.drawLine(CX + 38, 136, CX + 64, 130);
        g.drawLine(CX + 38, 142, CX + 64, 144);

        // 腮红
        g.setColor(new Color(0xF2, 0x7B, 0x88, 70));
        g.fillOval(CX - 52, 136, 18, 10);
        g.fillOval(CX + 34, 136, 18, 10);
    }

    void drawParticle(Graphics2D g, Particle p) {
        float alpha = (float) Math.max(0, Math.min(1, p.life / p.maxLife));
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        if (p.heart) {
            drawHeart(g, p.x, p.y, p.size, new Color(0xFF, 0x6B, 0x81));
        } else {
            g.setColor(new Color(0x8C, 0xA6, 0xC8));
            g.setFont(new Font("Segoe UI", Font.BOLD, (int) p.size));
            g.drawString("z", (float) p.x, (float) p.y);
        }
        g.setComposite(old);
    }

    void drawHeart(Graphics2D g, double x, double y, double s, Color c) {
        g.setColor(c);
        double r = s * 0.27;
        g.fillOval((int) (x - s * 0.22 - r), (int) (y - s * 0.20 - r), (int) (r * 2), (int) (r * 2));
        g.fillOval((int) (x + s * 0.22 - r), (int) (y - s * 0.20 - r), (int) (r * 2), (int) (r * 2));
        Path2D tri = new Path2D.Double();
        tri.moveTo(x - s * 0.45, y - s * 0.06);
        tri.lineTo(x + s * 0.45, y - s * 0.06);
        tri.lineTo(x, y + s * 0.52);
        tri.closePath();
        g.fill(tri);
    }

    // 粒子(爱心 / Zzz)
    static class Particle {
        double x, y, vx, vy, size, life, maxLife;
        final boolean heart;
        Particle(double x, double y, boolean heart, Random r) {
            this.x = x; this.y = y; this.heart = heart;
            if (heart) {
                size = 11 + r.nextInt(8);
                vx = (r.nextDouble() - .5) * 1.4;
                vy = -1.1 - r.nextDouble() * 0.8;
                maxLife = 55 + r.nextInt(30);
            } else {
                size = 15 + r.nextInt(9);
                vx = 0.35 + r.nextDouble() * 0.4;
                vy = -0.7 - r.nextDouble() * 0.4;
                maxLife = 85;
            }
            life = maxLife;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame();
            f.setUndecorated(true);
            f.setBackground(new Color(0, 0, 0, 0));   // 透明窗口
            f.setType(Frame.Type.UTILITY);            // 不占用任务栏
            f.setAlwaysOnTop(true);
            DeskPet pet = new DeskPet();
            pet.frame = f;
            pet.screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            f.setContentPane(pet);
            f.setSize(WIN_W, WIN_H);
            f.setLocation(pet.screen.x + pet.screen.width - WIN_W - 24,
                          pet.screen.y + pet.screen.height - WIN_H);
            pet.lastInteract = System.currentTimeMillis();
            f.setVisible(true);
        });
    }
}
