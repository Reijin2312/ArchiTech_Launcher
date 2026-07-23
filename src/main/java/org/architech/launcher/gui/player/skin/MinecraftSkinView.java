// SPDX-FileCopyrightText: 2026 Raijin2312
// SPDX-License-Identifier: GPL-3.0-only

package org.architech.launcher.gui.player.skin;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;
import javafx.scene.AmbientLight;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import org.architech.launcher.gui.player.skin.animation.AnimationPose;
import org.architech.launcher.gui.player.skin.animation.BoneTransform;
import org.architech.launcher.gui.player.skin.animation.SkinAnimationDirector;
import org.architech.launcher.gui.player.skin.animation.SkinBone;
import org.architech.launcher.utils.logging.LogManager;

/**
 * Native JavaFX Minecraft skin viewer with a transparent background.
 *
 * <p>The model proportions, UV atlas layout, joint placement, classic/slim
 * detection, and base animation curves are adapted from
 * bs-community/skinview3d and skinview-utils (MIT). Rendering remains entirely
 * native JavaFX; no browser or WebGL runtime is embedded.
 */
public final class MinecraftSkinView extends StackPane {
    private static final double MIN_ZOOM = 48.0;
    private static final double MAX_ZOOM = 115.0;

    private final Group world = new Group();
    private final Group player = new Group();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final Rotate worldRotateX = new Rotate(0.0, Rotate.X_AXIS);
    private final Rotate worldRotateY = new Rotate(-25.0, Rotate.Y_AXIS);
    private final StringProperty skinUrl = new SimpleStringProperty();
    private final SkinAnimationDirector animationDirector = new SkinAnimationDirector();
    private final PauseTransition interactionClickDelay = new PauseTransition(javafx.util.Duration.millis(220));

    private final AnimationTimer animationTimer;
    private SkinRig rig;
    private double zoom = 72.0;
    private Point2D dragAnchor;
    private double dragStartX;
    private double dragStartY;
    private boolean dragged;
    private long previousFrame;
    private long loadGeneration;

    public MinecraftSkinView() {
        setStyle("-fx-background-color: transparent;");
        setMinSize(120, 150);
        setPrefSize(250, 290);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setDepthTest(DepthTest.ENABLE);

        SubScene subScene = createSubScene();
        getChildren().add(subScene);
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());

        world.getTransforms().addAll(worldRotateY, worldRotateX);
        world.getChildren().add(player);
        rebuildModel(new SkinTextureProcessor.ProcessedSkin(
                SkinTextureProcessor.createFallbackSkin(), false, 1));

        installMouseControls();
        interactionClickDelay.setOnFinished(event -> animationDirector.triggerInteraction());
        skinUrl.addListener((observable, oldValue, newValue) -> loadSkin(newValue));

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (previousFrame == 0L) {
                    previousFrame = now;
                    return;
                }
                double deltaSeconds = Math.min(0.05, (now - previousFrame) / 1_000_000_000.0);
                previousFrame = now;
                applyAnimation(animationDirector.update(deltaSeconds));
            }
        };

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            previousFrame = 0L;
            if (newScene == null) {
                animationTimer.stop();
            } else {
                animationTimer.start();
            }
        });
    }

    public MinecraftSkinView(String url) {
        this();
        setSkinUrl(url);
    }

    public String getSkinUrl() {
        return skinUrl.get();
    }

    public void setSkinUrl(String url) {
        if (Platform.isFxApplicationThread()) {
            skinUrl.set(url);
        } else {
            Platform.runLater(() -> skinUrl.set(url));
        }
    }

    public StringProperty skinUrlProperty() {
        return skinUrl;
    }

    public void wave() {
        animationDirector.triggerGreeting();
    }

    public void resetView() {
        worldRotateX.setAngle(0.0);
        worldRotateY.setAngle(-25.0);
        zoom = 72.0;
        camera.setTranslateZ(-zoom);
    }

    private SubScene createSubScene() {
        camera.setNearClip(0.1);
        camera.setFarClip(1_000.0);
        camera.setFieldOfView(30.0);
        camera.setTranslateZ(-zoom);

        AmbientLight light = new AmbientLight(Color.rgb(245, 245, 245));
        world.getChildren().add(light);

        SubScene result = new SubScene(world, 250, 290, true, SceneAntialiasing.BALANCED);
        result.setFill(Color.TRANSPARENT);
        result.setCamera(camera);
        result.setDepthTest(DepthTest.ENABLE);
        return result;
    }

    private void installMouseControls() {
        setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            dragAnchor = new Point2D(event.getSceneX(), event.getSceneY());
            dragStartX = worldRotateX.getAngle();
            dragStartY = worldRotateY.getAngle();
            dragged = false;
            setCursor(javafx.scene.Cursor.CLOSED_HAND);
            event.consume();
        });

        setOnMouseDragged(event -> {
            if (dragAnchor == null || !event.isPrimaryButtonDown()) {
                return;
            }
            if (dragAnchor.distance(event.getSceneX(), event.getSceneY()) > 3.0) {
                dragged = true;
            }
            worldRotateY.setAngle(dragStartY + (event.getSceneX() - dragAnchor.getX()) * 0.55);
            worldRotateX.setAngle(clamp(
                    dragStartX - (event.getSceneY() - dragAnchor.getY()) * 0.38,
                    -35.0,
                    30.0));
            event.consume();
        });

        setOnMouseReleased(event -> {
            dragAnchor = null;
            setCursor(javafx.scene.Cursor.OPEN_HAND);
        });
        setOnMouseEntered(event -> setCursor(javafx.scene.Cursor.OPEN_HAND));
        setOnMouseExited(event -> {
            dragAnchor = null;
            setCursor(javafx.scene.Cursor.DEFAULT);
        });
        setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || dragged) {
                return;
            }
            if (event.getClickCount() == 2) {
                interactionClickDelay.stop();
                resetView();
            } else if (event.getClickCount() == 1) {
                interactionClickDelay.playFromStart();
            }
        });
        setOnScroll(event -> {
            zoom = clamp(zoom - event.getDeltaY() * 0.06, MIN_ZOOM, MAX_ZOOM);
            camera.setTranslateZ(-zoom);
            event.consume();
        });
    }

    private void loadSkin(String url) {
        long generation = ++loadGeneration;
        if (url == null || url.isBlank()) {
            rebuildModel(new SkinTextureProcessor.ProcessedSkin(
                    SkinTextureProcessor.createFallbackSkin(), false, 1));
            return;
        }

        try {
            Image image = new Image(url, true);
            image.progressProperty().addListener((observable, oldValue, newValue) -> {
                if (generation != loadGeneration || newValue.doubleValue() < 1.0) {
                    return;
                }
                if (image.isError()) {
                    logLoadFailure(url, image.getException());
                    return;
                }
                applyLoadedSkin(image, url, generation);
            });
            image.errorProperty().addListener((observable, oldValue, failed) -> {
                if (generation == loadGeneration && failed) {
                    logLoadFailure(url, image.getException());
                }
            });
            if (image.getProgress() >= 1.0) {
                if (image.isError()) {
                    logLoadFailure(url, image.getException());
                } else {
                    applyLoadedSkin(image, url, generation);
                }
            }
        } catch (RuntimeException error) {
            logLoadFailure(url, error);
        }
    }

    private void applyLoadedSkin(Image image, String url, long generation) {
        if (generation != loadGeneration) {
            return;
        }
        try {
            rebuildModel(SkinTextureProcessor.process(image));
            wave();
        } catch (RuntimeException error) {
            logLoadFailure(url, error);
        }
    }

    private void logLoadFailure(String url, Throwable error) {
        String message = error == null ? "unknown image error" : error.getMessage();
        LogManager.getLogger().warning("Не удалось загрузить 3D-скин " + url + ": " + message);
    }

    private void rebuildModel(SkinTextureProcessor.ProcessedSkin skin) {
        player.getChildren().clear();
        rig = new SkinRig(skin.texture(), skin.slim(), skin.atlasScale());
        player.getChildren().add(rig.root);
        // The model is slightly bottom-heavy (the leg overlay reaches farther
        // from the origin than the head overlay). Keep an explicit lower
        // viewport inset so feet stay visible at the default camera angle.
        player.setTranslateY(-1);
        animationDirector.reset();
        applyAnimation(animationDirector.update(0.0));
    }

    private void applyAnimation(AnimationPose pose) {
        if (rig == null) {
            return;
        }

        rig.resetPose();
        rig.root.apply(pose.bone(SkinBone.BODY));
        rig.head.apply(pose.bone(SkinBone.HEAD));
        rig.body.apply(pose.bone(SkinBone.TORSO));
        rig.rightArm.apply(pose.bone(SkinBone.RIGHT_ARM));
        rig.leftArm.apply(pose.bone(SkinBone.LEFT_ARM));
        rig.rightLeg.apply(pose.bone(SkinBone.RIGHT_LEG));
        rig.leftLeg.apply(pose.bone(SkinBone.LEFT_LEG));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SkinRig {
        private final Joint root = new Joint(0.0, 0.0, 0.0);
        private final Joint head;
        private final Joint body;
        private final Joint rightArm;
        private final Joint leftArm;
        private final Joint rightLeg;
        private final Joint leftLeg;

        private SkinRig(Image texture, boolean slim, int atlasScale) {
            PhongMaterial innerMaterial = material(texture);
            PhongMaterial outerMaterial = material(texture);

            // The standalone viewer uses an actual connected skeleton.  The torso
            // pivots at the hip; the head and arms are local children of it.  This
            // keeps adjoining parts connected when an Emotecraft clip rotates or
            // translates the torso (notably waving and backflip).
            body = new Joint(0.0, 6.0, 0.0);
            body.addLayers(
                    cuboid(8, 12.25, 4, new AtlasBox(16, 16, 8, 12, 4), innerMaterial, false, atlasScale),
                    cuboid(8.5, 12.75, 4.5, new AtlasBox(16, 32, 8, 12, 4), outerMaterial, true, atlasScale),
                    0.0,
                    -6.0,
                    0.0);

            head = new Joint(0.0, -12.0, 0.0);
            head.addLayers(
                    cuboid(8, 8, 8, new AtlasBox(0, 0, 8, 8, 8), innerMaterial, false, atlasScale),
                    cuboid(9, 9, 9, new AtlasBox(32, 0, 8, 8, 8), outerMaterial, true, atlasScale),
                    0.0,
                    -4.0,
                    0.0);

            double armWidth = slim ? 3.0 : 4.0;
            double armCenterOffset = slim ? 0.5 : 1.0;

            rightArm = new Joint(-5.0, -10.0, 0.0);
            rightArm.addLayers(
                    cuboid(armWidth, 12, 4, new AtlasBox(40, 16, armWidth, 12, 4), innerMaterial, false, atlasScale),
                    cuboid(
                            armWidth + 0.5,
                            12.5,
                            4.5,
                            new AtlasBox(40, 32, armWidth, 12, 4),
                            outerMaterial,
                            true,
                            atlasScale),
                    -armCenterOffset,
                    4.0,
                    0.0);

            leftArm = new Joint(5.0, -10.0, 0.0);
            leftArm.addLayers(
                    cuboid(armWidth, 12, 4, new AtlasBox(32, 48, armWidth, 12, 4), innerMaterial, false, atlasScale),
                    cuboid(
                            armWidth + 0.5,
                            12.5,
                            4.5,
                            new AtlasBox(48, 48, armWidth, 12, 4),
                            outerMaterial,
                            true,
                            atlasScale),
                    armCenterOffset,
                    4.0,
                    0.0);

            rightLeg = new Joint(-1.9, 0.0, -0.1);
            rightLeg.addLayers(
                    cuboid(4, 12.25, 4, new AtlasBox(0, 16, 4, 12, 4), innerMaterial, false, atlasScale),
                    cuboid(4.5, 12.75, 4.5, new AtlasBox(0, 32, 4, 12, 4), outerMaterial, true, atlasScale),
                    0.0,
                    6.0,
                    0.0);

            leftLeg = new Joint(1.9, 0.0, -0.1);
            leftLeg.addLayers(
                    cuboid(4, 12.25, 4, new AtlasBox(16, 48, 4, 12, 4), innerMaterial, false, atlasScale),
                    cuboid(4.5, 12.75, 4.5, new AtlasBox(0, 48, 4, 12, 4), outerMaterial, true, atlasScale),
                    0.0,
                    6.0,
                    0.0);

            body.getChildren().addAll(head, rightArm, leftArm, rightLeg, leftLeg);
            root.getChildren().add(body);
        }

        private void resetPose() {
            root.resetTransform();
            head.resetTransform();
            body.resetTransform();
            rightArm.resetTransform();
            leftArm.resetTransform();
            rightLeg.resetTransform();
            leftLeg.resetTransform();
        }

        private static PhongMaterial material(Image texture) {
            PhongMaterial result = new PhongMaterial(Color.WHITE);
            result.setDiffuseMap(texture);
            result.setSpecularColor(Color.BLACK);
            result.setSpecularPower(1.0);
            return result;
        }
    }

    private static final class Joint extends Group {
        private final Rotate rotateX = new Rotate(0.0, Rotate.X_AXIS);
        private final Rotate rotateY = new Rotate(0.0, Rotate.Y_AXIS);
        private final Rotate rotateZ = new Rotate(0.0, Rotate.Z_AXIS);
        private final double baseX;
        private final double baseY;
        private final double baseZ;

        private Joint(double x, double y, double z) {
            baseX = x;
            baseY = y;
            baseZ = z;
            setTranslateX(x);
            setTranslateY(y);
            setTranslateZ(z);
            getTransforms().addAll(rotateZ, rotateY, rotateX);
        }

        private void addLayers(
                MeshView inner,
                MeshView outer,
                double offsetX,
                double offsetY,
                double offsetZ) {
            Group layers = new Group(inner, outer);
            layers.setTranslateX(offsetX);
            layers.setTranslateY(offsetY);
            layers.setTranslateZ(offsetZ);
            getChildren().add(layers);
        }

        private void resetTransform() {
            setTranslateX(baseX);
            setTranslateY(baseY);
            setTranslateZ(baseZ);
            setAngleX(0.0);
            setAngleY(0.0);
            setAngleZ(0.0);
        }

        private void apply(BoneTransform transform) {
            setTranslateX(baseX + transform.offsetX());
            setTranslateY(baseY + transform.offsetY());
            setTranslateZ(baseZ + transform.offsetZ());
            setAngleX(transform.pitch());
            setAngleY(transform.yaw());
            setAngleZ(transform.roll());
        }

        private void setAngleX(double value) {
            rotateX.setAngle(value);
        }

        private void setAngleY(double value) {
            rotateY.setAngle(value);
        }

        private void setAngleZ(double value) {
            rotateZ.setAngle(value);
        }
    }

    private static MeshView cuboid(
            double width,
            double height,
            double depth,
            AtlasBox atlas,
            PhongMaterial material,
            boolean alphaCutout,
            int atlasScale) {
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        double halfDepth = depth / 2.0;
        TriangleMesh mesh = new TriangleMesh();

        Rect top = atlas.top();
        Rect bottom = atlas.bottom();
        Rect right = atlas.right();
        Rect front = atlas.front();
        Rect left = atlas.left();
        Rect back = atlas.back();

        addSurface(
                mesh,
                point(-halfWidth, -halfHeight, -halfDepth),
                point(halfWidth, -halfHeight, -halfDepth),
                point(halfWidth, halfHeight, -halfDepth),
                point(-halfWidth, halfHeight, -halfDepth),
                front,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);
        addSurface(
                mesh,
                point(halfWidth, -halfHeight, halfDepth),
                point(-halfWidth, -halfHeight, halfDepth),
                point(-halfWidth, halfHeight, halfDepth),
                point(halfWidth, halfHeight, halfDepth),
                back,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);
        addSurface(
                mesh,
                point(-halfWidth, -halfHeight, halfDepth),
                point(-halfWidth, -halfHeight, -halfDepth),
                point(-halfWidth, halfHeight, -halfDepth),
                point(-halfWidth, halfHeight, halfDepth),
                right,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);
        addSurface(
                mesh,
                point(halfWidth, -halfHeight, -halfDepth),
                point(halfWidth, -halfHeight, halfDepth),
                point(halfWidth, halfHeight, halfDepth),
                point(halfWidth, halfHeight, -halfDepth),
                left,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);
        addSurface(
                mesh,
                point(-halfWidth, -halfHeight, halfDepth),
                point(halfWidth, -halfHeight, halfDepth),
                point(halfWidth, -halfHeight, -halfDepth),
                point(-halfWidth, -halfHeight, -halfDepth),
                top,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);
        addSurface(
                mesh,
                point(-halfWidth, halfHeight, -halfDepth),
                point(halfWidth, halfHeight, -halfDepth),
                point(halfWidth, halfHeight, halfDepth),
                point(-halfWidth, halfHeight, halfDepth),
                bottom,
                material.getDiffuseMap(),
                alphaCutout,
                atlasScale);

        if (mesh.getTexCoords().size() == 0) {
            mesh.getTexCoords().addAll(0.0f, 0.0f);
        }

        MeshView result = new MeshView(mesh);
        result.setMaterial(material);
        result.setCullFace(CullFace.NONE);
        return result;
    }

    private static void addSurface(
            TriangleMesh mesh,
            float[] topLeft,
            float[] topRight,
            float[] bottomRight,
            float[] bottomLeft,
            Rect textureArea,
            Image texture,
            boolean alphaCutout,
            int atlasScale) {
        if (!alphaCutout) {
            addQuad(mesh, topLeft, topRight, bottomRight, bottomLeft, textureArea);
            return;
        }

        int columns = Math.max(
                1,
                (int) Math.round((textureArea.u1() - textureArea.u0()) * 64.0 * atlasScale));
        int rows = Math.max(
                1,
                (int) Math.round((textureArea.v1() - textureArea.v0()) * 64.0 * atlasScale));
        double cellU = (textureArea.u1() - textureArea.u0()) / columns;
        double cellV = (textureArea.v1() - textureArea.v0()) / rows;
        double insetU = 0.5 / texture.getWidth();
        double insetV = 0.5 / texture.getHeight();
        PixelReader pixels = texture.getPixelReader();

        for (int row = 0; row < rows; row++) {
            double y0 = (double) row / rows;
            double y1 = (double) (row + 1) / rows;
            for (int column = 0; column < columns; column++) {
                double u0 = textureArea.u0() + column * cellU;
                double v0 = textureArea.v0() + row * cellV;
                int sampleX = clampPixel((u0 + cellU * 0.5) * texture.getWidth(), texture.getWidth());
                int sampleY = clampPixel((v0 + cellV * 0.5) * texture.getHeight(), texture.getHeight());
                if ((pixels.getArgb(sampleX, sampleY) >>> 24) == 0) {
                    continue;
                }

                double x0 = (double) column / columns;
                double x1 = (double) (column + 1) / columns;
                addQuad(
                        mesh,
                        interpolate(topLeft, topRight, bottomRight, bottomLeft, x0, y0),
                        interpolate(topLeft, topRight, bottomRight, bottomLeft, x1, y0),
                        interpolate(topLeft, topRight, bottomRight, bottomLeft, x1, y1),
                        interpolate(topLeft, topRight, bottomRight, bottomLeft, x0, y1),
                        new Rect(
                                (float) (u0 + insetU),
                                (float) (v0 + insetV),
                                (float) (u0 + cellU - insetU),
                                (float) (v0 + cellV - insetV)));
            }
        }
    }

    private static int clampPixel(double coordinate, double size) {
        return Math.max(0, Math.min((int) size - 1, (int) coordinate));
    }

    private static float[] interpolate(
            float[] topLeft,
            float[] topRight,
            float[] bottomRight,
            float[] bottomLeft,
            double x,
            double y) {
        float[] result = new float[3];
        for (int axis = 0; axis < result.length; axis++) {
            double top = topLeft[axis] + (topRight[axis] - topLeft[axis]) * x;
            double bottom = bottomLeft[axis] + (bottomRight[axis] - bottomLeft[axis]) * x;
            result[axis] = (float) (top + (bottom - top) * y);
        }
        return result;
    }

    private static void addQuad(
            TriangleMesh mesh,
            float[] topLeft,
            float[] topRight,
            float[] bottomRight,
            float[] bottomLeft,
            Rect texture) {
        int pointOffset = mesh.getPoints().size() / 3;
        int textureOffset = mesh.getTexCoords().size() / 2;

        mesh.getPoints().addAll(
                topLeft[0], topLeft[1], topLeft[2],
                topRight[0], topRight[1], topRight[2],
                bottomRight[0], bottomRight[1], bottomRight[2],
                bottomLeft[0], bottomLeft[1], bottomLeft[2]);
        mesh.getTexCoords().addAll(
                texture.u0(), texture.v0(),
                texture.u1(), texture.v0(),
                texture.u1(), texture.v1(),
                texture.u0(), texture.v1());

        mesh.getFaces().addAll(
                pointOffset, textureOffset,
                pointOffset + 3, textureOffset + 3,
                pointOffset + 2, textureOffset + 2,
                pointOffset + 2, textureOffset + 2,
                pointOffset + 1, textureOffset + 1,
                pointOffset, textureOffset);
    }

    private static float[] point(double x, double y, double z) {
        return new float[] {(float) x, (float) y, (float) z};
    }

    private record AtlasBox(double u, double v, double width, double height, double depth) {
        private Rect top() {
            return rect(u + depth, v, width, depth);
        }

        private Rect bottom() {
            return rect(u + width + depth, v, width, depth);
        }

        private Rect right() {
            return rect(u, v + depth, depth, height);
        }

        private Rect front() {
            return rect(u + depth, v + depth, width, height);
        }

        private Rect left() {
            return rect(u + width + depth, v + depth, depth, height);
        }

        private Rect back() {
            return rect(u + width + depth * 2.0, v + depth, width, height);
        }

        private static Rect rect(double x, double y, double width, double height) {
            return new Rect(
                    (float) (x / 64.0),
                    (float) (y / 64.0),
                    (float) ((x + width) / 64.0),
                    (float) ((y + height) / 64.0));
        }
    }

    private record Rect(float u0, float v0, float u1, float v1) {}
}
