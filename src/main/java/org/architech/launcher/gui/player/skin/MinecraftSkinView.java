package org.architech.launcher.gui.player.skin;

import javafx.beans.property.*;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.scene.*;
import javafx.scene.image.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;

public final class MinecraftSkinView extends StackPane {
    private final Group world = new Group();
    private final Group model = new Group();
    private final PerspectiveCamera cam = new PerspectiveCamera(true);
    private SubScene subScene;

    private final DoubleProperty angleX = new SimpleDoubleProperty(-15);
    private final DoubleProperty angleY = new SimpleDoubleProperty(30);
    private final DoubleProperty zoom = new SimpleDoubleProperty(80);

    // API
    private final StringProperty skinUrl = new SimpleStringProperty();
    public MinecraftSkinView() {
        setStyle("-fx-background-color: transparent;");
        buildSubScene();
        world.getChildren().add(model);
        bindCamera();
        enableMouseControls();
        skinUrl.addListener((obs, o, n) -> { if (n != null && !n.isBlank()) loadSkin(n); });
    }
    public MinecraftSkinView(String url) { this(); setSkinUrl(url); }
    public String getSkinUrl() { return skinUrl.get(); }
    public void setSkinUrl(String url) { skinUrl.set(url); }
    public StringProperty skinUrlProperty() { return skinUrl; }

    // ---------- scene ----------
    private void buildSubScene() {
        cam.setNearClip(0.1);
        cam.setFarClip(10000);
        cam.setVerticalFieldOfView(false);
        subScene = new SubScene(world, 100, 100, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(cam);
        subScene.setFill(Color.TRANSPARENT);
        getChildren().add(subScene);
        widthProperty().addListener((__,___,w)-> subScene.setWidth(w.doubleValue()));
        heightProperty().addListener((__,___,h)-> subScene.setHeight(h.doubleValue()));
    }
    private void bindCamera() {
        cam.translateZProperty().bind(zoom.multiply(-1));
        Rotate rx = new Rotate(0, Rotate.X_AXIS);
        Rotate ry = new Rotate(0, Rotate.Y_AXIS);
        rx.angleProperty().bind(angleX);
        ry.angleProperty().bind(angleY);
        world.getTransforms().setAll(ry, rx);
    }
    private void enableMouseControls() {
        final ObjectProperty<Point2D> anchor = new SimpleObjectProperty<>();
        final DoubleProperty startX = new SimpleDoubleProperty();
        final DoubleProperty startY = new SimpleDoubleProperty();
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            anchor.set(new Point2D(e.getSceneX(), e.getSceneY()));
            startX.set(angleX.get()); startY.set(angleY.get());
        });
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            Point2D a = anchor.get();
            angleY.set(startY.get() + (e.getSceneX()-a.getX()) * 0.5);
            angleX.set(startX.get() - (e.getSceneY()-a.getY()) * 0.5);
        });
        setOnScroll(e -> zoom.set(Math.max(30, Math.min(300, zoom.get() + e.getDeltaY() * -0.1))));
    }

    // ---------- load + normalize ----------
    private void loadSkin(String url) {
        Task<Image> task = new Task<>() {
            @Override protected Image call() {
                // backgroundLoading = false
                Image raw = new Image(url, 0, 0, true, false, false);
                return normalizeTo64x64(raw);
            }
        };
        task.setOnSucceeded(ev -> buildModel(task.getValue()));   // уже на FX-потоке
        task.setOnFailed(ev -> task.getException().printStackTrace());
        new Thread(task, "skin-loader").start();
    }


    // Если 64×32 — дорисовываем недостающие квадранты и пустые оверлеи
    private Image normalizeTo64x64(Image src) {
        int w = (int) src.getWidth(), h = (int) src.getHeight();

        // Подгоняем к 64×64 без сглаживания
        WritableImage out = new WritableImage(64, 64);
        PixelWriter pw = out.getPixelWriter();
        PixelReader pr = src.getPixelReader();

        // Копируем что есть
        for (int y=0;y<Math.min(h,64);y++)
            for (int x=0;x<Math.min(w,64);x++)
                pw.setArgb(x,y, pr.getArgb(x,y));

        // Если 64×32 — заполняем недостающие зоны как раньше
        if (h == 32) {
            // LLEG base <= RLEG base
            mirrorCopy(pr,pw, 0,16,16,16, 16,48, true);
            // LARM base <= RARM base
            mirrorCopy(pr,pw, 40,16,16,16, 32,48, true);
            // overlays остаются пустыми
            return out;
        }

        // ---- 64×64 fallback как в ванили ----
        // LLEG base (16..32,48..64) <= RLEG base (0..16,16..32)
        if (isRectTransparent(pr, 16,48,16,16))
            mirrorCopy(pr,pw, 0,16,16,16, 16,48, true);

        // LARM base (32..48,48..64) <= RARM base (40..56,16..32)
        if (isRectTransparent(pr, 32,48,16,16))
            mirrorCopy(pr,pw, 40,16,16,16, 32,48, true);

        // LLEG overlay (0..16,48..64) <= RLEG overlay (0..16,32..48)
        if (isRectTransparent(pr, 0,48,16,16))
            mirrorCopy(pr,pw, 0,32,16,16, 0,48, true);

        // LARM overlay (48..64,48..64) <= RARM overlay (40..56,32..48)
        if (isRectTransparent(pr, 48,48,16,16))
            mirrorCopy(pr,pw, 40,32,16,16, 48,48, true);

        return out;
    }

    private boolean isRectTransparent(PixelReader pr, int x,int y,int w,int h){
        for (int yy=0; yy<h; yy++)
            for (int xx=0; xx<w; xx++)
                if (((pr.getArgb(x+xx,y+yy)>>>24) & 0xFF) != 0) return false;
        return true;
    }

    private void mirrorCopy(PixelReader pr, PixelWriter pw,
                            int sx,int sy,int sw,int sh,
                            int dx,int dy, boolean mirrorX) {
        for (int y=0;y<sh;y++)
            for (int x=0;x<sw;x++) {
                int argb = pr.getArgb(sx + (mirrorX ? (sw-1-x) : x), sy+y);
                pw.setArgb(dx+x, dy+y, argb);
            }
    }
    // ---------- model ----------
    private void buildModel(Image skin) {
        model.getChildren().clear();
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(upscaleNearest(normalizeTo64x64(skin), 8));
        mat.setSpecularColor(Color.BLACK);

        // размеры в "пикселях"
        final float HEAD = 8, BODY_H=12, BODY_W=8, BODY_D=4;
        final float ARM_W=4, ARM_H=12, ARM_D=4;
        final float LEG_W=4, LEG_H=12, LEG_D=4;

        // head base
        MeshView head = box(8,8,8, uvHead(false, skin));
        head.setMaterial(mat);
        head.setTranslateY(-(BODY_H/2 + HEAD/2));

        // head overlay (+1px по всем осям)
        MeshView head2 = box(10,10,10, uvHead(true, skin));
        head2.setMaterial(mat);
        head2.setTranslateY(head.getTranslateY());

        // body base
        MeshView body = box(BODY_W,BODY_H,BODY_D, uvBody(false, skin));
        body.setMaterial(mat);

        // body overlay
        MeshView body2 = box(BODY_W+2,BODY_H+2,BODY_D+2, uvBody(true, skin));
        body2.setMaterial(mat);

        // right arm base
        MeshView rArm = box(ARM_W,ARM_H,ARM_D, uvRightArm(false, skin));
        rArm.setMaterial(mat);
        rArm.setTranslateX( (BODY_W/2) + (ARM_W/2) );
        rArm.setTranslateY(-(BODY_H/2 - ARM_H/2));

        // right arm overlay
        MeshView rArm2 = box(ARM_W+2,ARM_H+2,ARM_D+2, uvRightArm(true, skin));
        rArm2.setMaterial(mat);
        rArm2.setTranslateX(rArm.getTranslateX());
        rArm2.setTranslateY(rArm.getTranslateY());

        // left arm base
        MeshView lArm = box(ARM_W,ARM_H,ARM_D, uvLeftArm(false, skin));
        lArm.setMaterial(mat);
        lArm.setTranslateX(-(BODY_W/2) - (ARM_W/2));
        lArm.setTranslateY(rArm.getTranslateY());

        // left arm overlay
        MeshView lArm2 = box(ARM_W+2,ARM_H+2,ARM_D+2, uvLeftArm(true, skin));
        lArm2.setMaterial(mat);
        lArm2.setTranslateX(lArm.getTranslateX());
        lArm2.setTranslateY(lArm.getTranslateY());

        // right leg base
        MeshView rLeg = box(LEG_W,LEG_H,LEG_D, uvRightLeg(false, skin));
        rLeg.setMaterial(mat);
        rLeg.setTranslateX(LEG_W/2); // центр ног: ±2
        rLeg.setTranslateY(BODY_H/2 + LEG_H/2);

        // right leg overlay
        MeshView rLeg2 = box(LEG_W+2,LEG_H+2,LEG_D+2, uvRightLeg(true, skin));
        rLeg2.setMaterial(mat);
        rLeg2.setTranslateX(rLeg.getTranslateX());
        rLeg2.setTranslateY(rLeg.getTranslateY());

        // left leg base
        MeshView lLeg = box(LEG_W,LEG_H,LEG_D, uvLeftLeg(false, skin));
        lLeg.setMaterial(mat);
        lLeg.setTranslateX(-LEG_W/2);
        lLeg.setTranslateY(rLeg.getTranslateY());

        // left leg overlay
        MeshView lLeg2 = box(LEG_W+2,LEG_H+2,LEG_D+2, uvLeftLeg(true, skin));
        lLeg2.setMaterial(mat);
        lLeg2.setTranslateX(lLeg.getTranslateX());
        lLeg2.setTranslateY(lLeg.getTranslateY());

        // свет без бликов
        AmbientLight amb = new AmbientLight(Color.WHITE);
        model.getChildren().addAll(head,body,rArm,lArm,rLeg,lLeg, head2,body2,rArm2,lArm2,rLeg2,lLeg2, amb);
    }

    // ---------- UV maps (RIGHT, LEFT, TOP, BOTTOM, FRONT, BACK) ----------
    private Rects uvHead(boolean overlay, Image img) {
        int ox = overlay ? 32 : 0;
        return new Rects(img,
                r(0+ox,8,8,8),   // RIGHT
                r(16+ox,8,8,8),  // LEFT
                r(8+ox,0,8,8),   // TOP
                r(16+ox,0,8,8),  // BOTTOM
                r(8+ox,8,8,8),   // FRONT
                r(24+ox,8,8,8)   // BACK
        );
    }
    private Rects uvBody(boolean overlay, Image img) {
        int oy = overlay ? 32 : 16;
        return new Rects(img,
                r(16,4+oy,4,12),   // RIGHT
                r(28,4+oy,4,12),   // LEFT
                r(20,0+oy,8,4),    // TOP
                r(28,0+oy,8,4),    // BOTTOM
                r(20,4+oy,8,12),   // FRONT
                r(32,4+oy,8,12)    // BACK
        );
    }
    private Rects uvRightArm(boolean overlay, Image img) {
        int oy = overlay ? 32 : 16;
        return new Rects(img,
                r(40,4+oy,4,12),   // RIGHT (outside)
                r(48,4+oy,4,12),   // LEFT  (inside)
                r(44,0+oy,4,4),    // TOP
                r(48,0+oy,4,4),    // BOTTOM
                r(44,4+oy,4,12),   // FRONT
                r(52,4+oy,4,12)    // BACK
        );
    }
    private Rects uvLeftArm(boolean overlay, Image img) {
        int ox = overlay ? 48 : 32;
        int oy = 48;
        return new Rects(img,
                r(40,4+oy,4,12).shiftX(ox-40),   // RIGHT (inside)
                r(32,4+oy,4,12).shiftX(ox-32),   // LEFT  (outside)
                r(36,0+oy,4,4).shiftX(ox-36),    // TOP
                r(40,0+oy,4,4).shiftX(ox-40),    // BOTTOM
                r(36,4+oy,4,12).shiftX(ox-36),   // FRONT
                r(44,4+oy,4,12).shiftX(ox-44)    // BACK
        );
    }
    private Rects uvRightLeg(boolean overlay, Image img) {
        int oy = overlay ? 32 : 16;
        return new Rects(img,
                r(0,4+oy,4,12),    // RIGHT (outside)
                r(8,4+oy,4,12),    // LEFT  (inside)
                r(4,0+oy,4,4),     // TOP
                r(8,0+oy,4,4),     // BOTTOM
                r(4,4+oy,4,12),    // FRONT
                r(12,4+oy,4,12)    // BACK
        );
    }
    private Rects uvLeftLeg(boolean overlay, Image img) {
        int ox = overlay ? 0 : 16;
        int oy = 48;
        return new Rects(img,
                r(24,4+oy,4,12).shiftX(ox-24),  // RIGHT (inside)
                r(16,4+oy,4,12).shiftX(ox-16),  // LEFT  (outside)
                r(20,0+oy,4,4).shiftX(ox-20),   // TOP
                r(24,0+oy,4,4).shiftX(ox-24),   // BOTTOM
                r(20,4+oy,4,12).shiftX(ox-20),  // FRONT
                r(28,4+oy,4,12).shiftX(ox-28)   // BACK
        );
    }

    private Rect r(int x, int y, int w, int h){ return new Rect(x,y,w,h); }

    // ---------- geometry ----------
    private MeshView box(float w, float h, float d, Rects uv) {
        float hx=w/2f, hy=h/2f, hz=d/2f;
        TriangleMesh m = new TriangleMesh();

        // 24 vertices: 4 на грань
        // RIGHT
        addQuadVerts(m,  hx,-hy,-hz,  hx,-hy,hz,  hx,hy,hz,  hx,hy,-hz);
        // LEFT
        addQuadVerts(m, -hx,-hy,hz, -hx,-hy,-hz, -hx,hy,-hz, -hx,hy,hz);
        // TOP
        addQuadVerts(m, -hx,-hy,-hz,  hx,-hy,-hz,  hx,-hy,hz, -hx,-hy,hz);
        // BOTTOM
        addQuadVerts(m, -hx,hy,hz,  hx,hy,hz,  hx,hy,-hz, -hx,hy,-hz);
        // FRONT
        addQuadVerts(m, -hx,-hy,hz,  hx,-hy,hz,  hx,hy,hz, -hx,hy,hz);
        // BACK
        addQuadVerts(m,  hx,-hy,-hz, -hx,-hy,-hz, -hx,hy,-hz,  hx,hy,-hz);

        // UV: 4 на грань
        uv.applyToMesh(m);

        // faces: по 2 треугольника на грань, последовательность верш/uv совпадает
        for (int f=0; f<6; f++) {
            int vi = f*4;
            int ti = f*4;
            addFace(m, vi,vi+1,vi+2, ti,ti+1,ti+2);
            addFace(m, vi+2,vi+3,vi, ti+2,ti+3,ti);
        }
        MeshView mv = new MeshView(m);
        mv.setCullFace(CullFace.NONE);

        mv.setDrawMode(DrawMode.FILL);
        return mv;
    }
    private void addQuadVerts(TriangleMesh m,
                              float x0,float y0,float z0,
                              float x1,float y1,float z1,
                              float x2,float y2,float z2,
                              float x3,float y3,float z3) {
        m.getPoints().addAll(x0,y0,z0, x1,y1,z1, x2,y2,z2, x3,y3,z3);
    }
    private void addFace(TriangleMesh m, int v0,int v1,int v2, int t0,int t1,int t2){
        m.getFaces().addAll(v0,t0, v1,t1, v2,t2);
    }

    // ---------- helpers ----------
    private static final class Rect {
        int x,y,w,h;
        Rect(int x,int y,int w,int h){ this.x=x; this.y=y; this.w=w; this.h=h; }
        Rect shiftX(int dx){ return new Rect(x+dx,y,w,h); }
    }
    private static final class Rects {
        final Rect[] a = new Rect[6];
        final double iw, ih;
        Rects(Image img, Rect r0,Rect r1,Rect r2,Rect r3,Rect r4,Rect r5){
            this.iw = img.getWidth(); this.ih = img.getHeight();
            a[0]=r0;a[1]=r1;a[2]=r2;a[3]=r3;a[4]=r4;a[5]=r5;
        }
        void applyToMesh(TriangleMesh m){
            // 4 UV на грань (0..1)
            for (Rect r: a) {
                float u0 = (float)(r.x / iw), v0=(float)(r.y / ih);
                float u1 = (float)((r.x + r.w) / iw), v1=(float)((r.y + r.h) / ih);
                // порядок: (u0,v0),(u1,v0),(u1,v1),(u0,v1)
                m.getTexCoords().addAll(u0,v0,  u1,v0,  u1,v1,  u0,v1);
            }
        }
    }

    private static Image upscaleNearest(Image src, int scale) {
        int w = (int) src.getWidth(), h = (int) src.getHeight();
        WritableImage out = new WritableImage(w*scale, h*scale);
        PixelReader pr = src.getPixelReader();
        PixelWriter pw = out.getPixelWriter();
        for (int y=0; y<h; y++)
            for (int x=0; x<w; x++) {
                int argb = pr.getArgb(x,y);
                int ox = x*scale, oy = y*scale;
                for (int dy=0; dy<scale; dy++)
                    for (int dx=0; dx<scale; dx++)
                        pw.setArgb(ox+dx, oy+dy, argb);
            }
        return out;
    }

}
